# Chaty Privacy Policy (REVIEW DRAFT - not verified, not published)

Version 0.2-draft, 2026-11-04. Several statements below are unverified owner
claims, marked [OWNER-TO-CONFIRM] and listed in
docs/legal/OWNER_CONFIRM_CHECKLIST.md. Do not publish until that checklist is
fully resolved.

## What we collect
- Account data: email address, username, display name, optional profile photo.
- Content you create: messages, media, and voice messages you send, and call
  metadata (who called whom, when). Call audio and video are not recorded by
  the service. [OWNER-TO-CONFIRM: no recording path]
- Location: only when you explicitly share it in a chat.
- Device and log data needed to operate and secure the service.

## Translation
If you translate a message with the server/cloud option, the message text is
sent to Google's web translation service to produce the translation. Use the
in-app/offline translation option to keep message text on the service's own
infrastructure. [OWNER-TO-CONFIRM: shipped behavior and provider - the Kotlin
translate action is currently a stub and the settings copy references
"Server Translation (Google/Cloud)".]

## Link previews
When a chat shows a preview for a link, the app fetches the linked third-party
page or image to build that preview; the site you linked (and its image host)
receives a request from your device, including your IP address.
[OWNER-TO-CONFIRM: no automatic link-preview fetch was found in the current
Kotlin source; confirm intended behavior before publishing.]

## How we use it
To provide messaging, calls, synchronization across your sessions, and account
security. We do not sell your data [OWNER-TO-CONFIRM] and we do not show
advertising [OWNER-TO-CONFIRM].

## Where it is processed
On LogyByte-operated servers (api.hub.logybyte.in) [OWNER-TO-CONFIRM: operator
identity] and, where enabled, Supabase infrastructure for account and settings
data. Traffic is encrypted in transit using TLS [OWNER-TO-CONFIRM: all
endpoints, including tile and any third-party fetch endpoints].

## Retention and deletion
Content is kept while your account is active. [OWNER-TO-CONFIRM: actual
retention behavior; account deletion flow: pending confirmation - verify the
shipped delete-account path before publishing.]

## Your choices
You control profile details, privacy settings (last seen, online visibility,
read receipts), and location sharing from within the app.

## Contact
[Pending: official customer-care address - Bandi to supply.]

## Optional cross-app island (local wording approved 14 November 2026)

If you turn this feature on and grant Android notification access, Chaty can
read notification text and actions and media-session information to show the
island and perform actions you choose. The optional accessibility host places
a tappable island above other apps; its current configuration does not retrieve
screen content or inject gestures. Overlay access lets the island float above
other apps. Turn the island off in Chaty and revoke special access in Android
Settings. Battery settings are optional troubleshooting controls, not a promise
of always-on delivery. Ordinary messaging does not require the cross-app island.

[OWNER-TO-CONFIRM before publication: storage/transmission of notification text,
app inventory and media metadata, including logging, crash reports and SDKs.
No broad no-telemetry, never-uploaded or never-collected promise is made.]

Standard chats are not end-to-end encrypted in the current routing configuration.
App Lock is local access control, not encryption of messages held by the service.
