import 'package:flutter/material.dart';

import '../../core/api/api_client.dart';
import '../marketplace/marketplace.dart';
import '../marketplace/market_widgets.dart';
import 'review_screen.dart';
import 'reviews.dart';

class ProviderReviewsScreen extends StatefulWidget {
  const ProviderReviewsScreen({
    super.key,
    required this.api,
    required this.providerId,
    required this.providerName,
  });
  final ApiClient api;
  final String providerId, providerName;
  @override
  State<ProviderReviewsScreen> createState() => _ProviderReviewsScreenState();
}

class _ProviderReviewsScreenState extends State<ProviderReviewsScreen> {
  ProviderReviews? _reviews;
  int _page = 0;
  bool _loading = true;
  String? _error;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = ProviderReviews.fromJson(
        await widget.api.get('/api/v1/providers/${widget.providerId}/reviews', {
          'page': '$_page',
          'size': '20',
        }) as Json,
      );
      if (mounted) setState(() => _reviews = result);
    } catch (e) {
      if (mounted) {
        setState(() => _error = 'We couldn’t load reviews. Please try again.');
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Provider reviews'),
      actions: [
        IconButton(
          tooltip: 'Refresh reviews',
          onPressed: _loading ? null : _load,
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 700),
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Text(
              widget.providerName,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 16),
            if (_loading)
              const LinearProgressIndicator()
            else if (_error != null)
              MarketError(_error!, _load)
            else if (_reviews != null) ...[
              Text(
                _reviews!.total == 0
                    ? 'No reviews yet.'
                    : '${_reviews!.average} / 5 · ${_reviews!.total} ${_reviews!.total == 1 ? 'review' : 'reviews'}',
              ),
              for (final review in _reviews!.items) ReviewCard(review: review),
              if (_reviews!.total > 20)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    TextButton(
                      onPressed: _page == 0
                          ? null
                          : () {
                              _page--;
                              _load();
                            },
                      child: const Text('Previous'),
                    ),
                    Text('Page ${_page + 1}'),
                    TextButton(
                      onPressed: (_page + 1) * 20 >= _reviews!.total
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
  );
}
