# SMS to Sheets — Project Specification (`SPEC.md`)

**Application ID**: `com.sms2sheets.app`  
**Current Version**: `1.2.0` (`versionCode = 3`)  
**Minimum SDK**: Android 8.0 (API 26)  
**Target / Compile SDK**: Android 15 (API 35)  

---

## 1. Purpose & Core Capabilities

**SMS to Sheets** is a native Android application (Kotlin + Jetpack Compose) paired with a Google Apps Script Web App bridge (`apps-script/Code.gs`) that reads device SMS messages and uploads selected or rule-matched messages to specific Google Sheets and sheet tabs.

### Core Features
1. **Manual Inbox Sync (`InboxScreen`)**
   - Reads up to 500 recent inbox messages from `Telephony.Sms.Inbox.CONTENT_URI`.
   - Groups messages by sender address/number (`SenderSummary`) with per-sender counts, filter chips, and search.
   - Multi-select messages and upload to any configured Google Sheet destination, optional Tab override, and custom **Columns to Upload** selection (`UploadSheetDialog`).
   - Marks already-synced messages with a `Synced` badge.
2. **Automatic & Backfill Routing Rules (`RulesScreen`, `RuleEditorDialog`)**
   - Routes messages from specific phone numbers/sender IDs (comma-separated, or `*` for all) using `CONTAINS`, `EXACT` (with normalized trailing 10-digit phone comparison), or `REGEX` matching, plus optional comma-separated keyword filters.
   - Routes each rule to a chosen `SheetDestinationEntity` and optional `targetTabOverride`.
   - Configurable **Columns to Upload** (`SheetColumn`) per rule (`All`, `Minimal`, or individual column chips).
   - **Auto-Sync** toggle runs automatically in the background on incoming SMS (`SmsReceiver` → `WorkManager` `SmsUploadWorker` with `NetworkType.CONNECTED` constraint and exponential backoff retry up to 3 attempts).
   - **Sync Existing** runs any rule retroactively against existing inbox messages.
3. **Smart Data Extraction (`MessageExtractor`)**
   - **Extracted Amount**: Detects currency prefixes/suffixes (`BDT`, `Tk`, `TAKA`, `USD`, `EUR`, `GBP`, `INR`, `RS`, `SGD`, `AED`, `SAR`, `$`, `€`, `£`, `₹`, `৳`) and labeled amounts (`amount`, `paid`, `received`, `debited`, `credited`, `total`, `balance`).
   - **Extracted Code / OTP**: Extracts 4–8 digit OTP/PIN/verification codes or transaction/reference IDs (`TrxID`, `TxnID`, `Ref`).
   - **Custom Extract**: Runs a user-provided case-insensitive regex; returns capturing group 1 if present, otherwise the full match.
4. **Google Sheet Destinations (`DestinationsScreen`)**
   - Stores one or more Apps Script Web App endpoints with default tab name, optional `spreadsheetId` (for central multi-spreadsheet routing), and optional `secretToken`.
   - Includes **Test Connection** (`GET`) with script version checking (`v1.0.x` warning if outdated) and an in-app **Setup Guide**.
5. **Upload History & Retry (`LogsScreen`)**
   - Tracks recent uploads (`SUCCESS` / `FAILED`) with status badges, extracted summary, error details, and one-tap **Retry Upload** preserving the original `selectedColumns`.
6. **System Light / Dark Theme (`Theme.kt`, `values-night/themes.xml`)**
   - Automatically follows Android system Light/Dark mode (`isSystemInDarkTheme()`).
   - Disables vendor force-dark (`android:forceDarkAllowed="false"`) on API 29+ and uses explicit high-contrast palettes (`LocalStatusColors`) for success/error/info snackbar notifications, switches, chips, and log pills.

---

## 2. Technology Stack

| Layer | Technology / Version |
|---|---|
| Language | Kotlin `2.0.21` (JVM 17) |
| Build System | Gradle `8.9`, Android Gradle Plugin `8.7.3`, KSP `2.0.21-1.0.27` |
| UI | Jetpack Compose (BOM `2024.10.01`), Material 3, Material Icons Extended |
| Persistence | Room `2.6.1` (`sms_to_sheets.db`, schema v2 with `MIGRATION_1_2`) |
| Background Work | AndroidX WorkManager `2.9.1` |
| Networking | OkHttp `4.12.0` (`followRedirects = true`, `followSslRedirects = true`) + `org.json` |
| Backend Bridge | Google Apps Script Web App (`apps-script/Code.gs`, v`1.2.0`) |
| Testing | JUnit `4.13.2` |

---

## 3. Repository Layout

```text
.
├── apps-script/
│   └── Code.gs                       # Google Apps Script Web App bridge (v1.2.0)
├── app/
│   ├── build.gradle.kts              # Android module config, dependencies, release signing
│   ├── proguard-rules.pro            # R8 keep rules for Room, WorkManager, Receiver, enums
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # Permissions, MainActivity, SmsReceiver
│       │   ├── java/com/sms2sheets/app/
│       │   │   ├── SmsToSheetsApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   │   ├── db/           # Entities.kt, Daos.kt, Converters.kt, AppDatabase.kt
│       │   │   │   └── model/        # SmsMessageItem.kt, SheetColumn.kt
│       │   │   ├── domain/           # MessageExtractor.kt
│       │   │   ├── network/          # SheetsUploader.kt
│       │   │   ├── sms/              # SmsReaderRepository.kt, SmsReceiver.kt, SmsUploadWorker.kt, SyncOrchestrator.kt
│       │   │   └── ui/
│       │   │       ├── MainViewModel.kt
│       │   │       ├── theme/        # Theme.kt
│       │   │       ├── components/   # DestinationEditorDialog.kt, RuleEditorDialog.kt, UploadSheetDialog.kt, SetupGuideDialog.kt
│       │   │       └── screens/      # InboxScreen.kt, RulesScreen.kt, DestinationsScreen.kt, LogsScreen.kt
│       │   └── res/                  # values/, values-night/, values-v29/, values-night-v29/, drawable/, mipmap-anydpi-v26/
│       └── test/java/com/sms2sheets/app/
│           ├── data/model/SheetColumnTest.kt
│           └── domain/MessageExtractorTest.kt
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── keystore/                         # Private release keystore (git-ignored)
├── keystore.properties               # Release signing credentials (git-ignored)
├── local.properties                  # Local Android SDK path (git-ignored)
├── README.md
└── SPEC.md
```

---

## 4. Database Schema (`AppDatabase`, Version 2)

- **`sheet_destinations`** (`SheetDestinationEntity`):
  `id`, `name`, `webAppUrl`, `spreadsheetId`, `sheetTabName`, `secretToken`, `createdAt`
- **`routing_rules`** (`RoutingRuleEntity`, FK `destinationId` → `sheet_destinations.id` `ON DELETE CASCADE`):
  `id`, `name`, `senderFilter`, `matchMode` (`CONTAINS | EXACT | REGEX`), `keywordFilter`, `destinationId`, `targetTabOverride`, `extractAmount`, `extractOtp`, `customRegex`, `selectedColumns`, `autoSyncEnabled`, `isEnabled`, `createdAt`
- **`upload_logs`** (`UploadLogEntity`):
  `id`, `smsKey`, `sender`, `body`, `timestampMillis`, `extractedAmount`, `extractedCode`, `customExtract`, `ruleName`, `selectedColumns`, `destinationId`, `destinationName`, `sheetTabName`, `source` (`MANUAL | AUTO | RULE_SYNC | RETRY`), `status` (`PENDING | SUCCESS | FAILED`), `errorMessage`, `uploadedAt`
- **Migration (`MIGRATION_1_2`)**:
  Adds `selectedColumns` to `routing_rules` (defaulting to `SheetColumn.defaultSerialized`) and `upload_logs` (defaulting to `''`).

---

## 5. Available Sheet Columns (`SheetColumn`)

| Enum Name | JSON Key | Sheet Header | Default |
|---|---|---|---|
| `MESSAGE_ID` | `id` | `Message ID` | Yes |
| `TIMESTAMP` | `timestamp` | `Date / Time` | Yes |
| `SENDER` | `sender` | `Sender` | Yes |
| `BODY` | `body` | `Message Body` | Yes |
| `EXTRACTED_AMOUNT` | `extractedAmount` | `Extracted Amount` | Yes |
| `EXTRACTED_CODE` | `extractedCode` | `Extracted Code / OTP` | Yes |
| `CUSTOM_EXTRACT` | `customExtract` | `Custom Extract` | No |
| `RULE_NAME` | `ruleName` | `Rule Name` | Yes |
| `SYNC_MODE` | `source` | `Sync Mode` | Yes |
| `UPLOADED_AT` | `uploadedAt` | `Uploaded At` | Yes |

---

## 6. Google Apps Script API Contract (`apps-script/Code.gs` v1.2.0)

### Health Check (`GET <webAppUrl>`)
Response:
```json
{
  "ok": true,
  "service": "SMS to Sheets Bridge",
  "version": "1.2.0",
  "timestamp": "2026-11-25T10:00:00.000Z"
}
```

### Batch Upload (`POST <webAppUrl>`)
Request JSON:
```json
{
  "secret": "optional-secret-token",
  "spreadsheetId": "optional-spreadsheet-id",
  "sheetName": "TargetTab",
  "columns": ["Date / Time", "Sender", "Extracted Amount"],
  "rows": [
    {
      "timestamp": "2026-11-25 15:37:00",
      "sender": "StanChartBD",
      "extractedAmount": "BDT 88,091.00"
    }
  ]
}
```
Behavior:
- Acquires `LockService.getScriptLock()` (25s timeout).
- Validates `SECRET_TOKEN` if non-empty in `Code.gs`.
- Opens `SpreadsheetApp.openById(spreadsheetId)` if provided, else the bound active spreadsheet.
- Gets or creates `sheetName`.
- If `sheet.getLastRow() <= 1`, writes exact `columns` to Row 1; otherwise preserves existing Row 1 headers and appends any newly requested columns.
- Deduplicates by `Message ID` if the `"Message ID"` column exists.
- Returns `{ ok, version, spreadsheetName, sheetName, columns, inserted, skipped }`.

---

## 7. Security & Permissions

- **Manifest Permissions**: `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_SMS`, `RECEIVE_SMS`.
- **`android:allowBackup="false"`**: Prevents backing up local SMS logs to cloud backups.
- **Deduplication**: Deterministic 24-hex-char SHA-256 prefix (`sender|timestampMillis|body`) checked locally in Room and on Google Sheets when `Message ID` is included.
- **Apps Script Protection**: Keep your deployed `/exec` URL private and configure `SECRET_TOKEN` in both `Code.gs` and the app's destination settings.
