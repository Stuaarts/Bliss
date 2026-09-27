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
  int _generation = 0;
  Future<void> _storageWrites = Future<void>.value();
  bool get _allowHttp => kDebugMode && Uri.parse(issuerUrl).host == 'localhost';

  @override
  Future<void> restore() async {
    final generation = _generation;
    await _storageWrites;
    if (generation != _generation) return;
    final refresh = await _storage.read(key: _key);
    if (generation != _generation) return;
    _refresh = refresh;
    if (_refresh != null) await accessToken();
  }

  // Serialize platform writes: a delete must follow any write already in flight.
  Future<void> _writeStorage(Future<void> Function() action) {
    final operation = _storageWrites.then((_) => action());
    _storageWrites = operation.catchError((Object _) {});
    return operation;
  }

  Future<void> _save(TokenResponse response, int generation) async {
    if (generation != _generation) return;
    final refresh = response.refreshToken ?? _refresh;
    await _writeStorage(() async {
      if (generation == _generation && refresh != null) {
        await _storage.write(key: _key, value: refresh);
      }
    });
    if (generation != _generation) return;
    _access = response.accessToken;
    _expires = response.accessTokenExpirationDateTime;
    _idToken = response.idToken ?? _idToken;
    _refresh = refresh;
  }

  @override
  Future<void> signIn() async {
    final generation = _generation + 1;
    await _clear();
    if (generation != _generation) return;
    final response = await _appAuth.authorizeAndExchangeCode(
      AuthorizationTokenRequest(
        _clientId,
        _redirect,
        issuer: issuerUrl,
        scopes: ['openid', 'profile', 'email'],
        allowInsecureConnections: _allowHttp,
      ),
    );
    await _save(response, generation);
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
    final refreshing = _renew(_generation);
    _refreshing = refreshing;
    try {
      return await refreshing;
    } finally {
      if (identical(_refreshing, refreshing)) _refreshing = null;
    }
  }

  Future<String?> _renew(int generation) async {
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
      await _save(response, generation);
      return generation == _generation ? _access : null;
    } catch (_) {
      if (generation == _generation) await _clear();
      rethrow;
    }
  }

  Future<void> _clear() async {
    _generation++;
    _access = null;
    _refresh = null;
    _idToken = null;
    _expires = null;
    _refreshing = null;
    await _writeStorage(() => _storage.delete(key: _key));
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
