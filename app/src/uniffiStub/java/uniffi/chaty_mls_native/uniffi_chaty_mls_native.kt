package uniffi.chaty_mls_native

import java.lang.Exception

open class MlsNativeException(message: String) : Exception(message)

enum class MlsCiphersuite {
    MLS128_DHKEM_X25519_AES128GCM_SHA256_ED25519
}

enum class MlsWireFormatPolicy {
    CIPHERTEXT
}

enum class ProcessedMessageType {
    APPLICATION,
    PROPOSAL,
    STAGED_COMMIT
}

data class MlsGroupConfig(
    val ciphersuite: MlsCiphersuite,
    val wireFormatPolicy: MlsWireFormatPolicy,
    val useRatchetTreeExtension: Boolean,
    val maxPastEpochs: UInt,
    val paddingSize: UInt,
    val senderRatchetMaxOutOfOrder: UInt,
    val senderRatchetMaxForwardDistance: UInt,
    val numberOfResumptionPsks: UInt
)

data class FlexibleCommitOptions(
    val addKeyPackages: List<ByteArray>,
    val removeIndices: List<UInt>,
    val forceSelfUpdate: Boolean,
    val consumePendingProposals: Boolean,
    val createGroupInfo: Boolean,
    val useRatchetTreeExtension: Boolean
)

data class AddMembersResult(
    val commit: ByteArray,
    val welcome: ByteArray
)

data class CreateGroupResult(
    val groupId: ByteArray
)

data class JoinGroupResult(
    val groupId: ByteArray
)

data class KeyPackageResult(
    val keyPackageBytes: ByteArray
)

data class CreateMessageResult(
    val ciphertext: ByteArray
)

data class ProcessedMessage(
    val messageType: ProcessedMessageType,
    val applicationMessage: ByteArray?
)

data class Member(
    val credential: ByteArray,
    val index: UInt
)

class ChatyMlsEngine {
    companion object {
        fun create(dbPath: String, encryptionKey: ByteArray): ChatyMlsEngine =
            ChatyMlsEngine()
    }

    fun shutdown() {}
    fun createKeyPackage(ciphersuite: MlsCiphersuite, signerBytes: ByteArray, credentialIdentity: ByteArray, signerPublicKey: ByteArray): KeyPackageResult =
        throw MlsNativeException("MLS native binding not installed in this build")
    fun createGroup(config: MlsGroupConfig, signerBytes: ByteArray, credentialIdentity: ByteArray, signerPublicKey: ByteArray): CreateGroupResult =
        throw MlsNativeException("MLS native binding not installed in this build")
    fun joinGroupFromWelcome(config: MlsGroupConfig, welcomeBytes: ByteArray, signerBytes: ByteArray): JoinGroupResult =
        throw MlsNativeException("MLS native binding not installed in this build")
    fun groupEpoch(groupIdBytes: ByteArray): UInt = 0u
    fun groupIsActive(groupIdBytes: ByteArray): Boolean = false
    fun groupMembers(groupIdBytes: ByteArray): List<Member> = emptyList()
    fun addMembers(groupId: ByteArray, signerBytes: ByteArray, keyPackages: List<ByteArray>): AddMembersResult =
        throw MlsNativeException("MLS native binding not installed in this build")
    fun flexibleCommit(groupId: ByteArray, signerBytes: ByteArray, options: FlexibleCommitOptions): AddMembersResult =
        throw MlsNativeException("MLS native binding not installed in this build")
    fun mergePendingCommit(groupId: ByteArray) {}
    fun createMessage(groupId: ByteArray, signerBytes: ByteArray, message: ByteArray, aad: ByteArray): CreateMessageResult =
        throw MlsNativeException("MLS native binding not installed in this build")
    fun processMessage(groupId: ByteArray, message: ByteArray): ProcessedMessage =
        throw MlsNativeException("MLS native binding not installed in this build")
    fun exportSecret(groupId: ByteArray, label: String, context: ByteArray, length: UInt): ByteArray =
        throw MlsNativeException("MLS native binding not installed in this build")
    fun deleteGroup(groupId: ByteArray) {}
}

fun signatureKeyPairGenerateRaw(ciphersuite: MlsCiphersuite): List<ByteArray> =
    listOf(ByteArray(32), ByteArray(32))

fun serializeSigner(ciphersuite: MlsCiphersuite, privateKey: ByteArray, publicKey: ByteArray): ByteArray =
    ByteArray(64)

fun credentialIdentity(credentialBytes: ByteArray): ByteArray =
    ByteArray(32)
