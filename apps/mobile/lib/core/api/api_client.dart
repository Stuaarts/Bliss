import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

class ApiException implements Exception {
  const ApiException(this.message);
  final String message;
  @override
  String toString() => message;
}

class ApiClient {
  ApiClient({required this.baseUrl, http.Client? client})
    : _client = client ?? http.Client();

  final String baseUrl;
  final http.Client _client;

  Future<Object?> get(String path, [Map<String, String>? query]) async {
    final uri = Uri.parse('$baseUrl$path').replace(queryParameters: query);
    try {
      final response = await _client
          .get(uri, headers: {'Accept': 'application/json'})
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) {
        throw const ApiException(
          'We couldn’t load services. Please try again.',
        );
      }
      return jsonDecode(response.body);
    } on TimeoutException {
      throw const ApiException(
        'The connection took too long. Please try again.',
      );
    } on http.ClientException {
      throw const ApiException(
        'Can’t reach Bliss. Check your connection and try again.',
      );
    } on FormatException {
      throw const ApiException(
        'We couldn’t read the response. Please try again.',
      );
    }
  }

  void close() => _client.close();
}
