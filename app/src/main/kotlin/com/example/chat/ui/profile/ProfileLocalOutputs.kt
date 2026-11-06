package com.example.chat.ui.profile

import android.content.Context
import java.io.File

/** Feature-local failures never crash Activity startup or composition. No broad cache sweep. */
internal object ProfileLocalOutputs {
    private var owner: ProfileLocalOutputOwner? = null
    private val availability = ProfilePhotoAvailability()
    fun state(): ProfilePhotoFeatureState = availability.state()
    fun markBlocked(error: Exception) { availability.block() }
    fun requireReady() = availability.requireReady()
    @Synchronized fun initialize(context: Context) {
        if (owner != null || state() is ProfilePhotoFeatureState.CleanupBlocked) return
        try {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("profile_local_outputs", Context.MODE_PRIVATE)
            val cache = app.cacheDir.canonicalFile
            val terminal = File(cache, "profile_camera_terminal")
            fun allowed(path: String): Boolean {
                val f = File(path)
                val parent = f.parentFile?.canonicalFile ?: return false
                return (parent == cache && f.name.matches(Regex("(?:chaty_pick_|cropped_avatar_|cropped_banner_)[a-zA-Z0-9-]+\\.(?:jpg|png)"))) ||
                    (parent == terminal.canonicalFile && f.name.matches(Regex("capture_[a-f0-9-]+\\.jpg")))
            }
            val restored = prefs.getStringSet("paths", emptySet())!!.filter(::allowed).toSet()
            owner = ProfileLocalOutputOwner(
                persist = { paths -> check(prefs.edit().putStringSet("paths", paths).commit()) { "Could not save profile photo ownership." } },
                delete = { path -> check(allowed(path)); val f = File(path); !f.exists() || f.delete() },
                restored = restored,
            )
            owner!!.restoreCleanup()
        } catch (error: Exception) { markBlocked(error) }
    }
    @Synchronized fun record(file: File) {
        requireReady()
        try { (owner ?: error("Profile output journal not initialized")).record(file.absolutePath) }
        catch (error: Exception) { markBlocked(error); throw error }
    }
    @Synchronized fun delete(file: File) {
        // Cleanup itself is allowed while blocked; new producers are not.
        try { (owner ?: error("Profile output journal not initialized")).remove(file.absolutePath) }
        catch (error: Exception) { markBlocked(error); throw error }
    }
}
