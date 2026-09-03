# Publishing Paraphrase to Google Play

What is already done, and what only you can do. The steps that need your Google
account, your money, or your signing key are marked **[you]** — I can't do those
for you.

## 0. What is ready

- `app-release.aab` builds, minified and resource-shrunk (~1.8 MB)
- release signing wired to `keystore.properties` (gitignored)
- listing copy in `listing.md`, privacy policy in `../PRIVACY.md`
- store icon and feature graphic in `../brand/exports/`
- device screenshots in `screenshots/` (and 9:16-padded copies in
  `screenshots/9x16/` if Play rejects the tall originals)
- in-app reporting of AI output, required by Play's generative-AI policy

## 1. **[you]** Create the signing key

This is the identity of the app forever — if you lose it you cannot update the
app. Back up the `.jks` file and its passwords somewhere safe (a password
manager, not this repo).

```bash
keytool -genkeypair -v -keystore paraphrase-release.jks \
  -alias paraphrase -keyalg RSA -keysize 4096 -validity 10000
```

Then copy `keystore.properties.sample` to `keystore.properties` in the project
root and fill in the store path, the two passwords, and the alias. Both files
are gitignored.

## 2. Build the bundle

```bash
./gradlew clean bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`. Confirm it is signed
with your key, not unsigned:

```bash
~/Library/Android/sdk/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

## 3. **[you]** Play Console account

- Register at https://play.google.com/console — **$25 one-time fee**, and you
  must accept the Developer Distribution Agreement yourself.
- Personal accounts created since Nov 2023 must also verify identity, and must
  run a **closed test with at least 12 testers who stay opted in for 14 days**
  before they can apply for production access. Start that clock early: it is
  usually the longest part of shipping.

## 4. **[you]** Create the app and fill the listing

- App name, short and full description → `listing.md`
- App icon → `../brand/exports/icon-512.png`
- Feature graphic → `../brand/exports/feature-1024x500.png`
- Phone screenshots → `screenshots/` (at least 2; 4-6 is better)
- Privacy policy URL → publish `PRIVACY.md` at a public URL. The repo is public,
  so enabling GitHub Pages gives you one; a `github.com/.../blob/main/PRIVACY.md`
  link is also accepted.
- Data safety form → `data-safety.md`
- Content rating questionnaire → answer honestly; expect Everyone / PEGI 3
- Notes for the reviewer → bottom of `listing.md` (the app is fully testable
  without an API key, which avoids a common rejection)

The contact address (`report_email` in `strings.xml`, and the bottom of
`PRIVACY.md`) is set to anish.m.prasad@gmail.com. Change it in both places if
you later move to a support alias.

## 5. Run the trial

Two different things, easy to confuse:

| Track | Testers | Speed | Use it for |
|---|---|---|---|
| **Internal testing** | up to 100, by email | live in minutes, no review wait | your own trial run |
| **Closed testing** | your 12+ testers | needs review | the 14-day requirement above |
| **Production** | everyone | full review | after the closed test |

For your trial: Play Console → Testing → **Internal testing** → Create new
release → upload the `.aab` → add your own Google account as a tester → copy the
opt-in link, open it on your phone, accept, then install from Play.

Note the installed app will be signed by Google (Play App Signing), so it is a
different signature from your local debug build — uninstall the sideloaded
version first or the install will fail.

## 5b. "Finish setting up your app" — exact answers

The dashboard task list gates closed testing (internal testing does not wait for
it). Answers for Paraphrase as built:

| Task | Answer |
|---|---|
| **App access** | All functionality is available without special access. True: with no API key the app falls back to the on-device rewriter, so every screen works. |
| **Ads** | No, my app does not contain ads. |
| **Content ratings** | Category: *Utility, Productivity, Communication or Other*. No violence, sexuality, profanity, drugs, gambling. Users cannot interact or exchange content. No location sharing. No digital purchases. Where asked about AI-generated content, say yes — the app displays model output — and point to the in-app Report action. |
| **Target audience** | 18 and over. Choosing 13-17 pulls the app into extra Families policy requirements it does not need. |
| **News app** | No. |
| **Data safety** | See `data-safety.md`. One type: App activity → Other user-generated content; shared with a third party, not collected; encrypted in transit; optional; purpose is app functionality. |
| **Government apps** | No. |
| **Financial features** | None of these. |
| **Health** | No. |
| **Privacy policy** | https://github.com/anishmprasad/paraphrase/blob/main/PRIVACY.md |
| **Store listing** | Copy from `listing.md`; icon `../brand/exports/icon-512.png`; feature graphic `../brand/exports/feature-1024x500.png`; screenshots from `screenshots/`. |
| **App category** | Productivity. Contact email: anish.m.prasad@gmail.com |

Order that wastes the least time:

1. Upload the AAB to **Internal testing** now — it does not wait for any of the
   above, and it gets the app onto your phone in minutes.
2. Work through the table while that is live.
3. Start the **closed test** as soon as the tasks clear, because its 14-day
   clock is the long pole.

## 6. Automating uploads (optional)

`.github/workflows/play-internal.yml` uploads a tagged build to the internal
track. It is inert until you add three repository secrets:

- `KEYSTORE_BASE64` — `base64 -i paraphrase-release.jks | pbcopy`
- `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`
- `PLAY_SERVICE_ACCOUNT_JSON` — from Play Console → Setup → API access, granting
  the service account "Release manager" on this app only

Then `git tag v1.0.0 && git push --tags` builds and uploads.

## Things likely to come up in review

- **Screenshots must match the app.** The ones in `screenshots/` were taken with
  no API key configured, so they show the on-device fallback rewriter and its
  "No API key set" notice. Retake screenshot 05 with your own Gemini key so the
  listing shows what a real user with a key sees.
- **Don't imply unlimited free AI.** The listing says the user brings their own
  key on a provider's free tier, which is accurate; keep it that way.
- **`ACTION_PROCESS_TEXT` needs no special permission** and is not sensitive —
  the app requests only `INTERNET`. If a reviewer asks why the app appears in
  other apps' menus, that intent filter is the answer.
