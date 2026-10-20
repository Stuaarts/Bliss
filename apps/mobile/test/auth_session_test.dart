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
  int signInCalls = 0;
  @override
  Future<void> restore() async {}
  @override
  Future<void> signIn() async {
    signInCalls++;
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

  test('restore reads an existing profile after bootstrap 503 without signing in', () async {
    final tokens = FakeTokens()..token = 'saved-access';
    final calls = <String>[];
    final session = AuthSession(
      tokens: tokens,
      apiBaseUrl: 'https://api.test',
      client: MockClient((r) async {
        expect(r.headers['Authorization'], 'Bearer saved-access');
        calls.add('${r.method} ${r.url.path}');
        return r.method == 'POST'
            ? http.Response('private server detail', 503)
            : http.Response(
                '{"id":"own","displayName":"Alex","capabilities":["customer"]}',
                200,
              );
      }),
    );
    await session.restore();
    expect(calls, ['POST /api/v1/me/bootstrap', 'GET /api/v1/me']);
    expect(session.signedIn, isTrue);
    expect(session.capabilities, ['customer']);
    expect(session.error, isNull);
    expect(session.canRetryProfile, isFalse);
    expect(tokens.signInCalls, 0);
    session.dispose();
  });

  for (final readStatus in [404, 503]) {
    test(
      'profile unavailable after 503/read $readStatus retries without another sign-in',
      () async {
        var paused = true;
        final tokens = FakeTokens()..token = 'saved-access';
        final calls = <String>[];
        final session = AuthSession(
          tokens: tokens,
          apiBaseUrl: 'https://api.test',
          client: MockClient((r) async {
            calls.add('${r.method} ${r.url.path}');
            if (!paused) {
              return http.Response(
                '{"id":"own","displayName":"Alex","capabilities":[]}',
                200,
              );
            }
            return http.Response(
              'private server detail',
              r.method == 'POST' ? 503 : readStatus,
            );
          }),
        );
        if (readStatus == 404) {
          await session.signIn();
        } else {
          await session.restore();
        }
        final prompts = tokens.signInCalls;
        expect(session.signedIn, isFalse);
        expect(session.canRetryProfile, isTrue);
        expect(session.error, const AccountRequestException(503).message);
        expect(session.error, isNot(contains('private')));
        expect(calls.length, 2);
        paused = false;
        await session.retryProfile();
        expect(session.signedIn, isTrue);
        expect(session.canRetryProfile, isFalse);
        expect(session.error, isNull);
        expect(tokens.signInCalls, prompts);
        expect(calls, [
          'POST /api/v1/me/bootstrap',
          'GET /api/v1/me',
          'POST /api/v1/me/bootstrap',
        ]);
        session.dispose();
      },
    );
  }

  for (final status in [401, 403, 404, 500]) {
    test(
      'bootstrap $status does not trigger the 503-only read fallback',
      () async {
        final tokens = FakeTokens()..token = 'saved-access';
        var calls = 0;
        final session = AuthSession(
          tokens: tokens,
          apiBaseUrl: 'https://api.test',
          client: MockClient((r) async {
            calls++;
            expect(r.method, 'POST');
            return http.Response('private response', status);
          }),
        );
        await session.restore();
        expect(calls, 1);
        expect(session.signedIn, isFalse);
        expect(session.canRetryProfile, status >= 500);
        expect(session.error, AccountRequestException(status).message);
        expect(session.error, isNot(contains('private')));
        expect(tokens.signInCalls, 0);
        session.dispose();
      },
    );
  }

  test('401 during fallback clears retry state and cannot be retried as authenticated', () async {
    final tokens = FakeTokens()..token = 'saved-access';
    var calls = 0;
    final session = AuthSession(
      tokens: tokens,
      apiBaseUrl: 'https://api.test',
      client: MockClient(
        (r) async => http.Response('', ++calls == 1 ? 503 : 401),
      ),
    );
    await session.restore();
    expect(session.signedIn, isFalse);
    expect(session.canRetryProfile, isFalse);
    expect(session.error, const AccountRequestException(401).message);
    await session.retryProfile();
    expect(calls, 2);
    expect(tokens.signInCalls, 0);
    session.dispose();
  });
}
