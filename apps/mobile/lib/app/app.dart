import 'package:flutter/material.dart';

import '../features/discovery/catalog.dart';
import '../features/discovery/discovery_screen.dart';

class BlissApp extends StatelessWidget {
  const BlissApp({super.key, required this.catalog});
  final CatalogRepository catalog;

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Bliss',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF176B52)),
      scaffoldBackgroundColor: const Color(0xFFF7F8F4),
      useMaterial3: true,
      appBarTheme: const AppBarTheme(backgroundColor: Color(0xFFF7F8F4)),
    ),
    home: DiscoveryScreen(catalog: catalog),
  );
}
