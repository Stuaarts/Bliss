import 'dart:async';
import 'dart:convert';

import 'package:bliss_mobile/core/api/api_client.dart';
import 'package:bliss_mobile/features/reviews/provider_reviews_screen.dart';
import 'package:bliss_mobile/features/reviews/review_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'marketplace_test.dart' show session, json, host;

const review = {
  'id': 'review',
  'rating': 5,
  'comment': 'Great <b>work</b> 🌿',
  'createdAt': '2026-09-25T12:00:00Z',
};

Future<void> chooseFive(WidgetTester tester) async {
  await tester.tap(find.byType(DropdownButtonFormField<int>));
  await tester.pumpAndSettle();
  await tester.tap(find.text('5 stars').last);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'validates rating then publishes and shows immutable plain text review',
    (tester) async {
      Object? sent;
      final s = session((r) async {
        if (r.method == 'POST') {
          sent = jsonDecode(r.body);
          return json(review, 201);
        }
        return json({'eligible': true, 'review': null});
      });
      await tester.pumpWidget(
        host(s, ReviewScreen(session: s, bookingId: 'booking')),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('will be public'), findsOneWidget);
      await tester.tap(find.text('Publish review'));
      await tester.pumpAndSettle();
      expect(find.text('Choose a rating from 1 to 5.'), findsOneWidget);
      expect(sent, isNull);
      await chooseFive(tester);
      await tester.enterText(
        find.byType(TextField),
        '  Great <b>work</b> 🌿  ',
      );
      await tester.tap(find.text('Publish review'));
      await tester.pumpAndSettle();
      expect(sent, {'rating': 5, 'comment': 'Great <b>work</b> 🌿'});
      expect(find.text('Review published'), findsOneWidget);
      expect(find.text('Great <b>work</b> 🌿'), findsOneWidget);
      expect(find.text('Publish review'), findsNothing);
      s.dispose();
    },
  );

  testWidgets(
    'lost response freezes intent and retries same review key and body',
    (tester) async {
      final requests = <http.Request>[];
      final s = session((r) async {
        if (r.method == 'POST') {
          requests.add(r);
          if (requests.length == 1) throw http.ClientException('lost');
          return json(review);
        }
        return json({'eligible': true, 'review': null});
      });
      await tester.pumpWidget(
        host(s, ReviewScreen(session: s, bookingId: 'booking')),
      );
      await tester.pumpAndSettle();
      await chooseFive(tester);
      await tester.enterText(find.byType(TextField), 'Great <b>work</b> 🌿');
      await tester.tap(find.text('Publish review'));
      await tester.pumpAndSettle();
      expect(tester.widget<TextField>(find.byType(TextField)).enabled, isFalse);
      expect(
        tester
            .widget<DropdownButtonFormField<int>>(
              find.byType(DropdownButtonFormField<int>),
            )
            .onChanged,
        isNull,
      );
      await tester.ensureVisible(find.text('Retry review'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Retry review'));
      await tester.pumpAndSettle();
      expect(requests.length, 2);
      expect(requests[0].body, requests[1].body);
      expect(
        requests[0].headers['Idempotency-Key'],
        requests[1].headers['Idempotency-Key'],
      );
      expect(find.text('Review published'), findsOneWidget);
      s.dispose();
    },
  );

  testWidgets(
    'ineligible bookings cannot publish and conflict reloads existing review',
    (tester) async {
      var eligible = false, published = false;
      final s = session((r) async {
        if (r.method == 'POST') {
          published = true;
          return json({}, 409);
        }
        return json({
          'eligible': eligible && !published,
          'review': published ? review : null,
        });
      });
      await tester.pumpWidget(
        host(s, ReviewScreen(session: s, bookingId: 'booking')),
      );
      await tester.pumpAndSettle();
      expect(find.text('Publish review'), findsNothing);
      expect(find.textContaining('Only the customer'), findsOneWidget);
      eligible = true;
      await tester.tap(find.byTooltip('Refresh review'));
      await tester.pumpAndSettle();
      await chooseFive(tester);
      await tester.tap(find.text('Publish review'));
      await tester.pumpAndSettle();
      expect(find.text('Review published'), findsOneWidget);
      s.dispose();
    },
  );

  testWidgets('load retry and logout ignore late submission at phone width', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    var reads = 0;
    final response = Completer<http.Response>();
    final s = session((r) async {
      if (r.method == 'POST') return response.future;
      if (++reads == 1) return json({}, 503);
      return json({'eligible': true, 'review': null});
    });
    await tester.pumpWidget(
      host(s, ReviewScreen(session: s, bookingId: 'booking')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    await chooseFive(tester);
    await tester.ensureVisible(find.text('Publish review'));
    await tester.tap(find.text('Publish review'));
    await tester.pump();
    await s.signOut();
    await tester.pumpAndSettle();
    response.complete(json(review, 201));
    await tester.pumpAndSettle();
    expect(find.text('Review published'), findsNothing);
    expect(find.byType(TextField), findsNothing);
    expect(tester.takeException(), isNull);
    s.dispose();
  });

  testWidgets(
    'public reviews show aggregate and paginate with retry and empty state',
    (tester) async {
      var fail = true, empty = false;
      final pages = <String?>[];
      final api = ApiClient(
        baseUrl: 'https://api.test',
        client: MockClient((r) async {
          expect(r.headers['Authorization'], isNull);
          pages.add(r.url.queryParameters['page']);
          if (fail) {
            fail = false;
            return json({}, 503);
          }
          return json({
            'items': empty
                ? []
                : [
                    {
                      ...review,
                      'comment': r.url.queryParameters['page'] == '1'
                          ? 'Earlier review'
                          : review['comment'],
                    },
                  ],
            'total': empty ? 0 : 21,
            'averageRating': empty ? null : '4.52',
          });
        }),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: ProviderReviewsScreen(
            api: api,
            providerId: 'provider',
            providerName: 'Garden team',
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text('4.52 / 5 · 21 reviews'), findsOneWidget);
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      expect(pages.last, '1');
      expect(find.text('Earlier review'), findsOneWidget);
      expect(
        tester
            .widget<TextButton>(find.widgetWithText(TextButton, 'Next'))
            .onPressed,
        isNull,
      );
      empty = true;
      await tester.tap(find.byTooltip('Refresh reviews'));
      await tester.pumpAndSettle();
      expect(find.text('No reviews yet.'), findsOneWidget);
      api.close();
    },
  );
}
