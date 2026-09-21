import 'dart:convert';

import 'package:bliss_mobile/core/auth/auth_session.dart';
import 'package:bliss_mobile/features/provider/provider_forms.dart';
import 'package:bliss_mobile/features/provider/provider_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'auth_session_test.dart' show FakeTokens;

void main() {
  testWidgets(
    'owned services render, stale archive explains conflict, logout hides data',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final tokens = FakeTokens()..token = 'test-access';
      final calls = <http.Request>[];
      final session = AuthSession(
        tokens: tokens,
        apiBaseUrl: 'https://api.test',
        client: MockClient((request) async {
          calls.add(request);
          expect(request.headers['Authorization'], 'Bearer test-access');
          if (request.method == 'DELETE') return http.Response('{}', 409);
          final Object response = switch (request.url.path) {
            '/api/v1/providers/me' => [
              {'providerId': 'own', 'name': 'My business', 'role': 'OWNER'},
            ],
            '/api/v1/providers/me/own' => {
              'id': 'own',
              'displayName': 'My business',
              'description': 'Local help',
              'city': 'Toronto',
              'type': 'COMPANY',
              'version': 0,
            },
            '/api/v1/providers/me/own/offerings' => {
              'items': [
                {
                  'id': 'service',
                  'title': 'Window cleaning',
                  'description': 'Clean windows',
                  'price': '45.50',
                  'currency': 'CAD',
                  'active': true,
                  'version': 4,
                },
              ],
              'total': 1,
            },
            _ => throw StateError('Unexpected route'),
          };
          return http.Response(jsonEncode(response), 200);
        }),
      );
      session.profile = {
        'id': 'owner',
        'capabilities': ['provider'],
      };
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: const TextScaler.linear(1.5)),
            child: child!,
          ),
          home: ProviderScreen(session: session),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.scrollUntilVisible(find.text('Window cleaning'), 200);
      expect(find.text('Window cleaning'), findsOneWidget);
      expect(find.text('CAD 45.50 · Published'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.widgetWithText(TextButton, 'Archive'),
        200,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, 'Archive'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Archive'));
      await tester.pumpAndSettle();
      expect(calls.last.url.queryParameters['version'], '4');
      await tester.drag(find.byType(ListView), const Offset(0, 1200));
      await tester.pumpAndSettle();
      expect(
        find.text('This record changed. Refresh before editing again.'),
        findsOneWidget,
      );
      await session.signOut();
      await tester.pumpAndSettle();
      expect(find.text('Window cleaning'), findsNothing);
      expect(
        find.text('Your session ended. Go back to sign in again.'),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox());
      session.dispose();
    },
  );

  testWidgets(
    'offering form preserves decimal strings and rejects invalid precision',
    (tester) async {
      Map<String, dynamic>? submitted;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                child: const Text('Open'),
                onPressed: () async {
                  submitted = await showDialog<Map<String, dynamic>>(
                    context: context,
                    builder: (_) => const OfferingForm(
                      categories: [
                        {'id': 'cleaning', 'name': 'Cleaning'},
                      ],
                      initial: {
                        'title': 'Windows',
                        'description': 'Clean',
                        'categoryId': 'cleaning',
                        'price': '45.50',
                        'currency': 'CAD',
                      },
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      final price = find.widgetWithText(TextFormField, 'Price');
      await tester.enterText(price, '1.001');
      await tester.tap(find.text('Save service'));
      await tester.pumpAndSettle();
      expect(
        find.text('Enter a non-negative price with up to two decimals.'),
        findsOneWidget,
      );
      expect(submitted, isNull);
      await tester.enterText(price, '51.20');
      await tester.tap(find.text('Save service'));
      await tester.pumpAndSettle();
      expect(submitted?['price'], '51.20');
      expect(submitted?.containsKey('providerId'), isFalse);
    },
  );
}
