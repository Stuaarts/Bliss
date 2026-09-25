import '../marketplace/marketplace.dart';

class ConversationSummary {
  ConversationSummary.fromJson(Json j)
    : id = j['id'] as String,
      bookingId = j['bookingId'] as String,
      title = j['title'] as String;
  final String id, bookingId, title;
}

class ChatMessage {
  ChatMessage.fromJson(Json j)
    : id = j['id'] as String,
      sequence = j['sequence'] as int,
      body = j['body'] as String,
      senderName = j['senderName'] as String,
      senderRole = j['senderRole'] as String,
      mine = j['mine'] as bool,
      createdAt = DateTime.parse(j['createdAt'] as String);
  final String id, body, senderName, senderRole;
  final int sequence;
  final bool mine;
  final DateTime createdAt;
}

class MessagePage {
  MessagePage.fromJson(Json j)
    : items = (j['items'] as List)
          .map((e) => ChatMessage.fromJson(e as Json))
          .toList(),
      hasMore = j['hasMore'] as bool;
  final List<ChatMessage> items;
  final bool hasMore;
}
