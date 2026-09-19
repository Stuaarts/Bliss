import 'package:flutter/material.dart';

import '../../core/api/api_client.dart';
import 'catalog.dart';

class DiscoveryScreen extends StatefulWidget {
  const DiscoveryScreen({super.key, required this.catalog});
  final CatalogRepository catalog;

  @override
  State<DiscoveryScreen> createState() => _DiscoveryScreenState();
}

class _DiscoveryScreenState extends State<DiscoveryScreen> {
  List<Category> _categories = [];
  List<Offering> _offerings = [];
  String? _categoryId;
  String? _error;
  bool _loading = true;
  int _page = 0;
  int _total = 0;
  int _request = 0;

  @override
  void initState() {
    super.initState();
    _load(reloadCategories: true);
  }

  Future<void> _load({bool reloadCategories = false}) async {
    final request = ++_request;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final categories = reloadCategories || _categories.isEmpty
          ? await widget.catalog.categories()
          : _categories;
      final result = await widget.catalog.offerings(
        categoryId: _categoryId,
        page: _page,
      );
      if (!mounted || request != _request) return;
      setState(() {
        _categories = categories;
        _offerings = result.items;
        _total = result.total;
        _loading = false;
      });
    } catch (error) {
      if (!mounted || request != _request) return;
      setState(() {
        _loading = false;
        _error = error is ApiException
            ? error.message
            : 'Something went wrong. Please try again.';
      });
    }
  }

  void _selectCategory(String? id) {
    setState(() {
      _categoryId = id;
      _page = 0;
    });
    _load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text(
        'bliss',
        style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: -1),
      ),
      actions: [
        IconButton(
          tooltip: 'Refresh services',
          onPressed: _loading ? null : () => _load(reloadCategories: true),
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 900),
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Text(
              'A little help.\nA better everyday.',
              style: Theme.of(context).textTheme.headlineLarge
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            Text(
              'Discover local people who can take care of the details.',
              style: Theme.of(context).textTheme.bodyLarge,
            ),
            const SizedBox(height: 28),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ChoiceChip(
                  label: const Text('All services'),
                  selected: _categoryId == null,
                  onSelected: (_) => _selectCategory(null),
                ),
                for (final category in _categories)
                  ChoiceChip(
                    label: Text(category.name),
                    selected: _categoryId == category.id,
                    onSelected: (_) => _selectCategory(category.id),
                  ),
              ],
            ),
            const SizedBox(height: 28),
            if (_loading)
              const Padding(
                padding: EdgeInsets.all(48),
                child: Center(
                  child: CircularProgressIndicator(
                    semanticsLabel: 'Loading services',
                  ),
                ),
              )
            else if (_error != null)
              _Message(
                icon: Icons.cloud_off_outlined,
                title: 'Let’s try that again',
                message: _error!,
                action: FilledButton(
                  onPressed: () => _load(reloadCategories: true),
                  child: const Text('Try again'),
                ),
              )
            else if (_offerings.isEmpty)
              const _Message(
                icon: Icons.spa_outlined,
                title: 'More help is on the way',
                message:
                    'No services in this category yet. Try another category.',
              )
            else ...[
              Text(
                '$_total services to explore',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 12),
              for (final offering in _offerings)
                _OfferingCard(offering: offering),
              if (_total > 20)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    TextButton(
                      onPressed: _page == 0
                          ? null
                          : () {
                              setState(() => _page--);
                              _load();
                            },
                      child: const Text('Previous'),
                    ),
                    Text('Page ${_page + 1}'),
                    TextButton(
                      onPressed: (_page + 1) * 20 >= _total
                          ? null
                          : () {
                              setState(() => _page++);
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
  );
}

class _OfferingCard extends StatelessWidget {
  const _OfferingCard({required this.offering});
  final Offering offering;

  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 12),
    child: InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (context) => Scaffold(
            appBar: AppBar(title: Text(offering.category.name)),
            body: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    offering.title,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 12),
                  Text('${offering.providerName} · ${offering.city}'),
                  const SizedBox(height: 24),
                  Text(
                    offering.description,
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                  const SizedBox(height: 24),
                  Text(
                    '${offering.currency} ${offering.price}',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 24),
                  const Text('Booking will be available in a future update.'),
                ],
              ),
            ),
          ),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              offering.category.name.toUpperCase(),
              style: Theme.of(context).textTheme.labelSmall,
            ),
            const SizedBox(height: 8),
            Text(offering.title, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            Text('${offering.providerName} · ${offering.city}'),
            const SizedBox(height: 16),
            Text(
              '${offering.currency} ${offering.price}',
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
          ],
        ),
      ),
    ),
  );
}

class _Message extends StatelessWidget {
  const _Message({
    required this.icon,
    required this.title,
    required this.message,
    this.action,
  });
  final IconData icon;
  final String title;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 32),
    child: Column(
      children: [
        Icon(icon, size: 40),
        const SizedBox(height: 16),
        Text(
          title,
          style: Theme.of(context).textTheme.titleLarge,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(message, textAlign: TextAlign.center),
        if (action != null)
          Padding(padding: const EdgeInsets.only(top: 16), child: action),
      ],
    ),
  );
}
