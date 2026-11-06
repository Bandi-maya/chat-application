# Stage8 scope popup and first message-layout pass

Apply AFTER exact stage6+7 incremental delta, which itself applies after stage5. Preserve live publisher/Bandi commits. No backend work.

All three Apply entries (tile, Apply button, interactive preview) open Calls only / Messages only / Both. Cancel writes nothing. Await actual store write before closing. Both persists call/message pair in a single string commit under write mutex, publishes only on success. Legacy call key migrates on first explicit scoped change; unset message selection preserves prior custom message design. The existing presentation store is device-local, not account/cloud-synced. Server sync is optional and absent.

Actual message consumers: ChatsHomeScreen row template height/divider; ChatDetailScreen theme geometry passed to bubbles; ChatEntryBar stock/reference corner radius; AttachmentGallerySheet category corner radius. Nine geometry profiles reuse existing bubble engine without importing official assets or changing theme colors. This is an initial layout pass, NOT a complete vendor Messages clone or full reference parity. Context menus, navigation, media preview arrangement, all message/attachment variants and every large-text state remain partial or unchanged. Existing explicit entry-style overrides may retain their own geometry.

Three preference-store JVM tests PASS: scope isolation, Both single commit, failure publishes nothing, restoration/legacy migration, invalid IDs rejected. Actual preference source compiles against existing service classes. Android/Compose compile and production pixels not performed locally. Existing parity tests expecting every call selection to write legacy call-only key may need narrow migration-contract update, not weakening behavior tests.

Need publisher Android/strict/runtime/pixel capture for all nine message profiles, new/unset and legacy custom choices, Calls-only no message changes, Messages-only no call changes, Both, cancelled/failed apply, restart, light/dark/custom theme, 220/280/360dp, font1/1.5/2, TalkBack, attachment dock and Direct regression. No release/APK or exact-reference claim from source tests.
