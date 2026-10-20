import 'dart:convert';

import 'package:bliss_mobile/core/auth/auth_session.dart';
import 'package:bliss_mobile/features/account/account_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'auth_session_test.dart' show FakeTokens;

void main() {
  testWidgets(
    'account retry after unavailable bootstrap avoids another sign-in at phone width',
    (tester) async {
      tester.view.physicalSize = const Size(320, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      var paused = true;
      final tokens = FakeTokens()..token = 'saved-access';
      final calls = <String>[];
      final session = AuthSession(
        tokens: tokens,
        apiBaseUrl: 'https://api.test',
        client: MockClient((r) async {
          expect(r.headers['Authorization'], 'Bearer saved-access');
          calls.add('${r.method} ${r.url.path}');
          if (paused) {
            return http.Response(
              'private response must not render',
              r.method == 'POST' ? 503 : 404,
            );
          }
          return http.Response(
            jsonEncode({
              'id': 'own',
              'displayName': 'Alex',
              'capabilities': ['customer'],
            }),
            200,
          );
        }),
      );
      await session.restore();
      await tester.pumpWidget(
        MaterialApp(home: AccountScreen(session: session)),
      );
      await tester.pumpAndSettle();
      expect(
        find.text(const AccountRequestException(503).message),
        findsOneWidget,
      );
      expect(find.text('Retry account access'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Sign in'), findsNothing);
      expect(find.text('Sign out'), findsOneWidget);
      expect(find.textContaining('private response'), findsNothing);
      expect(tester.takeException(), isNull);
      paused = false;
      await tester.ensureVisible(find.text('Retry account access'));
      await tester.tap(find.text('Retry account access'));
      await tester.pumpAndSettle();
      expect(find.text('Hello, Alex'), findsOneWidget);
      expect(find.text('Retry account access'), findsNothing);
      expect(session.error, isNull);
      expect(session.canRetryProfile, isFalse);
      expect(tokens.signInCalls, 0);
      expect(calls, [
        'POST /api/v1/me/bootstrap',
        'GET /api/v1/me',
        'POST /api/v1/me/bootstrap',
      ]);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      session.dispose();
    },
  );

  testWidgets('an unavailable account can sign out and clear profile retry', (
    tester,
  ) async {
    final tokens = FakeTokens()..token = 'saved-access';
    final session = AuthSession(
      tokens: tokens,
      apiBaseUrl: 'https://api.test',
      client: MockClient(
        (r) async => http.Response('', r.method == 'POST' ? 503 : 404),
      ),
    );
    await session.restore();
    await tester.pumpWidget(MaterialApp(home: AccountScreen(session: session)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sign out'));
    await tester.pumpAndSettle();
    expect(tokens.loggedOut, isTrue);
    expect(tokens.token, isNull);
    expect(session.canRetryProfile, isFalse);
    expect(find.text('Retry account access'), findsNothing);
    expect(find.widgetWithText(FilledButton, 'Sign in'), findsOneWidget);
    expect(tokens.signInCalls, 0);
    await tester.pumpWidget(const SizedBox());
    session.dispose();
  });
}
