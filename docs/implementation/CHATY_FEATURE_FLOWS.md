# Chaty Feature Flows and State Transitions

## 1. Flow notation
Each flow must be traced from user gesture through presentation/controller/repository/backend and back to rendered state. Widgets must not claim success before the service result. Optimistic state must be reversible.

## 2. Appearance and templates
| Step | Actor | Action | State transition | Failure handling |
|---|---|---|---|---|
| 1 | User | Open Settings → Appearance | Load active profile and presets | Show loading then actionable load error |
| 2 | Controller | Load profile | Validate schema and migrate legacy keys | Fall back to defaults only with logged non-sensitive diagnostic |
| 3 | User | Select template/category | Update preview state only | Keep applied state untouched |
| 4 | Preview widgets | Render real shared components | Resolve tokens from preview profile | Surface invalid value safely |
| 5 | User | Apply | Validate complete candidate and persist atomically | Keep previous applied profile and show error |
| 6 | Controller | Publish active profile | Notify dependent shared components | Avoid full app restart unless required |
| 7 | User | Restart app | Reload versioned profile | Verify same appearance |
| 8 | User | Reset category/all | Patch category or replace profile after confirmation | Preserve unrelated settings on category reset |

## 3. Message send
Compose → validate input → create client id/idempotency key → ensure conversation MLS state is current → encrypt payload → call MLS send RPC → reconcile server message → update timeline/realtime. On failure, preserve draft and offer retry; never send plaintext for an MLS conversation.

## 4. Message edit
Open own message menu → edit draft → validate length/ownership → encrypt edited content via MLS for the same conversation → use MLS edit RPC with sender device/group/epoch/ciphertext → reconcile server state. If failure, restore old text and show retry. Never direct-update encrypted body in plaintext.

## 5. Message delete
Choose delete scope → confirm scope → repository/RPC authorization → reconcile message tombstone/hidden state → update other conversation views. Repeated requests are idempotent; offline errors are visible.

## 6. Attachment
Open attachment tray → request permission only when needed → picker/camera → validate size/type → preview/cancel → encrypt or prepare payload according to supported E2EE media contract → upload → send message → reconcile progress. Clean temporary files on cancel/failure; don't expose a success state before completion.

## 7. Voice note
Start record → permission/audio-focus acquisition → record with lifecycle tracking → stop/pause/cancel → preview → upload/send → playback state. Handle interruptions, call audio focus, app background, device rotation, denied permission and file cleanup.

## 8. Status
Open Updates → create text/media → validate supported type/size → preview audience/expiry → publish → server confirms → update status list. Viewing records a view only through the supported server flow and respects expiry/audience. Do not expose anti-revoke or fake-view privacy claims.

## 9. Call
Select voice/video → check permissions → create call session → signaling/ringing → accept/decline/timeout/busy → WebRTC connect → active controls → end → server-authoritative terminal history record. All exits clean up tracks, listeners and timers. Handle ICE failure, network switch, backgrounding and repeated end events.

## 10. Device revocation
Settings → Linked devices → select device → explain consequence → confirm → server revocation RPC → verify authoritative list → invalidate local state/MLS enrollment according to policy. Show success only after server confirmation.

## 11. Account switch/sign out
Confirm → cancel pending user-scoped tasks → detach listeners → clear user-specific in-memory caches and temporary secrets as appropriate → sign out/switch → initialize the new session. Old async results must not render in the new account.

## 12. Settings import/export
Export: serialize appearance-only schema → validate no forbidden fields → encode JSON → user-controlled share/save. Import: select file → size/type guard → decode/validate schema → show preview/warnings → apply atomically. Invalid input cannot change current settings.

## 13. Error state contract
Every network-backed flow supports: loading, success, recoverable error, retry, cancellation, offline behavior and stale-result protection. Errors must be actionable, non-sensitive and consistent.
