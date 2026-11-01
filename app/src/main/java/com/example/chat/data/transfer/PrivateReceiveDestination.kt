package com.example.chat.data.transfer

import java.io.File

/**
 * Canonical destination guard shared by the P2P receiver and JVM tests.
 * Receivers are restricted to a direct child of the app-private
 * files/p2p-received directory; symlinked directories/files and traversal
 * segments are rejected before any partial transfer file is created.
 */
internal fun validatePrivateReceiveDestination(appFilesDir: File, requested: File): File {
    val filesRoot = appFilesDir.canonicalFile
    val receiveDirectory = File(filesRoot, "p2p-received")
    if (!receiveDirectory.exists() && !receiveDirectory.mkdirs()) {
        throw IllegalStateException("Unable to create private receive storage.")
    }
    if (!receiveDirectory.isDirectory) throw SecurityException("Private receive storage is not a directory.")
    val canonicalReceiveDirectory = receiveDirectory.canonicalFile
    if (canonicalReceiveDirectory.name != "p2p-received" ||
        canonicalReceiveDirectory.parentFile != filesRoot
    ) {
        throw SecurityException("Private receive storage path is not trusted.")
    }

    val name = requested.name
    if (name.isBlank() || name == "." || name == ".." || name.length > 180 ||
        name.any { it == '/' || it == '\\' || it.code < 0x20 }
    ) throw SecurityException("Invalid received file name.")

    val requestedParent = requested.parentFile?.canonicalFile
        ?: throw SecurityException("Received file must have a private destination.")
    if (requestedParent != canonicalReceiveDirectory) {
        throw SecurityException("Received files can only be saved inside Chaty's private storage.")
    }
    val destination = File(canonicalReceiveDirectory, name)
    val canonicalDestination = destination.canonicalFile
    if (canonicalDestination.parentFile != canonicalReceiveDirectory ||
        destination.exists() || canonicalDestination.exists()
    ) {
        throw SecurityException("Received file destination already exists or is not safe.")
    }
    return canonicalDestination
}
