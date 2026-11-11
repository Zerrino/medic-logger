# Tips with Google Play Billing (Android)

The Settings → Support card sells optional tips as **consumable in-app products**. They unlock nothing and send no journal data.

Create three products in Play Console → Monetize → Products → **In-app products**:

| Product ID | Name | Suggested price |
|---|---|---|
| `fr.mediclogger.app.tip.small` | Small coffee | 0.99 € |
| `fr.mediclogger.app.tip.medium` | Large coffee | 2.99 € |
| `fr.mediclogger.app.tip.large` | Coffee and croissant | 4.99 € |

- Activate each product. On `tip.small`, enable **multi-quantity purchase** if you want a custom amount in Google's payment sheet.
- A payments profile is required, and a build containing the Billing library must already be uploaded (it is, from 1.4.4).
- Test with your account under Settings → License testing; test purchases are not charged. Products only load for the exact package `fr.mediclogger.app`, so the QA build (`.qa`) shows "Tips are unavailable".
- The Billing library adds the `com.android.vending.BILLING`, `INTERNET` and `ACCESS_NETWORK_STATE` permissions. Update the Data safety form accordingly (purchase history is handled by Google Play; the journal is never sent).
- The app consumes each purchase right after payment, and any payment confirmed later is consumed on the next start.
