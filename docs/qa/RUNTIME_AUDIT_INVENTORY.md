# Chaty runtime screen and component inventory

**Status: audit harness added; runtime inventory pending execution against the user's running session.** Do not mark a screen passed until it has been opened and its visible interactions have been exercised with observed outcomes.

## How to generate the initial inventory

See [Playwright runtime audit](../../qa/playwright/README.md). The audit attaches to an existing Chrome debugging session, records the visible screen and accessible controls, listens for runtime errors/network failures, and saves JSON/Markdown plus screenshots.

## Required per-screen record

| Field | Required evidence |
|---|---|
| Screen/route | Actual visible title, route/URL, navigation path |
| State | Signed out/in, loading/empty/populated/error, theme, viewport |
| Components | Visible buttons, links, tabs, fields, selectors, menus, dialogs, switches |
| Action | Exact control label and action performed |
| Expected | Observable state transition or server result |
| Actual | Observed result, including failure or no-op |
| Runtime | Console, page exception, failed request, Flutter/native log |
| Regression | Automated test or explicit reason automation is blocked |
| Platforms | Web/Chrome, Android device/emulator, iOS Simulator/device |
| Result | Pass, fail, blocked, not tested |

## Discovery checklist

The current source should be used to enumerate the actual routes and screens; do not assume this list is exhaustive.

- [ ] Startup, session restoration, login, signup, password recovery, verification, logout
- [ ] Main navigation, every tab and overflow destination
- [ ] Chat list, search, create/open conversation, empty/loading/error states
- [ ] Conversation: composer, send, replies, reactions, edits, deletion, forwarding, selection, starred messages, receipts
- [ ] Attachments, image/video viewer, audio playback, voice-note record/cancel/send
- [ ] Group conversation, participant management, contact info, linked devices/revocation
- [ ] Updates/status: list, viewer, creation, privacy, media and audio controls
- [ ] Calls: incoming, outgoing, connecting, connected, reconnecting, missed, history, termination, media controls
- [ ] Profile, account, privacy, security, app lock and device settings
- [ ] Every settings category, appearance/theme editor, template/component customization, navigation order, import/export/reset
- [ ] Shared dialogs, bottom sheets, popovers, menus, confirmation/error/success states
- [ ] Responsive/mobile/tablet layout, keyboard visible/hidden, safe areas, reduced motion, light/dark themes

## Safety rules

- Do not clear local storage, delete data, log out, or change production preferences merely to make the test deterministic.
- Do not trigger external messages, calls, uploads, invitations, destructive actions, or account changes without a dedicated test account and explicit safe test data.
- A visible click is not a passing assertion; verify the resulting UI state and, where applicable, backend state.
- Never record credentials, session cookies, access tokens, message plaintext, or private key material in reports.

## Latest completed source/automation audit

- Workflow: [Chaty Runtime Audit Inventory](https://github.com/Bandi-maya/chat-application/actions/runs/37955040175)
- Commit tested: b5090432aaf0a76e678c3e6b1f86cc0c50bbe217
- Dart files scanned: 256
- Candidate screen/component files: 102
- Route-constructor references: 85
- GitHub Actions workflow lint: passed
- Playwright TypeScript type-check: passed
- Playwright test discovery: passed
- Source inventory generation and artifact upload: passed

These are source and harness checks only. The report does not establish runtime coverage of all routes or prove Android/iOS feature parity. The latest commit's native runtime smoke and Flutter regression jobs are tracked separately in GitHub Actions.
