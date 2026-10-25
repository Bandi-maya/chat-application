package com.example.chat.core.crypto.uniffi

import com.example.chat.core.crypto.CommitResult
import com.example.chat.core.crypto.CreateGroupResult
import com.example.chat.core.crypto.CreateMessageResult
import com.example.chat.core.crypto.FlexibleCommitOptions
import com.example.chat.core.crypto.JoinGroupResult
import com.example.chat.core.crypto.KeyPackageResult
import com.example.chat.core.crypto.MlsCiphersuite
import com.example.chat.core.crypto.MlsEngine
import com.example.chat.core.crypto.MlsE2eeException
import com.example.chat.core.crypto.MlsGroupConfig
import com.example.chat.core.crypto.MlsMember
import com.example.chat.core.crypto.MlsNativeStatics
import com.example.chat.core.crypto.MlsWireFormatPolicy
import com.example.chat.core.crypto.ProcessedMessageResult
import com.example.chat.core.crypto.ProcessedMessageType
import uniffi.chaty_mls_native.AddMembersResult as UAddMembersResult
import uniffi.chaty_mls_native.ChatyMlsEngine
import uniffi.chaty_mls_native.FlexibleCommitOptions as UFlexibleCommitOptions
import uniffi.chaty_mls_native.MlsCiphersuite as UCiphersuite
import uniffi.chaty_mls_native.MlsGroupConfig as UGroupConfig
import uniffi.chaty_mls_native.MlsNativeException
import uniffi.chaty_mls_native.MlsWireFormatPolicy as UWireFormatPolicy
import uniffi.chaty_mls_native.ProcessedMessageType as UProcessedMessageType
import uniffi.chaty_mls_native.credentialIdentity
import uniffi.chaty_mls_native.serializeSigner
import uniffi.chaty_mls_native.signatureKeyPairGenerateRaw

/**
 * UniFFI-backed implementation of the MLS engine interface: delegates to the
 * generated bindings for mls-native/src/lib.rs (chaty_mls_native cdylib).
 * Installed into MlsEngine.factory / MlsNativeStatics.instance at app start.
 */
class UniffiMlsEngine private constructor(private val inner: ChatyMlsEngine) : MlsEngine {

    override suspend fun close() = wrap { inner.shutdown() }

    override suspend fun createKeyPackage(
        ciphersuite: MlsCiphersuite,
        signerBytes: ByteArray,
        credentialIdentity: ByteArray,
        signerPublicKey: ByteArray,
    ): KeyPackageResult = wrap {
        KeyPackageResult(
            inner.createKeyPackage(ciphersuite.toU(), signerBytes, credentialIdentity, signerPublicKey)
                .keyPackageBytes
        )
    }

    override suspend fun createGroup(
        config: MlsGroupConfig,
        signerBytes: ByteArray,
        credentialIdentity: ByteArray,
        signerPublicKey: ByteArray,
    ): CreateGroupResult = wrap {
        CreateGroupResult(
            inner.createGroup(config.toU(), signerBytes, credentialIdentity, signerPublicKey).groupId
        )
    }

    override suspend fun joinGroupFromWelcome(
        config: MlsGroupConfig,
        welcomeBytes: ByteArray,
        signerBytes: ByteArray,
    ): JoinGroupResult = wrap {
        JoinGroupResult(inner.joinGroupFromWelcome(config.toU(), welcomeBytes, signerBytes).groupId)
    }

    override suspend fun groupEpoch(groupIdBytes: ByteArray): Long =
        wrap { inner.groupEpoch(groupIdBytes).toLong() }

    override suspend fun groupIsActive(groupIdBytes: ByteArray): Boolean =
        wrap { inner.groupIsActive(groupIdBytes) }

    override suspend fun groupMembers(groupIdBytes: ByteArray): List<MlsMember> = wrap {
        inner.groupMembers(groupIdBytes).map { MlsMember(credential = it.credential, index = it.index.toInt()) }
    }

    override suspend fun addMembers(
        groupIdBytes: ByteArray,
        signerBytes: ByteArray,
        keyPackagesBytes: List<ByteArray>,
    ): com.example.chat.core.crypto.AddMembersResult = wrap {
        val r: UAddMembersResult = inner.addMembers(groupIdBytes, signerBytes, keyPackagesBytes)
        com.example.chat.core.crypto.AddMembersResult(commit = r.commit, welcome = r.welcome)
    }

    override suspend fun flexibleCommit(
        groupIdBytes: ByteArray,
        signerBytes: ByteArray,
        options: FlexibleCommitOptions,
    ): CommitResult = wrap {
        val r = inner.flexibleCommit(
            groupIdBytes,
            signerBytes,
            UFlexibleCommitOptions(
                addKeyPackages = options.addKeyPackages,
                removeIndices = options.removeIndices.map { it.toUInt() },
                forceSelfUpdate = options.forceSelfUpdate,
                consumePendingProposals = options.consumePendingProposals,
                createGroupInfo = options.createGroupInfo,
                useRatchetTreeExtension = options.useRatchetTreeExtension,
            ),
        )
        CommitResult(commit = r.commit, welcome = r.welcome)
    }

    override suspend fun mergePendingCommit(groupIdBytes: ByteArray) =
        wrap { inner.mergePendingCommit(groupIdBytes) }

    override suspend fun createMessage(
        groupIdBytes: ByteArray,
        signerBytes: ByteArray,
        message: ByteArray,
        aad: ByteArray,
    ): CreateMessageResult = wrap {
        CreateMessageResult(inner.createMessage(groupIdBytes, signerBytes, message, aad).ciphertext)
    }

    override suspend fun processMessage(
        groupIdBytes: ByteArray,
        messageBytes: ByteArray,
    ): ProcessedMessageResult = wrap {
        val r = inner.processMessage(groupIdBytes, messageBytes)
        ProcessedMessageResult(
            messageType = when (r.messageType) {
                UProcessedMessageType.APPLICATION -> ProcessedMessageType.application
                UProcessedMessageType.PROPOSAL -> ProcessedMessageType.proposal
                UProcessedMessageType.STAGED_COMMIT -> ProcessedMessageType.stagedCommit
            },
            applicationMessage = r.applicationMessage,
        )
    }

    override suspend fun exportSecret(
        groupIdBytes: ByteArray,
        label: String,
        context: ByteArray,
        keyLength: Int,
    ): ByteArray = wrap { inner.exportSecret(groupIdBytes, label, context, keyLength.toUInt()) }

    override suspend fun deleteGroup(groupIdBytes: ByteArray) =
        wrap { inner.deleteGroup(groupIdBytes) }

    companion object {
        suspend fun create(dbPath: String, encryptionKey: ByteArray): UniffiMlsEngine =
            wrap { UniffiMlsEngine(ChatyMlsEngine.create(dbPath, encryptionKey)) }

        /** Installs this binding as the engine factory + statics provider. */
        fun install() {
            MlsEngine.factory = { dbPath, encryptionKey -> create(dbPath, encryptionKey) }
            MlsNativeStatics.instance = object : MlsNativeStatics {
                override fun signatureKeyPairGenerate(ciphersuite: MlsCiphersuite): Pair<ByteArray, ByteArray> {
                    val raw = signatureKeyPairGenerateRaw(ciphersuite.toU())
                    return Pair(raw[0], raw[1])
                }

                override fun serializeSigner(
                    ciphersuite: MlsCiphersuite,
                    privateKey: ByteArray,
                    publicKey: ByteArray,
                ): ByteArray = serializeSigner(ciphersuite.toU(), privateKey, publicKey)

                override fun credentialIdentity(credentialBytes: ByteArray): ByteArray =
                    credentialIdentity(credentialBytes)
            }
        }

        private suspend fun <T> wrap(block: suspend () -> T): T = try {
            block()
        } catch (e: MlsNativeException) {
            throw MlsE2eeException(e.message ?: "MLS native operation failed.")
        }

        private fun MlsCiphersuite.toU(): UCiphersuite = when (this) {
            MlsCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519 ->
                UCiphersuite.MLS128_DHKEM_X25519_AES128GCM_SHA256_ED25519
        }

        private fun MlsGroupConfig.toU(): UGroupConfig = UGroupConfig(
            ciphersuite = ciphersuite.toU(),
            wireFormatPolicy = when (wireFormatPolicy) {
                MlsWireFormatPolicy.ciphertext -> UWireFormatPolicy.CIPHERTEXT
            },
            useRatchetTreeExtension = useRatchetTreeExtension,
            maxPastEpochs = maxPastEpochs.toUInt(),
            paddingSize = paddingSize.toUInt(),
            senderRatchetMaxOutOfOrder = senderRatchetMaxOutOfOrder.toUInt(),
            senderRatchetMaxForwardDistance = senderRatchetMaxForwardDistance.toUInt(),
            numberOfResumptionPsks = numberOfResumptionPsks.toUInt(),
        )
    }
}
