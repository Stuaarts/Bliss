import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'auth_tokens.dart';

class AuthSession extends ChangeNotifier {
  AuthSession({
    required this.tokens,
    required this.apiBaseUrl,
    http.Client? client,
  }) : _client = client ?? http.Client();
  final AuthTokens tokens;
  final String apiBaseUrl;
  final http.Client _client;
  Map<String, dynamic>? profile;
  bool busy = false;
  int _generation = 0;
  int get generation => _generation;
  final Map<String, ({String key, String body})> _pending = {};
  bool _canRetryProfile = false;
  bool get canRetryProfile => _canRetryProfile;

  void _clear() {
    _generation++;
    _pending.clear();
    _canRetryProfile = false;
    profile = null;
    notifyListeners();
  }

  Object? pendingBody(String path) =>
      _pending[path] == null ? null : jsonDecode(_pending[path]!.body);
  bool hasPending(String path) => _pending.containsKey(path);

  Future<Object?> idempotentPost(String path, Object? body) async {
    final encoded = jsonEncode(body);
    final existing = _pending[path];
    if (existing != null && existing.body != encoded) {
      throw StateError(
        'Retry the original pending request before changing its details.',
      );
    }
    final entry = existing ?? (key: _uuid(), body: encoded);
    _pending[path] = entry;
    final generation = _generation;
    try {
      final result = await request('POST', path, body, entry.key);
      if (generation == _generation) _pending.remove(path);
      return result;
    } on AccountRequestException catch (e) {
      if (e.status < 500 && generation == _generation) _pending.remove(path);
      rethrow;
    }
  }

  static String _uuid() {
    final random = Random.secure();
    final bytes = List.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 15) | 64;
    bytes[8] = (bytes[8] & 63) | 128;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  String? error;
  bool get signedIn => profile != null;
  List<String> get capabilities =>
      (profile?['capabilities'] as List? ?? []).cast<String>();

  Future<void> restore() => _run(() async {
    await tokens.restore();
    if (await tokens.accessToken() != null) await _bootstrap();
  });

  Future<void> signIn() => _run(() async {
    await tokens.signIn();
    if (await tokens.accessToken() != null) await _bootstrap();
  });

  Future<void> _bootstrap() async {
    _canRetryProfile = true;
    try {
      Object? result;
      try {
        result = await request('POST', '/api/v1/me/bootstrap');
      } on AccountRequestException catch (e) {
        if (e.status != 503) rethrow;
        // A paused bootstrap need not prevent an existing account from reading.
        // New accounts still require bootstrap after write admission reopens.
        try {
          result = await request('GET', '/api/v1/me');
        } on AccountRequestException catch (readError) {
          if (readError.status == 404) throw e;
          rethrow;
        }
      }
      profile = result as Map<String, dynamic>;
      _canRetryProfile = false;
    } on AccountRequestException catch (e) {
      if (e.status < 500) _canRetryProfile = false;
      rethrow;
    }
  }

  Future<void> retryProfile() =>
      _canRetryProfile ? _run(_bootstrap) : Future<void>.value();

  Future<void> rename(String name) => _run(() async {
    profile = await request('PUT', '/api/v1/me', {
      'displayName': name.trim(),
    }) as Map<String, dynamic>;
  });

  Future<void> refresh() => _run(() async {
    profile = await request('GET', '/api/v1/me') as Map<String, dynamic>;
  });

  Future<Object?> request(
    String method,
    String path, [
    Object? body,
    String? idempotencyKey,
  ]) async {
    if (!path.startsWith('/api/v1/')) throw ArgumentError('Invalid API path');
    final generation = _generation;
    String? token;
    try {
      token = await tokens.accessToken();
    } catch (_) {
      if (generation == _generation) _clear();
      throw StateError('Session expired');
    }
    if (token == null) {
      if (generation == _generation) _clear();
      throw StateError('Session expired');
    }
    if (generation != _generation) throw StateError('Session changed');
    final request = http.Request(method, Uri.parse('$apiBaseUrl$path'))
      ..headers.addAll({
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      });
    if (idempotencyKey != null) {
      request.headers['Idempotency-Key'] = idempotencyKey;
    }
    if (body != null) request.body = jsonEncode(body);
    final response = await _client
        .send(request)
        .then(http.Response.fromStream)
        .timeout(const Duration(seconds: 15));
    if (generation != _generation) throw StateError('Session changed');
    if (response.statusCode == 401) _clear();
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AccountRequestException(response.statusCode);
    }
    if (response.statusCode == 204) return null;
    return jsonDecode(response.body);
  }

  Future<void> signOut() => _run(() async {
    _clear();
    await tokens.signOut();
  });

  Future<void> _run(Future<void> Function() action) async {
    if (busy) return;
    busy = true;
    error = null;
    notifyListeners();
    try {
      await action();
    } on AccountRequestException catch (e) {
      error = e.message;
    } catch (_) {
      error = 'We couldn’t complete that action. Please try again or sign in again.';
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _client.close();
    super.dispose();
  }
}

class AccountRequestException implements Exception {
  const AccountRequestException(this.status);
  final int status;
  String get message => switch (status) {
    400 => 'Check the fields and try again.',
    401 => 'Your session ended. Please sign in again.',
    403 || 404 => 'This item is not available to your account.',
    409 => 'This item changed or an action is pending. Refresh and review it before trying again.',
    503 => 'Bliss is temporarily unavailable. Please try again later.',
    _ => 'We couldn’t complete that action. Please try again.',
  };
}
