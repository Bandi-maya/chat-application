# OWNER-TO-CONFIRM checklist - Chaty legal/Data Safety claims
2026-11-04, publisher review of writer-F delta 7/8. Every row is an unverified
claim currently present in the bundled legal drafts or Data Safety draft.
Bandi confirms or corrects each row; publishing is blocked until all rows are
resolved. ReleaseFeatures.LEGAL_ACCEPTANCE_GATE stays OFF until then.

| # | Claim | Where it appears | What to confirm |
|---|---|---|---|
| 1 | Service is "operated by LogyByte" | ToS section 2; privacy "Where it is processed" | Exact legal entity/operator name to publish. |
| 2 | Official contact/customer-care address | ToS section 8; privacy "Contact"; login error-report action | The real address/route. Still pending - also gates the direct mailto in LoginScreen. |
| 3 | "We do not sell your data" | privacy "How we use it"; Data Safety | Confirm as business practice. |
| 4 | "We do not show advertising" | privacy "How we use it"; Data Safety (no ads SDK found in source) | Confirm practice + confirm no ads SDK in the built artifact. |
| 5 | "Call audio and video are not recorded by the service" | privacy "What we collect" | No recording path found client-side; confirm server side. |
| 6 | "Reports are reviewed and acted on" | ToS section 5 | The actual moderation/review process and who operates it. |
| 7 | "Content is kept while your account is active" (retention) | privacy "Retention and deletion" | Actual retention/deletion behavior per data class. |
| 8 | Account deletion flow exists | privacy "Retention and deletion"; Data Safety security practices | Confirm the shipped delete-account path; if none, Data Safety is a submission blocker. |
| 9 | "Traffic is encrypted in transit using TLS" | privacy "Where it is processed"; Data Safety | Confirm ALL endpoints enforce TLS: api.hub.logybyte.in, Supabase, openfreemap.org tiles, and any third-party fetch endpoint (translation, link preview). |
| 10 | Translation sends message text to Google's web translation service | privacy "Translation"; Data Safety messages row | Current candidate includes user-triggered Google web translation (en/ar/hi/fr/es); Android runtime proof pending. Confirm final provider practice and disclosure scope. |
| 11 | Link previews fetch third-party pages/images from the user's device | privacy "Link previews" | Next staged candidate includes automatic public-page/image link previews from the device; Android/runtime proof pending. Confirm intended behavior and disclosures. |
| 12 | End-to-end encryption claim for messages | Data Safety messages row (draft says claim only "encrypted in transit" until verified) | Server architecture: can the relay read message content? MLS/Signal stack is client-present; the E2E claim needs backend confirmation. |
