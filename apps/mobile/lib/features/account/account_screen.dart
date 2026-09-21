import 'package:flutter/material.dart';

import '../../core/auth/auth_session.dart';

class AccountScreen extends StatefulWidget {
  const AccountScreen({super.key, required this.session});
  final AuthSession session;
  @override
  State<AccountScreen> createState() => _AccountScreenState();
}

class _AccountScreenState extends State<AccountScreen> {
  final _name = TextEditingController();
  final _form = GlobalKey<FormState>();
  Object? _profileId;
  @override
  void initState() {
    super.initState();
    widget.session.addListener(_syncProfile);
    _syncProfile();
  }

  void _syncProfile() {
    final id = widget.session.profile?['id'];
    if (id != _profileId) {
      _profileId = id;
      _name.text = widget.session.profile?['displayName'] as String? ?? '';
    }
  }

  @override
  void dispose() {
    widget.session.removeListener(_syncProfile);
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.session,
    builder: (context, _) {
      final session = widget.session;
      return Scaffold(
        appBar: AppBar(title: const Text('Your account')),
        body: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: ListView(
              padding: const EdgeInsets.all(24),
              children: [
                if (session.busy) const LinearProgressIndicator(),
                if (session.error != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Semantics(
                      liveRegion: true,
                      child: Text(session.error!),
                    ),
                  ),
                if (!session.signedIn) ...[
                  Text(
                    'Make yourself at home.',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 12),
                  const Text('Sign in to manage your Bliss profile.'),
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: session.busy ? null : session.signIn,
                    child: const Text('Sign in'),
                  ),
                ] else ...[
                  Text(
                    'Hello, ${session.profile!['displayName']}',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 8,
                    children: [
                      for (final role in session.capabilities)
                        Chip(label: Text(role)),
                    ],
                  ),
                  const SizedBox(height: 24),
                  Form(
                    key: _form,
                    child: TextFormField(
                      controller: _name,
                      maxLength: 120,
                      decoration: const InputDecoration(
                        labelText: 'Display name',
                      ),
                      validator: (value) =>
                          value == null || value.trim().isEmpty
                          ? 'Enter a display name.'
                          : null,
                    ),
                  ),
                  FilledButton(
                    onPressed: session.busy
                        ? null
                        : () async {
                            if (_form.currentState!.validate()) {
                              await session.rename(_name.text);
                            }
                          },
                    child: const Text('Save profile'),
                  ),
                  TextButton(
                    onPressed: session.busy ? null : session.refresh,
                    child: const Text('Refresh account'),
                  ),
                  const SizedBox(height: 24),
                  OutlinedButton(
                    onPressed: session.busy ? null : session.signOut,
                    child: const Text('Sign out'),
                  ),
                ],
              ],
            ),
          ),
        ),
      );
    },
  );
}
