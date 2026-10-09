import 'dart:convert';
import 'dart:io';

import 'package:bliss_mobile/core/auth/auth_native.dart';
import 'package:bliss_mobile/core/auth/auth_tokens.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

// Run only on an isolated test emulator/simulator. Three invocations verify
// persistence across process death, using synthetic credentials and a local
// OIDC fixture. No browser sign-in or real Keycloak session is claimed.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  const phase = String.fromEnvironment('NATIVE_TEST_PHASE');
  const fixtureIssuer = 'http://localhost:18180';
  const key = 'bliss.refresh-token';
  const seed = 'bliss-integration-refresh-fixture';
  const storage = FlutterSecureStorage();

  testWidgets('native session process phase: $phase', (tester) async {
    expect(issuerUrl, fixtureIssuer, reason: 'Use the isolated OIDC fixture.');
    expect(['seed', 'restore', 'signed-out'], contains(phase));
    await tester.runAsync(() async {
      if (phase == 'seed') {
        expect(
          await storage.read(key: key),
          isNull,
          reason: 'Refuse to overwrite an existing session.',
        );
        await storage.write(key: key, value: seed);
        expect(await storage.read(key: key), seed);
        return;
      }
      if (phase == 'signed-out') {
        expect(await storage.read(key: key), isNull);
        final tokens = NativeAuthTokens();
        await tokens.restore();
        expect(await tokens.accessToken(), isNull);
        return;
      }

      expect(
        await storage.read(key: key),
        seed,
        reason: 'The previous process must have persisted the fixture.',
      );
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 18180);
      final requests = <Map<String, String>>[];
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/.well-known/openid-configuration') {
          request.response.write(
            jsonEncode({
              'issuer': fixtureIssuer,
              'authorization_endpoint': '$fixtureIssuer/authorize',
              'token_endpoint': '$fixtureIssuer/token',
              'jwks_uri': '$fixtureIssuer/keys',
              'response_types_supported': ['code'],
              'subject_types_supported': ['public'],
              'id_token_signing_alg_values_supported': ['RS256'],
            }),
          );
        } else if (request.method == 'POST' && request.uri.path == '/token') {
          final form = Uri.splitQueryString(
            await utf8.decoder.bind(request).join(),
          );
          requests.add(form);
          request.response.write(
            jsonEncode({
              'access_token': 'fixture-access',
              'refresh_token': 'fixture-rotated-refresh',
              'token_type': 'Bearer',
              'expires_in': 3600,
            }),
          );
        } else {
          request.response.statusCode = HttpStatus.notFound;
        }
        await request.response.close();
      });
      try {
        final tokens = NativeAuthTokens();
        await tokens.restore();
        expect(await tokens.accessToken(), 'fixture-access');
        expect(requests, hasLength(1));
        expect(requests.single['grant_type'], 'refresh_token');
        expect(requests.single['refresh_token'], seed);
        expect(requests.single['client_id'], 'bliss-mobile');
        expect(await storage.read(key: key), 'fixture-rotated-refresh');
        await tokens.signOut();
        expect(await storage.read(key: key), isNull);
        expect(await tokens.accessToken(), isNull);
      } finally {
        await server.close(force: true);
      }
    });
  });
}
