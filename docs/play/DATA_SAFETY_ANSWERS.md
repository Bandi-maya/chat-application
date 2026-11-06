# Play Console Data Safety - draft answers (P-07)

DRAFT 2026-11-04. Per publisher review: every row needs confirmation against
the built artifact and current official Play guidance before submission.
Source: https://support.google.com/googleplay/android-developer/answer/10787469

| Category | Data type | Collected? | Shared? | Purpose | Notes / verify |
|---|---|---|---|---|---|
| Personal info | Email address | Yes | No | Account management | Register screen collects it. |
| Personal info | Name / username | Yes | No | Account management | |
| Personal info | User IDs | Yes | No | Account management, app functionality | REST + GoTrue account ids. |
| Messages | In-app messages | Yes | No | App functionality | VERIFY before claiming E2E: MLS/Signal stack exists client-side; whether the server can read content is an architecture claim that needs backend confirmation. Claim only "encrypted in transit (TLS)" until verified. |
| Photos and videos | User-sent media | Yes | No | App functionality | Only what the user attaches. |
| Audio | Voice messages, call audio | Voice messages: Yes | No | App functionality | Calls are not recorded (VERIFY: no recording code path found client-side). |
| Location | Precise location | Optional | No | App functionality | Only when the user shares location in a chat (maps feature). |
| App activity | - | No | - | - | VERIFY no analytics SDK bundled (Bandi bans Firebase; confirm no other tracker in the built artifact). |
| Device or other IDs | - | No | - | - | VERIFY against built artifact (dependency scan). |

Security practices:
- Data encrypted in transit: Yes (HTTPS/WSS to api.hub.logybyte.in; Supabase over TLS).
- Data deletion request mechanism: PENDING - confirm the shipped delete-account
  path; if none, this is a submission blocker, not a wording issue.
- Committed to Play Families Policy: No (app is not child-directed).

Third-party processors: Supabase (auth/settings plane), LogyByte-operated hub
(api.hub.logybyte.in). No advertising or data-sale answers anywhere.
