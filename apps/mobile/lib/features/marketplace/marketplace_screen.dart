import 'package:flutter/material.dart';

import '../../core/auth/auth_session.dart';
import 'marketplace.dart';
import 'market_widgets.dart';
import 'booking_screen.dart';
import 'request_screen.dart';
import '../conversation/conversation.dart';
import '../conversation/chat_screen.dart';
import '../dispute/dispute.dart';
import '../dispute/dispute_screen.dart';

class MarketplaceScreen extends StatefulWidget {
  const MarketplaceScreen({super.key, required this.session});
  final AuthSession session;
  @override
  State<MarketplaceScreen> createState() => _MarketplaceScreenState();
}

class _MarketplaceScreenState extends State<MarketplaceScreen> {
  late final _repo = MarketplaceRepository(widget.session);
  String _section = 'Bookings';
  String? _providerId, _error;
  List<Json> _providers = [];
  List<Object> _items = [];
  int _page = 0, _total = 0, _request = 0;
  bool _loading = false;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final request = ++_request;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final providers = widget.session.capabilities.contains('provider')
          ? await _repo.providers()
          : <Json>[];
      if (!mounted || request != _request) return;
      final providerId = providers.any((p) => p['providerId'] == _providerId)
          ? _providerId
          : providers.firstOrNull?['providerId'] as String?;
      final MarketPage<Object> result;
      if (_section == 'Bookings') {
        result = await _repo.bookings(_page);
      } else if (_section == 'Messages') {
        result = MarketPage.read(
          await _repo.get('conversations?page=$_page&size=20'),
          ConversationSummary.fromJson,
        );
      } else if (_section == 'Disputes') {
        result = MarketPage.read(
          await _repo.get('disputes?page=$_page&size=20'),
          DisputeSummary.fromJson,
        );
      } else if (_section == 'My quotes') {
        result = providerId == null
            ? const MarketPage([], 0)
            : await _repo.quotes('providers/me/$providerId/quotes', _page);
      } else {
        result = await _repo.requests(
          _page,
          opportunities: _section == 'Open opportunities',
        );
      }
      if (mounted && request == _request) {
        setState(() {
          _providers = providers;
          _providerId = providerId;
          _items = result.items;
          _total = result.total;
        });
      }
    } catch (e) {
      if (mounted && request == _request) {
        setState(() {
          _error = marketError(e);
          _items = [];
        });
      }
    } finally {
      if (mounted && request == _request) setState(() => _loading = false);
    }
  }

  Future<void> _create() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final categories = await _repo.categories();
      if (!mounted) return;
      final result = await privatePage<Object?>(
        context,
        widget.session,
        MarketForm(
          title: 'Create request',
          fields: [
            const FieldSpec('title', 'Request title', limit: 160),
            const FieldSpec('description', 'Describe the work', limit: 2000),
            const FieldSpec('city', 'Service city'),
            FieldSpec(
              'categoryId',
              'Category',
              options: {
                for (final c in categories)
                  c['id'] as String: c['name'] as String,
              },
            ),
            const FieldSpec(
              'currency',
              'Currency',
              initial: 'CAD',
              options: {'CAD': 'CAD', 'USD': 'USD', 'EUR': 'EUR', 'GBP': 'GBP'},
            ),
            const FieldSpec(
              'budget',
              'Budget (optional)',
              money: true,
              optional: true,
              limit: 13,
            ),
          ],
          submit: (values) => widget.session.request(
            'POST',
            '/api/v1/service-requests',
            {...values, if (values['budget']!.isEmpty) 'budget': null},
          ),
        ),
      );
      if (!mounted) return;
      if (result is Json) {
        await privatePage<void>(
          context,
          widget.session,
          RequestScreen(
            session: widget.session,
            requestId: result['id'] as String,
          ),
        );
      }
      if (mounted) await _load();
    } catch (e) {
      if (mounted) setState(() => _error = marketError(e));
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _open(Object item) async {
    if (item is DisputeSummary) {
      await privatePage<void>(
        context,
        widget.session,
        DisputeScreen(session: widget.session, disputeId: item.id),
      );
    }
    if (item is ConversationSummary && mounted) {
      await privatePage<void>(
        context,
        widget.session,
        ChatScreen(session: widget.session, conversationId: item.id),
      );
    }
    if (item is Booking && mounted) {
      await privatePage<void>(
        context,
        widget.session,
        BookingScreen(session: widget.session, bookingId: item.id),
      );
    }
    if (item is ServiceRequest && mounted) {
      await privatePage<void>(
        context,
        widget.session,
        RequestScreen(
          session: widget.session,
          requestId: item.id,
          opportunity: _section == 'Open opportunities',
        ),
      );
    }
    if (mounted) _load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Your activity'),
      actions: [
        IconButton(
          tooltip: 'Refresh activity',
          onPressed: _loading ? null : _load,
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: MarketBody(
      children: [
        Text(
          'Good help starts here.',
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final section in [
              'Bookings',
              'Messages',
              'Disputes',
              if (widget.session.capabilities.contains('customer'))
                'My requests',
              if (widget.session.capabilities.contains('provider')) ...[
                'Open opportunities',
                'My quotes',
              ],
            ])
              ChoiceChip(
                label: Text(section),
                selected: _section == section,
                onSelected: _loading
                    ? null
                    : (_) {
                        setState(() {
                          _section = section;
                          _page = 0;
                          _items = [];
                        });
                        _load();
                      },
              ),
          ],
        ),
        const SizedBox(height: 16),
        if (_section == 'My requests')
          FilledButton.icon(
            onPressed: _loading ? null : _create,
            icon: const Icon(Icons.add),
            label: const Text('Create request'),
          ),
        if (_section == 'My quotes' && _providers.isNotEmpty)
          DropdownButton<String>(
            value: _providerId,
            isExpanded: true,
            items: [
              for (final p in _providers)
                DropdownMenuItem(
                  value: p['providerId'] as String,
                  child: Text(p['name'] as String),
                ),
            ],
            onChanged: _loading
                ? null
                : (id) {
                    setState(() {
                      _providerId = id;
                      _page = 0;
                    });
                    _load();
                  },
          ),
        if (_loading) const LinearProgressIndicator(),
        if (_error != null) MarketError(_error!, _loading ? null : _load),
        if (!_loading && _error == null && _items.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 32),
            child: Text(
              _section == 'Open opportunities'
                  ? 'No open requests right now. Check back soon.'
                  : 'Nothing here yet. Your activity will appear here.',
            ),
          ),
        for (final item in _items)
          Card(
            child: switch (item) {
              ConversationSummary c => ListTile(
                title: Text(c.title),
                subtitle: const Text('Booking conversation'),
                leading: const Icon(Icons.chat_bubble_outline),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => _open(c),
              ),
              DisputeSummary d => ListTile(
                title: Text(d.title),
                subtitle: Text(disputeStatus(d.status)),
                leading: const Icon(Icons.help_outline),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => _open(d),
              ),
              Booking b => ListTile(
                title: Text(b.title),
                subtitle: Text(
                  '${b.status == 'WAIT_CUSTOMER' && _providers.any((p) => p['providerId'] == b.providerId) ? 'Waiting for customer' : statusLabel(b.status)} · ${b.city}\n${b.currency} ${b.price}',
                ),
                isThreeLine: true,
                trailing: const Icon(Icons.chevron_right),
                onTap: () => _open(b),
              ),
              ServiceRequest r => ListTile(
                title: Text(r.title),
                subtitle: Text(
                  '${statusLabel(r.status)} · ${r.city}\n${r.budget == null ? 'Budget open' : '${r.currency} ${r.budget}'}',
                ),
                isThreeLine: true,
                trailing: const Icon(Icons.chevron_right),
                onTap: () => _open(r),
              ),
              Quote q => Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(q.description),
                    Text('${q.currency} ${q.price} · ${statusLabel(q.status)}'),
                    Text('Valid until ${localTime(context, q.expiresAt)}'),
                  ],
                ),
              ),
              _ => const SizedBox.shrink(),
            },
          ),
        Pager(
          page: _page,
          total: _total,
          change: _loading
              ? null
              : (page) {
                  setState(() => _page = page);
                  _load();
                },
        ),
      ],
    ),
  );
}
