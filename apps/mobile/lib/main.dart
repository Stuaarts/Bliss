import 'package:flutter/material.dart';

import 'app/app.dart';
import 'core/api/api_client.dart';
import 'features/discovery/catalog.dart';

void main() {
  const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );
  runApp(BlissApp(catalog: CatalogRepository(ApiClient(baseUrl: baseUrl))));
}
