package com.example.chat

/**
 * Features that are hidden in this release.
 *
 * The app talks to backend_python over REST + WebSocket. The features below were built
 * against the old Supabase backend and backend_python has no endpoints for them yet,
 * so the buttons/settings did nothing (or failed silently). They are hidden, not deleted:
 * flip a flag to `true` once the matching backend support ships (next release).
 */
object ReleaseFeatures {
    /** Status / stories ("Updates" tab, camera centre action). Needs a status API. */
    const val STATUS_UPDATES = false

    /** Linked devices list + QR pairing. Needs a device-link API. */
    const val LINKED_DEVICES = false

    /** Message automation & scheduler. Needs server-side scheduled sends. */
    const val MESSAGE_AUTOMATION = false

    /** Polls in chats. Needs poll create/vote endpoints. */
    const val POLLS = false

    /** Message reactions are enabled with local-first encrypted persistence; remote sync is best-effort
     *  and becomes cross-device only when the deployed backend exposes toggle_message_reaction. */
    const val MESSAGE_REACTIONS = true

    /** Call history list in the Calls tab. Calls work; history needs a call-log API. */
    const val CALL_HISTORY = false

    /** Blocking contacts. Needs a block-list API. */
    const val BLOCK_CONTACTS = false

    /** Developer demo/sandbox actions in the attachment sheet. Never shown to users. */
    const val DEMO_TOOLS = false
}
