# Chaty legal documents (P-07) - DRAFT, NOT PUBLISHED

Canonical texts live in `app/src/main/assets/legal/` (bundled for the in-app
acceptance step, P-06):
- `terms_of_use.md`
- `privacy_policy.md`

Both are v0.1 drafts (2026-11-04) and need Bandi's review plus two pending
facts before publishing:
1. Official customer-care/support address (asked, pending - the same gap that
   holds the login "Contact customer care" direct mailto).
2. Confirmation of the shipped account-deletion flow (Data Safety + policy both
   reference it; verify the actual delete path before claiming it).

## Free hosting recommendation
GitHub Pages on this existing public repo (Bandi-maya/chat-application):
- Settings > Pages > deploy from branch (e.g. main, /docs or a docs/ folder).
- Free, no new service, no billing, uses the repo Bandi already has; gives a
  stable https://bandi-maya.github.io/chat-application/... URL that Play
  Console accepts for the privacy policy field.
- Alternative with zero setup: link the raw file
  (https://raw.githubusercontent.com/Bandi-maya/chat-application/<branch>/docs/legal/privacy_policy.md).
  Play accepts it, but it renders as plain text; Pages is the better face.
Do NOT publish until the two pending facts above are resolved. Nothing here
has been published (no push access from my side anyway).
