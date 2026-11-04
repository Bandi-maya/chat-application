# QA component matrix

The initial static component census is `qa/component-inventory-initial.csv`: 783 composable candidates across 228 files. Static candidates are not routes, runtime coverage or a complete option-to-consumer map. Every initial row is NOT VERIFIED/NOT RUN.

The permission/service declaration census is `qa/permission-service-inventory-initial.csv`, likewise AUDIT REQUIRED/NOT RUN.

## Final acceptance families

Bootstrap/auth/process restore; every navigation/template/destination; inbox sorting/swipes; timeline/message types/status/actions; composer/attachments/emoji; media/camera/editors; voice/DSP; calls/state/routes/permissions; search/contacts/profile/groups; privacy/locks/vault/MLS; settings/persistence/consumers; maps/location; island/notification/overlays; tasks/automation/transfers/updates and gated features.

Each family needs explicit route/fixture, expected behavior, positive/negative/failure tests, owner, source SHA, executed result and pixel/trace artifact. Do not expose gated features merely to satisfy reachability. Do not send real messages, call real contacts or delete existing account data for QA.

## Open gate

The census is partial. Full option inventory, consumer wiring, back/navigation, every theme/viewport/accessibility and real backend/physical tests remain unverified. No full-app QA completion claim is supported.
