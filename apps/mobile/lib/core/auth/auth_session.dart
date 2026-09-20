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
        await _request('POST', '/api/v1/me/bootstrap') as Map<String, dynamic>;
  }

  Future<void> rename(String name) => _run(() async {
    profile = await _request('PUT', '/api/v1/me', {
      'displayName': name.trim(),
    }) as Map<String, dynamic>;
  });

  Future<void> refresh() => _run(() async {
    profile = await _request('GET', '/api/v1/me') as Map<String, dynamic>;
  });

  Future<Object?> _request(String method, String path, [Object? body]) async {
    String? token;
    try {
      token = await tokens.accessToken();
    } catch (_) {
      profile = null;
      throw StateError('Session expired');
    }
    if (token == null) {
      profile = null;
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
    if (response.statusCode == 401) profile = null;
    if (response.statusCode != 200) throw StateError('Request failed');
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
