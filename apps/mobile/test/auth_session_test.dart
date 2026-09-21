import 'dart:convert';

import 'package:bliss_mobile/core/auth/auth_session.dart';
import 'package:bliss_mobile/core/auth/auth_tokens.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class FakeTokens implements AuthTokens {
  String? token;
  bool failRefresh = false;
  bool loggedOut = false;
  @override
  Future<void> restore() async {}
  @override
  Future<void> signIn() async {
    token = 'test-access';
  }

  @override
  Future<String?> accessToken() async {
    if (failRefresh) throw StateError('refresh denied');
    return token;
  }

  @override
  Future<void> signOut() async {
    token = null;
    loggedOut = true;
  }
}

void main() {
  test(
    'sign-in bootstraps from bearer identity and allows dual capabilities',
    () async {
      final tokens = FakeTokens();
      final session = AuthSession(
        tokens: tokens,
        apiBaseUrl: 'https://api.test',
        client: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/me/bootstrap');
          expect(request.headers['Authorization'], 'Bearer test-access');
          expect(request.body, isEmpty);
          return http.Response(
            jsonEncode({
              'id': 'own',
              'displayName': 'Alex',
              'capabilities': ['customer', 'provider'],
            }),
            200,
          );
        }),
      );
      await session.signIn();
      expect(session.signedIn, isTrue);
      expect(session.capabilities, ['customer', 'provider']);
      await session.signOut();
      expect(session.profile, isNull);
      expect(tokens.loggedOut, isTrue);
      session.dispose();
    },
  );

  for (final failRefresh in [false, true]) {
    test(
      'expired session clears private profile (refresh failure: $failRefresh)',
      () async {
        final tokens = FakeTokens()..token = 'old';
        var requests = 0;
        final session = AuthSession(
          tokens: tokens,
          apiBaseUrl: 'https://api.test',
          client: MockClient((request) async {
            requests++;
            return requests == 1
                ? http.Response(
                    '{"id":"own","displayName":"Alex","capabilities":[]}',
                    200,
                  )
                : http.Response('Unauthorized', 401);
          }),
        );
        await session.restore();
        expect(session.signedIn, isTrue);
        tokens.failRefresh = failRefresh;
        await session.refresh();
        expect(session.signedIn, isFalse);
        expect(session.error, isNotNull);
        expect(session.error, isNot(contains('old')));
        session.dispose();
      },
    );
  }

  test('rename sends no client-selected identity or role', () async {
    final tokens = FakeTokens()..token = 'test-access';
    final session = AuthSession(
      tokens: tokens,
      apiBaseUrl: 'https://api.test',
      client: MockClient((request) async {
        expect(request.url.path, '/api/v1/me');
        expect(request.method, 'PUT');
        expect(jsonDecode(request.body), {'displayName': 'New name'});
        return http.Response(
          '{"id":"own","displayName":"New name","capabilities":[]}',
          200,
        );
      }),
    );
    await session.rename('  New name  ');
    expect(session.profile?['displayName'], 'New name');
    session.dispose();
  });
}
