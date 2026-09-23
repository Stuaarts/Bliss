import 'package:flutter/material.dart';

import '../../core/auth/auth_session.dart';
import 'marketplace.dart';

class SessionGate extends StatefulWidget {
  const SessionGate({super.key, required this.session, required this.child});
  final AuthSession session;
  final Widget child;
  @override
  State<SessionGate> createState() => _SessionGateState();
}

class _SessionGateState extends State<SessionGate> {
  late final Object? _id = widget.session.profile?['id'];
  late final int _generation = widget.session.generation;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.session,
    builder: (context, _) {
      if (_id != null &&
          _id == widget.session.profile?['id'] &&
          _generation == widget.session.generation) {
        return widget.child;
      }
      return Scaffold(
        appBar: AppBar(title: const Text('Your activity')),
        body: const Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text(
              'Your session ended. Return to Your account and sign in again.',
            ),
          ),
        ),
      );
    },
  );
}

Future<T?> privatePage<T>(
  BuildContext context,
  AuthSession session,
  Widget child,
) => Navigator.of(context).push<T>(
  MaterialPageRoute(
    builder: (_) => SessionGate(session: session, child: child),
  ),
);

class MarketBody extends StatelessWidget {
  const MarketBody({super.key, required this.children});
  final List<Widget> children;
  @override
  Widget build(BuildContext context) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 850),
      child: ListView(padding: const EdgeInsets.all(24), children: children),
    ),
  );
}

class MarketError extends StatelessWidget {
  const MarketError(this.message, this.retry, {super.key});
  final String message;
  final VoidCallback? retry;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Semantics(liveRegion: true, child: Text(message)),
        if (retry != null)
          TextButton(onPressed: retry, child: const Text('Try again')),
      ],
    ),
  );
}

class Pager extends StatelessWidget {
  const Pager({
    super.key,
    required this.page,
    required this.total,
    required this.change,
  });
  final int page, total;
  final ValueChanged<int>? change;
  @override
  Widget build(BuildContext context) => total <= 20 && page == 0
      ? const SizedBox.shrink()
      : Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            TextButton(
              onPressed: page == 0 || change == null
                  ? null
                  : () => change!(page - 1),
              child: const Text('Previous'),
            ),
            Text('Page ${page + 1}'),
            TextButton(
              onPressed: (page + 1) * 20 >= total || change == null
                  ? null
                  : () => change!(page + 1),
              child: const Text('Next'),
            ),
          ],
        );
}

String localTime(BuildContext context, DateTime date) {
  final local = date.toLocal();
  return '${MaterialLocalizations.of(context).formatMediumDate(local)} · ${TimeOfDay.fromDateTime(local).format(context)}';
}

Future<bool> confirmAction(
  BuildContext context,
  String title,
  String detail,
) async =>
    await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(detail),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Go back'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(title),
          ),
        ],
      ),
    ) ??
    false;

class FieldSpec {
  const FieldSpec(
    this.key,
    this.label, {
    this.initial = '',
    this.limit = 120,
    this.optional = false,
    this.money = false,
    this.options,
  });
  final String key, label, initial;
  final int limit;
  final bool optional, money;
  final Map<String, String>? options;
}

class MarketForm extends StatefulWidget {
  const MarketForm({
    super.key,
    required this.title,
    required this.fields,
    required this.submit,
    this.summary,
    this.safeRetry = false,
  });
  final String title;
  final List<FieldSpec> fields;
  final String? summary;
  final bool safeRetry;
  final Future<Object?> Function(Map<String, String>) submit;
  @override
  State<MarketForm> createState() => _MarketFormState();
}

class _MarketFormState extends State<MarketForm> {
  final _form = GlobalKey<FormState>();
  late final _values = {
    for (final f in widget.fields)
      f.key: TextEditingController(text: f.initial),
  };
  bool _busy = false, _uncertain = false;
  String? _error;
  @override
  void dispose() {
    for (final controller in _values.values) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await widget.submit({
        for (final f in widget.fields) f.key: _values[f.key]!.text.trim(),
      });
      if (mounted) Navigator.pop(context, result);
    } catch (e) {
      if (mounted) {
        setState(() {
          _uncertain = e is! AccountRequestException || e.status >= 500;
          _error = _uncertain && !widget.safeRetry
              ? 'Submission may have succeeded. Go back and check your list before creating another.'
              : marketError(e);
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.title)),
    body: MarketBody(
      children: [
        if (widget.summary != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 24),
            child: Text(widget.summary!),
          ),
        if (_busy) const LinearProgressIndicator(),
        if (_error != null) MarketError(_error!, null),
        Form(
          key: _form,
          child: Column(
            children: [
              for (final f in widget.fields)
                Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: f.options != null
                      ? DropdownButtonFormField<String>(
                          initialValue: _values[f.key]!.text.isEmpty
                              ? null
                              : _values[f.key]!.text,
                          isExpanded: true,
                          decoration: InputDecoration(labelText: f.label),
                          items: [
                            for (final entry in f.options!.entries)
                              DropdownMenuItem(
                                value: entry.key,
                                child: Text(entry.value),
                              ),
                          ],
                          onChanged: _busy || _uncertain
                              ? null
                              : (v) => _values[f.key]!.text = v ?? '',
                          validator: (v) => v == null
                              ? 'Choose ${f.label.toLowerCase()}.'
                              : null,
                        )
                      : TextFormField(
                          controller: _values[f.key],
                          enabled: !_busy && !_uncertain,
                          maxLength: f.limit,
                          minLines: f.limit > 500 ? 3 : 1,
                          maxLines: f.limit > 500 ? 5 : 1,
                          keyboardType: f.money
                              ? const TextInputType.numberWithOptions(
                                  decimal: true,
                                )
                              : TextInputType.text,
                          decoration: InputDecoration(labelText: f.label),
                          validator: (value) {
                            final text = value?.trim() ?? '';
                            if (text.isEmpty) {
                              return f.optional
                                  ? null
                                  : 'Enter ${f.label.toLowerCase()}.';
                            }
                            if (f.money &&
                                !RegExp(r'^(0|[1-9][0-9]{0,9})(\.[0-9]{1,2})?$')
                                    .hasMatch(text)) {
                              return 'Use an amount such as 80.50.';
                            }
                            return null;
                          },
                        ),
                ),
            ],
          ),
        ),
        FilledButton(
          onPressed: _busy || (_uncertain && !widget.safeRetry)
              ? null
              : _submit,
          child: Text(_uncertain ? 'Retry original submission' : widget.title),
        ),
      ],
    ),
  );
}
