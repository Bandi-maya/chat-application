# Chaty Playwright runtime coverage

## Available harness

Location: qa/playwright/

- Attaches to a currently running Chrome session over CDP.
- Selects the existing application tab or an explicitly configured target URL.
- Captures page title, URL, headings, visible text, viewport, and visible controls.
- Listens for console errors, uncaught page exceptions, failed requests, and HTTP error responses.
- Takes screenshots and attaches a JSON audit report.
- Cautiously clicks low-risk navigation/menu actions only.
- Fails when browser console errors or uncaught page exceptions are observed.

## Run

From qa/playwright:

    npm install
    npm run audit

Environment variables:

- CHATY_CDP_URL: CDP endpoint, default http://127.0.0.1:9222
- CHATY_BASE_URL: optional URL substring to select an existing tab; if no matching tab exists, opens that URL in the attached context

## Coverage boundaries

| Area | Current harness | Additional verification required |
|---|---|---|
| Existing Chrome session | CDP attach; no replacement browser | Verify endpoint/profile/session in the actual developer environment |
| Visible UI inventory | Captures currently visible accessible controls | Navigate through each screen and rerun or add route-specific fixtures |
| Low-risk navigation/menu | Attempts a small allowlist of controls | Confirm expected route/state changes and expand safe allowlist from observed labels |
| Destructive/external actions | Inventoried, not automatically triggered | Controlled test account and explicit test data |
| Console/page errors | Captured; console/page exceptions fail the test | Reproduce and fix each error, then rerun |
| HTTP/request failures | Captured in report | Classify expected auth/404/cancellation versus true failures |
| Flutter widgets | Not covered by Playwright | Flutter widget and integration tests |
| Android/iOS native APIs | Not covered by Playwright | Device/simulator builds, permission flows, logcat/Xcode logs |
| WebRTC media | Browser events alone are insufficient | Two-client audio/video and signaling verification |

## Evidence requirements

Each run should preserve the commit SHA, target URL, test account type (never credentials), viewport, JSON/Markdown report, screenshots/traces, runtime issue count, and known limitations. Remove private message contents and personally identifying data before sharing artifacts.
