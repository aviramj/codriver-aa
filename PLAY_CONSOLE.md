# Getting CoDriver AI into your car (Google Play Console)

Android Auto **won't load a sideloaded Car App Library app in a real vehicle** —
the "Unknown sources" developer toggle explicitly doesn't apply to these apps.
The app must be installed from a **trusted source**. The sanctioned, review-free
path for personal use is Google Play's **Internal testing** track:

> Google's own docs: *"To test your app in real vehicles, you must install it
> from a trusted source such as Google Play… You can use Internal App Sharing or
> an Internal Test Track to distribute your app to devices **without going through
> the Google Play review process**."*

Good news about the much-discussed **"12 testers / 14 days" rule**: it applies
only to unlocking **production** access for personal accounts, **not** to internal
testing. Internal testing is available immediately, up to 100 testers — which for
"just me and my car" is all you need.

**What only you can do** (they need your identity/payment/Google login): create
the developer account, and upload/configure in your own console. **What's already
done for you here:** the app targets API 36 (required for new uploads as of Aug
31 2026), release signing is wired up, and `./build-aab.sh` produces the exact
file you'll upload.

---

## Step 0 — Build the signed AAB (on a machine with the Android SDK)

```bash
./setup-toolchain.sh     # once, if you don't already have the Android SDK
./make-upload-key.sh     # once: creates upload-keystore.jks + keystore.properties
./build-aab.sh           # -> codriver-release.aab
```

Keep **`upload-keystore.jks`** safe and backed up — it's your upload key. (If you
ever lose it, Play can reset it, because Google holds the real app-signing key.)
Both the keystore and `keystore.properties` are gitignored; never commit them.

> Prefer Android Studio? `Build ▸ Generate Signed App Bundle ▸ Android App
> Bundle`, create/choose the keystore, pick the **release** build. Same result.

## Step 1 — Create a Google Play developer account

1. Go to <https://play.google.com/console> and sign in with the Google account
   you want to own the app.
2. Choose **Personal** account type, pay the **one-time $25** fee, and complete
   **identity verification** (legal name + address; Google may ask for ID). This
   verification can take anywhere from minutes to a couple of days — it's the one
   step that might make you wait, so start it early.

## Step 2 — Create the app

In Play Console: **Create app**, then:
- **App name:** CoDriver AI
- **Default language:** your choice
- **App or game:** App
- **Free or paid:** Free
- Accept the developer program policies & US export declarations → **Create app**.

## Step 3 — Publish a privacy policy, then fill App content

Because the app uses the **microphone**, Play requires a privacy policy URL. A
ready-made page is at **`docs/index.html`** (filled in with your email), and this
repo is **public**, so GitHub Pages hosts it for free:
1. On GitHub, open this repo's **Settings ▸ Pages**, set **Source: Deploy from a
   branch**, choose branch **`main`** and folder **`/docs`**, then **Save**. After
   ~1 minute your policy is live at **https://aviramj.github.io/codriver-aa/**
2. In Play Console: **App content** (left nav) → **Privacy policy** → paste that URL.
3. Work through the other **App content** items the console marks required:
   - **Data safety:** declare that the app sends **audio** and **text** you
     provide to a third-party AI provider for processing (to transcribe speech
     and generate replies); the app itself keeps no server-side copy. Be honest
     and match `PRIVACY.md`.
   - **Content rating** questionnaire (quick).
   - **Target audience:** not directed to children.
   - **Ads:** No ads.
   - **Government apps / financial / health:** No.

> You can complete these before or after uploading the build, but internal-test
> rollout won't proceed until the items shown in red are done.

## Step 4 — Set up the Internal testing release

1. Left nav: **Testing ▸ Internal testing** → **Create new release**.
2. **App signing:** when prompted, choose **"Use Google-generated key"** (Play
   App Signing, recommended). You upload an AAB signed with your upload key;
   Google re-signs for distribution.
3. **Upload** `codriver-release.aab`.
4. **Release name** / notes: anything (e.g. "1.0 (1)").
5. **Next / Save**, review warnings (a non-blocking warning or two is normal),
   then **Start rollout to Internal testing** → confirm.

## Step 5 — Add yourself as a tester

1. On the **Internal testing** page, open the **Testers** tab.
2. **Create email list**, add the Google account you'll use **on your phone**
   (it must be a Google account you control and that's signed in on the phone),
   save, and tick the list for this track.
3. Copy the **"Copy link"** opt-in URL (it appears once the release has finished
   processing — can take a few minutes).

## Step 6 — Install on your phone

1. On the **phone signed into that tester account**, open the opt-in URL in a
   browser, tap **Become a tester / Accept**, then **"Download it on Google
   Play"** — this opens the Play Store listing.
2. Install **CoDriver AI** from Play. Installing from Play is what makes it a
   "trusted source."
3. Open the app, enter your API key / settings, and tap **Send test & speak** to
   confirm it works.

## Step 7 — Verify in the car

1. Connect the phone to your car (USB or wireless Android Auto) as usual.
2. CoDriver AI should appear in the Android Auto app grid. If it doesn't show,
   on the phone open **Android Auto settings ▸ Customize launcher** and make sure
   it's enabled/visible.
3. Tap it, tap **Speak**, and talk.

You do **not** need Android Auto "developer mode / unknown sources" for this —
the Play install already satisfies the trusted-source requirement.

---

## Updating the app later

1. Bump **`versionCode`** (and usually `versionName`) in `app/build.gradle.kts`.
2. `./build-aab.sh` again.
3. Play Console ▸ Internal testing ▸ **Create new release** ▸ upload ▸ roll out.
   Testers get the update through Play automatically.

## Troubleshooting

- **"Your app targets an old version of Android"** on upload → make sure you
  built from this project (targetSdk is 36). Rebuild with `./build-aab.sh`.
- **Opt-in link 404 / "not a tester"** → the phone must be signed into the exact
  Google account you added to the tester list; the release must have finished
  processing.
- **Installed but not in the car** → confirm it was installed **from Play** (not
  a leftover sideloaded build — uninstall any sideloaded copy first), and check
  **Customize launcher** in Android Auto settings.
- **Data safety / privacy policy blocking rollout** → complete those App content
  items; the privacy policy URL must be publicly reachable.

## Honest caveats

- **Category is a placeholder.** The app declares the `POI` car category because
  there is no "AI assistant" category. That's fine for **internal testing**
  (no content review). Promoting to **production** (public) would require passing
  Play's car-app review, which this app is **not** intended for as-is.
- **This keeps the app yours.** Internal testing is private to the testers you
  add — it won't be publicly listed.
