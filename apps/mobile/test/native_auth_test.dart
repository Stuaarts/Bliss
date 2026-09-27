import 'dart:async';

import 'package:bliss_mobile/core/auth/auth_native.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

const authChannel = MethodChannel('crossingthestreams.io/flutter_appauth');
const storageChannel = MethodChannel(
  'plugins.it_nomads.com/flutter_secure_storage',
);
const refreshKey = 'bliss.refresh-token';

Map<String, Object> response(String account) => {
  'accessToken': '$account-access',
  'refreshToken': '$account-refresh',
  'idToken': '$account-id',
  'accessTokenExpirationTime': DateTime.now()
      .add(const Duration(hours: 1))
      .millisecondsSinceEpoch,
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late Map<String, String> stored;
  late Future<Object?> Function(MethodCall) auth;
  Future<void> Function(MethodCall)? beforeStorage;

  setUp(() {
    stored = {};
    beforeStorage = null;
    auth = (call) async =>
        call.method == 'endSession' ? <String, Object>{} : response('current');
    messenger.setMockMethodCallHandler(authChannel, (call) => auth(call));
    messenger.setMockMethodCallHandler(storageChannel, (call) async {
      final key = call.arguments['key'] as String;
      final previous = stored[key];
      await beforeStorage?.call(call);
      switch (call.method) {
        case 'read':
          return previous;
        case 'write':
          stored[key] = call.arguments['value'] as String;
          return null;
        case 'delete':
          stored.remove(key);
          return null;
        default:
          throw StateError('Unexpected storage operation');
      }
    });
  });
  tearDown(() {
    messenger.setMockMethodCallHandler(authChannel, null);
    messenger.setMockMethodCallHandler(storageChannel, null);
  });

  test('refresh completing after logout cannot restore credentials', () async {
    stored[refreshKey] = 'previous-refresh';
    final started = Completer<void>();
    final reply = Completer<Object?>();
    auth = (call) {
      started.complete();
      return reply.future;
    };
    final tokens = NativeAuthTokens();
    final restoring = tokens.restore();
    await started.future;
    await tokens.signOut();
    reply.complete(response('previous'));
    await restoring;
    expect(await tokens.accessToken(), isNull);
    expect(stored, isEmpty);
  });

  test(
    'failed old refresh cannot erase a subsequently signed-in account',
    () async {
      stored[refreshKey] = 'previous-refresh';
      final started = Completer<void>();
      final reply = Completer<Object?>();
      auth = (call) {
        if (call.method == 'token') {
          started.complete();
          return reply.future;
        }
        return Future.value(response('new'));
      };
      final tokens = NativeAuthTokens();
      final restoring = tokens.restore();
      final failed = expectLater(restoring, throwsA(isA<PlatformException>()));
      await started.future;
      await tokens.signOut();
      await tokens.signIn();
      reply.completeError(PlatformException(code: 'invalid_grant'));
      await failed;
      expect(await tokens.accessToken(), 'new-access');
      expect(stored[refreshKey], 'new-refresh');
    },
  );

  test('logout wins over an already running secure-storage write', () async {
    final writing = Completer<void>();
    final release = Completer<void>();
    beforeStorage = (call) async {
      if (call.method == 'write') {
        writing.complete();
        await release.future;
      }
    };
    final tokens = NativeAuthTokens();
    final signingIn = tokens.signIn();
    await writing.future;
    final signingOut = tokens.signOut();
    await Future<void>.delayed(Duration.zero);
    release.complete();
    await Future.wait([signingIn, signingOut]);
    expect(stored, isEmpty);
    expect(await tokens.accessToken(), isNull);
  });

  test('a stale storage read cannot revive a signed-out session', () async {
    stored[refreshKey] = 'previous-refresh';
    final reading = Completer<void>();
    final release = Completer<void>();
    beforeStorage = (call) async {
      if (call.method == 'read') {
        reading.complete();
        await release.future;
      }
    };
    var exchanges = 0;
    auth = (call) async {
      exchanges++;
      return response('previous');
    };
    final tokens = NativeAuthTokens();
    final restoring = tokens.restore();
    await reading.future;
    await tokens.signOut();
    release.complete();
    await restoring;
    expect(exchanges, 0);
    expect(await tokens.accessToken(), isNull);
    expect(stored, isEmpty);
  });

  test(
    'parallel refresh is shared, rotation persists and logout uses latest hint',
    () async {
      stored[refreshKey] = 'previous-refresh';
      final started = Completer<void>();
      final reply = Completer<Object?>();
      var exchanges = 0;
      String? logoutHint;
      auth = (call) async {
        if (call.method == 'endSession') {
          logoutHint = call.arguments['idTokenHint'] as String?;
          return <String, Object>{};
        }
        exchanges++;
        expect(call.arguments['refreshToken'], 'previous-refresh');
        started.complete();
        return reply.future;
      };
      final tokens = NativeAuthTokens();
      final restoring = tokens.restore();
      await started.future;
      final parallel = tokens.accessToken();
      reply.complete(response('rotated'));
      await restoring;
      expect(await parallel, 'rotated-access');
      expect(exchanges, 1);
      expect(stored[refreshKey], 'rotated-refresh');
      await tokens.signOut();
      expect(logoutHint, 'rotated-id');
      expect(stored, isEmpty);
    },
  );

  test(
    'authorization returning after logout cannot sign the account back in',
    () async {
      final started = Completer<void>();
      final reply = Completer<Object?>();
      auth = (call) {
        started.complete();
        return reply.future;
      };
      final tokens = NativeAuthTokens();
      final signingIn = tokens.signIn();
      await started.future;
      await tokens.signOut();
      reply.complete(response('previous'));
      await signingIn;
      expect(await tokens.accessToken(), isNull);
      expect(stored, isEmpty);
    },
  );

  test(
    'failed storage write is reported without blocking later logout and login',
    () async {
      beforeStorage = (call) async {
        if (call.method == 'write') {
          throw PlatformException(code: 'storage_unavailable');
        }
      };
      final tokens = NativeAuthTokens();
      await expectLater(tokens.signIn(), throwsA(isA<PlatformException>()));
      expect(await tokens.accessToken(), isNull);
      beforeStorage = null;
      await tokens.signOut();
      await tokens.signIn();
      expect(await tokens.accessToken(), 'current-access');
      expect(stored[refreshKey], 'current-refresh');
    },
  );
}
