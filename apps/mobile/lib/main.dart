import 'package:flutter/material.dart';

import 'app/app.dart';
import 'core/api/api_client.dart';
import 'features/discovery/catalog.dart';
import 'core/auth/auth_platform.dart';
import 'core/auth/auth_session.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8088',
  );
  final session = AuthSession(tokens: createAuthTokens(), apiBaseUrl: baseUrl);
  runApp(
    BlissApp(
      catalog: CatalogRepository(ApiClient(baseUrl: baseUrl)),
      session: session,
    ),
  );
  await session.restore();
}
