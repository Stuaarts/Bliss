import 'package:flutter/material.dart';

import '../../core/auth/auth_session.dart';
import 'marketplace.dart';
import 'market_widgets.dart';
import 'booking_screen.dart';

class RequestScreen extends StatefulWidget {
  const RequestScreen({
    super.key,
    required this.session,
    required this.requestId,
    this.opportunity = false,
  });
  final AuthSession session;
  final String requestId;
  final bool opportunity;
  @override
  State<RequestScreen> createState() => _RequestScreenState();
}

class _RequestScreenState extends State<RequestScreen> {
  late final _repo = MarketplaceRepository(widget.session);
  ServiceRequest? _request;
  List<Json> _providers = [];
  List<Quote> _quotes = [];
  int _page = 0, _total = 0;
  bool _busy = false;
  String? _error, _notice;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final request = ServiceRequest.fromJson(
        await _repo.get(
          '${widget.opportunity ? 'provider/opportunities' : 'service-requests'}/${widget.requestId}',
        ),
      );
      final quotes = widget.opportunity
          ? const MarketPage<Quote>([], 0)
          : await _repo.quotes(
              'service-requests/${widget.requestId}/quotes',
              _page,
            );
      final providers = widget.opportunity ? await _repo.providers() : <Json>[];
      if (mounted) {
        setState(() {
          _request = request;
          _quotes = quotes.items;
          _total = quotes.total;
          _providers = providers;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = marketError(e);
          _request = null;
          _quotes = [];
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _quote() async {
    final r = _request!;
    final expiresAt = DateTime.now()
        .toUtc()
        .add(const Duration(days: 7))
        .toIso8601String();
    final result = await privatePage<Object?>(
      context,
      widget.session,
      MarketForm(
        title: 'Send quote',
        summary: 'Describe exactly what is included. This quote stays valid for seven days.',
        fields: [
          FieldSpec(
            'providerId',
            'Your business',
            initial: _providers.first['providerId'] as String,
            options: {
              for (final p in _providers)
                p['providerId'] as String: p['name'] as String,
            },
          ),
          const FieldSpec('description', 'Scope of work', limit: 2000),
          FieldSpec('price', 'Price (${r.currency})', money: true, limit: 13),
        ],
        submit: (values) => widget.session.request(
          'POST',
          '/api/v1/service-requests/${r.id}/quotes',
          {...values, 'currency': r.currency, 'expiresAt': expiresAt},
        ),
      ),
    );
    if (mounted && result != null) {
      setState(() => _notice = 'Quote sent. You can follow it in My quotes.');
    }
  }

  Future<void> _cancel() async {
    if (!await confirmAction(
          context,
          'Cancel request',
          'This closes the request and declines its outstanding quotes.',
        ) ||
        !mounted) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.session.request(
        'DELETE',
        '/api/v1/service-requests/${widget.requestId}?version=${_request!.version}',
      );
      if (mounted) await _load();
    } catch (e) {
      if (mounted) setState(() => _error = marketError(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _decide(Quote quote, bool accept) async {
    final label = accept ? 'Accept quote' : 'Decline quote';
    if (!await confirmAction(
          context,
          label,
          accept
              ? 'Create a booking for the scope and price shown? Payment is simulated. The provider will confirm the schedule.'
              : 'Decline this quote?',
        ) ||
        !mounted) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await widget.session.request(
        'POST',
        '/api/v1/quotes/${quote.id}/${accept ? 'accept' : 'decline'}',
      );
      if (!mounted) return;
      if (accept && result is Json) {
        await privatePage<void>(
          context,
          widget.session,
          BookingScreen(
            session: widget.session,
            bookingId: result['id'] as String,
          ),
        );
      }
      if (mounted) await _load();
    } catch (e) {
      if (mounted) setState(() => _error = marketError(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final r = _request;
    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.opportunity ? 'Service opportunity' : 'Your request',
        ),
        actions: [
          IconButton(
            tooltip: 'Refresh request',
            onPressed: _busy ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: MarketBody(
        children: [
          if (_busy) const LinearProgressIndicator(),
          if (_error != null) MarketError(_error!, _busy ? null : _load),
          if (_notice != null) Text(_notice!),
          if (r != null) ...[
            Text(r.title, style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 16),
            Text(r.description),
            const SizedBox(height: 16),
            Text('${r.city} · ${statusLabel(r.status)}'),
            Text(
              r.budget == null
                  ? 'Budget open · ${r.currency}'
                  : 'Budget: ${r.currency} ${r.budget}',
            ),
            const SizedBox(height: 24),
            if (widget.opportunity &&
                _providers.isNotEmpty &&
                _notice == null &&
                r.status == 'OPEN')
              FilledButton(
                onPressed: _busy ? null : _quote,
                child: const Text('Send quote'),
              ),
            if (widget.opportunity && _providers.isEmpty)
              const Text(
                'Create a business from Your account before sending a quote.',
              ),
            if (!widget.opportunity) ...[
              if (r.status == 'OPEN')
                OutlinedButton(
                  onPressed: _busy ? null : _cancel,
                  child: const Text('Cancel request'),
                ),
              const SizedBox(height: 24),
              Text('Quotes', style: Theme.of(context).textTheme.titleLarge),
              if (_quotes.isEmpty)
                const Text(
                  'No quotes yet. Providers can respond while your request is open.',
                ),
              for (final q in _quotes)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          q.providerName,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        Text(q.description),
                        Text(
                          '${q.currency} ${q.price} · ${statusLabel(q.status)}',
                        ),
                        Text('Valid until ${localTime(context, q.expiresAt)}'),
                        if (q.available && r.status == 'OPEN')
                          Wrap(
                            spacing: 12,
                            children: [
                              FilledButton(
                                onPressed: _busy
                                    ? null
                                    : () => _decide(q, true),
                                child: const Text('Accept quote'),
                              ),
                              TextButton(
                                onPressed: _busy
                                    ? null
                                    : () => _decide(q, false),
                                child: const Text('Decline quote'),
                              ),
                            ],
                          ),
                      ],
                    ),
                  ),
                ),
              Pager(
                page: _page,
                total: _total,
                change: _busy
                    ? null
                    : (page) {
                        setState(() => _page = page);
                        _load();
                      },
              ),
            ],
          ],
        ],
      ),
    );
  }
}
