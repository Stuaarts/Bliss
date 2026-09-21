import 'package:flutter/material.dart';

class ProviderForm extends StatefulWidget {
  const ProviderForm({super.key, this.initial});
  final Map<String, dynamic>? initial;
  @override
  State<ProviderForm> createState() => _ProviderFormState();
}

class _ProviderFormState extends State<ProviderForm> {
  final _form = GlobalKey<FormState>();
  late final _name = TextEditingController(
    text: widget.initial?['displayName'] as String?,
  );
  late final _description = TextEditingController(
    text: widget.initial?['description'] as String?,
  );
  late final _city = TextEditingController(
    text: widget.initial?['city'] as String?,
  );
  String _type = 'INDIVIDUAL';
  @override
  void dispose() {
    _name.dispose();
    _description.dispose();
    _city.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.initial == null ? 'Add a business' : 'Edit business'),
    content: SizedBox(
      width: 460,
      child: Form(
        key: _form,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _field(_name, 'Business name', 120),
              if (widget.initial == null)
                DropdownButtonFormField<String>(
                  isExpanded: true,
                  initialValue: _type,
                  decoration: const InputDecoration(labelText: 'Business type'),
                  items: const [
                    DropdownMenuItem(
                      value: 'INDIVIDUAL',
                      child: Text('Individual'),
                    ),
                    DropdownMenuItem(value: 'COMPANY', child: Text('Company')),
                  ],
                  onChanged: (value) => _type = value!,
                ),
              _field(_city, 'City', 120),
              _field(_description, 'Description', 2000, lines: 3),
            ],
          ),
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      FilledButton(
        onPressed: () {
          if (_form.currentState!.validate()) {
            Navigator.pop(context, {
              'displayName': _name.text.trim(),
              'description': _description.text.trim(),
              'city': _city.text.trim(),
              if (widget.initial == null)
                'type': _type
              else
                'version': widget.initial!['version'],
            });
          }
        },
        child: const Text('Save business'),
      ),
    ],
  );
}

class OfferingForm extends StatefulWidget {
  const OfferingForm({super.key, required this.categories, this.initial});
  final List<Map<String, dynamic>> categories;
  final Map<String, dynamic>? initial;
  @override
  State<OfferingForm> createState() => _OfferingFormState();
}

class _OfferingFormState extends State<OfferingForm> {
  final _form = GlobalKey<FormState>();
  late final _title = TextEditingController(
    text: widget.initial?['title'] as String?,
  );
  late final _description = TextEditingController(
    text: widget.initial?['description'] as String?,
  );
  late final _price = TextEditingController(
    text: widget.initial?['price'] as String?,
  );
  late final _currency = TextEditingController(
    text: widget.initial?['currency'] as String? ?? 'CAD',
  );
  late String? _category = widget.initial?['categoryId'] as String?;
  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    _price.dispose();
    _currency.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.initial == null ? 'Add a service' : 'Edit service'),
    content: SizedBox(
      width: 460,
      child: Form(
        key: _form,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _field(_title, 'Service title', 160),
              DropdownButtonFormField<String>(
                isExpanded: true,
                initialValue: _category,
                decoration: const InputDecoration(labelText: 'Category'),
                items: [
                  for (final c in widget.categories)
                    DropdownMenuItem(
                      value: c['id'] as String,
                      child: Text(c['name'] as String),
                    ),
                ],
                validator: (v) => v == null ? 'Choose a category.' : null,
                onChanged: (v) => _category = v,
              ),
              _field(_description, 'Description', 2000, lines: 3),
              TextFormField(
                controller: _price,
                decoration: const InputDecoration(labelText: 'Price'),
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                validator: (v) =>
                    RegExp(r'^(0|[1-9][0-9]{0,9})(\.[0-9]{1,2})?$')
                        .hasMatch(v?.trim() ?? '')
                    ? null
                    : 'Enter a non-negative price with up to two decimals.',
              ),
              TextFormField(
                controller: _currency,
                maxLength: 3,
                decoration: const InputDecoration(
                  labelText: 'Currency',
                  helperText: 'Three-letter currency with two decimal places, e.g. CAD.',
                  helperMaxLines: 3,
                ),
                textCapitalization: TextCapitalization.characters,
                validator: (v) =>
                    RegExp(r'^[A-Z]{3}$').hasMatch(v?.trim() ?? '')
                    ? null
                    : 'Use a three-letter currency code.',
              ),
            ],
          ),
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      FilledButton(
        onPressed: () {
          if (_form.currentState!.validate()) {
            Navigator.pop(context, {
              'title': _title.text.trim(),
              'description': _description.text.trim(),
              'categoryId': _category,
              'price': _price.text.trim(),
              'currency': _currency.text.trim(),
            });
          }
        },
        child: const Text('Save service'),
      ),
    ],
  );
}

Widget _field(
  TextEditingController controller,
  String label,
  int limit, {
  int lines = 1,
}) => TextFormField(
  controller: controller,
  maxLength: limit,
  minLines: lines,
  maxLines: lines,
  decoration: InputDecoration(labelText: label),
  validator: (v) =>
      v == null || v.trim().isEmpty ? 'This field is required.' : null,
);
