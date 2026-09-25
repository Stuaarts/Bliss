import 'package:flutter/material.dart';

import '../../core/auth/auth_session.dart';
import '../marketplace/marketplace.dart';
import '../marketplace/market_widgets.dart';
import 'reviews.dart';

class ReviewScreen extends StatefulWidget {
  const ReviewScreen({
    super.key,
    required this.session,
    required this.bookingId,
  });
  final AuthSession session;
  final String bookingId;
  @override
  State<ReviewScreen> createState() => _ReviewScreenState();
}

class _ReviewScreenState extends State<ReviewScreen> {
  late final _path = '/api/v1/bookings/${widget.bookingId}/reviews';
  late final Json? _pending = widget.session.pendingBody(_path) as Json?;
  late int? _rating = _pending?['rating'] as int?;
  late final _comment = TextEditingController(
    text: _pending?['comment'] as String? ?? '',
  );
  bool _loading = true, _saving = false, _eligible = false;
  ServiceReview? _review;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _comment.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final state = await widget.session.request('GET', _path) as Json;
      if (!mounted) return;
      setState(() {
        _eligible = state['eligible'] as bool;
        _review = state['review'] == null
            ? null
            : ServiceReview.fromJson(state['review'] as Json);
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = marketError(e);
          _eligible = false;
          _review = null;
        });
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _submit() async {
    if (_rating == null) {
      setState(() => _error = 'Choose a rating from 1 to 5.');
      return;
    }
    final body =
        widget.session.pendingBody(_path) as Json? ??
        {'rating': _rating, 'comment': _comment.text.trim()};
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final result = await widget.session.idempotentPost(_path, body) as Json;
      if (!mounted) return;
      setState(() {
        _review = ServiceReview.fromJson(result);
        _eligible = false;
        _comment.clear();
      });
    } catch (e) {
      if (!mounted) return;
      if (e is AccountRequestException && e.status == 409) {
        await _load();
      } else {
        setState(() {
          _error = marketError(e);
          if (e is AccountRequestException &&
              [401, 403, 404].contains(e.status)) {
            _eligible = false;
            _review = null;
            _comment.clear();
          }
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final pending = widget.session.hasPending(_path);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Booking review'),
        actions: [
          IconButton(
            tooltip: 'Refresh review',
            onPressed: _loading || _saving ? null : _load,
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
              if (_loading) const LinearProgressIndicator(),
              if (_error != null)
                MarketError(_error!, _loading || _saving ? null : _load),
              if (!_loading && _review != null) ...[
                Text(
                  'Review published',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                ReviewCard(review: _review!),
              ] else if (!_loading && _eligible) ...[
                Text(
                  'How did it go?',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 12),
                const Text(
                  'Your rating and optional comment will be public on this provider’s reviews. Don’t include personal details. Reviews cannot be edited after publishing.',
                ),
                const SizedBox(height: 20),
                DropdownButtonFormField<int>(
                  initialValue: _rating,
                  decoration: const InputDecoration(labelText: 'Rating'),
                  items: [
                    for (var n = 1; n <= 5; n++)
                      DropdownMenuItem(
                        value: n,
                        child: Text('$n ${n == 1 ? 'star' : 'stars'}'),
                      ),
                  ],
                  onChanged: _saving || pending
                      ? null
                      : (v) => setState(() => _rating = v),
                ),
                const SizedBox(height: 20),
                TextField(
                  controller: _comment,
                  enabled: !_saving && !pending,
                  maxLength: 2000,
                  minLines: 3,
                  maxLines: 6,
                  decoration: const InputDecoration(
                    labelText: 'Comment (optional)',
                    border: OutlineInputBorder(),
                  ),
                ),
                if (pending)
                  const Text(
                    'Publication is unconfirmed. Retry the original review to check its result.',
                  ),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: _saving ? null : _submit,
                  child: Text(
                    _saving
                        ? 'Publishing…'
                        : pending
                        ? 'Retry review'
                        : 'Publish review',
                  ),
                ),
              ] else if (!_loading && _error == null)
                const Text(
                  'Only the customer can review this booking after it is completed.',
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class ReviewCard extends StatelessWidget {
  const ReviewCard({super.key, required this.review});
  final ServiceReview review;
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${review.rating} / 5 · Verified customer',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          if (review.comment.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text(review.comment),
            ),
          Text(localTime(context, review.createdAt)),
        ],
      ),
    ),
  );
}
