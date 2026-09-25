import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/auth/auth_session.dart';
import '../marketplace/marketplace.dart';
import '../marketplace/market_widgets.dart';
import 'conversation.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({
    super.key,
    required this.session,
    required this.conversationId,
  });
  final AuthSession session;
  final String conversationId;
  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  late final String _path =
      '/api/v1/conversations/${widget.conversationId}/messages';
  late final _draft = TextEditingController(
    text:
        (widget.session.pendingBody(_path) as Json?)?['body'] as String? ?? '',
  );
  final _scroll = ScrollController();
  Timer? _timer;
  String? _title, _error;
  List<ChatMessage> _messages = [];
  bool _fetching = false,
      _sending = false,
      _ready = false,
      _denied = false,
      _older = false;
  int _newest = 0;
  @override
  void initState() {
    super.initState();
    _load();
    _timer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (mounted &&
          !_denied &&
          !_sending &&
          !_fetching &&
          widget.session.signedIn &&
          (ModalRoute.of(context)?.isCurrent ?? false)) {
        _load(quiet: true);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _draft.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _merge(List<ChatMessage> items) {
    final byId = {for (final m in _messages) m.id: m};
    for (final m in items) {
      byId[m.id] = m;
    }
    _messages = byId.values.toList()
      ..sort((a, b) => a.sequence.compareTo(b.sequence));
  }

  void _failure(Object error) {
    if (!mounted) return;
    setState(() {
      _error = marketError(error);
      if (error is AccountRequestException &&
          [401, 403, 404].contains(error.status)) {
        _messages = [];
        _title = null;
        _ready = false;
        _denied = true;
        _newest = 0;
        _older = false;
        _draft.clear();
      }
    });
  }

  Future<void> _load({bool quiet = false, bool older = false}) async {
    if (_fetching) return;
    _fetching = true;
    if (!quiet) setState(() => _error = null);
    try {
      String? title = _title;
      if (!_ready) {
        final detail = await widget.session.request(
          'GET',
          '/api/v1/conversations/${widget.conversationId}',
        ) as Json;
        title = ConversationSummary.fromJson(detail).title;
      }
      final cursor = !_ready
          ? ''
          : older
          ? '&before=${_messages.first.sequence}'
          : '&after=$_newest';
      final page = MessagePage.fromJson(
        await widget.session.request('GET', '$_path?size=50$cursor') as Json,
      );
      if (!mounted) return;
      final changed =
          !_ready || page.items.isNotEmpty || _error != null || !quiet;
      if (changed) {
        setState(() {
          _title = title;
          if (!_ready || older) _older = page.hasMore;
          _merge(page.items);
          // Sending may insert a receipt ahead of intervening messages. Only reads
          // advance this cursor so polling still retrieves those messages.
          if (!older && page.items.isNotEmpty) {
            _newest = page.items.last.sequence;
          }
          _ready = true;
          _denied = false;
          _error = null;
        });
      }
    } catch (e) {
      _failure(e);
    } finally {
      _fetching = false;
      if (mounted && !quiet) setState(() {});
    }
  }

  Future<void> _send() async {
    final body =
        widget.session.pendingBody(_path) as Json? ??
        {'body': _draft.text.trim()};
    if ((body['body'] as String).isEmpty) {
      setState(() => _error = 'Write a message first.');
      return;
    }
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      final message = ChatMessage.fromJson(
        await widget.session.idempotentPost(_path, body) as Json,
      );
      if (!mounted) return;
      setState(() {
        _merge([message]);
        _draft.clear();
      });
      if (_scroll.hasClients) _scroll.jumpTo(0);
      await _load(quiet: true);
    } catch (e) {
      _failure(e);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final pending = widget.session.hasPending(_path);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Messages'),
        actions: [
          IconButton(
            tooltip: 'Refresh messages',
            onPressed: _fetching ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 850),
            child: Column(
              children: [
                if (_title != null)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
                    child: Text(
                      _title!,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                if (!_ready && _fetching) const LinearProgressIndicator(),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    child: MarketError(_error!, _fetching ? null : _load),
                  ),
                Expanded(
                  child: !_ready
                      ? const SizedBox()
                      : _messages.isEmpty
                      ? const Center(
                          child: Text(
                            'No messages yet. Start the conversation.',
                          ),
                        )
                      : ListView.builder(
                          controller: _scroll,
                          reverse: true,
                          padding: const EdgeInsets.all(16),
                          itemCount: _messages.length + (_older ? 1 : 0),
                          itemBuilder: (context, index) {
                            if (index == _messages.length) {
                              return TextButton(
                                onPressed: _fetching
                                    ? null
                                    : () => _load(older: true),
                                child: const Text('Load earlier messages'),
                              );
                            }
                            final m = _messages[_messages.length - 1 - index];
                            return Align(
                              key: ValueKey(m.id),
                              alignment: m.mine
                                  ? Alignment.centerRight
                                  : Alignment.centerLeft,
                              child: Container(
                                constraints: const BoxConstraints(
                                  maxWidth: 560,
                                ),
                                margin: const EdgeInsets.only(bottom: 12),
                                padding: const EdgeInsets.all(14),
                                decoration: BoxDecoration(
                                  color: m.mine
                                      ? Theme.of(context)
                                            .colorScheme
                                            .primaryContainer
                                      : Theme.of(context)
                                            .colorScheme
                                            .surfaceContainerHighest,
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      '${m.mine ? 'You' : m.senderName} · ${m.senderRole == 'PROVIDER' ? 'Provider' : 'Customer'}',
                                      style: Theme.of(context)
                                          .textTheme
                                          .labelMedium,
                                    ),
                                    const SizedBox(height: 6),
                                    SelectableText(m.body),
                                    const SizedBox(height: 6),
                                    Text(
                                      localTime(context, m.createdAt),
                                      style: Theme.of(context)
                                          .textTheme
                                          .labelSmall,
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                ),
                if (pending && _ready)
                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 20),
                    child: Text(
                      'Delivery is unconfirmed. Retry the original message before writing another.',
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _draft,
                          enabled: _ready && !_denied && !_sending && !pending,
                          minLines: 1,
                          maxLines: 4,
                          maxLength: 2000,
                          decoration: const InputDecoration(
                            labelText: 'Message',
                            border: OutlineInputBorder(),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Padding(
                        padding: const EdgeInsets.only(bottom: 24),
                        child: FilledButton(
                          onPressed: _ready && !_denied && !_sending
                              ? _send
                              : null,
                          child: Text(
                            _sending
                                ? 'Sending…'
                                : pending
                                ? 'Retry send'
                                : 'Send',
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
