# Google Play SMS Permission Declaration

Last checked: 2026-04-24

## Why MoneyTalk requests SMS permissions

MoneyTalk requests:

- `android.permission.READ_SMS`
- `android.permission.RECEIVE_SMS`

The app uses these permissions for SMS-based money management: it reads financial SMS messages from card companies, banks, and payment services to extract transaction amount, merchant/source, payment/deposit time, card/account context, and then records those transactions in the user's household ledger.

This matches the Google Play "Use of SMS or Call Log permission groups" exception category:

- Use: SMS-based money management
- Example: apps that track and manage budget
- Eligible permissions: `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_SMS`, `RECEIVE_WAP_PUSH`

Official policy references:

- https://support.google.com/googleplay/android-developer/answer/10208820?hl=en
- https://support.google.com/googleplay/android-developer/answer/9888076

## Core functionality statement for Play Console

MoneyTalk's core feature is automatic household ledger creation from financial SMS messages. Without access to financial SMS messages, users must manually enter every card payment, bank deposit, and transfer, which removes the app's primary automated tracking experience.

The app does not provide a general-purpose SMS inbox, SMS translation, contact profiling, social graph analysis, marketing research, or SMS notification enhancement. SMS data is used only to create and manage the user's own financial records.

## Data handling summary

Collected from SMS:

- Message body
- Sender phone number
- Received time
- Parsed financial fields such as amount, merchant/source, card name, transaction type, and memo/original-message reference

Used for:

- Automatic expense, income, and transfer recording
- Duplicate prevention
- Category classification
- User-visible transaction history and financial summaries

Storage and sharing:

- Transaction data is stored locally in the app database.
- Google Drive backup is user-initiated and stored in the user's own Drive account.
- AI features may send store names, amounts, transaction context, or masked message snippets to Google Gemini API for classification and advice.
- Direct identifiers such as phone numbers should be removed or minimized where possible before AI processing.
- SMS data is not sold and is not used for advertising.

## In-app disclosure checklist

Before requesting SMS runtime permissions, the app must clearly disclose:

- SMS content, sender number, and received time are accessed.
- The data is used to automatically create household ledger records.
- Some AI features may process minimized or masked transaction context through Google Gemini API.
- Users can deny the permission and still use limited manual features.

Current disclosure surfaces:

- Intro permission screen: `permission_sms_description`, `permission_note`
- In-app privacy policy dialog: `privacy_collect_detail`, `privacy_usage_detail`, `privacy_storage_detail`, `privacy_thirdparty_detail`
- Privacy policy HTML: `docs/privacy-policy.html`

## Play Console declaration notes

When submitting the release, complete the Permissions Declaration Form for SMS permissions and choose the SMS-based money management use case. The store listing should prominently describe automatic budget/ledger tracking from financial SMS messages as a core feature.

Suggested review note:

> MoneyTalk is a household ledger app whose core feature is SMS-based money management. With user consent, it reads financial SMS messages from card companies, banks, and payment services to automatically create expense, income, and transfer records. SMS data is used only for the user's financial records, duplicate prevention, and category classification. Users can deny SMS permission and manually add transactions, but the primary automated tracking feature requires SMS access. The app does not sell SMS data or use it for advertising.

## Release gate

Do not upload a production release with SMS permissions unless:

- The Permissions Declaration Form is completed.
- The store listing clearly describes SMS-based money management as a core feature.
- The in-app disclosure and privacy policy match the actual data flow.
- The Data safety section discloses SMS data access, local storage, AI processing where applicable, Google Drive backup, Firebase/AdMob usage, and user deletion controls.
