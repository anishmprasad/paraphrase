# Paraphase — Privacy Policy

_Last updated: 3 September 2026_

Paraphase is an Android app that rewrites text you select in other apps. This
policy explains exactly what happens to that text and to your API key.

**Paraphase has no servers.** There is no account, no login, no analytics, no
advertising SDK, and no crash reporting. Nothing is sent to the developer.

## What leaves your device, and where it goes

**The text you choose to rewrite.** When you tap *Paraphrase* on a selection,
that selected text is sent over HTTPS directly from your phone to the AI
provider you configured, so that it can be rewritten, and the result is sent
back. It is not sent anywhere else and it is not stored by the app.

The provider is your choice, and each has its own privacy policy that governs
what it does with the text:

- Google Gemini — https://ai.google.dev/gemini-api/terms
- Groq — https://groq.com/privacy-policy/
- Any OpenAI-compatible endpoint you configure — governed by whoever operates it

If you use the **on-device basic rewriter**, no network request is made at all
and the text never leaves your phone.

## What stays on your device

- **Your API key**, stored in the app's private storage. It is sent only to the
  provider it belongs to, as that provider's authentication header. The
  developer never receives it.
- **Your settings** — chosen provider, model, rewrite style, and whether
  instant replace is on.

Neither is transmitted to the developer, and both are deleted when you
uninstall the app or clear its data.

## What Paraphase never collects

No name, email address, phone number, contacts, location, device identifiers,
advertising ID, installed-app list, or usage analytics.

## Reporting AI output

AI models can produce wrong, offensive, or harmful text. Every result has a
**Report** option, which opens your email app with the input and output filled
in. Nothing is sent until you send it yourself, and you can edit or delete
anything before sending.

## Children

Paraphase is a general-purpose writing utility and is not directed at children.

## Changes

Any change to this policy will be published in this file in the app's public
repository, with the date above updated.

## Contact

Questions about this policy: **TODO — add your contact email**

Source code: https://github.com/anishmprasad/paraphase
