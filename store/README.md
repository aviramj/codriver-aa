# Play Store listing assets

Generated graphics for the Google Play listing, plus the HTML/CSS sources and a
Playwright render script so they can be regenerated or tweaked.

| File | Size | Play Console slot |
|------|------|-------------------|
| `icon-512.png` | 512×512 | App icon (hi-res) |
| `feature-1024x500.png` | 1024×500 | Feature graphic |
| `screenshot-1-settings.png` | 1080×1920 | Phone screenshot (settings) |
| `screenshot-2-incar.png` | 1080×1920 | Phone screenshot (in use) |

Google Play needs the icon + feature graphic + at least **2** phone screenshots —
all four here cover that.

## Regenerate
Edit the `*.html` files (text/colours), then:

```bash
npm i playwright            # or: npx playwright install chromium
node render.js              # writes the PNGs next to the HTML
```
