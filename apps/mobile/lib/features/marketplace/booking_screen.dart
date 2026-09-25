import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';

import '../../core/auth/auth_session.dart';
import '../discovery/catalog.dart';
import '../conversation/chat_screen.dart';
import '../reviews/review_screen.dart';
import 'marketplace.dart';
import 'market_widgets.dart';

Future<void> openDirectBooking(
  BuildContext context,
  AuthSession session,
  Offering offering,
) async {
  final path = '/api/v1/offerings/${offering.id}/bookings';
  final pending = session.hasPending(path);
  final result = await privatePage<Object?>(
    context,
    session,
    MarketForm(
      title: pending ? 'Check previous booking' : 'Request booking',
      summary: pending
          ? 'A previous request may have succeeded. Check its result before starting another booking.'
          : '${offering.title}\n${offering.description}\n\n${offering.currency} ${offering.price}\n${offering.providerName}\n\nThe provider will confirm the schedule. Payments are simulated; no money moves.',
      safeRetry: true,
      fields: pending
          ? []
          : [FieldSpec('city', 'Service city', initial: offering.city)],
      submit: (values) => session.idempotentPost(
        path,
        pending
            ? session.pendingBody(path)
            : {'offeringVersion': offering.version, 'city': values['city']},
      ),
    ),
  );
  if (result is Json && context.mounted && session.signedIn) {
    await privatePage<void>(
      context,
      session,
      BookingScreen(session: session, bookingId: result['id'] as String),
    );
  }
}

class BookingScreen extends StatefulWidget {
  const BookingScreen({
    super.key,
    required this.session,
    required this.bookingId,
  });
  final AuthSession session;
  final String bookingId;
  @override
  State<BookingScreen> createState() => _BookingScreenState();
}

class _BookingScreenState extends State<BookingScreen> {
  late final _repo = MarketplaceRepository(widget.session);
  Timer? _timer;
  Booking? _booking;
  Json? _payment, _execution;
  List<Json> _history = [];
  bool _provider = false, _loading = false, _acting = false, _waiting = false;
  String? _error, _waitingStatus;
  bool _refreshing = false;
  String? _snapshot;
  @override
  void initState() {
    super.initState();
    _load();
    _timer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (mounted &&
          (ModalRoute.of(context)?.isCurrent ?? false) &&
          !(_booking?.terminal ?? false) &&
          !_acting &&
          !_loading &&
          widget.session.signedIn) {
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
    if (_refreshing) return;
    _refreshing = true;
    if (!quiet) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final path = 'bookings/${widget.bookingId}';
      final result = await Future.wait([
        _repo.get(path),
        _repo.get('$path/history?size=100'),
        _repo.get('$path/payment'),
        _repo.get('$path/execution'),
      ]);
      final providers = widget.session.capabilities.contains('provider')
          ? await _repo.providers()
          : <Json>[];
      if (!mounted) return;
      final booking = Booking.fromJson(result[0]);
      final provider = providers.any(
        (p) => p['providerId'] == booking.providerId,
      );
      final snapshot = jsonEncode([
        ...result,
        providers,
        booking.actions(
          provider: provider,
          customer: widget.session.capabilities.contains('customer'),
        ),
      ]);
      if (quiet && snapshot == _snapshot && _error == null) return;
      setState(() {
        _snapshot = snapshot;
        _booking = booking;
        _payment = result[2];
        _execution = result[3];
        _history = (result[1]['items'] as List).cast<Json>();
        _provider = provider;
        if (_waitingStatus != booking.status) _waiting = false;
        _error = null;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = marketError(e);
          if (e is AccountRequestException &&
              [401, 403, 404].contains(e.status)) {
            _booking = null;
            _payment = null;
            _execution = null;
            _history = [];
          }
        });
      }
    } finally {
      _refreshing = false;
      if (mounted && !quiet) setState(() => _loading = false);
    }
  }

  Future<void> _act(String action) async {
    final path = '/api/v1/bookings/${widget.bookingId}/$action';
    Object? body = widget.session.pendingBody(path);
    if (!widget.session.hasPending(path)) {
      if (action == 'provider-accept') {
        final scheduled = await showDialog<DateTime>(
          context: context,
          builder: (_) => const ScheduleDialog(),
        );
        if (scheduled == null || !mounted) return;
        body = {'scheduledAt': scheduled.toUtc().toIso8601String()};
      } else {
        final detail = action == 'confirm-completion'
            ? 'Confirm that the agreed work is finished. This completes the booking and captures the simulated payment.'
            : 'Apply this action to this booking?';
        if (!await confirmAction(context, actionLabel(action), detail) ||
            !mounted) {
          return;
        }
      }
    }
    setState(() {
      _acting = true;
      _error = null;
    });
    final submittedStatus = _booking!.status;
    try {
      final receipt = await widget.session.idempotentPost(path, body) as Json;
      if (!mounted) return;
      setState(() {
        _waiting = receipt['processed'] != true;
        _waitingStatus = submittedStatus;
      });
      await _load();
    } catch (e) {
      if (mounted) setState(() => _error = marketError(e));
    } finally {
      if (mounted) setState(() => _acting = false);
    }
  }

  Future<void> _chat() async {
    setState(() {
      _acting = true;
      _error = null;
    });
    try {
      final conversation = await widget.session.request(
        'POST',
        '/api/v1/bookings/${widget.bookingId}/conversation',
      ) as Json;
      if (!mounted) return;
      await privatePage<void>(
        context,
        widget.session,
        ChatScreen(
          session: widget.session,
          conversationId: conversation['id'] as String,
        ),
      );
    } catch (e) {
      if (mounted) setState(() => _error = marketError(e));
    } finally {
      if (mounted) setState(() => _acting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final booking = _booking;
    final pending =
        [
              'provider-accept',
              'provider-reject',
              'start',
              'mark-complete',
              'confirm-completion',
              'cancel',
            ]
            .where(
              (a) => widget.session.hasPending(
                '/api/v1/bookings/${widget.bookingId}/$a',
              ),
            )
            .toList();
    final actions =
        booking?.actions(
          provider: _provider,
          customer: widget.session.capabilities.contains('customer'),
        ) ??
        [];
    return Scaffold(
      appBar: AppBar(
        title: const Text('Booking'),
        actions: [
          IconButton(
            tooltip: 'Refresh booking',
            onPressed: _loading || _acting ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: MarketBody(
        children: [
          if (_loading || _acting) const LinearProgressIndicator(),
          if (_error != null)
            MarketError(_error!, _loading || _acting ? null : _load),
          if (booking != null) ...[
            Text(
              booking.title,
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 12),
            Text(booking.description),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: _acting || _loading ? null : _chat,
                icon: const Icon(Icons.chat_bubble_outline),
                label: const Text('Messages'),
              ),
            ),
            if (booking.status == 'COMPLETED')
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: _acting || _loading
                      ? null
                      : () => privatePage<void>(
                          context,
                          widget.session,
                          ReviewScreen(
                            session: widget.session,
                            bookingId: widget.bookingId,
                          ),
                        ),
                  icon: const Icon(Icons.star_outline),
                  label: const Text('Booking review'),
                ),
              ),
            const SizedBox(height: 12),
            Text(
              '${booking.city} · ${booking.currency} ${booking.price}',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            Align(
              alignment: Alignment.centerLeft,
              child: Chip(
                label: Text(
                  _provider && booking.status == 'WAIT_CUSTOMER'
                      ? 'Waiting for customer'
                      : statusLabel(booking.status),
                ),
              ),
            ),
            if (booking.scheduledAt != null)
              Text('Scheduled: ${localTime(context, booking.scheduledAt!)}'),
            if (booking.status == 'WAIT_PROVIDER' && booking.deadline != null)
              Text(
                'Provider response due: ${localTime(context, booking.deadline!)}',
              ),
            if (booking.status == 'SCHEDULED')
              const Text(
                'The provider can start the service at the scheduled time.',
              ),
            if (_waiting)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  'Action received. Waiting for the booking to update…',
                ),
              ),
            const SizedBox(height: 16),
            if (pending.isNotEmpty) ...[
              const Text(
                'The last response was not confirmed. Retry the original action to check its result.',
              ),
              for (final action in pending)
                FilledButton(
                  onPressed: _acting || _loading ? null : () => _act(action),
                  child: Text('Retry ${actionLabel(action).toLowerCase()}'),
                ),
            ] else
              Wrap(
                spacing: 12,
                runSpacing: 8,
                children: [
                  for (final action in actions)
                    FilledButton(
                      onPressed: _waiting || _acting || _loading
                          ? null
                          : () => _act(action),
                      child: Text(actionLabel(action)),
                    ),
                ],
              ),
            const SizedBox(height: 24),
            Text(
              'Payment simulation',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            Text(
              '${statusLabel(_payment?['status'] as String? ?? 'NOT_AUTHORIZED')} · ${booking.currency} ${booking.price}',
            ),
            const Text('Local demo only. No real money moves.'),
            if (['FAULTED', 'ERROR'].contains(_execution?['status']))
              const Text(
                'This booking needs support attention before it can continue.',
              ),
            const SizedBox(height: 24),
            Text(
              'Booking history',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            for (final row in _history)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.check_circle_outline),
                title: Text(statusLabel(row['status'] as String)),
                subtitle: Text(
                  localTime(
                    context,
                    DateTime.parse(row['occurredAt'] as String),
                  ),
                ),
              ),
          ],
        ],
      ),
    );
  }
}

class ScheduleDialog extends StatefulWidget {
  const ScheduleDialog({super.key});
  @override
  State<ScheduleDialog> createState() => _ScheduleDialogState();
}

class _ScheduleDialogState extends State<ScheduleDialog> {
  DateTime _date = DateTime.now().add(const Duration(minutes: 2));
  String? _error;
  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Schedule service'),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Choose a future date and time in your local time zone.'),
        TextButton(
          onPressed: () async {
            final picked = await showDatePicker(
              context: context,
              initialDate: _date,
              firstDate: DateTime.now(),
              lastDate: DateTime.now().add(const Duration(days: 365)),
            );
            if (picked != null && mounted) {
              setState(
                () => _date = DateTime(
                  picked.year,
                  picked.month,
                  picked.day,
                  _date.hour,
                  _date.minute,
                ),
              );
            }
          },
          child: Text(
            MaterialLocalizations.of(context).formatMediumDate(_date),
          ),
        ),
        TextButton(
          onPressed: () async {
            final picked = await showTimePicker(
              context: context,
              initialTime: TimeOfDay.fromDateTime(_date),
            );
            if (picked != null && mounted) {
              setState(
                () => _date = DateTime(
                  _date.year,
                  _date.month,
                  _date.day,
                  picked.hour,
                  picked.minute,
                ),
              );
            }
          },
          child: Text(TimeOfDay.fromDateTime(_date).format(context)),
        ),
        if (_error != null) Text(_error!),
      ],
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Go back'),
      ),
      FilledButton(
        onPressed: () {
          final date = DateTime(
            _date.year,
            _date.month,
            _date.day,
            _date.hour,
            _date.minute,
          );
          if (!date.isAfter(DateTime.now())) {
            setState(() => _error = 'Choose a future time.');
            return;
          }
          Navigator.pop(context, date);
        },
        child: const Text('Accept and schedule'),
      ),
    ],
  );
}
