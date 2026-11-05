const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const root = path.resolve(__dirname, "..");
const edge = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";

async function capture(browser, viewport, file, { openChat = false, pageType = "" } = {}) {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  const errors = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("http://127.0.0.1:8000/ui/", { waitUntil: "networkidle" });
  await page.locator(".character-card").first().waitFor();
  if (openChat) {
    await page.locator(".character-card").nth(1).click();
    await page.locator(".chat-workbench").waitFor();
    await page.waitForTimeout(250);
  }
  if (pageType === "settings") {
    await page.locator(".sidebar nav button").nth(2).click();
    await page.locator(".modal.full-page").waitFor();
  } else if (pageType === "create") {
    await page.locator(".sidebar nav button").nth(1).click();
    await page.locator(".modal.full-page").waitFor();
  }
  const layout = await page.evaluate(() => {
    const box = (selector) => {
      const element = document.querySelector(selector);
      if (!element) return null;
      const rect = element.getBoundingClientRect();
      return {
        top: Math.round(rect.top),
        right: Math.round(rect.right),
        bottom: Math.round(rect.bottom),
        left: Math.round(rect.left),
        width: Math.round(rect.width),
        height: Math.round(rect.height),
      };
    };
    return {
      viewport: { width: window.innerWidth, height: window.innerHeight },
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth,
      shell: box(".app-shell"),
      sidebar: box(".sidebar"),
      chat: box(".chat-screen"),
      inspector: box(".character-inspector"),
      chatTop: box(".chat-top"),
      composer: box(".composer"),
      activeElement: document.activeElement?.tagName,
      imagesMissingDimensions: Array.from(document.images).filter(
        (image) => !image.hasAttribute("width") || !image.hasAttribute("height"),
      ).length,
      unnamedIconButtons: Array.from(document.querySelectorAll("button")).filter(
        (button) =>
          !button.textContent?.trim()
          && !button.getAttribute("aria-label")
          && !button.getAttribute("title"),
      ).length,
    };
  });
  await page.screenshot({ path: path.join(root, file), fullPage: false });
  let interaction = {};
  if (pageType === "settings") {
    await page.keyboard.press("Escape");
    interaction.dialogClosedWithEscape = (await page.locator(".modal.full-page").count()) === 0;
  }
  if (openChat) {
    const toggle = page.locator('button[aria-controls="character-inspector"]');
    if (await page.locator(".character-inspector").count()) await toggle.click();
    await toggle.click();
    await page.waitForTimeout(100);
    interaction.inspectorOpened = (await page.locator(".character-inspector").count()) === 1;
    const inspectorBox = await page.locator(".character-inspector").boundingBox();
    interaction.inspectorInsideViewport = Boolean(
      inspectorBox
      && inspectorBox.x >= 0
      && inspectorBox.x + inspectorBox.width <= viewport.width + 1,
    );
    await page.screenshot({
      path: path.join(root, file.replace(".png", "-inspector.png")),
      fullPage: false,
    });
  }
  await context.close();
  return { layout, interaction, errors };
}

(async () => {
  const browser = await chromium.launch({ executablePath: edge, headless: true });
  const report = {
    desktopHome: await capture(browser, { width: 1440, height: 960 }, "docs/images/PC-0.18-home.png"),
    desktopChat: await capture(browser, { width: 1440, height: 960 }, "docs/images/PC-0.18-chat.png", { openChat: true }),
    desktopSettings: await capture(browser, { width: 1440, height: 960 }, "docs/images/PC-0.18-settings.png", { pageType: "settings" }),
    desktopCreate: await capture(browser, { width: 1440, height: 960 }, "docs/images/PC-0.18-create.png", { pageType: "create" }),
    compactChat: await capture(browser, { width: 768, height: 900 }, "docs/images/PC-0.18-chat-compact.png", { openChat: true }),
    mobileLibrary: await capture(browser, { width: 375, height: 812 }, "docs/images/PC-0.18-mobile.png"),
    mobileChat: await capture(browser, { width: 375, height: 812 }, "docs/images/PC-0.18-chat-mobile.png", { openChat: true }),
  };
  await fs.writeFile(
    path.join(root, "outputs", "ui-qa-0.18.json"),
    JSON.stringify(report, null, 2),
    "utf8",
  );
  await browser.close();
  process.stdout.write(JSON.stringify(report, null, 2));
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
