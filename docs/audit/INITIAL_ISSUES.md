# Initial Issues

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

MAP-01 blank pre-map screen observed on user phone; CALL-01 user reports nonconnecting calls. Audit-discovered source and synthetic-service findings appear separately in ISSUE_REGISTER.md. Staged patch does not mean fixed/tested; retain original evidence when later tests arrive.


## Runtime outcome update,01:54 IST
Next4 runtime job failed during Android test compilation, BEFORE app installation/instrumentation: AttachmentFileCacheTest.kt:295 unresolved EditSession/outputFile reference. No app started, no runtime XML or app visual passes. This is a test-source compile failure, not an observed app crash. Native and main application build and1006 JVM unit passes remain valid. Source: https://github.com/Bandi-maya/chat-application/actions/runs/40389044496/job/121812020284 . A missing EditSession import is the test compile cause; outputFile is a cascade error. No production defect is shown by this compile failure. Next5 launch was recorded at01:49; no completed outcome is recorded in this review.
