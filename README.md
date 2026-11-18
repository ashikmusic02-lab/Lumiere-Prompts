# PromptLens — Configuration & Deployment Guide (`CONFIG_GUIDE`)

This project is the **PromptLens Android User Application** (`com.farhadvarietiesstore.promptlens`). All administrator operations are handled by a separate Admin Panel (`com.farhadvarietiesstore.promptlensadmin`) connected to the same Firebase backend (`aajekbo`).

---

## 1. Android Application Identifiers & Signing Fingerprints

- **App Name**: `PromptLens`
- **Package Name (`applicationId`)**: `com.farhadvarietiesstore.promptlens`
- **Firebase Project ID**: `aajekbo` (`app/google-services.json` is pre-configured)
- **Web Client ID (`default_web_client_id`)**: `737068256742-8pfphc0v7v9irpd305g0kqfq1gto3sp7.apps.googleusercontent.com` (generated automatically from `app/google-services.json`)
- **Debug SHA-1 Fingerprint**: `5F:84:64:95:08:74:CE:64:2F:4B:EB:FC:C3:0F:07:80:C6:E1:3E:D4`
- **Debug SHA-256 Fingerprint**: `97:FE:B8:1C:52:F7:2F:04:13:F3:AB:EE:DE:B0:43:1C:B7:E5:45:6C:E5:F6:02:65:EA:C0:37:41:72:14:0F:CE`

> **Tip**: You can also copy these values directly from the **Profile** tab inside the app.

---

## 2. What You Must Configure By Hand

### Step A — Enable Firebase Authentication Providers & Register SHA Fingerprints
1. In the **Firebase Console** (`aajekbo`), open **Build -> Authentication -> Sign-in method** and enable **both**:
   - [ ] **Anonymous** *(Required for automatic guest browsing, view counting, favorites, and rewarded-ad prompt unlocks without forced login)*
   - [ ] **Google** *(Required for account linking via `linkWithCredential`, likes, comments, and comment reports)*
2. In **Project Settings -> Your Apps -> `com.farhadvarietiesstore.promptlens`**, add your **SHA-1** and **SHA-256** certificate fingerprints (both the debug fingerprints above for testing and your Play App Signing SHA-1 / SHA-256 fingerprints for production) so Google Sign-In works on all builds.

### Step B — Deploy Firestore Database, Security Rules & Composite Indexes
1. Ensure Cloud Firestore (`(default)` database) is created in the Firebase Console (`Build -> Firestore Database`).
2. Deploy `/firestore.rules` and `/firestore.indexes.json` from the project root:
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes --project aajekbo
   ```
3. Bootstrap your first owner document in `adminUsers/{your_google_uid}` via the Firebase Console with:
   - `role`: `"owner"`
   - `active`: `true`
   - `createdAt`: *(Server timestamp)*
   - `updatedAt`: *(Server timestamp)*
   See [`SCHEMA.md`](./SCHEMA.md) for the complete collection contract.

### Step C — Configure OneSignal Push Notifications (When Ready)
`ONESIGNAL_APP_ID` is currently set to `YOUR_ONESIGNAL_APP_ID` in `.env.example` (the app safely skips OneSignal initialization when this placeholder is present so it never crashes).
1. When ready, set your real OneSignal App ID in the **Secrets panel in AI Studio** (`.env`):
   ```ini
   ONESIGNAL_APP_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
   ```
2. Deep links use the custom scheme `promptlens://image/{imageId}` or OneSignal additional data payload `{"imageId": "{imageId}"}` / `{"image_id": "{imageId}"}`.

### Step D — Optional Firebase App Check & AdMob SSV
1. **Firebase App Check**:
   - **Debug Builds**: Set `FIREBASE_APPCHECK_DEBUG_TOKEN` in `.env` and register that token under **Firebase Console -> App Check -> Apps -> Manage debug tokens**.
   - **Release Builds**: Register your production signing key's **SHA-256 fingerprint** under **App Check -> Play Integrity**.
2. **Google AdMob** (Pre-configured with your real IDs):
   - App ID (`strings.xml`): `ca-app-pub-1922649184647561~5841219615`
   - Banner Ad Unit ID (`.env.example`): `ca-app-pub-1922649184647561/5001287373`
   - Rewarded Ad Unit ID (`.env.example`): `ca-app-pub-1922649184647561/9917417183`
   - Interstitial Ad Unit ID (`.env.example`): `ca-app-pub-1922649184647561/5668335930`
   - `AdvertisementSettings.testAdMode` defaults to `false` so production ad units are used unless overridden in Firestore `advertisementSettings/global`.
