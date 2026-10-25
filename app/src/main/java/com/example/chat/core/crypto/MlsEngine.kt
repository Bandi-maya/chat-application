package com.example.chat.core.crypto

/**
 * Kotlin-side binding surface for the repo-owned OpenMLS Rust crate
 * (third_party/openmls_android/rust/src/api/*.rs). Method names and argument
 * order mirror the Rust `pub async fn` signatures 1:1 so the UniFFI/JNI
 * generated binding can implement this interface without adapting logic.
 *
 * Security boundaries (unchanged from the Flutter implementation):
 * - MLS private keys and ratchet state never leave the device.
 * - Ratchet state is persisted only in OpenMLS' SQLCipher database.
 * - The SQLCipher key and signature private key are held in secure storage.
 * - Supabase receives only public device material, one-time KeyPackages,
 *   Welcome/Commit protocol messages and opaque application ciphertexts.
 * - Membership drift fails closed. Chaty never silently falls back to
 *   plaintext when an MLS conversation cannot be synchronized.
 */

enum class MlsCiphersuite { MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519 }

enum class MlsWireFormatPolicy { ciphertext }

data class MlsGroupConfig(
    val ciphersuite: MlsCiphersuite,
    val wireFormatPolicy: MlsWireFormatPolicy,
    val useRatchetTreeExtension: Boolean,
    val maxPastEpochs: Int,
    val paddingSize: Int,
    val senderRatchetMaxOutOfOrder: Int,
    val senderRatchetMaxForwardDistance: Int,
    val numberOfResumptionPsks: Int,
) {
    companion object {
        /** Mirrors MlsGroupConfig.defaultConfig values as consumed by the
         *  service; service overrides maxPastEpochs to >= 5. */
        fun defaultConfig(ciphersuite: MlsCiphersuite) = MlsGroupConfig(
            ciphersuite = ciphersuite,
            wireFormatPolicy = MlsWireFormatPolicy.ciphertext,
            useRatchetTreeExtension = true,
            maxPastEpochs = 5,
            paddingSize = 0,
            senderRatchetMaxOutOfOrder = 0,
            senderRatchetMaxForwardDistance = 0,
            numberOfResumptionPsks = 0,
        )
    }
}

data class CreateGroupResult(val groupId: ByteArray)
data class JoinGroupResult(val groupId: ByteArray)
data class AddMembersResult(val commit: ByteArray, val welcome: ByteArray?)
data class FlexibleCommitOptions(
    val addKeyPackages: List<ByteArray>,
    val removeIndices: IntArray,
    val forceSelfUpdate: Boolean,
    val consumePendingProposals: Boolean,
    val createGroupInfo: Boolean,
    val useRatchetTreeExtension: Boolean,
)
data class CommitResult(val commit: ByteArray, val welcome: ByteArray?)
data class CreateMessageResult(val ciphertext: ByteArray)
enum class ProcessedMessageType { applicationMessage, stagedCommit, proposal, publicMessage }
data class ProcessedMessageResult(
    val messageType: ProcessedMessageType,
    val applicationMessage: ByteArray?,
)
data class KeyPackageResult(val keyPackageBytes: ByteArray)
data class MlsMember(val credential: ByteArray, val index: Int)

interface MlsEngine {
    suspend fun close()

    suspend fun createKeyPackage(
        ciphersuite: MlsCiphersuite,
        signerBytes: ByteArray,
        credentialIdentity: ByteArray,
        signerPublicKey: ByteArray,
    ): KeyPackageResult

    suspend fun createGroup(
        config: MlsGroupConfig,
        signerBytes: ByteArray,
        credentialIdentity: ByteArray,
        signerPublicKey: ByteArray,
    ): CreateGroupResult

    suspend fun joinGroupFromWelcome(
        config: MlsGroupConfig,
        welcomeBytes: ByteArray,
        signerBytes: ByteArray,
    ): JoinGroupResult

    suspend fun groupEpoch(groupIdBytes: ByteArray): Long
    suspend fun groupIsActive(groupIdBytes: ByteArray): Boolean
    suspend fun groupMembers(groupIdBytes: ByteArray): List<MlsMember>

    suspend fun addMembers(
        groupIdBytes: ByteArray,
        signerBytes: ByteArray,
        keyPackagesBytes: List<ByteArray>,
    ): AddMembersResult

    suspend fun flexibleCommit(
        groupIdBytes: ByteArray,
        signerBytes: ByteArray,
        options: FlexibleCommitOptions,
    ): CommitResult

    suspend fun mergePendingCommit(groupIdBytes: ByteArray)

    suspend fun createMessage(
        groupIdBytes: ByteArray,
        signerBytes: ByteArray,
        message: ByteArray,
        aad: ByteArray,
    ): CreateMessageResult

    suspend fun processMessage(
        groupIdBytes: ByteArray,
        messageBytes: ByteArray,
    ): ProcessedMessageResult

    suspend fun exportSecret(
        groupIdBytes: ByteArray,
        label: String,
        context: ByteArray,
        keyLength: Int,
    ): ByteArray

    suspend fun deleteGroup(groupIdBytes: ByteArray)

    companion object {
        /** Factory mirrors MlsEngine.create(dbPath, encryptionKey). The
         *  concrete implementation is supplied by the UniFFI/JNI binding. */
        var factory: (suspend (dbPath: String, encryptionKey: ByteArray) -> MlsEngine)? = null

        suspend fun create(dbPath: String, encryptionKey: ByteArray): MlsEngine =
            (factory ?: throw MlsE2eeException("MLS native binding is not installed."))
                .invoke(dbPath, encryptionKey)
    }
}

/** Native static helpers (keys.rs / credential.rs). Bound alongside engine. */
interface MlsNativeStatics {
    fun signatureKeyPairGenerate(ciphersuite: MlsCiphersuite): Pair<ByteArray, ByteArray>
    fun serializeSigner(ciphersuite: MlsCiphersuite, privateKey: ByteArray, publicKey: ByteArray): ByteArray
    fun credentialIdentity(credentialBytes: ByteArray): ByteArray

    companion object {
        var instance: MlsNativeStatics? = null
        fun require(): MlsNativeStatics =
            instance ?: throw MlsE2eeException("MLS native binding is not installed.")
    }
}

open class MlsE2eeException(message: String) : Exception(message)

class MlsMembershipPendingException(message: String) : MlsE2eeException(message)

enum class MlsStorageInitializationFailure { unsupportedPlatformLock, databaseInUse, unavailable }

/** A typed, non-secret-bearing failure raised before MLS state can be opened.
 *  Exact port of the Dart classification logic. */
class MlsStorageInitializationException private constructor(
    val failure: MlsStorageInitializationFailure,
    message: String,
) : MlsE2eeException(message) {
    companion object {
        fun fromCause(cause: Throwable): MlsStorageInitializationException {
            val normalized = cause.toString().lowercase()
            if (normalized.contains("try_lock() not supported") ||
                normalized.contains("try_lock not supported") ||
                (normalized.contains("failed to lock") && normalized.contains("not supported"))
            ) {
                return MlsStorageInitializationException(
                    MlsStorageInitializationFailure.unsupportedPlatformLock,
                    "Encrypted messaging storage is not supported by this native build.",
                )
            }
            if (normalized.contains("database is already open") ||
                normalized.contains("another connection or process") ||
                normalized.contains("database is locked")
            ) {
                return MlsStorageInitializationException(
                    MlsStorageInitializationFailure.databaseInUse,
                    "Encrypted messaging storage is already in use.",
                )
            }
            return MlsStorageInitializationException(
                MlsStorageInitializationFailure.unavailable,
                "Encrypted messaging storage could not be opened.",
            )
        }
    }
}
