import 'dart:async';
import 'dart:convert';

import 'package:bliss_mobile/features/dispute/dispute_screen.dart';
import 'package:bliss_mobile/features/marketplace/marketplace_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'marketplace_test.dart' show session, json, host;

const detail = {
  'id': 'case',
  'bookingId': 'booking',
  'title': 'Garden work',
  'amount': '80.50',
  'currency': 'CAD',
  'status': 'COLLECTING_EVIDENCE',
  'reason': 'Work needs attention.',
  'evidenceDeadline': '2099-01-01T00:00:00Z',
  'canAddEvidence': true,
  'canDecide': false,
  'decision': null,
};
const evidence = {
  'role': 'CUSTOMER',
  'body': 'Work needs attention.',
  'createdAt': '2026-09-25T12:00:00Z',
};
http.Response reads(
  http.Request r,
  Map<String, dynamic> d, {
  String execution = 'WAITING',
  int total = 1,
}) {
  if (r.url.path.endsWith('/evidence')) {
    return json({
      'items': [evidence],
      'total': total,
    });
  }
  if (r.url.path.endsWith('/history')) {
    return json({
      'items': [
        {'status': 'COLLECTING_EVIDENCE', 'occurredAt': '2026-09-25T12:00:00Z'},
      ],
      'total': 1,
    });
  }
  if (r.url.path.endsWith('/execution')) return json({'status': execution});
  return json(d);
}

Future<void> clickButton(WidgetTester tester, String text) async {
  final button = find.widgetWithText(FilledButton, text);
  await tester.ensureVisible(button);
  await tester.pumpAndSettle();
  await tester.tap(button);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'customer opens case and duplicate open navigates to existing case',
    (tester) async {
      var opened = false;
      final s = session((r) async {
        if (r.url.path == '/api/v1/bookings/booking/disputes') {
          if (r.method == 'POST') {
            expect(jsonDecode(r.body), {'reason': 'Work needs attention.'});
            opened = true;
            return json(detail, 201);
          }
          return json({
            'eligible': !opened,
            'filingDeadline': '2099-01-01T00:00:00Z',
            'dispute': opened ? detail : null,
          });
        }
        return reads(r, detail);
      });
      await tester.pumpWidget(
        host(
          s,
          Builder(
            builder: (context) => Scaffold(
              body: FilledButton(
                onPressed: () => openBookingDispute(context, s, 'booking'),
                child: const Text('Support'),
              ),
            ),
          ),
        ),
      );
      await clickButton(tester, 'Support');
      expect(find.textContaining('pauses settlement'), findsOneWidget);
      await tester.enterText(
        find.byType(TextFormField),
        'Work needs attention.',
      );
      await clickButton(tester, 'Open dispute');
      expect(find.text('Collecting evidence'), findsWidgets);
      await tester.pageBack();
      await tester.pumpAndSettle();
      await clickButton(tester, 'Support');
      expect(find.text('Open dispute'), findsNothing);
      expect(find.text('Garden work'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'evidence retry keeps original key and body then polling shows admin wait',
    (tester) async {
      var d = Map<String, dynamic>.from(detail);
      final sent = <http.Request>[];
      final s = session((r) async {
        if (r.method == 'POST') {
          sent.add(r);
          if (sent.length == 1) throw http.ClientException('lost');
          d = {...detail, 'status': 'UNDER_REVIEW', 'canAddEvidence': false};
          return json(evidence);
        }
        return reads(r, d);
      });
      await tester.pumpWidget(
        host(s, DisputeScreen(session: s, disputeId: 'case')),
      );
      await tester.pumpAndSettle();
      await clickButton(tester, 'Add evidence');
      await tester.enterText(find.byType(TextFormField), 'Provider response');
      await clickButton(tester, 'Add evidence');
      expect(
        tester.widget<TextFormField>(find.byType(TextFormField)).enabled,
        isFalse,
      );
      await clickButton(tester, 'Retry original submission');
      expect(sent.length, 2);
      expect(sent[0].body, sent[1].body);
      expect(
        sent[0].headers['Idempotency-Key'],
        sent[1].headers['Idempotency-Key'],
      );
      expect(find.text('Waiting for admin review'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Add evidence'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'admin decision is pending until polling confirms applied settlement',
    (tester) async {
      var d = {
        ...detail,
        'status': 'UNDER_REVIEW',
        'canAddEvidence': false,
        'canDecide': true,
      };
      final s = session((r) async {
        if (r.method == 'POST') {
          final body = jsonDecode(r.body) as Map<String, dynamic>;
          expect(body, {
            'outcome': 'CUSTOMER_FAVORED',
            'rationale': 'Reviewed both parties.',
          });
          d = {
            ...d,
            'canDecide': false,
            'decision': {...body, 'processed': false},
          };
          return json(d['decision'], 202);
        }
        return reads(r, d);
      }, roles: ['admin']);
      await tester.pumpWidget(
        host(s, DisputeScreen(session: s, disputeId: 'case')),
      );
      await tester.pumpAndSettle();
      await clickButton(tester, 'Resolve dispute');
      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Customer favored').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byType(TextFormField),
        'Reviewed both parties.',
      );
      await clickButton(tester, 'Resolve dispute');
      expect(
        find.text('Decision received. Waiting for settlement…'),
        findsOneWidget,
      );
      d = {
        ...d,
        'status': 'RESOLVED_CUSTOMER',
        'decision': {
          ...(d['decision'] as Map<String, dynamic>),
          'processed': true,
        },
      };
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
      expect(find.text('Decision applied.'), findsOneWidget);
      expect(find.text('Resolved in customer’s favor'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'private read errors clear case and workflow failure does not imply resolution',
    (tester) async {
      var denied = false, failed = false;
      final s = session(
        (r) async => denied
            ? json({}, 404)
            : reads(r, detail, execution: failed ? 'ERROR' : 'WAITING'),
      );
      await tester.pumpWidget(
        host(s, DisputeScreen(session: s, disputeId: 'case')),
      );
      await tester.pumpAndSettle();
      failed = true;
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
      expect(find.textContaining('needs support attention'), findsOneWidget);
      expect(find.text('Decision applied.'), findsNothing);
      denied = true;
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
      expect(find.text('Garden work'), findsNothing);
      expect(find.text('Work needs attention.'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'evidence paging and logout during pending submission are safe on phone',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final pending = Completer<http.Response>();
      String? page;
      final s = session((r) async {
        if (r.method == 'POST') return pending.future;
        if (r.url.path.endsWith('/evidence')) {
          page = r.url.queryParameters['page'];
        }
        return reads(r, detail, total: 21);
      });
      await tester.pumpWidget(
        host(s, DisputeScreen(session: s, disputeId: 'case')),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Next'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      expect(page, '1');
      await clickButton(tester, 'Add evidence');
      await tester.enterText(find.byType(TextFormField), 'A private note');
      await tester.ensureVisible(
        find.widgetWithText(FilledButton, 'Add evidence'),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Add evidence'));
      await tester.pump();
      await s.signOut();
      await tester.pumpAndSettle();
      pending.complete(json(evidence));
      await tester.pumpAndSettle();
      expect(find.byType(TextFormField), findsNothing);
      expect(find.text('A private note'), findsNothing);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'activity exposes scoped admin cases and ineligible bookings explain access',
    (tester) async {
      final s = session((r) async {
        if (r.url.path == '/api/v1/disputes') {
          return json({
            'items': [
              {'id': 'case', 'title': 'Garden work', 'status': 'UNDER_REVIEW'},
            ],
            'total': 1,
          });
        }
        if (r.url.path.endsWith('/disputes')) {
          return json({
            'eligible': false,
            'filingDeadline': null,
            'dispute': null,
          });
        }
        return json({'items': [], 'total': 0});
      }, roles: ['admin']);
      await tester.pumpWidget(host(s, MarketplaceScreen(session: s)));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Disputes'));
      await tester.pumpAndSettle();
      expect(find.text('Garden work'), findsOneWidget);
      await tester.pumpWidget(
        host(
          s,
          Builder(
            builder: (context) => Scaffold(
              body: FilledButton(
                onPressed: () => openBookingDispute(context, s, 'booking'),
                child: const Text('Support'),
              ),
            ),
          ),
        ),
      );
      await clickButton(tester, 'Support');
      expect(find.text('Dispute unavailable'), findsOneWidget);
      expect(find.byType(TextFormField), findsNothing);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );
}
