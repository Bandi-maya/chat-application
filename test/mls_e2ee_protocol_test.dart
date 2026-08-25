import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:openmls/openmls.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'OpenMLS encrypts application payload across two independent devices',
    () async {
      await Openmls.init();
      const suite = MlsCiphersuite.mls128DhkemX25519Aes128GcmSha256Ed25519;
      final defaults = MlsGroupConfig.defaultConfig(ciphersuite: suite);
      final config = MlsGroupConfig(
        ciphersuite: suite,
        wireFormatPolicy: MlsWireFormatPolicy.ciphertext,
        useRatchetTreeExtension: true,
        maxPastEpochs: defaults.maxPastEpochs < 5 ? 5 : defaults.maxPastEpochs,
        paddingSize: defaults.paddingSize,
        senderRatchetMaxOutOfOrder: defaults.senderRatchetMaxOutOfOrder,
        senderRatchetMaxForwardDistance:
            defaults.senderRatchetMaxForwardDistance,
        numberOfResumptionPsks: defaults.numberOfResumptionPsks,
      );

      final alice = await MlsEngine.create(
        dbPath: ':memory:',
        encryptionKey: List<int>.generate(32, (index) => index + 1),
      );
      final bob = await MlsEngine.create(
        dbPath: ':memory:',
        encryptionKey: List<int>.generate(32, (index) => 255 - index),
      );

      try {
        final aliceKey = MlsSignatureKeyPair.generate(ciphersuite: suite);
        final bobKey = MlsSignatureKeyPair.generate(ciphersuite: suite);
        final aliceSigner = serializeSigner(
          ciphersuite: suite,
          privateKey: aliceKey.privateKey(),
          publicKey: aliceKey.publicKey(),
        );
        final bobSigner = serializeSigner(
          ciphersuite: suite,
          privateKey: bobKey.privateKey(),
          publicKey: bobKey.publicKey(),
        );

        final created = await alice.createGroup(
          config: config,
          signerBytes: aliceSigner,
          credentialIdentity: utf8.encode('alice:device-a'),
          signerPublicKey: aliceKey.publicKey(),
        );
        final bobPackage = await bob.createKeyPackage(
          ciphersuite: suite,
          signerBytes: bobSigner,
          credentialIdentity: utf8.encode('bob:device-b'),
          signerPublicKey: bobKey.publicKey(),
        );
        final add = await alice.addMembers(
          groupIdBytes: created.groupId,
          signerBytes: aliceSigner,
          keyPackagesBytes: <Uint8List>[bobPackage.keyPackageBytes],
        );
        final joined = await bob.joinGroupFromWelcome(
          config: config,
          welcomeBytes: add.welcome,
          signerBytes: bobSigner,
        );
        expect(base64Encode(joined.groupId), base64Encode(created.groupId));

        final cleartext = utf8.encode(
          jsonEncode(<String, dynamic>{
            'conversation_id': 'conversation-1',
            'text': 'MLS private message',
          }),
        );
        final encrypted = await alice.createMessage(
          groupIdBytes: created.groupId,
          signerBytes: aliceSigner,
          message: cleartext,
        );
        expect(encrypted.ciphertext, isNot(cleartext));
        expect(
          utf8.decode(encrypted.ciphertext, allowMalformed: true),
          isNot(contains('MLS private message')),
        );

        final decrypted = await bob.processMessage(
          groupIdBytes: joined.groupId,
          messageBytes: encrypted.ciphertext,
        );
        expect(decrypted.applicationMessage, isNotNull);
        expect(decrypted.applicationMessage, orderedEquals(cleartext));

        final replyCleartext = utf8.encode('reply from bob');
        final reply = await bob.createMessage(
          groupIdBytes: joined.groupId,
          signerBytes: bobSigner,
          message: replyCleartext,
        );
        final replyDecrypted = await alice.processMessage(
          groupIdBytes: created.groupId,
          messageBytes: reply.ciphertext,
        );
        expect(
          replyDecrypted.applicationMessage,
          orderedEquals(replyCleartext),
        );
      } finally {
        await alice.close();
        await bob.close();
      }
    },
  );
}
