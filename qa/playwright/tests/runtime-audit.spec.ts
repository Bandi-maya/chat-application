import { test, expect, chromium, type Page } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

type RuntimeIssue = {
  kind: 'console-error' | 'page-error' | 'request-failed' | 'http-error';
  message: string;
  url?: string;
  status?: number;
};

type Control = {
  index: number;
  tag: string;
  role: string | null;
  label: string;
  title: string | null;
  testId: string | null;
  href: string | null;
  disabled: boolean;
  visible: boolean;
  rect: { x: number; y: number; width: number; height: number };
};

type ScreenSnapshot = {
  capturedAt: string;
  url: string;
  title: string;
  heading: string;
  viewport: { width: number; height: number };
  visibleText: string;
  controls: Control[];
};

const cdpEndpoint = process.env.CHATY_CDP_URL ?? 'http://127.0.0.1:9222';
const targetUrl = process.env.CHATY_BASE_URL;
const destructiveOrExternal = /delete|remove|revoke|logout|log out|sign out|send|submit|upload|record|call|accept|decline|invite|block|report|reset|apply|save|confirm|forward|share|purchase|payment|clear chat|leave group|create group|new chat/i;
const safeInteraction = /^(back|close|cancel|menu|more options|open menu|settings|chats|updates|calls|profile|search|expand|collapse|show more|show less|next tab|previous tab)$/i;

async function snapshot(page: Page): Promise<ScreenSnapshot> {
  return page.evaluate(() => {
    const isVisible = (element: Element): boolean => {
      const node = element as HTMLElement;
      const style = window.getComputedStyle(node);
      const rect = node.getBoundingClientRect();
      return style.display !== 'none' && style.visibility !== 'hidden' &&
        Number(style.opacity) !== 0 && rect.width > 0 && rect.height > 0;
    };
    const selector = [
      'button', 'a[href]', 'input', 'textarea', 'select',
      '[role="button"]', '[role="link"]', '[role="tab"]',
      '[role="menuitem"]', '[role="switch"]', '[role="checkbox"]',
      '[role="radio"]', '[role="combobox"]', '[tabindex]:not([tabindex="-1"])',
    ].join(',');
    const controls = Array.from(document.querySelectorAll(selector))
      .filter(isVisible)
      .map((element, index) => {
        const node = element as HTMLElement;
        const rect = node.getBoundingClientRect();
        const label = (
          node.getAttribute('aria-label') ||
          node.getAttribute('aria-labelledby') ||
          node.getAttribute('title') ||
          node.innerText ||
          (node as HTMLInputElement).placeholder ||
          (node as HTMLInputElement).value ||
          ''
        ).replace(/\s+/g, ' ').trim().slice(0, 180);
        return {
          index,
          tag: node.tagName.toLowerCase(),
          role: node.getAttribute('role'),
          label,
          title: node.getAttribute('title'),
          testId: node.getAttribute('data-testid'),
          href: (node as HTMLAnchorElement).href || null,
          disabled: Boolean((node as HTMLButtonElement).disabled) ||
            node.getAttribute('aria-disabled') === 'true',
          visible: true,
          rect: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) },
        };
      });
    const headings = Array.from(document.querySelectorAll('h1,h2,h3,[role="heading"]'))
      .filter(isVisible).map((node) => (node.textContent || '').trim()).filter(Boolean);
    return {
      capturedAt: new Date().toISOString(),
      url: location.href,
      title: document.title,
      heading: headings.join(' | ').slice(0, 500),
      viewport: { width: innerWidth, height: innerHeight },
      visibleText: (document.body?.innerText || '').replace(/\n{3,}/g, '\n\n').slice(0, 12_000),
      controls,
    };
  });
}

test('audit the currently running Chaty screen in the existing Chrome session', async ({}, testInfo) => {
  let browser;
  try {
    browser = await chromium.connectOverCDP(cdpEndpoint, { timeout: 8_000 });
  } catch (error) {
    throw new Error(
      `Could not attach to existing Chrome at ${cdpEndpoint}. Start/relaunch the existing Chrome profile with remote debugging enabled (for example --remote-debugging-port=9222), then rerun. No replacement browser was launched. Original error: ${String(error)}`,
    );
  }

  const issues: RuntimeIssue[] = [];
  const snapshots: ScreenSnapshot[] = [];
  const context = browser.contexts()[0];
  if (!context) {
    await browser.close();
    throw new Error('The attached Chrome session has no accessible browser context.');
  }

  let pages = context.pages();
  let page = targetUrl
    ? pages.find((candidate) => candidate.url().includes(targetUrl))
    : pages.find((candidate) => !candidate.url().startsWith('chrome://') && !candidate.url().startsWith('devtools://'));

  if (!page && targetUrl) {
    page = await context.newPage();
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
  }
  if (!page) {
    await browser.close();
    throw new Error('No ordinary application tab was found. Open Chaty in the existing Chrome session or set CHATY_BASE_URL.');
  }

  page.on('console', (message) => {
    if (message.type() === 'error') issues.push({ kind: 'console-error', message: message.text(), url: page!.url() });
  });
  page.on('pageerror', (error) => issues.push({ kind: 'page-error', message: error.stack || error.message, url: page!.url() }));
  page.on('requestfailed', (request) => {
    issues.push({ kind: 'request-failed', message: request.failure()?.errorText || 'Request failed', url: request.url() });
  });
  page.on('response', (response) => {
    if (response.status() >= 400) {
      issues.push({ kind: 'http-error', message: response.statusText(), url: response.url(), status: response.status() });
    }
  });

  // Keep the user's current tab/session. Do not clear storage, log out, or
  // reset app preferences; those would destroy the state being investigated.
  await page.waitForTimeout(1_000);
  snapshots.push(await snapshot(page));
  const initial = snapshots[0];
  await testInfo.attach('initial-screen.json', {
    body: Buffer.from(JSON.stringify(initial, null, 2)),
    contentType: 'application/json',
  });
  await page.screenshot({ path: testInfo.outputPath('initial-screen.png'), fullPage: true }).catch(() => undefined);

  // Exercise only low-risk navigation/menu controls. Business actions that
  // send, delete, upload, call, save, reset, or otherwise mutate data are
  // inventoried but intentionally not triggered without an explicit test plan.
  const attempted = new Set<string>();
  for (let pass = 0; pass < 12; pass += 1) {
    const current = await snapshot(page);
    const candidate = current.controls.find((control) => {
      if (!control.visible || control.disabled || !control.label) return false;
      if (destructiveOrExternal.test(control.label)) return false;
      if (!safeInteraction.test(control.label)) return false;
      const key = `${current.url}| ${control.role || control.tag}|${control.label}`;
      return !attempted.has(key);
    });
    if (!candidate) break;
    const key = `${current.url}| ${candidate.role || candidate.tag}|${candidate.label}`;
    attempted.add(key);

    try {
      const locator = candidate.testId
        ? page.getByTestId(candidate.testId).first()
        : candidate.role
          ? page.getByRole(candidate.role as 'button' | 'link' | 'tab' | 'menuitem' | 'switch' | 'checkbox' | 'radio' | 'combobox', { name: candidate.label, exact: true }).first()
          : page.getByText(candidate.label, { exact: true }).first();
      await locator.click({ timeout: 2_000 });
      await page.waitForTimeout(250);
      snapshots.push(await snapshot(page));
      await page.screenshot({ path: testInfo.outputPath(`interaction-${snapshots.length}.png`), fullPage: true }).catch(() => undefined);
    } catch (error) {
      issues.push({ kind: 'console-error', message: `Safe interaction failed for "${candidate.label}": ${String(error)}`, url: page.url() });
    }
  }

  const report = {
    generatedAt: new Date().toISOString(),
    attachedChromeEndpoint: cdpEndpoint,
    targetUrl: targetUrl || null,
    note: 'This report covers the attached session and controls visible during this run; it is not proof that undiscovered routes or native Android/iOS paths passed.',
    screens: snapshots,
    issues,
    interactionAttempts: Array.from(attempted),
    summary: {
      snapshots: snapshots.length,
      uniqueControls: new Set(snapshots.flatMap((screen) => screen.controls.map((control) => `${screen.url}|${control.role || control.tag}|${control.label}`))).size,
      runtimeIssues: issues.length,
      consoleErrors: issues.filter((issue) => issue.kind === 'console-error').length,
      pageErrors: issues.filter((issue) => issue.kind === 'page-error').length,
      failedRequests: issues.filter((issue) => issue.kind === 'request-failed').length,
      httpErrors: issues.filter((issue) => issue.kind === 'http-error').length,
    },
  };

  await testInfo.attach('runtime-audit.json', {
    body: Buffer.from(JSON.stringify(report, null, 2)),
    contentType: 'application/json',
  });
  await fs.mkdir(path.resolve('reports'), { recursive: true });
  await fs.writeFile(path.resolve('reports/runtime-audit.json'), JSON.stringify(report, null, 2));
  await fs.writeFile(path.resolve('reports/runtime-audit.md'), [
    '# Chaty Runtime Audit',
    '',
    `- Generated: ${report.generatedAt}`,
    `- Initial URL: ${initial.url}`,
    `- Screens/snapshots: ${report.summary.snapshots}`,
    `- Unique visible controls: ${report.summary.uniqueControls}`,
    `- Runtime issues: ${report.summary.runtimeIssues}`,
    '',
    '## Runtime issues',
    ...(issues.length ? issues.map((issue, index) => `${index + 1}. **${issue.kind}** ${issue.status ? `(${issue.status}) ` : ''}${issue.message.replace(/\n/g, ' ')} — ${issue.url || ''}`) : ['No runtime issues were observed after the audit listeners attached. This does not prove the app is error-free before attachment or on other screens/platforms.']),
    '',
    '## Visible controls by snapshot',
    ...snapshots.flatMap((screen, index) => [
      `### Snapshot ${index + 1}: ${screen.heading || screen.title || screen.url}`,
      `URL: ${screen.url}`,
      ...screen.controls.map((control) => `- [${control.disabled ? 'disabled' : 'visible'}] ${control.role || control.tag}: ${control.label || '(unlabelled)'}${control.testId ? ` (test id: ${control.testId})` : ''}`),
      '',
    ]),
  ].join('\n'));

  expect(snapshots.length, 'The audit should capture the current application screen').toBeGreaterThan(0);
  await browser.close();
});
