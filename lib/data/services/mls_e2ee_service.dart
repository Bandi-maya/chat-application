import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:openmls/openmls.dart';
import 'package:path_provider/path_provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

/// Production client-side RFC 9420 Messaging Layer Security transport.
///
/// Security boundaries:
/// - MLS private keys and ratchet state never leave the device.
/// - Ratchet state is persisted only in OpenMLS' SQLCipher database.
/// - The SQLCipher key and signature private key are held in secure storage.
/// - Supabase receives only public device material, one-time KeyPackages,
///   Welcome/Commit protocol messages and opaque application ciphertexts.
/// - Membership drift fails closed. Chaty never silently falls back to
///   plaintext when an MLS conversation cannot be synchronized.
class MlsE2eeService extends ChangeNotifier {
  MlsE2eeService({SupabaseClient? client, FlutterSecureStorage? secureStorage})
    : _client = client ?? Supabase.instance.client,
      _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const String protocolSuite = 'mls-rfc9420-v1';
  static const String serverCiphersuite =
      'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519';
  static const int targetKeyPackagePool = 10;
  static const int replenishBelow = 5;

  static const MlsCiphersuite _ciphersuite =
      MlsCiphersuite.mls128DhkemX25519Aes128GcmSha256Ed25519;

  final SupabaseClient _client;
  final FlutterSecureStorage _secureStorage;
  final Uuid _uuid = const Uuid();
  final Random _random = Random.secure();
  final Map<String, _ConversationGate> _conversationGates =
      <String, _ConversationGate>{};

  MlsEngine? _engine;
  Uint8List? _signerBytes;
  Uint8List? _signerPublicKey;
  String? _userId;
  String? _deviceId;
  String? _credentialIdentity;
  bool _initializing = false;

  bool get isReady =>
      _engine != null &&
      _signerBytes != null &&
      _signerPublicKey != null &&
      _userId != null &&
      _deviceId != null;
  String? get currentDeviceId => _deviceId;
  String? get currentUserId => _userId;

  MlsGroupConfig get _groupConfig {
    final defaults = MlsGroupConfig.defaultConfig(ciphersuite: _ciphersuite);
    return MlsGroupConfig(
      ciphersuite: _ciphersuite,
      wireFormatPolicy: MlsWireFormatPolicy.ciphertext,
      useRatchetTreeExtension: true,
      maxPastEpochs: max(defaults.maxPastEpochs, 5),
      paddingSize: defaults.paddingSize,
      senderRatchetMaxOutOfOrder: defaults.senderRatchetMaxOutOfOrder,
      senderRatchetMaxForwardDistance: defaults.senderRatchetMaxForwardDistance,
      numberOfResumptionPsks: defaults.numberOfResumptionPsks,
    );
  }

  Future<void> initializeForCurrentSession() async {
    if (_initializing) {
      while (_initializing) {
        await Future<void>.delayed(const Duration(milliseconds: 20));
      }
      return;
    }

    final user = _client.auth.currentUser;
    if (user == null) {
      await close();
      return;
    }
    if (isReady && _userId == user.id) return;

    _initializing = true;
    try {
      await close();
      await Openmls.init();

      final userId = user.id;
      final prefix = 'chaty.mls.$userId';
      final deviceId = await _loadOrCreateDeviceId(prefix);
      final dbKey = await _loadOrCreateSecret('$prefix.db_key.v1', 32);
      final signerMaterial = await _loadOrCreateSigner(prefix);

      final support = await getApplicationSupportDirectory();
      final safeUser = userId.replaceAll(RegExp(r'[^A-Za-z0-9_-]'), '_');
      final dbPath = '${support.path}/chaty_mls_$safeUser.db';
      final engine = await MlsEngine.create(
        dbPath: dbPath,
        encryptionKey: dbKey,
      );

      final signerBytes = serializeSigner(
        ciphersuite: _ciphersuite,
        privateKey: signerMaterial.privateKey,
        publicKey: signerMaterial.publicKey,
      );

      _engine = engine;
      _signerBytes = Uint8List.fromList(signerBytes);
      _signerPublicKey = Uint8List.fromList(signerMaterial.publicKey);
      _userId = userId;
      _deviceId = deviceId;
      _credentialIdentity = '$userId:$deviceId';

      _zero(dbKey);
      _zero(signerMaterial.privateKey);

      await _registerAndReplenishDevice();
      notifyListeners();
    } catch (_) {
      await close();
      rethrow;
    } finally {
      _initializing = false;
    }
  }

  /// Ensures the local device is a synchronized active member of the server's
  /// MLS group for [conversationId]. This method never creates a plaintext
  /// fallback. If another device must publish a membership commit first, it
  /// throws [MlsMembershipPendingException].
  Future<MlsConversationState> ensureConversationReady(
    String conversationId,
  ) async {
    await initializeForCurrentSession();
    return _gate(conversationId).run<MlsConversationState>(
      () => _ensureConversationReadyLocked(conversationId),
    );
  }

  Future<MlsEncryptedPayload> encryptPayload({
    required String conversationId,
    required Map<String, dynamic> payload,
  }) async {
    await initializeForCurrentSession();
    return _gate(conversationId).run<MlsEncryptedPayload>(() async {
      final state = await _ensureConversationReadyLocked(conversationId);
      final group = state.group;
      if (group == null) {
        throw const MlsE2eeException('MLS group is not initialized.');
      }
      final engine = _requireEngine();
      final groupId = base64Decode(group.groupId);
      final localEpoch = await engine.groupEpoch(groupIdBytes: groupId);
      if (localEpoch.toInt() != group.epoch) {
        throw MlsE2eeException(
          'MLS epoch mismatch: local=${localEpoch.toInt()} server=${group.epoch}.',
        );
      }

      final envelope = <String, dynamic>{
        'version': 1,
        'conversation_id': conversationId,
        'payload': payload,
      };
      final encrypted = await engine.createMessage(
        groupIdBytes: groupId,
        signerBytes: _requireSigner(),
        message: utf8.encode(jsonEncode(envelope)),
        aad: Uint8List.fromList(
          utf8.encode('chaty:mls:rfc9420:v1:$conversationId'),
        ),
      );
      return MlsEncryptedPayload(
        groupId: group.groupId,
        epoch: localEpoch.toInt(),
        ciphertext: base64Encode(encrypted.ciphertext),
      );
    });
  }

  Future<Map<String, dynamic>> decryptPayload({
    required String conversationId,
    required String ciphertext,
  }) async {
    await initializeForCurrentSession();
    return _gate(conversationId).run<Map<String, dynamic>>(() async {
      final state = await _ensureConversationReadyLocked(conversationId);
      final group = state.group;
      if (group == null) {
        throw const MlsE2eeException('MLS group is not initialized.');
      }
      final result = await _requireEngine().processMessage(
        groupIdBytes: base64Decode(group.groupId),
        messageBytes: base64Decode(ciphertext),
      );
      final bytes = result.applicationMessage;
      if (bytes == null) {
        throw const MlsE2eeException(
          'MLS message did not contain application plaintext.',
        );
      }
      final decoded = jsonDecode(utf8.decode(bytes));
      if (decoded is! Map) {
        throw const MlsE2eeException('Invalid encrypted Chaty payload.');
      }
      final envelope = Map<String, dynamic>.from(decoded);
      if (envelope['version'] != 1 ||
          envelope['conversation_id']?.toString() != conversationId ||
          envelope['payload'] is! Map) {
        throw const MlsE2eeException(
          'Encrypted payload failed Chaty context validation.',
        );
      }
      return Map<String, dynamic>.from(envelope['payload'] as Map);
    });
  }

  Future<MlsEncryptedPayload> encryptEditedPayload({
    required String conversationId,
    required Map<String, dynamic> payload,
  }) => encryptPayload(conversationId: conversationId, payload: payload);

  /// Derives an MLS exporter secret for encrypted attachment key material.
  /// Phase 2 uses this method to AES-GCM encrypt bytes before Storage upload.
  Future<Uint8List> exportAttachmentSecret({
    required String conversationId,
    required String attachmentId,
  }) async {
    await initializeForCurrentSession();
    return _gate(conversationId).run<Uint8List>(() async {
      final state = await _ensureConversationReadyLocked(conversationId);
      final group = state.group;
      if (group == null) {
        throw const MlsE2eeException('MLS group is not initialized.');
      }
      return _requireEngine().exportSecret(
        groupIdBytes: base64Decode(group.groupId),
        label: 'chaty-attachment-v1',
        context: utf8.encode('$conversationId:$attachmentId'),
        keyLength: 32,
      );
    });
  }

  Future<void> close() async {
    final engine = _engine;
    _engine = null;
    if (engine != null) {
      try {
        await engine.close();
      } catch (_) {}
    }
    _zero(_signerBytes);
    _zero(_signerPublicKey);
    _signerBytes = null;
    _signerPublicKey = null;
    _userId = null;
    _deviceId = null;
    _credentialIdentity = null;
    _conversationGates.clear();
    notifyListeners();
  }

  /// Irreversibly removes every local trace of [userId]'s MLS identity:
  /// closes the engine, deletes the per-user SQLCipher database including
  /// SQLite sidecar files, and erases derived secrets from secure storage.
  /// Used by permanent account deletion; regular logout keeps identity data.
  Future<void> purgeLocalIdentityForUser(String userId) async {
    final prefix = 'chaty.mls.$userId';
    final wasLoadedForUser = _userId == userId;
    await close();

    final support = await getApplicationSupportDirectory();
    final safeUser = userId.replaceAll(RegExp(r'[^A-Za-z0-9_-]'), '_');
    final basePath = '${support.path}/chaty_mls_$safeUser';
    for (final path in <String>[basePath, '$basePath-wal', '$basePath-shm']) {
      try {
        final file = File(path);
        if (await file.exists()) await file.delete();
      } catch (error) {
        debugPrint('Chaty MLS purge could not remove $path: $error');
      }
    }

    for (final suffix in const <String>[
      '.db_key.v1',
      '.device_id.v1',
      '.signer_private.v1',
      '.signer_public.v1',
    ]) {
      try {
        await _secureStorage.delete(key: '$prefix$suffix');
      } catch (error) {
        debugPrint('Chaty MLS purge could not clear $prefix$suffix: $error');
      }
    }
    assert(!wasLoadedForUser || _engine == null);
  }

  Future<MlsConversationState> _ensureConversationReadyLocked(
    String conversationId,
  ) async {
    if (conversationId.trim().isEmpty) {
      throw const MlsE2eeException('Conversation id is required.');
    }
    final deviceId = _requireDeviceId();
    var state = await _fetchState(conversationId, afterEpoch: 0);

    if (state.group == null) {
      state = await _createInitialGroup(conversationId);
    } else {
      state = await _joinOrCatchUp(conversationId, state);
    }

    state = await _reconcileMembership(conversationId, state);
    final group = state.group;
    if (group == null) {
      throw const MlsE2eeException('MLS group initialization failed.');
    }

    final groupId = base64Decode(group.groupId);
    bool active;
    try {
      active = await _requireEngine().groupIsActive(groupIdBytes: groupId);
    } catch (_) {
      active = false;
    }
    if (!active) {
      throw MlsE2eeException('MLS group is not active for device $deviceId.');
    }

    final epoch = await _requireEngine().groupEpoch(groupIdBytes: groupId);
    if (epoch.toInt() != group.epoch) {
      throw MlsE2eeException(
        'MLS synchronization incomplete: local=${epoch.toInt()} server=${group.epoch}.',
      );
    }
    return state;
  }

  Future<MlsConversationState> _createInitialGroup(
    String conversationId,
  ) async {
    final claimed = await _claimPackages(conversationId);
    final packages = claimed.packages;
    final engine = _requireEngine();
    final created = await engine.createGroup(
      config: _groupConfig,
      signerBytes: _requireSigner(),
      credentialIdentity: utf8.encode(_requireCredentialIdentity()),
      signerPublicKey: _requireSignerPublicKey(),
    );
    final groupId = created.groupId;

    Uint8List? welcome;
    if (packages.isNotEmpty) {
      final added = await engine.addMembers(
        groupIdBytes: groupId,
        signerBytes: _requireSigner(),
        keyPackagesBytes: packages
            .map((package) => base64Decode(package.keyPackage))
            .toList(growable: false),
      );
      welcome = added.welcome;
    }
    final epoch = await engine.groupEpoch(groupIdBytes: groupId);

    try {
      await _client.rpc(
        'publish_mls_group_v1',
        params: <String, dynamic>{
          'p_conversation_id': conversationId,
          'p_sender_device_id': _requireDeviceId(),
          'p_group_id': base64Encode(groupId),
          'p_ciphersuite': serverCiphersuite,
          'p_epoch': epoch.toInt(),
          'p_welcome_payload': welcome == null ? '' : base64Encode(welcome),
          'p_recipient_packages': packages
              .map((package) => package.serverIdentityJson)
              .toList(growable: false),
        },
      );
    } catch (error) {
      // Another device can legitimately win the first-group race. The losing
      // local group is cryptographically unrelated and must be discarded.
      try {
        await engine.deleteGroup(groupIdBytes: groupId);
      } catch (_) {}
      final server = await _fetchState(conversationId, afterEpoch: 0);
      if (server.group == null) rethrow;
      return _joinOrCatchUp(conversationId, server);
    }

    return _fetchState(conversationId, afterEpoch: epoch.toInt());
  }

  Future<MlsConversationState> _joinOrCatchUp(
    String conversationId,
    MlsConversationState state,
  ) async {
    final group = state.group!;
    final groupId = base64Decode(group.groupId);
    final engine = _requireEngine();
    bool localGroupExists;
    int localEpoch = 0;
    try {
      localGroupExists = await engine.groupIsActive(groupIdBytes: groupId);
      if (localGroupExists) {
        localEpoch = (await engine.groupEpoch(groupIdBytes: groupId)).toInt();
      }
    } catch (_) {
      localGroupExists = false;
    }

    if (!localGroupExists) {
      final welcome = state.welcome;
      if (welcome == null) {
        throw const MlsE2eeException(
          'This device has no recoverable MLS Welcome. Re-link the device to restore encrypted chat access.',
        );
      }
      final joined = await engine.joinGroupFromWelcome(
        config: _groupConfig,
        welcomeBytes: base64Decode(welcome.welcomePayload),
        signerBytes: _requireSigner(),
      );
      if (base64Encode(joined.groupId) != group.groupId) {
        throw const MlsE2eeException('MLS Welcome group id mismatch.');
      }
      await _client.rpc(
        'ack_mls_welcome_v1',
        params: <String, dynamic>{
          'p_welcome_id': welcome.id,
          'p_device_id': _requireDeviceId(),
        },
      );
      localEpoch = (await engine.groupEpoch(groupIdBytes: groupId)).toInt();
      await _registerAndReplenishDevice();
      state = await _fetchState(conversationId, afterEpoch: localEpoch);
    } else {
      state = await _fetchState(conversationId, afterEpoch: localEpoch);
    }

    for (final control in state.controls) {
      if (control.epoch <= localEpoch) continue;
      if (control.epoch != localEpoch + 1) {
        throw MlsE2eeException(
          'Missing MLS membership commit before epoch ${control.epoch}.',
        );
      }
      final result = await engine.processMessage(
        groupIdBytes: groupId,
        messageBytes: base64Decode(control.commitPayload),
      );
      if (result.messageType != ProcessedMessageType.stagedCommit) {
        throw const MlsE2eeException(
          'MLS control stream contained a non-commit message.',
        );
      }
      localEpoch = (await engine.groupEpoch(groupIdBytes: groupId)).toInt();
      if (localEpoch != control.epoch) {
        throw MlsE2eeException(
          'MLS commit application failed at epoch ${control.epoch}.',
        );
      }
    }

    if (localEpoch != group.epoch) {
      state = await _fetchState(conversationId, afterEpoch: localEpoch);
      if (state.controls.isNotEmpty) {
        return _joinOrCatchUp(conversationId, state);
      }
    }
    return _fetchState(conversationId, afterEpoch: localEpoch);
  }

  Future<MlsConversationState> _reconcileMembership(
    String conversationId,
    MlsConversationState state,
  ) async {
    final group = state.group;
    if (group == null) return state;

    final serverActive = <String, MlsDeviceDescriptor>{
      for (final device in state.serverDevices) device.key: device,
    };
    final groupActive = <String, MlsGroupDevice>{
      for (final device in state.groupDevices)
        if (device.removedEpoch == null) device.key: device,
    };
    final additions = serverActive.keys
        .where((key) => !groupActive.containsKey(key))
        .toSet();
    final removals = groupActive.keys
        .where((key) => !serverActive.containsKey(key))
        .toSet();
    if (additions.isEmpty && removals.isEmpty) return state;

    final eligibleCoordinators =
        groupActive.keys.where(serverActive.containsKey).toList()..sort();
    final myKey = '${_requireUserId()}:${_requireDeviceId()}';
    if (eligibleCoordinators.isEmpty || eligibleCoordinators.first != myKey) {
      throw const MlsMembershipPendingException(
        'Encrypted membership is updating on another trusted device.',
      );
    }

    final claimed = additions.isEmpty
        ? const _ClaimedPackages(<MlsKeyPackageDescriptor>[])
        : await _claimPackages(conversationId);
    final claimedByKey = <String, MlsKeyPackageDescriptor>{
      for (final package in claimed.packages) package.key: package,
    };
    if (!additions.every(claimedByKey.containsKey)) {
      throw const MlsE2eeException(
        'MLS membership update is missing a KeyPackage for a new device.',
      );
    }

    final groupIdBytes = base64Decode(group.groupId);
    final members = await _requireEngine().groupMembers(
      groupIdBytes: groupIdBytes,
    );
    final removalIndices = <int>[];
    for (final member in members) {
      final credential = MlsCredential.deserialize(bytes: member.credential);
      final identity = utf8.decode(
        credential.identity(),
        allowMalformed: false,
      );
      if (removals.contains(identity)) removalIndices.add(member.index);
    }
    if (removalIndices.length != removals.length) {
      throw const MlsE2eeException(
        'MLS local membership does not match the server removal set.',
      );
    }

    final orderedAdditions = additions.toList()..sort();
    final commit = await _requireEngine().flexibleCommit(
      groupIdBytes: groupIdBytes,
      signerBytes: _requireSigner(),
      options: FlexibleCommitOptions(
        addKeyPackages: orderedAdditions
            .map((key) => base64Decode(claimedByKey[key]!.keyPackage))
            .toList(growable: false),
        removeIndices: Uint32List.fromList(removalIndices),
        forceSelfUpdate: false,
        consumePendingProposals: true,
        createGroupInfo: true,
        useRatchetTreeExtension: true,
      ),
    );
    await _requireEngine().mergePendingCommit(groupIdBytes: groupIdBytes);
    final newEpoch = (await _requireEngine().groupEpoch(
      groupIdBytes: groupIdBytes,
    )).toInt();

    try {
      await _client.rpc(
        'publish_mls_membership_update_v1',
        params: <String, dynamic>{
          'p_conversation_id': conversationId,
          'p_sender_device_id': _requireDeviceId(),
          'p_group_id': group.groupId,
          'p_new_epoch': newEpoch,
          'p_commit_payload': base64Encode(commit.commit),
          'p_welcome_payload': commit.welcome == null
              ? ''
              : base64Encode(commit.welcome!),
          'p_additions': orderedAdditions
              .map((key) => claimedByKey[key]!.serverIdentityJson)
              .toList(growable: false),
          'p_removals': removals
              .map((key) {
                final split = _splitDeviceKey(key);
                return <String, dynamic>{
                  'user_id': split.$1,
                  'device_id': split.$2,
                };
              })
              .toList(growable: false),
        },
      );
    } catch (error) {
      // The local engine already advanced. Failing closed is safer than trying
      // to synthesize a rollback of MLS ratchet state.
      throw MlsE2eeException(
        'Server rejected the MLS membership commit after local advancement. Re-open this account on the device to resynchronize. ($error)',
      );
    }

    return _fetchState(conversationId, afterEpoch: newEpoch);
  }

  Future<_ClaimedPackages> _claimPackages(String conversationId) async {
    final raw = await _client.rpc(
      'claim_mls_conversation_key_packages_v1',
      params: <String, dynamic>{
        'p_conversation_id': conversationId,
        'p_sender_device_id': _requireDeviceId(),
      },
    );
    final map = _asMap(raw);
    final rows = map['packages'];
    if (rows is! List) {
      throw const MlsE2eeException('Invalid MLS KeyPackage response.');
    }
    return _ClaimedPackages(
      rows
          .whereType<Map>()
          .map(
            (row) => MlsKeyPackageDescriptor.fromJson(
              Map<String, dynamic>.from(row),
            ),
          )
          .toList(growable: false),
    );
  }

  Future<MlsConversationState> _fetchState(
    String conversationId, {
    required int afterEpoch,
  }) async {
    final raw = await _client.rpc(
      'get_mls_conversation_state_v1',
      params: <String, dynamic>{
        'p_conversation_id': conversationId,
        'p_device_id': _requireDeviceId(),
        'p_after_epoch': max(afterEpoch, 0),
      },
    );
    return MlsConversationState.fromJson(_asMap(raw));
  }

  Future<void> _registerAndReplenishDevice() async {
    final base = await _registerDevice(const <Map<String, dynamic>>[]);
    final available = _integer(base['available_key_packages']);
    if (available >= replenishBelow) return;

    final needed = max(targetKeyPackagePool - available, 0);
    final packages = <Map<String, dynamic>>[];
    for (var index = 0; index < needed; index++) {
      final keyPackage = await _requireEngine().createKeyPackage(
        ciphersuite: _ciphersuite,
        signerBytes: _requireSigner(),
        credentialIdentity: utf8.encode(_requireCredentialIdentity()),
        signerPublicKey: _requireSignerPublicKey(),
      );
      packages.add(<String, dynamic>{
        'id': _uuid.v4(),
        'key_package': base64Encode(keyPackage.keyPackageBytes),
        'expires_at': DateTime.now()
            .toUtc()
            .add(const Duration(days: 30))
            .toIso8601String(),
      });
    }
    if (packages.isNotEmpty) await _registerDevice(packages);
  }

  Future<Map<String, dynamic>> _registerDevice(
    List<Map<String, dynamic>> packages,
  ) async {
    final raw = await _client.rpc(
      'register_mls_device_v1',
      params: <String, dynamic>{
        'p_device_id': _requireDeviceId(),
        'p_device_name': 'Chaty ${_platformName()} MLS device',
        'p_platform': _platformName(),
        'p_ciphersuite': serverCiphersuite,
        'p_credential_identity': _requireCredentialIdentity(),
        'p_signature_public_key': base64Encode(_requireSignerPublicKey()),
        'p_key_packages': packages,
      },
    );
    return _asMap(raw);
  }

  Future<String> _loadOrCreateDeviceId(String prefix) async {
    final key = '$prefix.device_id.v1';
    var value = await _secureStorage.read(key: key);
    if (value == null || value.length < 8) {
      value = 'mls_${_uuid.v4()}';
      await _secureStorage.write(key: key, value: value);
    }
    return value;
  }

  Future<Uint8List> _loadOrCreateSecret(String key, int length) async {
    final stored = await _secureStorage.read(key: key);
    if (stored != null && stored.isNotEmpty) {
      final bytes = base64Decode(stored);
      if (bytes.length != length) {
        throw MlsE2eeException('Invalid secure MLS secret length for $key.');
      }
      return Uint8List.fromList(bytes);
    }
    final bytes = Uint8List.fromList(
      List<int>.generate(length, (_) => _random.nextInt(256)),
    );
    await _secureStorage.write(key: key, value: base64Encode(bytes));
    return bytes;
  }

  Future<_SignerMaterial> _loadOrCreateSigner(String prefix) async {
    final privateKeyName = '$prefix.signer_private.v1';
    final publicKeyName = '$prefix.signer_public.v1';
    final privateStored = await _secureStorage.read(key: privateKeyName);
    final publicStored = await _secureStorage.read(key: publicKeyName);
    if (privateStored != null && publicStored != null) {
      return _SignerMaterial(
        Uint8List.fromList(base64Decode(privateStored)),
        Uint8List.fromList(base64Decode(publicStored)),
      );
    }
    if (privateStored != null || publicStored != null) {
      throw const MlsE2eeException(
        'Incomplete MLS signing identity in secure storage.',
      );
    }

    final keyPair = MlsSignatureKeyPair.generate(ciphersuite: _ciphersuite);
    final privateKey = Uint8List.fromList(keyPair.privateKey());
    final publicKey = Uint8List.fromList(keyPair.publicKey());
    await _secureStorage.write(
      key: privateKeyName,
      value: base64Encode(privateKey),
    );
    await _secureStorage.write(
      key: publicKeyName,
      value: base64Encode(publicKey),
    );
    return _SignerMaterial(privateKey, publicKey);
  }

  MlsEngine _requireEngine() {
    final value = _engine;
    if (value == null) throw const MlsE2eeException('MLS is not initialized.');
    return value;
  }

  Uint8List _requireSigner() {
    final value = _signerBytes;
    if (value == null)
      throw const MlsE2eeException('MLS signer is unavailable.');
    return value;
  }

  Uint8List _requireSignerPublicKey() {
    final value = _signerPublicKey;
    if (value == null) {
      throw const MlsE2eeException('MLS signer public key is unavailable.');
    }
    return value;
  }

  String _requireUserId() {
    final value = _userId;
    if (value == null) throw const MlsE2eeException('MLS user is unavailable.');
    return value;
  }

  String _requireDeviceId() {
    final value = _deviceId;
    if (value == null)
      throw const MlsE2eeException('MLS device is unavailable.');
    return value;
  }

  String _requireCredentialIdentity() {
    final value = _credentialIdentity;
    if (value == null) {
      throw const MlsE2eeException('MLS credential is unavailable.');
    }
    return value;
  }

  _ConversationGate _gate(String conversationId) =>
      _conversationGates.putIfAbsent(conversationId, _ConversationGate.new);

  static Map<String, dynamic> _asMap(dynamic value) {
    if (value is Map) return Map<String, dynamic>.from(value);
    throw const MlsE2eeException('Invalid MLS server response.');
  }

  static int _integer(dynamic value) {
    if (value is int) return value;
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }

  static (String, String) _splitDeviceKey(String key) {
    final index = key.indexOf(':');
    if (index <= 0 || index >= key.length - 1) {
      throw const MlsE2eeException('Invalid MLS device key.');
    }
    return (key.substring(0, index), key.substring(index + 1));
  }

  static void _zero(Uint8List? bytes) {
    if (bytes == null) return;
    bytes.fillRange(0, bytes.length, 0);
  }

  static String _platformName() {
    if (kIsWeb) return 'web';
    return switch (defaultTargetPlatform) {
      TargetPlatform.android => 'android',
      TargetPlatform.iOS => 'ios',
      TargetPlatform.macOS => 'macos',
      TargetPlatform.windows => 'windows',
      TargetPlatform.linux => 'linux',
      TargetPlatform.fuchsia => 'fuchsia',
    };
  }
}

class MlsEncryptedPayload {
  final String groupId;
  final int epoch;
  final String ciphertext;

  const MlsEncryptedPayload({
    required this.groupId,
    required this.epoch,
    required this.ciphertext,
  });
}

class MlsConversationState {
  final MlsGroupDescriptor? group;
  final MlsWelcomeDescriptor? welcome;
  final List<MlsControlDescriptor> controls;
  final List<MlsDeviceDescriptor> serverDevices;
  final List<MlsGroupDevice> groupDevices;

  const MlsConversationState({
    required this.group,
    required this.welcome,
    required this.controls,
    required this.serverDevices,
    required this.groupDevices,
  });

  factory MlsConversationState.fromJson(Map<String, dynamic> json) {
    final groupRaw = json['group'];
    final welcomeRaw = json['welcome'];
    return MlsConversationState(
      group: groupRaw is Map
          ? MlsGroupDescriptor.fromJson(Map<String, dynamic>.from(groupRaw))
          : null,
      welcome: welcomeRaw is Map
          ? MlsWelcomeDescriptor.fromJson(Map<String, dynamic>.from(welcomeRaw))
          : null,
      controls: _mapList(json['controls'], MlsControlDescriptor.fromJson),
      serverDevices: _mapList(
        json['server_devices'],
        MlsDeviceDescriptor.fromJson,
      ),
      groupDevices: _mapList(json['group_devices'], MlsGroupDevice.fromJson),
    );
  }

  static List<T> _mapList<T>(
    dynamic raw,
    T Function(Map<String, dynamic>) map,
  ) {
    if (raw is! List) return <T>[];
    return raw
        .whereType<Map>()
        .map((item) => map(Map<String, dynamic>.from(item)))
        .toList(growable: false);
  }
}

class MlsGroupDescriptor {
  final String groupId;
  final int epoch;
  final String protocolSuite;
  final String ciphersuite;

  const MlsGroupDescriptor({
    required this.groupId,
    required this.epoch,
    required this.protocolSuite,
    required this.ciphersuite,
  });

  factory MlsGroupDescriptor.fromJson(Map<String, dynamic> json) =>
      MlsGroupDescriptor(
        groupId: json['group_id']?.toString() ?? '',
        epoch: int.tryParse(json['epoch']?.toString() ?? '') ?? 0,
        protocolSuite: json['protocol_suite']?.toString() ?? '',
        ciphersuite: json['ciphersuite']?.toString() ?? '',
      );
}

class MlsWelcomeDescriptor {
  final String id;
  final int epoch;
  final String welcomePayload;

  const MlsWelcomeDescriptor({
    required this.id,
    required this.epoch,
    required this.welcomePayload,
  });

  factory MlsWelcomeDescriptor.fromJson(Map<String, dynamic> json) =>
      MlsWelcomeDescriptor(
        id: json['id']?.toString() ?? '',
        epoch: int.tryParse(json['epoch']?.toString() ?? '') ?? 0,
        welcomePayload: json['welcome_payload']?.toString() ?? '',
      );
}

class MlsControlDescriptor {
  final String id;
  final int epoch;
  final String commitPayload;

  const MlsControlDescriptor({
    required this.id,
    required this.epoch,
    required this.commitPayload,
  });

  factory MlsControlDescriptor.fromJson(Map<String, dynamic> json) =>
      MlsControlDescriptor(
        id: json['id']?.toString() ?? '',
        epoch: int.tryParse(json['epoch']?.toString() ?? '') ?? 0,
        commitPayload: json['commit_payload']?.toString() ?? '',
      );
}

class MlsDeviceDescriptor {
  final String userId;
  final String deviceId;
  final String credentialIdentity;
  final String signaturePublicKey;

  const MlsDeviceDescriptor({
    required this.userId,
    required this.deviceId,
    required this.credentialIdentity,
    required this.signaturePublicKey,
  });

  String get key => '$userId:$deviceId';

  factory MlsDeviceDescriptor.fromJson(Map<String, dynamic> json) =>
      MlsDeviceDescriptor(
        userId: json['user_id']?.toString() ?? '',
        deviceId: json['device_id']?.toString() ?? '',
        credentialIdentity: json['credential_identity']?.toString() ?? '',
        signaturePublicKey: json['signature_public_key']?.toString() ?? '',
      );
}

class MlsGroupDevice {
  final String userId;
  final String deviceId;
  final int joinedEpoch;
  final int? removedEpoch;

  const MlsGroupDevice({
    required this.userId,
    required this.deviceId,
    required this.joinedEpoch,
    required this.removedEpoch,
  });

  String get key => '$userId:$deviceId';

  factory MlsGroupDevice.fromJson(Map<String, dynamic> json) => MlsGroupDevice(
    userId: json['user_id']?.toString() ?? '',
    deviceId: json['device_id']?.toString() ?? '',
    joinedEpoch: int.tryParse(json['joined_epoch']?.toString() ?? '') ?? 0,
    removedEpoch: json['removed_epoch'] == null
        ? null
        : int.tryParse(json['removed_epoch'].toString()),
  );
}

class MlsKeyPackageDescriptor {
  final String keyPackageId;
  final String userId;
  final String deviceId;
  final String keyPackage;

  const MlsKeyPackageDescriptor({
    required this.keyPackageId,
    required this.userId,
    required this.deviceId,
    required this.keyPackage,
  });

  String get key => '$userId:$deviceId';

  Map<String, dynamic> get serverIdentityJson => <String, dynamic>{
    'key_package_id': keyPackageId,
    'user_id': userId,
    'device_id': deviceId,
  };

  factory MlsKeyPackageDescriptor.fromJson(Map<String, dynamic> json) =>
      MlsKeyPackageDescriptor(
        keyPackageId: json['key_package_id']?.toString() ?? '',
        userId: json['user_id']?.toString() ?? '',
        deviceId: json['device_id']?.toString() ?? '',
        keyPackage: json['key_package']?.toString() ?? '',
      );
}

class MlsE2eeException implements Exception {
  final String message;
  const MlsE2eeException(this.message);

  @override
  String toString() => message;
}

class MlsMembershipPendingException extends MlsE2eeException {
  const MlsMembershipPendingException(super.message);
}

class _ClaimedPackages {
  final List<MlsKeyPackageDescriptor> packages;
  const _ClaimedPackages(this.packages);
}

class _SignerMaterial {
  final Uint8List privateKey;
  final Uint8List publicKey;
  const _SignerMaterial(this.privateKey, this.publicKey);
}

class _ConversationGate {
  Future<void> _tail = Future<void>.value();

  Future<T> run<T>(Future<T> Function() operation) {
    final completer = Completer<T>();
    _tail = _tail.then((_) async {
      try {
        completer.complete(await operation());
      } catch (error, stackTrace) {
        completer.completeError(error, stackTrace);
      }
    });
    return completer.future;
  }
}
