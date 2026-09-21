import 'dart:async';
import 'dart:convert';

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
    profile =
        await request('POST', '/api/v1/me/bootstrap') as Map<String, dynamic>;
  }

  Future<void> rename(String name) => _run(() async {
    profile = await request('PUT', '/api/v1/me', {
      'displayName': name.trim(),
    }) as Map<String, dynamic>;
  });

  Future<void> refresh() => _run(() async {
    profile = await request('GET', '/api/v1/me') as Map<String, dynamic>;
  });

  Future<Object?> request(String method, String path, [Object? body]) async {
    if (!path.startsWith('/api/v1/')) throw ArgumentError('Invalid API path');
    String? token;
    try {
      token = await tokens.accessToken();
    } catch (_) {
      profile = null;
      notifyListeners();
      throw StateError('Session expired');
    }
    if (token == null) {
      profile = null;
      notifyListeners();
      throw StateError('Session expired');
    }
    final request = http.Request(method, Uri.parse('$apiBaseUrl$path'))
      ..headers.addAll({
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      });
    if (body != null) request.body = jsonEncode(body);
    final response = await _client
        .send(request)
        .then(http.Response.fromStream)
        .timeout(const Duration(seconds: 15));
    if (response.statusCode == 401) {
      profile = null;
      notifyListeners();
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AccountRequestException(response.statusCode);
    }
    if (response.statusCode == 204) return null;
    return jsonDecode(response.body);
  }

  Future<void> signOut() => _run(() async {
    profile = null;
    notifyListeners();
    await tokens.signOut();
  });

  Future<void> _run(Future<void> Function() action) async {
    if (busy) return;
    busy = true;
    error = null;
    notifyListeners();
    try {
      await action();
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
    403 || 404 => 'This business or service is not available to your account.',
    409 => 'This record changed. Refresh before editing again.',
    _ => 'We couldn’t complete that action. Please try again.',
  };
}
