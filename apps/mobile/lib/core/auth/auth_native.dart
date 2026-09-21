import 'package:flutter/foundation.dart';
import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'auth_tokens.dart';

AuthTokens createAuthTokens() => NativeAuthTokens();

class NativeAuthTokens implements AuthTokens {
  static const _clientId = 'bliss-mobile';
  static const _redirect = 'com.bliss.mobile:/oauthredirect';
  static const _key = 'bliss.refresh-token';
  final _appAuth = const FlutterAppAuth();
  final _storage = const FlutterSecureStorage();
  String? _access;
  String? _refresh;
  String? _idToken;
  DateTime? _expires;
  Future<String?>? _refreshing;
  bool get _allowHttp => kDebugMode && Uri.parse(issuerUrl).host == 'localhost';

  @override
  Future<void> restore() async {
    _refresh = await _storage.read(key: _key);
    if (_refresh != null) await accessToken();
  }

  Future<void> _save(TokenResponse response) async {
    _access = response.accessToken;
    _expires = response.accessTokenExpirationDateTime;
    _idToken = response.idToken ?? _idToken;
    _refresh = response.refreshToken ?? _refresh;
    if (_refresh != null) await _storage.write(key: _key, value: _refresh);
  }

  @override
  Future<void> signIn() async {
    final response = await _appAuth.authorizeAndExchangeCode(
      AuthorizationTokenRequest(
        _clientId,
        _redirect,
        issuer: issuerUrl,
        scopes: ['openid', 'profile', 'email'],
        allowInsecureConnections: _allowHttp,
      ),
    );
    await _save(response);
  }

  @override
  Future<String?> accessToken() async {
    if (_access != null &&
        _expires != null &&
        _expires!.isAfter(DateTime.now().add(const Duration(seconds: 30)))) {
      return _access;
    }
    if (_refresh == null) return null;
    if (_refreshing != null) return _refreshing;
    _refreshing = _renew();
    try {
      return await _refreshing;
    } finally {
      _refreshing = null;
    }
  }

  Future<String?> _renew() async {
    try {
      final response = await _appAuth.token(
        TokenRequest(
          _clientId,
          _redirect,
          issuer: issuerUrl,
          refreshToken: _refresh,
          allowInsecureConnections: _allowHttp,
        ),
      );
      await _save(response);
      return _access;
    } catch (_) {
      await _clear();
      rethrow;
    }
  }

  Future<void> _clear() async {
    _access = null;
    _refresh = null;
    _idToken = null;
    _expires = null;
    await _storage.delete(key: _key);
  }

  @override
  Future<void> signOut() async {
    final hint = _idToken;
    await _clear();
    if (hint != null) {
      await _appAuth.endSession(
        EndSessionRequest(
          idTokenHint: hint,
          postLogoutRedirectUrl: _redirect,
          issuer: issuerUrl,
          allowInsecureConnections: _allowHttp,
        ),
      );
    }
  }
}
