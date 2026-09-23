import 'dart:async';
import 'dart:convert';

import 'package:bliss_mobile/core/auth/auth_session.dart';
import 'package:bliss_mobile/features/discovery/catalog.dart';
import 'package:bliss_mobile/features/marketplace/booking_screen.dart';
import 'package:bliss_mobile/features/marketplace/marketplace.dart';
import 'package:bliss_mobile/features/marketplace/market_widgets.dart';
import 'package:bliss_mobile/features/marketplace/marketplace_screen.dart';
import 'package:bliss_mobile/features/marketplace/request_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'auth_session_test.dart' show FakeTokens;

const booking = {
  'id': 'booking',
  'providerId': 'provider',
  'title': 'Garden care',
  'description': 'Trim hedges',
  'city': 'Toronto',
  'price': '80.50',
  'currency': 'CAD',
  'status': 'WAIT_PROVIDER',
  'decisionDeadline': '2099-01-01T00:00:00Z',
};
const request = {
  'id': 'request',
  'categoryId': 'category',
  'title': 'Garden help',
  'description': 'Trim two hedges',
  'city': 'Toronto',
  'budget': '100.00',
  'currency': 'CAD',
  'status': 'OPEN',
  'version': 2,
};
http.Response json(Object? value, [int code = 200]) => http.Response(
  jsonEncode(value),
  code,
  headers: {'content-type': 'application/json'},
);
AuthSession session(
  Future<http.Response> Function(http.Request) handler, {
  List<String> roles = const ['customer'],
}) => AuthSession(
  tokens: FakeTokens()..token = 'test',
  apiBaseUrl: 'https://api.test',
  client: MockClient(handler),
)..profile = {'id': 'customer', 'displayName': 'Alex', 'capabilities': roles};
Widget host(AuthSession s, Widget child) => MaterialApp(
  home: SessionGate(session: s, child: child),
);

void main() {
  testWidgets(
    'retry of an already processed event does not leave the booking waiting',
    (tester) async {
      var calls = 0;
      final s = session((r) async {
        if (r.method == 'POST') {
          if (++calls == 1) throw http.ClientException('lost response');
          return json({'eventId': 'event', 'processed': true}, 202);
        }
        if (r.url.path == '/api/v1/providers/me') {
          return json([
            {'providerId': 'provider', 'name': 'Garden team', 'role': 'OWNER'},
          ]);
        }
        if (r.url.path.endsWith('/history')) {
          return json({'items': [], 'total': 0});
        }
        if (r.url.path.endsWith('/payment')) {
          return json({'status': 'AUTHORIZED'});
        }
        if (r.url.path.endsWith('/execution')) {
          return json({'status': 'WAITING'});
        }
        return json({...booking, 'status': 'WAIT_CUSTOMER'});
      }, roles: ['provider', 'customer']);
      await expectLater(
        s.idempotentPost('/api/v1/bookings/booking/mark-complete', null),
        throwsA(isA<http.ClientException>()),
      );
      await tester.pumpWidget(
        host(s, BookingScreen(session: s, bookingId: 'booking')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Retry mark work complete'));
      await tester.pumpAndSettle();
      expect(calls, 2);
      expect(find.textContaining('Action received.'), findsNothing);
      expect(find.text('Waiting for customer'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'a session change disposes a private form and ignores its late result',
    (tester) async {
      final result = Completer<Object?>();
      final s = session((r) async => json({}));
      await tester.pumpWidget(
        host(
          s,
          MarketForm(
            title: 'Create request',
            fields: const [
              FieldSpec(
                'description',
                'Private scope',
                initial: 'Private test detail',
              ),
            ],
            submit: (_) => result.future,
          ),
        ),
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Create request'));
      await tester.pump();
      await s.signOut();
      await tester.pumpAndSettle();
      expect(find.byType(TextFormField), findsNothing);
      result.complete({'id': 'late'});
      await tester.pumpAndSettle();
      expect(find.textContaining('Your session ended.'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'booking access loss removes previously visible private details',
    (tester) async {
      var allowed = true;
      final s = session((r) async {
        if (!allowed) return json({}, 403);
        if (r.url.path.endsWith('/history')) {
          return json({'items': [], 'total': 0});
        }
        if (r.url.path.endsWith('/payment')) {
          return json({'status': 'AUTHORIZED'});
        }
        if (r.url.path.endsWith('/execution')) {
          return json({'status': 'WAITING'});
        }
        return json(booking);
      });
      await tester.pumpWidget(
        host(s, BookingScreen(session: s, bookingId: 'booking')),
      );
      await tester.pumpAndSettle();
      expect(find.text('Garden care'), findsOneWidget);
      allowed = false;
      await tester.tap(find.byTooltip('Refresh booking'));
      await tester.pumpAndSettle();
      expect(find.text('Garden care'), findsNothing);
      expect(
        find.text('This item is not available to your account.'),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  test(
    'uncertain booking retries retain the key and frozen body until resolved',
    () async {
      final calls = <http.Request>[];
      final s = session((r) async {
        calls.add(r);
        if (calls.length == 1) throw http.ClientException('lost response');
        return json(booking);
      });
      const path = '/api/v1/offerings/service/bookings';
      final body = {'offeringVersion': 4, 'city': 'Toronto'};
      await expectLater(
        s.idempotentPost(path, body),
        throwsA(isA<http.ClientException>()),
      );
      expect(s.hasPending(path), isTrue);
      await expectLater(
        s.idempotentPost(path, {...body, 'city': 'Ottawa'}),
        throwsStateError,
      );
      await s.idempotentPost(path, body);
      expect(calls, hasLength(2));
      expect(
        calls[1].headers['Idempotency-Key'],
        calls[0].headers['Idempotency-Key'],
      );
      expect(
        calls[0].headers['Idempotency-Key'],
        matches(
          RegExp(
            r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
          ),
        ),
      );
      expect(s.hasPending(path), isFalse);
      await s.idempotentPost(path, body);
      expect(
        calls[2].headers['Idempotency-Key'],
        isNot(calls[1].headers['Idempotency-Key']),
      );
      s.dispose();
    },
  );

  test('server failure retains intent; logout clears it and rejects late private responses', () async {
    final response = Completer<http.Response>();
    final started = Completer<void>();
    final s = session((r) async {
      if (r.method == 'POST') return json({}, 503);
      started.complete();
      return response.future;
    });
    await expectLater(
      s.idempotentPost('/api/v1/bookings/booking/cancel', null),
      throwsA(isA<AccountRequestException>()),
    );
    expect(s.hasPending('/api/v1/bookings/booking/cancel'), isTrue);
    final pending = s.request('GET', '/api/v1/bookings');
    final rejected = expectLater(pending, throwsStateError);
    await started.future;
    await s.signOut();
    response.complete(
      json({
        'items': [booking],
      }),
    );
    await rejected;
    expect(s.signedIn, isFalse);
    expect(s.hasPending('/api/v1/bookings/booking/cancel'), isFalse);
    s.dispose();
  });

  test('mapping preserves decimal amounts and gates actions by role, deadline and schedule', () {
    final b = Booking.fromJson(booking);
    expect(b.price, '80.50');
    expect(b.deadline!.isUtc, isTrue);
    expect(b.actions(provider: true, customer: true, now: DateTime.utc(2026)), [
      'provider-accept',
      'provider-reject',
    ]);
    expect(
      b.actions(provider: true, customer: false, now: DateTime.utc(2100)),
      isEmpty,
    );
    expect(b.actions(provider: false, customer: true), ['cancel']);
    final scheduled = Booking.fromJson({
      ...booking,
      'status': 'SCHEDULED',
      'scheduledAt': '2026-09-22T12:00:00Z',
    });
    expect(
      scheduled.actions(
        provider: true,
        customer: false,
        now: DateTime.utc(2026, 9, 22, 11),
      ),
      isEmpty,
    );
    expect(
      scheduled.actions(
        provider: true,
        customer: false,
        now: DateTime.utc(2026, 9, 22, 12),
      ),
      ['start'],
    );
    final completed = Booking.fromJson({...booking, 'status': 'COMPLETED'});
    expect(completed.terminal, isTrue);
    expect(completed.actions(provider: false, customer: true), isEmpty);
  });

  testWidgets(
    'activity recovers from failure, paginates and clears private content on logout',
    (tester) async {
      var fail = true;
      final pages = <String?>[];
      final s = session((r) async {
        pages.add(r.url.queryParameters['page']);
        if (fail) return json({}, 503);
        return json({
          'items': r.url.queryParameters['page'] == '1' ? [] : [booking],
          'total': 21,
        });
      });
      await tester.pumpWidget(host(s, MarketplaceScreen(session: s)));
      await tester.pumpAndSettle();
      expect(find.text('Try again'), findsOneWidget);
      expect(find.text('Open opportunities'), findsNothing);
      fail = false;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text('Garden care'), findsOneWidget);
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      expect(pages.last, '1');
      expect(
        find.text('Nothing here yet. Your activity will appear here.'),
        findsOneWidget,
      );
      await s.signOut();
      await tester.pumpAndSettle();
      expect(find.textContaining('Your session ended.'), findsOneWidget);
      expect(find.text('My requests'), findsNothing);
      s.dispose();
    },
  );

  testWidgets(
    'direct booking submits only reviewed revision and city with an idempotency key',
    (tester) async {
      http.Request? sent;
      final s = session((r) async {
        if (r.method == 'POST') {
          sent = r;
          return json(booking, 201);
        }
        if (r.url.path.endsWith('/history')) {
          return json({'items': [], 'total': 0});
        }
        if (r.url.path.endsWith('/payment')) {
          return json({'status': 'AUTHORIZED'});
        }
        if (r.url.path.endsWith('/execution')) {
          return json({'status': 'WAITING'});
        }
        return json(booking);
      });
      final offering = Offering.fromJson({
        'id': 'service',
        'title': 'Garden care',
        'description': 'Trim hedges',
        'price': '80.50',
        'currency': 'CAD',
        'version': 4,
        'category': {
          'id': 'category',
          'name': 'Gardening',
          'slug': 'gardening',
        },
        'provider': {
          'id': 'provider',
          'displayName': 'Garden team',
          'city': 'Toronto',
          'type': 'COMPANY',
        },
      });
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: FilledButton(
                onPressed: () => openDirectBooking(context, s, offering),
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Request booking'));
      await tester.pumpAndSettle();
      expect(jsonDecode(sent!.body), {'offeringVersion': 4, 'city': 'Toronto'});
      expect(sent!.headers['Idempotency-Key'], isNotEmpty);
      expect(find.text('Payment simulation'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets('uncertain form freezes input and retries the original values', (
    tester,
  ) async {
    final inputs = <Map<String, String>>[];
    await tester.pumpWidget(
      MaterialApp(
        home: MarketForm(
          title: 'Submit',
          safeRetry: true,
          fields: const [FieldSpec('city', 'City', initial: 'Toronto')],
          submit: (v) async {
            inputs.add(v);
            throw const AccountRequestException(503);
          },
        ),
      ),
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Submit'));
    await tester.pumpAndSettle();
    expect(
      tester.widget<TextFormField>(find.byType(TextFormField)).enabled,
      isFalse,
    );
    await tester.tap(find.text('Retry original submission'));
    await tester.pumpAndSettle();
    expect(inputs, [
      {'city': 'Toronto'},
      {'city': 'Toronto'},
    ]);
  });

  testWidgets(
    'provider action waits for the workflow then offers the next action',
    (tester) async {
      var status = 'IN_PROGRESS';
      http.Request? sent;
      final s = session((r) async {
        if (r.url.path == '/api/v1/providers/me') {
          return json([
            {'providerId': 'provider', 'name': 'Garden team', 'role': 'OWNER'},
          ]);
        }
        if (r.method == 'POST') {
          sent = r;
          return json({'eventId': 'event', 'processed': false}, 202);
        }
        if (r.url.path.endsWith('/history')) {
          return json({'items': [], 'total': 0});
        }
        if (r.url.path.endsWith('/payment')) {
          return json({'status': 'AUTHORIZED'});
        }
        if (r.url.path.endsWith('/execution')) {
          return json({'status': 'WAITING'});
        }
        return json({...booking, 'status': status});
      }, roles: ['customer', 'provider']);
      await tester.pumpWidget(
        host(s, BookingScreen(session: s, bookingId: 'booking')),
      );
      await tester.pumpAndSettle();
      expect(find.text('Cancel booking'), findsNothing);
      await tester.tap(find.text('Mark work complete'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.widgetWithText(FilledButton, 'Mark work complete').last,
      );
      await tester.pumpAndSettle();
      expect(sent!.url.path, '/api/v1/bookings/booking/mark-complete');
      expect(sent!.headers['Idempotency-Key'], isNotEmpty);
      expect(find.textContaining('Action received.'), findsOneWidget);
      status = 'WAIT_CUSTOMER';
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
      expect(find.text('Waiting for customer'), findsOneWidget);
      expect(find.text('Confirm completion'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'customer accepts a quote and navigates to the resulting booking',
    (tester) async {
      var accepted = false;
      final s = session((r) async {
        if (r.method == 'POST') {
          expect(r.url.path, '/api/v1/quotes/quote/accept');
          accepted = true;
          return json(booking);
        }
        if (r.url.path.endsWith('/quotes')) {
          return json({
            'items': [
              {
                'id': 'quote',
                'providerId': 'provider',
                'providerName': 'Garden team',
                'description': 'Trim hedges',
                'price': '80.50',
                'currency': 'CAD',
                'status': 'SUBMITTED',
                'expiresAt': '2099-01-01T00:00:00Z',
              },
            ],
            'total': 1,
          });
        }
        if (r.url.path.endsWith('/history')) {
          return json({'items': [], 'total': 0});
        }
        if (r.url.path.endsWith('/payment')) {
          return json({'status': 'AUTHORIZED'});
        }
        if (r.url.path.endsWith('/execution')) {
          return json({'status': 'WAITING'});
        }
        return json(r.url.path.contains('/bookings/') ? booking : request);
      });
      await tester.pumpWidget(
        host(s, RequestScreen(session: s, requestId: 'request')),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Accept quote'));
      await tester.tap(find.text('Accept quote'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Accept quote').last);
      await tester.pumpAndSettle();
      expect(accepted, isTrue);
      expect(find.text('Payment simulation'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );
}
