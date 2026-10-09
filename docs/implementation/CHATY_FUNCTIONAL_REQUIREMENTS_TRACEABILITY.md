# Chaty Functional Requirements and Traceability

Use this matrix during implementation. Update the source file, test IDs, and status only after verifying current repository behavior. "Planned" is not "implemented."

| ID | Requirement | Component/domain | Acceptance test | Priority |
|---|---|---|---|---|
| APP-001 | Existing routes and working behaviors remain unchanged unless explicitly migrated | Router/app shell | Route regression suite | P0 |
| APP-002 | Async work is scoped to current auth session | Controllers/repositories | Rapid sign-out/account-switch test | P0 |
| SEC-001 | MLS message edit uses encrypted payload and MLS RPC | Message repository/MLS | Server stores ciphertext; recipients decrypt new text | P0 |
| SEC-002 | No plaintext fallback for MLS messages | Message edit path | Force RPC failure; assert no plaintext write | P0 |
| SEC-003 | Linked-device revocation persists server-side | Device service | Revoked device no longer active | P0 |
| SEC-004 | Privacy settings are real and accurately described | Privacy UI/backend | Server contract tests | P0 |
| SET-001 | Typed versioned profile is canonical | Appearance model/repository | Schema validation tests | P0 |
| SET-002 | Legacy preferences migrate without loss | Migration service | Fixture migration tests | P0 |
| SET-003 | Preview does not mutate applied profile | Settings controller | Cancel preview test | P0 |
| SET-004 | Apply persists atomically | Repository/controller | Persistence failure rollback test | P0 |
| SET-005 | Category reset preserves other categories | Settings | Reset isolation test | P1 |
| SET-006 | Full reset requires confirmation | Settings | Confirmation/cancel test | P1 |
| SET-007 | Invalid imports cannot partially apply | Import validator | Malformed/unknown/oversized profile test | P0 |
| SET-008 | Export contains appearance only | Exporter | Forbidden-field scan | P0 |
| SET-009 | Theme changes update all consumers | Theme resolver/shared widgets | Golden/widget tests | P1 |
| SET-010 | Built-in and custom templates are supported | Template gallery/repository | Save/duplicate/apply/delete tests | P1 |
| UI-001 | Shared header uses profile tokens consistently | Header components | Cross-route golden test | P1 |
| UI-002 | Bottom navigation variants remain route-correct | App shell | All-tab navigation test | P0 |
| UI-003 | Bubble variants preserve message semantics | Message bubble | Incoming/outgoing/reply/media tests | P0 |
| UI-004 | Tick style never changes actual receipt state | Receipt component | Model-to-icon state tests | P0 |
| UI-005 | Composer variants preserve send/record/attachment actions | Composer | Text/media/voice flow tests | P0 |
| UI-006 | Modal headers/footers and internal scrolling behave correctly | Sheets/dialogs | Small-screen/keyboard tests | P1 |
| UI-007 | No content overlaps status bar/notch/home indicator | All screens | Android/iOS inset tests | P0 |
| UI-008 | System font scaling and screen reader are supported | Shared UI | Large text/TalkBack/VoiceOver tests | P0 |
| UI-009 | Contrast and target sizes meet documented thresholds | Tokens/components | Accessibility guideline tests | P1 |
| MSG-001 | Offline message retry is idempotent | Outbox/repository | Duplicate/reconnect test | P0 |
| MSG-002 | Message edit/delete/reaction state reconciles from server | Message repository | RPC failure/reload test | P0 |
| MEDIA-001 | Media upload has progress/cancel/retry | Media service | Failure and cleanup tests | P1 |
| VOICE-001 | Recording works with permission/lifecycle changes | Audio service | Android/iOS device tests | P0 |
| CALL-001 | Call state machine handles timeout/busy/network loss | Call service | State transition integration tests | P0 |
| CALL-002 | Call history is authoritative and not duplicated | Call history service | Exactly-one terminal record test | P0 |
| STATUS-001 | Publish/view state is persisted by real service | Status service | Server persistence and expiry test | P1 |
| DEV-001 | Device list and revocation reflect backend truth | Device service | Cross-device revocation test | P0 |
| PERF-001 | Long chat lists remain smooth and stable | Chat screens | Profiled large-history scroll test | P1 |
| CI-001 | Hosted Supabase Auth preflight passes | CI workflow/config | Production-like secret/config check | P0 |
| CI-002 | Flutter analyze/tests and Android build pass | CI | Required checks green | P0 |
| IOS-001 | iOS build/tests pass on available macOS runner | CI | iOS workflow green | P1 |
