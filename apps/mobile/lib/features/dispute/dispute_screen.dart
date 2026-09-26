import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';

import '../../core/auth/auth_session.dart';
import '../marketplace/marketplace.dart';
import '../marketplace/market_widgets.dart';
import 'dispute.dart';

Future<void> openBookingDispute(
  BuildContext context,
  AuthSession session,
  String bookingId,
) async {
  final path = '/api/v1/bookings/$bookingId/disputes';
  final state = await session.request('GET', path) as Json;
  if (!context.mounted) return;
  Json? dispute = state['dispute'] as Json?;
  if (dispute == null && state['eligible'] == true) {
    final pending = session.hasPending(path);
    final result = await privatePage<Object?>(
      context,
      session,
      MarketForm(
        title: pending ? 'Check pending dispute' : 'Open dispute',
        summary: 'Describe what went wrong. Your explanation and evidence are shared with the provider and the reviewing admin. Opening a dispute pauses settlement. Evidence collection ends when both parties have responded or the deadline passes; then an independent admin decides. This demo uses simulated payments only.',
        safeRetry: true,
        fields: pending
            ? []
            : [const FieldSpec('reason', 'What happened?', limit: 2000)],
        submit: (values) => session.idempotentPost(
          path,
          pending ? session.pendingBody(path) : values,
        ),
      ),
    );
    if (result is Json) dispute = result;
  } else if (dispute == null) {
    await showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Dispute unavailable'),
        content: Text(
          state['filingDeadline'] == null
              ? 'Only the customer can open a dispute after the provider marks the work complete.'
              : 'Only the customer may file one dispute for a finished service, before ${localTime(c, DateTime.parse(state['filingDeadline'] as String))}. This booking is not currently eligible.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
  if (dispute != null && context.mounted && session.signedIn) {
    await privatePage<void>(
      context,
      session,
      DisputeScreen(session: session, disputeId: dispute['id'] as String),
    );
  }
}

class DisputeScreen extends StatefulWidget {
  const DisputeScreen({
    super.key,
    required this.session,
    required this.disputeId,
  });
  final AuthSession session;
  final String disputeId;
  @override
  State<DisputeScreen> createState() => _DisputeScreenState();
}

class _DisputeScreenState extends State<DisputeScreen> {
  late final _path = '/api/v1/disputes/${widget.disputeId}';
  Json? _detail;
  String? _execution;
  List<Json> _evidence = [], _history = [];
  int _page = 0, _total = 0;
  bool _fetching = false, _loading = false, _acting = false;
  String? _error, _snapshot;
  Timer? _timer;
  bool get _resolved =>
      (_detail?['status'] as String? ?? '').startsWith('RESOLVED');
  @override
  void initState() {
    super.initState();
    _load();
    _timer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (mounted &&
          !_resolved &&
          !_acting &&
          !_fetching &&
          widget.session.signedIn &&
          (ModalRoute.of(context)?.isCurrent ?? false)) {
        _load(quiet: true);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load({bool quiet = false}) async {
    if (_fetching) return;
    _fetching = true;
    if (!quiet) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final result = await Future.wait([
        widget.session.request('GET', _path),
        widget.session.request('GET', '$_path/evidence?page=$_page&size=20'),
        widget.session.request('GET', '$_path/history?size=100'),
        widget.session.request('GET', '$_path/execution'),
      ]);
      if (!mounted) return;
      final snapshot = jsonEncode(result);
      if (snapshot == _snapshot && _error == null && quiet) return;
      setState(() {
        _detail = result[0] as Json;
        final evidence = result[1] as Json;
        _evidence = (evidence['items'] as List).cast<Json>();
        _total = evidence['total'] as int;
        _history = ((result[2] as Json)['items'] as List).cast<Json>();
        _execution = (result[3] as Json)['status'] as String;
        _error = null;
        _snapshot = snapshot;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = marketError(e);
          if (e is AccountRequestException &&
              [401, 403, 404].contains(e.status)) {
            _detail = null;
            _execution = null;
            _evidence = [];
            _history = [];
          }
        });
      }
    } finally {
      _fetching = false;
      if (mounted && !quiet) setState(() => _loading = false);
    }
  }

  Future<void> _form({required bool decision}) async {
    final path = '$_path/${decision ? 'decision' : 'evidence'}';
    final pending = widget.session.hasPending(path);
    setState(() {
      _acting = true;
      _error = null;
    });
    try {
      await privatePage<Object?>(
        context,
        widget.session,
        MarketForm(
          title: pending
              ? 'Retry pending ${decision ? 'decision' : 'evidence'}'
              : decision
              ? 'Resolve dispute'
              : 'Add evidence',
          summary: decision
              ? 'Review both parties’ evidence before choosing an outcome. The decision is final for this case. Customer favored voids an authorization or refunds an existing capture; provider favored captures or keeps the payment. All payment actions are simulations.'
              : 'Share relevant service details with the other party and reviewing admin. Do not include sensitive personal details. Evidence cannot be edited after submission.',
          safeRetry: true,
          fields: pending
              ? []
              : decision
              ? [
                  const FieldSpec(
                    'outcome',
                    'Outcome',
                    options: {
                      'CUSTOMER_FAVORED': 'Customer favored',
                      'PROVIDER_FAVORED': 'Provider favored',
                    },
                  ),
                  const FieldSpec(
                    'rationale',
                    'Decision rationale',
                    limit: 2000,
                  ),
                ]
              : [const FieldSpec('body', 'Evidence', limit: 2000)],
          submit: (values) => widget.session.idempotentPost(
            path,
            pending ? widget.session.pendingBody(path) : values,
          ),
        ),
      );
      if (mounted) await _load();
    } catch (e) {
      if (mounted) setState(() => _error = marketError(e));
    } finally {
      if (mounted) setState(() => _acting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final d = _detail, decision = d?['decision'] as Json?;
    final pendingEvidence = widget.session.hasPending('$_path/evidence');
    final pendingDecision = widget.session.hasPending('$_path/decision');
    return Scaffold(
      appBar: AppBar(
        title: const Text('Dispute'),
        actions: [
          IconButton(
            tooltip: 'Refresh dispute',
            onPressed: _fetching || _acting ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: MarketBody(
        children: [
          if (_loading) const LinearProgressIndicator(),
          if (_error != null)
            MarketError(_error!, _fetching || _acting ? null : _load),
          if (d != null) ...[
            if (['FAULTED', 'ERROR'].contains(_execution))
              const Text(
                'This dispute needs support attention before settlement can continue.',
              ),
            Text(
              d['title'] as String,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 8),
            Text(disputeStatus(d['status'] as String)),
            Text('${d['currency']} ${d['amount']} · Simulated payment'),
            const SizedBox(height: 16),
            Text(d['reason'] as String),
            const SizedBox(height: 12),
            if (d['status'] == 'COLLECTING_EVIDENCE')
              Text(
                'Evidence closes after both parties respond or by ${localTime(context, DateTime.parse(d['evidenceDeadline'] as String))}.',
              ),
            if (d['status'] == 'UNDER_REVIEW' && decision == null)
              const Text(
                'An independent admin must review the evidence. No payment is settled automatically.',
              ),
            if (decision != null) ...[
              const SizedBox(height: 16),
              Text(
                decision['outcome'] == 'CUSTOMER_FAVORED'
                    ? 'Decision: customer favored'
                    : 'Decision: provider favored',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              Text(decision['rationale'] as String),
              Text(
                decision['processed'] == true
                    ? 'Decision applied.'
                    : 'Decision received. Waiting for settlement…',
              ),
            ],
            const SizedBox(height: 16),
            if (d['canAddEvidence'] == true || pendingEvidence)
              FilledButton(
                onPressed: _acting || _loading
                    ? null
                    : () => _form(decision: false),
                child: Text(
                  pendingEvidence ? 'Retry evidence' : 'Add evidence',
                ),
              ),
            if (d['canDecide'] == true || pendingDecision)
              FilledButton(
                onPressed: _acting || _loading
                    ? null
                    : () => _form(decision: true),
                child: Text(
                  pendingDecision ? 'Retry decision' : 'Resolve dispute',
                ),
              ),
            const SizedBox(height: 24),
            Text('Evidence', style: Theme.of(context).textTheme.titleLarge),
            for (final e in _evidence)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        e['role'] == 'CUSTOMER' ? 'Customer' : 'Provider',
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      const SizedBox(height: 8),
                      Text(e['body'] as String),
                      Text(
                        localTime(
                          context,
                          DateTime.parse(e['createdAt'] as String),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            Pager(
              page: _page,
              total: _total,
              change: _fetching || _acting
                  ? null
                  : (page) {
                      setState(() => _page = page);
                      _load();
                    },
            ),
            const SizedBox(height: 24),
            Text('Case history', style: Theme.of(context).textTheme.titleLarge),
            for (final h in _history)
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(disputeStatus(h['status'] as String)),
                subtitle: Text(
                  localTime(context, DateTime.parse(h['occurredAt'] as String)),
                ),
              ),
          ],
        ],
      ),
    );
  }
}
