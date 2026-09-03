# Play Console → Data safety form

Suggested answers for Paraphase. Confirm each against the form as it appears —
Google changes the wording periodically — but this is the accurate picture of
what the app does.

## Data collection and sharing

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Selected text is transmitted off the device to the AI provider. |
| Is all user data encrypted in transit? | **Yes** | All provider calls are HTTPS. `network_security_config.xml` blocks cleartext except for localhost, for self-hosted endpoints. |
| Do you provide a way for users to request data deletion? | **No / not applicable** | The app stores nothing off-device. Uninstalling or clearing app data removes the key and settings. |

## Data types

Declare exactly one type.

**App activity → Other user-generated content**

| Field | Answer |
|---|---|
| Collected (sent to *your* servers) | **No** — the developer runs no servers |
| Shared (sent to a third party) | **Yes** — to the AI provider the user configures |
| Processed ephemerally | Yes, by the app; the provider's own retention is governed by its policy |
| Required or optional | **Optional** — the on-device rewriter needs no network at all |
| Purpose | **App functionality** |

### Explicitly NOT collected or shared

Personal info, financial info, health, location, contacts, calendar, photos,
files, messages, browsing history, device or advertising IDs, app performance
or diagnostics.

### On the API key

The user's API key is stored only in app-private storage and is transmitted
only to the provider it authenticates against. Play's form covers data sent to
the developer or third parties on the developer's behalf; this is the user's
own credential going to the user's own account, and there is no other
"credentials" data type to declare. If in doubt, describe it in the Data safety
"additional information" box rather than leaving it unmentioned.

## Related policies to expect

- **Generative AI apps** — the app must let users report offensive AI output.
  Paraphase has a *Report* action on every result (`Report.kt`), which opens the
  user's mail app with the input and output prefilled. Set a real address in
  `report_email` (strings.xml) before publishing.
- **Ads** — none. Declare "No ads".
- **Content rating questionnaire** — a utility with no ads, no purchases, no
  user-to-user communication, and no data sharing beyond the above. Answer that
  the app can display AI-generated text, which is what the questionnaire is
  asking about when it covers user-generated or dynamic content.
