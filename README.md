# SMS to Sheets (Android App)

An Android app (Kotlin + Jetpack Compose, `v1.2.0`) that reads SMS messages and uploads selected messages—or automatically routes incoming messages from specific phone numbers/sender IDs—into specific Google Sheets tabs with customizable column selection.

> See [SPEC.md](SPEC.md) for the full technical specification, database schema, and Apps Script API contract.

## Features
- **Manual Inbox Sync**: Browse SMS grouped by sender number, search, multi-select messages, choose target columns, and upload to any connected Google Sheet & Tab.
- **Automatic Background Routing**: Create rules per sender number/ID (e.g., `bKash`, `StanChartBD`, `+88017...`) with optional keyword filters so matching incoming SMS auto-upload via `WorkManager` (with offline retry).
- **Per-Rule Column Selection**: Choose exactly which columns (`Message ID`, `Date / Time`, `Sender`, `Message Body`, `Extracted Amount`, `Extracted Code / OTP`, `Custom Extract`, `Rule Name`, `Sync Mode`, `Uploaded At`) are uploaded per rule or manual sync.
- **Smart Field Extraction**: Automatically extracts monetary amounts (`BDT`, `Tk`, `$`, `USD`, `INR`, etc.), OTPs / Transaction IDs (`TrxID`), and custom regex captures (e.g., `A/C Bal\s*(?:BDT|Tk\.?)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)`).
- **System Light / Dark Theme**: Automatically matches your phone's system Light or Dark theme with high-contrast notifications, toggles, and status badges.

---

## Step 1: Set Up Google Apps Script (`Code.gs` v1.2.0)

1. Open your Google Sheet in a browser and click **Extensions → Apps Script**.
2. Replace `Code.gs` with the full contents of [`apps-script/Code.gs`](apps-script/Code.gs).
3. *(Recommended)* Set `SECRET_TOKEN` on line 16 of `Code.gs` (for example `const SECRET_TOKEN = "your-long-secret";`).
4. Deploy as a Web App:
   - First-time deploy: **Deploy → New deployment → Type: Web app**.
   - Updating an existing script: **Deploy → Manage deployments → Edit (pencil) → Version: New version → Deploy**.
   - **Execute as**: `Me`
   - **Who has access**: **`Anyone`** *(must be `Anyone`, not `Only myself` or `Anyone with Google account`, otherwise the app receives HTTP `401`/`403`)*.
5. Copy the Web App URL ending in **`/exec`** *(do not use a `/dev` test URL)*.

---

## Step 2: Build & Run Instructions

### Prerequisites
- **JDK 17+** (e.g. `C:\Program Files\Android\openjdk\jdk-21.0.8`)
- **Android SDK** (Platform `android-35`, Build-Tools `35.0.0`, Platform-Tools) configured in `local.properties` or `ANDROID_HOME`

### Option A: Command Line (PowerShell)

1. Open PowerShell in the project folder:
   ```powershell
   cd "D:\Joy\New folder"
   $env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   ```
2. Run unit tests:
   ```powershell
   .\gradlew.bat testReleaseUnitTest --no-daemon
   ```
3. Build the signed Release APK (`app\build\outputs\apk\release\app-release.apk`):
   ```powershell
   .\gradlew.bat assembleRelease --no-daemon
   ```
   *(Or build a Debug APK via `.\gradlew.bat assembleDebug --no-daemon`.)*
4. Install directly onto a USB-connected Android phone (with **USB Debugging** enabled):
   ```powershell
   & "$env:ANDROID_HOME\platform-tools\adb.exe" install -r "app\build\outputs\apk\release\app-release.apk"
   ```

> **Release Signing Note**: Release builds read `keystore.properties` and `keystore/sms2sheets-release.jks`. Keep both files backed up safely if you plan to ship future updates without uninstalling the app. If upgrading from an older debug APK (`app-debug.apk`), uninstall the debug app first before installing `app-release.apk`.

### Option B: Android Studio
1. Open `D:\Joy\New folder` in **Android Studio**.
2. Wait for Gradle sync to finish.
3. Connect an Android device (API 26+) or start an emulator and click **Run ▶**.

---

## Step 3: Using the App on Your Phone

1. Grant **Read SMS** and **Receive SMS** permissions when prompted.
   - *Android 13+ sideload note*: If SMS permission is blocked as a *Restricted setting* after installing an APK directly from file manager, open **Settings → Apps → SMS to Sheets → ⋮ → Allow restricted settings**, then grant SMS permissions.
   - *Play Protect note*: Unpublished sideloaded APKs with SMS permissions may prompt once with **Scan app** or **More details → Install anyway**.
2. Open the **Sheets** tab → tap **+** → paste your `/exec` Web App URL (and matching Secret Token if set) → tap **Test Connection** (confirm it shows `v1.2.0`).
3. **Manual Sync**: Open **Inbox**, filter by sender chip or search, select messages, tap **Upload to Sheet**, choose your columns and tab, and tap **Upload Now**.
4. **Auto Rules**: Open **Auto Rules** → tap **+** (or tap **Auto-Rule** in Inbox) → pick sender number(s), target Sheet/Tab, **Columns to Upload**, optional **Custom Extract Regex**, and toggle **Auto-upload incoming SMS**.
