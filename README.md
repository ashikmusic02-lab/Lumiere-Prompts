# Lumiere Prompts — Configuration & Deployment Guide (`CONFIG_GUIDE`)

This project is the **Lumiere Prompts Android User Application** (`com.aistudio.lumiereprompts.vxkrmq`). All administrator operations are handled by a separate Admin Panel connected to the same Firebase backend.

---

## 1. Android Application Identifiers & Signing Fingerprints

Register these exact values in your **Firebase Console** (`Project Settings -> Your Apps -> Add Android App`):

- **Package Name (`applicationId`)**: `com.aistudio.lumiereprompts.vxkrmq`
- **Debug SHA-1 Fingerprint**: `5F:84:64:95:08:74:CE:64:2F:4B:EB:FC:C3:0F:07:80:C6:E1:3E:D4`
- **Debug SHA-256 Fingerprint**: `97:FE:B8:1C:52:F7:2F:04:13:F3:AB:EE:DE:B0:43:1C:B7:E5:45:6C:E5:F6:02:65:EA:C0:37:41:72:14:0F:CE`

> **Tip**: You can also copy these values directly from the **Profile** tab inside the app.

---

## 2. Step-by-Step Manual Configuration Checklist

### Step A — Enable Authentication Providers & Upload `google-services.json`
1. In the **Firebase Console**, open **Build -> Authentication -> Sign-in method** and enable **both**:
   - [x] **Anonymous** *(Required for automatic guest browsing, view counting, favorites, and rewarded-ad prompt unlocks without forced login)*
   - [x] **Google** *(Required for account linking via `linkWithCredential`, likes, comments, and comment reports)*
2. In **Build -> Firestore Database**, create your Cloud Firestore database.
3. Download `google-services.json` from **Project Settings -> Your Apps** and place it at:
   ```
   app/google-services.json
   ```

### Step B — Set Your Google Web Client ID (`default_web_client_id`)
1. Open your `google-services.json` and locate the `oauth_client` entry with `"client_type": 3` (Web client).
2. Copy its `client_id` value (ending in `.apps.googleusercontent.com`) into `app/src/main/res/values/strings.xml`:
   ```xml
   <string name="default_web_client_id">YOUR_WEB_CLIENT_ID_FROM_GOOGLE_SERVICES_JSON</string>
   ```
   *(If you build with the Google Services plugin and `app/google-services.json` present, the plugin also generates this resource automatically.)*

### Step C — Deploy Firestore Security Rules & Composite Indexes
Deploy `/firestore.rules` and `/firestore.indexes.json` from the project root:
```bash
firebase deploy --only firestore:rules,firestore:indexes
```
See [`SCHEMA.md`](./SCHEMA.md) for the complete collection contract and how to bootstrap your first owner document in `adminUsers/{uid}` via the Firebase Console.

### Step D — Configure Firebase App Check
The app automatically installs `DebugAppCheckProviderFactory` in debug builds and `PlayIntegrityAppCheckProviderFactory` in release builds (`AppCheckConfigurator.kt`).
- **Debug Builds**: Set `FIREBASE_APPCHECK_DEBUG_TOKEN` in the **Secrets panel in AI Studio** (`.env`) and register that UUID token under **Firebase Console -> App Check -> Apps -> Manage debug tokens**.
- **Release Builds**: Register your production signing key's **SHA-256 fingerprint** in Firebase Console under **App Check -> Play Integrity**.

### Step E — Configure Google AdMob IDs
1. **AdMob App ID**: Replace the official Google test App ID in `app/src/main/AndroidManifest.xml` (`com.google.android.gms.ads.APPLICATION_ID`) with your production AdMob App ID (`ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`) before publishing to Google Play.
2. **Ad Unit IDs**: Set your Banner, Rewarded, and Interstitial Ad Unit IDs in the **Secrets panel in AI Studio** (`.env`, see `.env.example`) AND/OR update them dynamically at any time in Firestore `advertisementSettings/global` (`bannerAdUnitId`, `rewardedAdUnitId`, `interstitialAdUnitId`, and set `testAdMode: false`).
3. **Optional AdMob Server-Side Verification (SSV — Blaze Plan)**:
   - `AdMobManager.kt` attaches `ServerSideVerificationOptions` (`userId` and `customData = imageId`) to every Rewarded Ad.
   - When you upgrade your Firebase project to the **Blaze plan**, deploy a Cloud Function SSV webhook that verifies Google's signature and writes `promptUnlocks/{uid}_{imageId}` via the Admin SDK, then plug your custom `RewardUnlockVerifier` into `LumiereRepository`.

### Step F — Configure OneSignal Push Notifications & Deep Links
1. Add your OneSignal App ID in the **Secrets panel in AI Studio** (`.env`):
   ```ini
   ONESIGNAL_APP_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
   ```
2. Add your official Play Store URL in `.env` (or in Firestore `appSettings/global.playStoreUrl`):
   ```ini
   OFFICIAL_PLAY_STORE_URL=https://play.google.com/store/apps/details?id=com.aistudio.lumiereprompts.vxkrmq
   ```

### Step G — Configure Android App Links (`assetlinks.json`)
To allow `https://lumiereprompts.app/image/{imageId}` links to open directly inside the app without a browser disambiguation dialog, host `https://lumiereprompts.app/.well-known/assetlinks.json` with your SHA-256 fingerprint:
```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.aistudio.lumiereprompts.vxkrmq",
      "sha256_cert_fingerprints": [
        "97:FE:B8:1C:52:F7:2F:04:13:F3:AB:EE:DE:B0:43:1C:B7:E5:45:6C:E5:F6:02:65:EA:C0:37:41:72:14:0F:CE"
      ]
    }
  }
]
```
*(Custom scheme deep links `lumiere://image/{imageId}` and OneSignal notification data payloads `{"imageId": "..."}` work out-of-the-box without hosting a domain.)*
