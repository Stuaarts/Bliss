import 'dart:convert';

import 'package:bliss_mobile/app/app.dart';
import 'package:bliss_mobile/core/api/api_client.dart';
import 'package:bliss_mobile/features/discovery/catalog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const category = {'id': 'cleaning', 'slug': 'cleaning', 'name': 'Cleaning'};
const offering = {
  'id': 'offering-1',
  'title': 'Home cleaning',
  'description': 'A fresh start.',
  'price': '80.00',
  'currency': 'CAD',
  'category': category,
  'provider': {'displayName': 'Alex', 'city': 'Toronto'},
};

void main() {
  test('preserves decimal money and sends category/page filters', () async {
    final api = ApiClient(
      baseUrl: 'http://example.test',
      client: MockClient((request) async {
        expect(request.url.path, '/api/v1/offerings');
        expect(request.url.queryParameters, {
          'page': '2',
          'size': '20',
          'categoryId': 'cleaning',
        });
        return http.Response(
          jsonEncode({
            'items': [offering],
            'total': 41,
          }),
          200,
        );
      }),
    );
    addTearDown(api.close);
    final page = await CatalogRepository(api)
        .offerings(categoryId: 'cleaning', page: 2);
    expect(page.items.single.price, '80.00');
    expect(page.total, 41);
  });

  test('server failure does not expose server response details', () async {
    final api = ApiClient(
      baseUrl: 'http://example.test',
      client: MockClient(
        (_) async => http.Response('private SQL details', 500),
      ),
    );
    addTearDown(api.close);
    await expectLater(
      api.get('/api/v1/categories'),
      throwsA(
        isA<ApiException>().having(
          (e) => e.message,
          'message',
          isNot(contains('SQL')),
        ),
      ),
    );
  });

  testWidgets('loads catalog and opens offering details', (tester) async {
    final api = ApiClient(
      baseUrl: 'http://example.test',
      client: MockClient(
        (request) async => http.Response(
          jsonEncode(
            request.url.path.endsWith('/categories')
                ? [category]
                : {
                    'items': [offering],
                    'total': 1,
                  },
          ),
          200,
        ),
      ),
    );
    addTearDown(api.close);
    await tester.pumpWidget(BlissApp(catalog: CatalogRepository(api)));
    await tester.pumpAndSettle();
    expect(find.text('Home cleaning'), findsOneWidget);
    expect(find.text('CAD 80.00'), findsOneWidget);
    await tester.tap(find.text('Home cleaning'));
    await tester.pumpAndSettle();
    expect(find.text('A fresh start.'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'retry recovers from failure and category filter shows empty state',
    (tester) async {
      var fail = true;
      final api = ApiClient(
        baseUrl: 'http://example.test',
        client: MockClient((request) async {
          if (fail) return http.Response('', 503);
          if (request.url.path.endsWith('/categories')) {
            return http.Response(jsonEncode([category]), 200);
          }
          final filtered = request.url.queryParameters.containsKey(
            'categoryId',
          );
          return http.Response(
            jsonEncode({
              'items': filtered ? [] : [offering],
              'total': filtered ? 0 : 1,
            }),
            200,
          );
        }),
      );
      addTearDown(api.close);
      await tester.pumpWidget(BlissApp(catalog: CatalogRepository(api)));
      await tester.pumpAndSettle();
      expect(find.text('Try again'), findsOneWidget);
      fail = false;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text('Home cleaning'), findsOneWidget);
      await tester.tap(find.widgetWithText(ChoiceChip, 'Cleaning'));
      await tester.pumpAndSettle();
      expect(find.text('More help is on the way'), findsOneWidget);
      expect(find.text('Home cleaning'), findsNothing);
    },
  );

  testWidgets('narrow screen with large text does not overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final api = ApiClient(
      baseUrl: 'http://example.test',
      client: MockClient(
        (request) async => http.Response(
          jsonEncode(
            request.url.path.endsWith('/categories')
                ? [category]
                : {
                    'items': [offering],
                    'total': 1,
                  },
          ),
          200,
        ),
      ),
    );
    addTearDown(api.close);
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(textScaler: TextScaler.linear(1.5)),
        child: BlissApp(catalog: CatalogRepository(api)),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
