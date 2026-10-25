package com.example.chat.data.e2ee

import com.example.chat.core.crypto.MlsE2eeException

// MlsEncryptedPayload lives in core.crypto (engine layer).

/** Server-side MLS descriptors. Exact ports of the Dart fromJson factories
 *  (mls_e2ee_service.dart). */

data class MlsConversationState(
    val group: MlsGroupDescriptor?,
    val welcome: MlsWelcomeDescriptor?,
    val controls: List<MlsControlDescriptor>,
    val serverDevices: List<MlsDeviceDescriptor>,
    val groupDevices: List<MlsGroupDevice>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): MlsConversationState = MlsConversationState(
            group = (json["group"] as? Map<String, Any?>)?.let { MlsGroupDescriptor.fromJson(it) },
            welcome = (json["welcome"] as? Map<String, Any?>)?.let { MlsWelcomeDescriptor.fromJson(it) },
            controls = mapList(json["controls"]) { MlsControlDescriptor.fromJson(it) },
            serverDevices = mapList(json["server_devices"]) { MlsDeviceDescriptor.fromJson(it) },
            groupDevices = mapList(json["group_devices"]) { MlsGroupDevice.fromJson(it) },
        )

        @Suppress("UNCHECKED_CAST")
        private fun <T> mapList(raw: Any?, map: (Map<String, Any?>) -> T): List<T> {
            if (raw !is List<*>) return emptyList()
            return raw.filterIsInstance<Map<String, Any?>>().map(map)
        }
    }
}

data class MlsGroupDescriptor(
    val groupId: String,
    val epoch: Int,
    val protocolSuite: String,
    val ciphersuite: String,
) {
    companion object {
        fun fromJson(json: Map<String, Any?>) = MlsGroupDescriptor(
            groupId = json["group_id"]?.toString() ?: "",
            epoch = json["epoch"]?.toString()?.toIntOrNull() ?: 0,
            protocolSuite = json["protocol_suite"]?.toString() ?: "",
            ciphersuite = json["ciphersuite"]?.toString() ?: "",
        )
    }
}

data class MlsWelcomeDescriptor(val id: String, val epoch: Int, val welcomePayload: String) {
    companion object {
        fun fromJson(json: Map<String, Any?>) = MlsWelcomeDescriptor(
            id = json["id"]?.toString() ?: "",
            epoch = json["epoch"]?.toString()?.toIntOrNull() ?: 0,
            welcomePayload = json["welcome_payload"]?.toString() ?: "",
        )
    }
}

data class MlsControlDescriptor(val id: String, val epoch: Int, val commitPayload: String) {
    companion object {
        fun fromJson(json: Map<String, Any?>) = MlsControlDescriptor(
            id = json["id"]?.toString() ?: "",
            epoch = json["epoch"]?.toString()?.toIntOrNull() ?: 0,
            commitPayload = json["commit_payload"]?.toString() ?: "",
        )
    }
}

data class MlsDeviceDescriptor(
    val userId: String,
    val deviceId: String,
    val credentialIdentity: String,
    val signaturePublicKey: String,
) {
    val key: String get() = "$userId:$deviceId"

    companion object {
        fun fromJson(json: Map<String, Any?>) = MlsDeviceDescriptor(
            userId = json["user_id"]?.toString() ?: "",
            deviceId = json["device_id"]?.toString() ?: "",
            credentialIdentity = json["credential_identity"]?.toString() ?: "",
            signaturePublicKey = json["signature_public_key"]?.toString() ?: "",
        )
    }
}

data class MlsGroupDevice(
    val userId: String,
    val deviceId: String,
    val joinedEpoch: Int,
    val removedEpoch: Int?,
) {
    val key: String get() = "$userId:$deviceId"

    companion object {
        fun fromJson(json: Map<String, Any?>) = MlsGroupDevice(
            userId = json["user_id"]?.toString() ?: "",
            deviceId = json["device_id"]?.toString() ?: "",
            joinedEpoch = json["joined_epoch"]?.toString()?.toIntOrNull() ?: 0,
            removedEpoch = json["removed_epoch"]?.toString()?.toIntOrNull(),
        )
    }
}

data class MlsKeyPackageDescriptor(
    val keyPackageId: String,
    val userId: String,
    val deviceId: String,
    val keyPackage: String,
) {
    val key: String get() = "$userId:$deviceId"

    val serverIdentityJson: Map<String, Any?>
        get() = mapOf(
            "key_package_id" to keyPackageId,
            "user_id" to userId,
            "device_id" to deviceId,
        )

    companion object {
        fun fromJson(json: Map<String, Any?>) = MlsKeyPackageDescriptor(
            keyPackageId = json["key_package_id"]?.toString() ?: "",
            userId = json["user_id"]?.toString() ?: "",
            deviceId = json["device_id"]?.toString() ?: "",
            keyPackage = json["key_package"]?.toString() ?: "",
        )
    }
}

internal fun requireMlsMap(value: Any?): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    if (value is Map<*, *>) return value as Map<String, Any?>
    throw MlsE2eeException("Invalid MLS server response.")
}
