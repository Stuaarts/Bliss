import 'package:flutter/material.dart';

import '../../core/auth/auth_session.dart';
import 'provider_forms.dart';

class ProviderScreen extends StatefulWidget {
  const ProviderScreen({super.key, required this.session});
  final AuthSession session;
  @override
  State<ProviderScreen> createState() => _ProviderScreenState();
}

class _ProviderScreenState extends State<ProviderScreen> {
  List<Map<String, dynamic>> _memberships = [];
  List<Map<String, dynamic>> _offerings = [];
  Map<String, dynamic>? _provider;
  String? _selected;
  String? _error;
  bool _busy = false;
  int _page = 0;
  int _total = 0;
  String get _path => '/api/v1/providers/me/$_selected';
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await action();
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = e is AccountRequestException
              ? e.message
              : 'Could not connect. Please try again.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _load() => _run(_fetch);
  Future<void> _fetch() async {
    final memberships =
        (await widget.session.request('GET', '/api/v1/providers/me') as List)
            .cast<Map<String, dynamic>>()
            .where((m) => m['role'] == 'OWNER')
            .toList();
    final selected = memberships.any((m) => m['providerId'] == _selected)
        ? _selected
        : memberships.firstOrNull?['providerId'] as String?;
    Map<String, dynamic>? provider;
    Map<String, dynamic>? offerings;
    if (selected != null) {
      provider = await widget.session.request(
        'GET',
        '/api/v1/providers/me/$selected',
      ) as Map<String, dynamic>;
      offerings = await widget.session.request(
        'GET',
        '/api/v1/providers/me/$selected/offerings?page=$_page&size=10',
      ) as Map<String, dynamic>;
    }
    if (!mounted) return;
    setState(() {
      _memberships = memberships;
      _selected = selected;
      _provider = provider;
      _offerings = (offerings?['items'] as List? ?? [])
          .cast<Map<String, dynamic>>();
      _total = offerings?['total'] as int? ?? 0;
    });
  }

  Future<void> _editProvider({bool create = false}) async {
    final body = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (_) => ProviderForm(initial: create ? null : _provider),
    );
    if (body == null || !mounted) return;
    await _run(() async {
      final result = await widget.session.request(
        create ? 'POST' : 'PUT',
        create ? '/api/v1/providers/me' : _path,
        body,
      ) as Map<String, dynamic>;
      _selected = result['id'] as String;
      _page = 0;
      await _fetch();
    });
  }

  Future<void> _editOffering([Map<String, dynamic>? offering]) async {
    await _run(() async {
      final categories = (await widget.session.request(
        'GET',
        '/api/v1/categories',
      ) as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      final body = await showDialog<Map<String, dynamic>>(
        context: context,
        builder: (_) => OfferingForm(categories: categories, initial: offering),
      );
      if (body == null) return;
      await widget.session.request(
        offering == null ? 'POST' : 'PUT',
        '$_path/offerings${offering == null ? '' : '/${offering['id']}'}',
        offering == null
            ? body
            : {'offering': body, 'version': offering['version']},
      );
      _page = 0;
      await _fetch();
    });
  }

  Future<void> _archive(Map<String, dynamic> offering) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Archive this service?'),
        content: Text(
          '${offering['title']} will no longer appear in discovery. Its history is kept.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Archive'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await _run(() async {
      await widget.session.request(
        'DELETE',
        '$_path/offerings/${offering['id']}?version=${offering['version']}',
      );
      await _fetch();
    });
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.session,
    builder: (context, _) => Scaffold(
      appBar: AppBar(
        title: const Text('Your business'),
        actions: [
          IconButton(
            onPressed: _busy ? null : _load,
            tooltip: 'Refresh business',
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: !widget.session.signedIn
          ? const Center(
              child: Text('Your session ended. Go back to sign in again.'),
            )
          : Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 760),
                child: ListView(
                  padding: const EdgeInsets.all(24),
                  children: [
                    if (_busy) const LinearProgressIndicator(),
                    if (_error != null)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        child: Text(_error!),
                      ),
                    if (_memberships.isEmpty && !_busy)
                      const Text(
                        'Set up your individual or company profile to offer services.',
                      ),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: TextButton.icon(
                        onPressed: _busy
                            ? null
                            : () => _editProvider(create: true),
                        icon: const Icon(Icons.add_business),
                        label: const Text('Add a business'),
                      ),
                    ),
                    if (_provider != null) ...[
                      DropdownButtonFormField<String>(
                        isExpanded: true,
                        key: ValueKey(_selected),
                        initialValue: _selected,
                        decoration: const InputDecoration(
                          labelText: 'Business',
                        ),
                        items: [
                          for (final m in _memberships)
                            DropdownMenuItem(
                              value: m['providerId'] as String,
                              child: Text(m['name'] as String),
                            ),
                        ],
                        onChanged: _busy
                            ? null
                            : (id) {
                                _selected = id;
                                _page = 0;
                                _load();
                              },
                      ),
                      const SizedBox(height: 16),
                      Text(
                        '${_provider!['city']} · ${_provider!['type'] == 'COMPANY' ? 'Company' : 'Individual'}',
                      ),
                      Text(_provider!['description'] as String),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: TextButton(
                          onPressed: _busy ? null : _editProvider,
                          child: const Text('Edit business profile'),
                        ),
                      ),
                      const Divider(),
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              'Your services',
                              style: Theme.of(context).textTheme.titleLarge,
                            ),
                          ),
                          FilledButton.icon(
                            onPressed: _busy ? null : _editOffering,
                            icon: const Icon(Icons.add),
                            label: const Text('Add service'),
                          ),
                        ],
                      ),
                      if (_offerings.isEmpty && !_busy)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 24),
                          child: Text(
                            'No services yet. Add your first offering.',
                          ),
                        ),
                      for (final o in _offerings)
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  o['title'] as String,
                                  style: Theme.of(context)
                                      .textTheme
                                      .titleMedium,
                                ),
                                Text(
                                  '${o['currency']} ${o['price']} · ${o['active'] == true ? 'Published' : 'Archived'}',
                                ),
                                Text(o['description'] as String),
                                if (o['active'] == true)
                                  Wrap(
                                    children: [
                                      TextButton(
                                        onPressed: _busy
                                            ? null
                                            : () => _editOffering(o),
                                        child: const Text('Edit'),
                                      ),
                                      TextButton(
                                        onPressed: _busy
                                            ? null
                                            : () => _archive(o),
                                        child: const Text('Archive'),
                                      ),
                                    ],
                                  ),
                              ],
                            ),
                          ),
                        ),
                      Wrap(
                        alignment: WrapAlignment.spaceBetween,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        spacing: 12,
                        children: [
                          TextButton(
                            onPressed: _busy || _page == 0
                                ? null
                                : () {
                                    _page--;
                                    _load();
                                  },
                            child: const Text('Previous'),
                          ),
                          Text('Page ${_page + 1} · $_total services'),
                          TextButton(
                            onPressed: _busy || (_page + 1) * 10 >= _total
                                ? null
                                : () {
                                    _page++;
                                    _load();
                                  },
                            child: const Text('Next'),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ),
    ),
  );
}
