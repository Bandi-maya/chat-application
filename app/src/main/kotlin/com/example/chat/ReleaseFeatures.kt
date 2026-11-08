package com.example.chat

/**
 * Features that are hidden in this release.
 *
 * Kotlin release gates for capabilities whose required backend contracts are not
 * currently available. Keep these disabled until the complete server and client
 * paths are deployed and covered by integration tests.
 */
object ReleaseFeatures {
    // Legal acceptance remains off pending owner-reviewed final policy.
    const val LEGAL_ACCEPTANCE_GATE = false
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

    /** Call history list in the Calls tab, backed by the server call-history plane (research 5.4). */
    const val CALL_HISTORY = true

    /** Blocking contacts. Needs a block-list API. */
    const val BLOCK_CONTACTS = true

    /** Per-contact privacy overrides (ContactInfo > Individual Privacy
     *  Controls): hidden until the backend enforces them server-side -
     *  a local-only toggle would leak receipts/typing the user hid. */
    const val CONTACT_PRIVACY_OVERRIDES = false

    /** Client available; server receipt required, unavailable backend surfaces honest failure. */
    const val UGC_REPORTING = true

    /** Developer demo/sandbox actions in the attachment sheet. Never shown to users. */
    const val DEMO_TOOLS = false
}
