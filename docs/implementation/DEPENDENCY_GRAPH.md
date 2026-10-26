# Dependency Graph

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

```text
source/checkpoint/inventory -> shared tokens + state/permission contracts
 -> home/nav/settings/themes/chat/media/tasks/profile/updates/maps in parallel
 -> call transport state -> call capsule/notification behavior
 -> security boundary checks across components
 -> integrated candidate -> build/units -> installed emulator/instrumentation/pixels
 -> signing/data safety + issue retest -> morning review with blockers
```
Manifest/AppRoot/ServiceLocator/build config changes need hunk ownership. Patches identify base/hash/dependencies/tests/limits. No destructive reset or overwrite of user work. Record later execution results with exact candidate, time and test evidence.
