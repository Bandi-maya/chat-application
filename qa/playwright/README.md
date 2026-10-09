# Chaty attached-Chrome Playwright runtime audit

This harness is for **runtime inspection of an already-running Chaty web session**. It does not start a replacement browser, clear browser storage, sign the user out, or reset application preferences. It inventories accessible controls, captures screenshots and page state, records console/page/network failures, and cautiously exercises only low-risk navigation/menu controls.

The repository is a Flutter app. This browser harness does not replace Flutter widget tests, Android emulator/device testing, or iOS Simulator/device testing.

## Requirements

- Node.js 20 or newer
- A Chrome/Chromium session with Chrome DevTools Protocol (CDP) enabled
- Chaty already open in that Chrome session
- Permission to inspect the session and its app data

Playwright cannot attach to a normal Chrome process unless remote debugging is enabled. If Chrome is already running without a CDP endpoint, do not kill it or discard its profile without permission. Ask the user/agent operator to open a debugging-enabled instance with the same intended profile, then log in as a dedicated test user if authentication is required.

## Run against the existing Chrome session

From the repository root:

```bash
cd qa/playwright
npm install
# Default endpoint: http://127.0.0.1:9222
# Optional: set CHATY_CDP_URL to the existing Chrome CDP endpoint.
# Optional: set CHATY_BASE_URL to select an existing tab by URL substring;
# if absent, the first non-Chrome internal tab is used.
npm run audit
```

Examples:

**Linux**
```bash
google-chrome --remote-debugging-port=9222 --user-data-dir=/tmp/chaty-playwright-profile
```

**Windows PowerShell**
```powershell
& "$env:ProgramFiles\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222 --user-data-dir="$env:TEMP\chaty-playwright-profile"
```

Use a dedicated test profile for the debugging-enabled session. Do not copy cookies or session tokens from the normal profile into logs or test artifacts.

## Outputs

- Playwright attachments: initial screen and runtime audit JSON.
- `qa/playwright/reports/runtime-audit.json`: screen snapshots, visible controls, interaction attempts, console/page/network errors.
- `qa/playwright/reports/runtime-audit.md`: human-readable summary.
- `artifacts/playwright/`: screenshots, traces and Playwright results.
- `artifacts/playwright-report/`: HTML report.

Generated reports can contain visible application text and URLs. Review/redact sensitive data before sharing or committing them.

## Important limitations

- The audit begins listening after it attaches, so errors emitted before attachment are not observable. Reload only when it is safe and explicitly intended.
- One run covers the attached tab and low-risk interactions exposed during that run. Navigate through every screen and rerun, or extend the test with route-specific fixtures and test accounts. Do not interpret one screen's clean output as a complete audit.
- Actions that send messages, mutate accounts, save/reset settings, place calls, revoke devices, or delete data are inventoried but intentionally not activated automatically.
- This does not prove Android/iOS native behavior. Use Flutter tests, Android emulator/device logs, and iOS Simulator/device logs for those paths.
