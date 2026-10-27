const { chromium } = require('playwright');
const dir = __dirname;

const assets = [
  ['icon.html', 'icon-512.png', 512, 512],
  ['feature.html', 'feature-1024x500.png', 1024, 500],
  ['screenshot-settings.html', 'screenshot-1-settings.png', 1080, 1920],
  ['screenshot-incar.html', 'screenshot-2-incar.png', 1080, 1920],
];

(async () => {
  const browser = await chromium.launch();
  for (const [html, out, w, h] of assets) {
    const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
    await page.goto('file://' + dir + '/' + html, { waitUntil: 'load' });
    await page.waitForTimeout(150);
    await page.screenshot({ path: dir + '/' + out, clip: { x: 0, y: 0, width: w, height: h } });
    await page.close();
    console.log('rendered', out, w + 'x' + h);
  }
  await browser.close();
})();
