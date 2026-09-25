import 'dart:async';
import 'dart:convert';

import 'package:bliss_mobile/features/conversation/chat_screen.dart';
import 'package:bliss_mobile/features/conversation/conversation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'marketplace_test.dart' show session, json, host;

const detail = {'id': 'chat', 'bookingId': 'booking', 'title': 'Garden care'};
Map<String, dynamic> message(int seq, {bool mine = false, String? body}) => {
  'id': 'message-$seq',
  'sequence': seq,
  'body': body ?? 'Message $seq',
  'senderName': 'Alex',
  'senderRole': mine ? 'CUSTOMER' : 'PROVIDER',
  'mine': mine,
  'createdAt': '2026-09-24T04:00:00Z',
};
void main() {
  test(
    'message mapping preserves plain text, UTC time and sender identity',
    () {
      final m = ChatMessage.fromJson(
        message(1, body: '<b>Hello</b> 👋\nSecond line'),
      );
      expect(m.body, '<b>Hello</b> 👋\nSecond line');
      expect(m.createdAt.isUtc, isTrue);
      expect(m.mine, isFalse);
    },
  );

  testWidgets(
    'failed initial load retries; sending preserves unseen intervening messages',
    (tester) async {
      var fail = true, sent = false;
      final cursors = <String?>[];
      final s = session((r) async {
        if (fail) return json({}, 503);
        if (!r.url.path.endsWith('/messages')) return json(detail);
        if (r.method == 'POST') {
          expect(jsonDecode(r.body), {'body': 'Hello'});
          expect(r.headers['Idempotency-Key'], isNotEmpty);
          sent = true;
          return json(message(3, mine: true, body: 'Hello'));
        }
        cursors.add(r.url.queryParameters['after']);
        return json({
          'items': sent
              ? [message(2), message(3, mine: true, body: 'Hello')]
              : [message(1)],
          'hasMore': false,
        });
      });
      await tester.pumpWidget(
        host(s, ChatScreen(session: s, conversationId: 'chat')),
      );
      await tester.pumpAndSettle();
      expect(find.text('Try again'), findsOneWidget);
      fail = false;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Hello');
      await tester.tap(find.text('Send'));
      await tester.pumpAndSettle();
      expect(cursors.last, '1');
      expect(find.text('Message 2'), findsOneWidget);
      expect(find.text('Hello'), findsOneWidget);
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        isEmpty,
      );
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'lost send response freezes the draft and retries with the same key',
    (tester) async {
      final requests = <http.Request>[];
      final s = session((r) async {
        if (r.method == 'POST') {
          requests.add(r);
          if (requests.length == 1) throw http.ClientException('Lost response');
          return json(message(1, mine: true, body: 'Keep this text'));
        }
        if (!r.url.path.endsWith('/messages')) return json(detail);
        return json({
          'items': requests.isEmpty
              ? []
              : [message(1, mine: true, body: 'Keep this text')],
          'hasMore': false,
        });
      });
      await tester.pumpWidget(
        host(s, ChatScreen(session: s, conversationId: 'chat')),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('No messages yet. Start the conversation.'),
        findsOneWidget,
      );
      await tester.enterText(find.byType(TextField), 'Keep this text');
      await tester.tap(find.text('Send'));
      await tester.pumpAndSettle();
      expect(tester.widget<TextField>(find.byType(TextField)).enabled, isFalse);
      await tester.tap(find.text('Retry send'));
      await tester.pumpAndSettle();
      expect(requests, hasLength(2));
      expect(
        requests[1].headers['Idempotency-Key'],
        requests[0].headers['Idempotency-Key'],
      );
      expect(requests[1].body, requests[0].body);
      expect(find.text('Keep this text'), findsOneWidget);
      expect(tester.widget<TextField>(find.byType(TextField)).enabled, isTrue);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets(
    'earlier messages use an exclusive cursor without shifting new-message polling',
    (tester) async {
      final queries = <Map<String, String>>[];
      final s = session((r) async {
        if (!r.url.path.endsWith('/messages')) return json(detail);
        queries.add(r.url.queryParameters);
        final older = r.url.queryParameters.containsKey('before'),
            after = r.url.queryParameters.containsKey('after');
        return json({
          'items': older
              ? [message(1), message(2)]
              : after
              ? [message(5)]
              : [message(3), message(4)],
          'hasMore': !older && !after,
        });
      });
      await tester.pumpWidget(
        host(s, ChatScreen(session: s, conversationId: 'chat')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Load earlier messages'));
      await tester.pumpAndSettle();
      expect(queries.last['before'], '3');
      expect(find.text('Load earlier messages'), findsNothing);
      await tester.tap(find.byTooltip('Refresh messages'));
      await tester.pumpAndSettle();
      expect(queries.last['after'], '4');
      expect(find.text('Message 5'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );

  testWidgets('access loss clears private history and disables sending', (
    tester,
  ) async {
    var denied = false;
    final s = session((r) async {
      if (denied) return json({}, 404);
      return json(
        r.url.path.endsWith('/messages')
            ? {
                'items': [message(1)],
                'hasMore': false,
              }
            : detail,
      );
    });
    await tester.pumpWidget(
      host(s, ChatScreen(session: s, conversationId: 'chat')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Private draft');
    denied = true;
    await tester.tap(find.byTooltip('Refresh messages'));
    await tester.pumpAndSettle();
    expect(find.text('Message 1'), findsNothing);
    expect(find.text('Garden care'), findsNothing);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller!.text,
      isEmpty,
    );
    expect(
      tester
          .widget<FilledButton>(find.widgetWithText(FilledButton, 'Send'))
          .onPressed,
      isNull,
    );
    await tester.pumpWidget(const SizedBox());
    s.dispose();
  });

  testWidgets(
    'logout hides a pending send and its late response at phone width',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final response = Completer<http.Response>();
      final s = session((r) async {
        if (r.method == 'POST') return response.future;
        return json(
          r.url.path.endsWith('/messages')
              ? {'items': [], 'hasMore': false}
              : detail,
        );
      });
      await tester.pumpWidget(
        host(s, ChatScreen(session: s, conversationId: 'chat')),
      );
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Private phone draft');
      await tester.tap(find.text('Send'));
      await tester.pump();
      await s.signOut();
      await tester.pumpAndSettle();
      response.complete(json(message(1, mine: true)));
      await tester.pumpAndSettle();
      expect(find.byType(TextField), findsNothing);
      expect(find.textContaining('Your session ended.'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      s.dispose();
    },
  );
}
