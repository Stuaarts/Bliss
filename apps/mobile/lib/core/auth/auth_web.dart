import 'dart:convert';
import 'dart:math';

import 'package:openid_client/openid_client.dart';
import 'package:web/web.dart' as web;

import 'auth_tokens.dart';

AuthTokens createAuthTokens() => WebAuthTokens();

/// Tokens stay in memory. Only the short-lived PKCE transaction survives redirect.
class WebAuthTokens implements AuthTokens {
  static const _transactionKey = 'bliss.oidc.transaction';
  Credential? _credential;
  Uri get _redirect => Uri.parse(web.window.location.origin).resolve('/');
  Future<Client> _client() async =>
      Client(await Issuer.discover(Uri.parse(issuerUrl)), 'bliss-web');
  String _random() => base64Url
      .encode(List<int>.generate(32, (_) => Random.secure().nextInt(256)))
      .replaceAll('=', '');

  @override
  Future<void> signIn() async {
    final client = await _client();
    final transaction = <String, dynamic>{
      'state': _random(),
      'verifier': _random(),
      'nonce': _random(),
      'created': DateTime.now().millisecondsSinceEpoch,
    };
    final flow = _flow(client, transaction);
    web.window.sessionStorage.setItem(_transactionKey, jsonEncode(transaction));
    web.window.location.assign(flow.authenticationUri.toString());
  }

  Flow _flow(Client client, Map<String, dynamic> transaction) =>
      Flow.authorizationCodeWithPKCE(
        client,
        state: transaction['state'] as String,
        codeVerifier: transaction['verifier'] as String,
        additionalParameters: {'nonce': transaction['nonce'] as String},
        scopes: const ['openid', 'profile', 'email'],
      )..redirectUri = _redirect;

  @override
  Future<void> restore() async {
    final query = Uri.parse(web.window.location.href).queryParameters;
    if (!query.containsKey('code') && !query.containsKey('error')) return;
    final saved = web.window.sessionStorage.getItem(_transactionKey);
    web.window.sessionStorage.removeItem(_transactionKey);
    web.window.history.replaceState(null, '', _redirect.toString());
    if (saved == null) throw StateError('Missing sign-in transaction');
    final transaction = jsonDecode(saved) as Map<String, dynamic>;
    final age =
        DateTime.now().millisecondsSinceEpoch - (transaction['created'] as int);
    if (age < 0 || age > 600000 || query['state'] != transaction['state']) {
      throw StateError('Expired or mismatched sign-in transaction');
    }
    final credential = await _flow(
      await _client(),
      transaction,
    ).callback(query);
    final errors = await credential.validateToken().toList();
    if (errors.isNotEmpty ||
        credential.idToken.claims['nonce'] != transaction['nonce']) {
      throw StateError('Invalid identity token');
    }
    _credential = credential;
  }

  @override
  Future<String?> accessToken() async {
    try {
      return (await _credential?.getTokenResponse())?.accessToken;
    } catch (_) {
      _credential = null;
      rethrow;
    }
  }

  @override
  Future<void> signOut() async {
    final logout = _credential?.generateLogoutUrl(redirectUri: _redirect);
    _credential = null;
    web.window.sessionStorage.removeItem(_transactionKey);
    if (logout != null) web.window.location.assign(logout.toString());
  }
}
