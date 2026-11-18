# PromptLens — Firestore Schema & Admin Panel Contract

This document defines the exact Cloud Firestore database schema, security rules contract, composite indexes, and deep-link conventions shared between the **PromptLens Android User Application** (`com.farhadvarietiesstore.promptlens`) and your separate **Administrator Panel** (`com.farhadvarietiesstore.promptlensadmin`).

---

## 1. Role-Based Access Control (`adminUsers/{uid}`)

Administrator privileges are controlled **exclusively** via the `adminUsers/{uid}` collection. No hardcoded emails or client-side admin flags are used.

### `adminUsers/{uid}`
| Field | Type | Required | Allowed Values / Constraints | Description |
|---|---|---|---|---|
| `role` | `string` | Yes | `'owner' \| 'admin' \| 'moderator'` | Role tier for the administrator. |
| `active` | `boolean` | Yes | `true \| false` | Must be `true` for privileges to apply. |
| `createdAt` | `timestamp` | Yes | `<= request.time` | Creation timestamp. |
| `updatedAt` | `timestamp` | Yes | `<= request.time` | Last update timestamp. |

### Role Permissions Summary
- **`owner`**: Full read/write access to all collections, including creating, updating, and deleting `adminUsers/{uid}` records.
- **`admin`**: Full read/write access to `images`, `imagePrompts`, `categories`, `notifications`, `appSettings`, `advertisementSettings`, `comments`, and `reports`. Cannot modify `adminUsers`.
- **`moderator`**: Can read and update `reports/{reportId}` (`status` field) and moderate `comments/{commentId}` (`status`, `adminReply`, `reported`, `reportReason`, `updatedAt`) or delete inappropriate comments. Cannot create/modify `images`, `imagePrompts`, `categories`, or global settings.

---

## 2. Core Content & Prompt Lock Collections

### `images/{imageId}`
Stores public metadata for photography inspirations.
> **CRITICAL SECURITY RULE (Prompt Lock)**: `images/{imageId}` **MUST NOT** contain `promptText`. Security rules reject any `images` document that includes a `promptText` field. Store the prompt text in `imagePrompts/{imageId}` using the **same `{imageId}`**.

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `title` | `string` | Yes | `1..150` chars | Full editorial title of the photography inspiration. |
| `shortName` | `string` | Yes | `1..80` chars | Short display name / slug for compact badges and search. |
| `description` | `string` | Yes | `0..1000` chars | Lighting, camera, or composition notes. |
| `categoryId` | `string` | Yes | `1..80` chars | ID of the parent category in `categories/{categoryId}`. |
| `categoryName` | `string` | Yes | `1..100` chars | Display name of the category (e.g., `"Women Portrait"`). |
| `imageUrl` | `string` | Yes | `1..1000` chars | Direct external HTTPS image URL (rendered inside the app; never exposed to users). |
| `aspectRatio` | `number` | Yes | `> 0 && <= 5` | Width-to-height ratio (e.g. `0.75` for 3:4 portrait, `1.33` for 4:3). |
| `featured` | `boolean` | Yes | `bool` | Whether shown in the Home "Featured Inspirations" carousel. |
| `trending` | `boolean` | Yes | `bool` | Whether shown in the Home "Trending Now" section. |
| `recommended` | `boolean` | Yes | `bool` | Whether shown in the Home "Recommended For You" grid. |
| `collectionName` | `string` | Yes | `0..100` chars | Optional curated collection title (empty string `""` if none). |
| `keywords` | `string` | Yes | `0..500` chars | Comma-separated search keywords (lowercase recommended). |
| `displayOrder` | `number` | Yes | `>= 0` | Curated ordering priority (lower numbers appear first). |
| `status` | `string` | Yes | `'published' \| 'draft'` | Only `'published'` documents are readable by end users. |
| `viewCount` | `int` | Yes | `>= 0` | Total view counter (clients can only increment by `+1`). |
| `likeCount` | `int` | Yes | `>= 0` | Total like counter (clients can only change by `+1` or `-1`). |
| `commentCount` | `int` | Yes | `>= 0` | Total comment counter (clients can only change by `+1` or `-1`). |
| `unlockCount` | `int` | Yes | `>= 0` | Total prompt unlock counter (clients can only increment by `+1`). |
| `shareCount` | `int` | Yes | `>= 0` | Total share counter (clients can only increment by `+1`). |
| `createdAt` | `timestamp` | Yes | `<= request.time` | Creation timestamp. |
| `updatedAt` | `timestamp` | Yes | `<= request.time` | Last modification timestamp. |

---

### `imagePrompts/{imageId}`
Stores the locked AI generation prompt corresponding to `images/{imageId}`.
- **Read Rule**: Readable (`get`) **only** when `promptUnlocks/{uid}_{imageId}` exists for the signed-in user, or when the user is an `admin` / `owner`. Listing (`list`) is forbidden (`allow list: if false`).
- **Write Rule**: Only `admin` or `owner` can create, update, or delete.

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `promptText` | `string` | Yes | `1..5000` chars | Complete AI generation prompt unlocked after watching a rewarded ad. |
| `updatedAt` | `timestamp` | Yes | `<= request.time` | Last update timestamp. |

---

### `promptUnlocks/{uid}_{imageId}`
Stores verified rewarded-ad unlock receipts. Document ID **must** be `${userId}_${imageId}`.
- **Read Rule**: Signed-in user can read their own unlock documents (`userId == request.auth.uid`).
- **Create Rule**: Signed-in user can create only their own document (`${uid}_${imageId}`) with `rewardVerified == true`. Immutable once created (`allow update, delete: if false`).
- **Blaze Plan SSV Upgrade Note**: For cryptographic protection against forged client unlock writes, enable AdMob Server-Side Verification (SSV) with a Firebase Cloud Function (Blaze plan) that writes `promptUnlocks/{uid}_{imageId}` using the Admin SDK.

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `userId` | `string` | Yes | `== request.auth.uid` | UID of the user who completed the rewarded ad. |
| `imageId` | `string` | Yes | Valid doc ID | Unlocked image ID. |
| `imageTitle` | `string` | Yes | `1..150` chars | Snapshot of the image title for history display. |
| `rewardVerified` | `boolean` | Yes | `== true` | Must be `true`. |
| `adNetwork` | `string` | Yes | `1..50` chars | Ad network identifier (e.g., `"AdMob"`). |
| `createdAt` | `timestamp` | Yes | `<= request.time` | Unlock timestamp. |

---

### `categories/{categoryId}`
Stores photography categories. Only `admin` or `owner` may write.

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `name` | `string` | Yes | `1..100` chars | Category display name (e.g., `"Cinematic Photography"`). |
| `description` | `string` | Yes | `0..500` chars | Subtitle / description of the genre. |
| `coverImageUrl` | `string` | Yes | `1..1000` chars | Direct HTTPS thumbnail image URL. |
| `imageCount` | `int` | Yes | `>= 0` | Number of published inspirations in this category. |
| `sortOrder` | `int` | Yes | `>= 0` | Display order in category carousels. |
| `status` | `string` | Yes | `'published' \| 'draft'` | Only `'published'` categories are shown to users. |
| `createdAt` | `timestamp` | Yes | `<= request.time` | Creation timestamp. |
| `updatedAt` | `timestamp` | Yes | `<= request.time` | Last update timestamp. |

---

## 3. Guest (Anonymous) vs. Real User Authentication & Community Collections

- **Automatic Anonymous Guest Session (`isSignedIn()`)**:
  - At app startup, if `FirebaseAuth.currentUser` is `null`, the app automatically calls `signInAnonymously()`.
  - Guests (anonymous UIDs) can browse published content, increment `viewCount` / `unlockCount` / `shareCount` (`+1`), save `favorites/{uid}_{imageId}`, create `promptUnlocks/{uid}_{imageId}` after watching a rewarded ad, and read unlocked `imagePrompts/{imageId}`.
  - When a guest signs in with Google, `linkWithCredential` upgrades the anonymous UID in-place so their saved favorites and unlocked prompts are preserved.
- **Real Authenticated Account (`isRealUser()`: `sign_in_provider != 'anonymous'`)**:
  - Required for `likes`, `comments`, and `reports`.

### `likes/{uid}_{imageId}`
- **Access**: Requires a real, non-anonymous account (`request.auth.token.firebase.sign_in_provider != 'anonymous'`).
- **Fields**: `userId` (`string`), `imageId` (`string`), `createdAt` (`timestamp`).

### `favorites/{uid}_{imageId}`
- **Access**: Any signed-in user (including anonymous guests) can create/delete their own favorite documents (`${uid}_${imageId}`).
- **Fields**: `userId` (`string`), `imageId` (`string`), `imageTitle` (`string`), `imageUrl` (`string`), `categoryName` (`string`), `aspectRatio` (`number`), `createdAt` (`timestamp`).

### `comments/{commentId}`
- **Access**: Anyone can read `'visible'` comments. Creating a comment requires a real (non-anonymous) account. Users can delete their own comments; `moderator`, `admin`, and `owner` can update `status` (`'visible' | 'hidden'`), `adminReply`, `reported`, `reportReason`, or delete comments.
- **Fields**: `imageId`, `userId`, `userName`, `text` (`1..500` chars), `reported` (`bool`), `reportReason` (`string`), `adminReply` (`string`), `status` (`'visible' | 'hidden'`), `createdAt`, `updatedAt`.

### `reports/{reportId}`
- **Access**: Real (non-anonymous) users can create a report with `reporterId == request.auth.uid` and `status == 'open'`. Only `moderator`, `admin`, and `owner` can read (`get`, `list`) or update `status` (`'open' | 'reviewed' | 'dismissed'`). Only `admin` or `owner` can delete reports.
- **Fields**: `commentId` (`string`), `imageId` (`string`), `reporterId` (`string`), `reason` (`1..300` chars), `status` (`'open' | 'reviewed' | 'dismissed'`), `createdAt` (`timestamp`).

### `users/{userId}`
- **Fields**: `userId`, `displayName`, `email`, `photoUrl`, `likedCount`, `favoriteCount`, `unlockedCount`, `createdAt`, `updatedAt`.

---

## 4. Notifications & Global Settings

### `notifications/{notificationId}`
- **Fields**: `title` (`1..120` chars), `body` (`1..500` chars), `imageId` (`0..128` chars), `imageUrl` (`0..1000` chars), `categoryName` (`0..100` chars), `status` (`'published' | 'draft'`), `createdAt` (`timestamp`).

### `appSettings/global`
- **Fields**: `playStoreUrl` (`string`), `requireLoginForPrompts` (`bool`), `allowComments` (`bool`), `welcomeHeadline` (`string`), `welcomeSubheadline` (`string`), `supportEmail` (`string`), `updatedAt` (`timestamp`).

### `advertisementSettings/global`
- **Fields**: `allAdsEnabled` (`bool`), `homeBannerEnabled` (`bool`), `detailBannerEnabled` (`bool`), `rewardedAdsEnabled` (`bool`), `interstitialAdsEnabled` (`bool`), `testAdMode` (`bool`), `reusePreviousUnlocks` (`bool`), `interstitialFrequency` (`int`, `1..50`), `adFallbackMessage` (`string`), `bannerAdUnitId` (`string`), `rewardedAdUnitId` (`string`), `interstitialAdUnitId` (`string`), `updatedAt` (`timestamp`).

---

## 5. Deep Link & Push Notification Format

To open a specific image detail screen directly inside the app from a push notification or deep link:
- **Custom URI Scheme**: `promptlens://image/{imageId}`
- **OneSignal Additional Data Payload**: `{ "imageId": "{imageId}" }` or `{ "image_id": "{imageId}" }`

---

## 6. Required Composite Indexes (`firestore.indexes.json`)

Deploy `/firestore.indexes.json` using `firebase deploy --only firestore:indexes`:
1. `images`: `status ASC, displayOrder ASC, createdAt DESC`
2. `images`: `status ASC, featured ASC, displayOrder ASC, createdAt DESC`
3. `images`: `status ASC, trending ASC, viewCount DESC`
4. `images`: `status ASC, recommended ASC, displayOrder ASC, createdAt DESC`
5. `images`: `status ASC, categoryId ASC, displayOrder ASC, createdAt DESC`
6. `images`: `status ASC, categoryName ASC, displayOrder ASC, createdAt DESC`
7. `comments`: `imageId ASC, status ASC, createdAt DESC`
