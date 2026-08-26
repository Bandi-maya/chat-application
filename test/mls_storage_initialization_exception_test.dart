import 'package:chat/data/services/mls_e2ee_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('MlsStorageInitializationException', () {
    test('classifies the Android try_lock compatibility failure', () {
      final error = MlsStorageInitializationException.fromCause(
        Exception(
          'failed to lock /data/user/0/com.example.chat/files/'
          'chat_mls_user.db.lock; try_lock() not supported',
        ),
      );

      expect(
        error.failure,
        MlsStorageInitializationFailure.unsupportedPlatformLock,
      );
      expect(error.message, isNot(contains('/data/user')));
    });

    test('keeps genuine single-writer contention distinct', () {
      final error = MlsStorageInitializationException.fromCause(
        Exception(
          'Database is already open by another connection or process',
        ),
      );

      expect(
        error.failure,
        MlsStorageInitializationFailure.databaseInUse,
      );
    });

    test('sanitizes unknown native initialization failures', () {
      final error = MlsStorageInitializationException.fromCause(
        Exception('secret database path and implementation detail'),
      );

      expect(error.failure, MlsStorageInitializationFailure.unavailable);
      expect(error.message, isNot(contains('secret database path')));
    });
  });
}
