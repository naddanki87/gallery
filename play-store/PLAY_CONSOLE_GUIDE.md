# PixelPocket — Google Play Console upload guide

Everything below is ready to copy and paste into Play Console.

| Item | Value / file |
|---|---|
| App name | **PixelPocket** |
| Package name | **`com.pixelpocket.gallery`** |
| App bundle to upload | `PixelPocket-release.aab` (signed with your upload key) |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | Free |
| Category | Photography |
| Min Android version | 8.0 (API 26) · Target API 36 |

---

## 1. Create the app

Play Console → **Create app**
- App name: `PixelPocket`
- Default language: `English (United States)`
- App or game: `App`
- Free or paid: `Free`
- Tick both declarations → **Create app**

## 2. Upload the bundle

**Test and release → Production** (or start with **Internal testing**, recommended) → **Create new release**
- **App signing:** choose **"Use Google-generated key"** (Play App Signing). Your
  `pixelpocket-upload.jks` becomes the *upload key*.
- Upload `PixelPocket-release.aab`. The package name `com.pixelpocket.gallery` is read
  from the bundle automatically.
- Release name: `1.0`
- Release notes:
  ```
  <en-US>
  First release of PixelPocket:
  • Browse all your photos and videos, newest first
  • Sort by date, name or size; filter photos / videos
  • Crop with aspect ratios, rotate and flip; save in JPEG, PNG or WEBP
  • Select one, many or all items to move, delete or share
  </en-US>
  ```

> Every future upload needs a higher version code. The CI build sets it to the
> GitHub Actions run number, so each new build is automatically higher.

## 3. Store listing (Grow → Store presence → Main store listing)

**App name** (max 30): `PixelPocket`

**Short description** (max 80):
```
Your photo & video gallery: sort, crop, move, delete and share your memories.
```

**Full description** (max 4000):
```
PixelPocket is a fast, simple and private gallery for all your photos and videos.

📅 YOUR MEMORIES, IN ORDER
• Everything on your phone in one place, grouped by day
• Newest first by default — or sort by oldest, name (A–Z / Z–A) or size
• Quick filters for All, Photos and Videos

✂️ CROP & EDIT
• Free crop or fixed ratios: Original, 1:1, 4:3, 3:4, 16:9, 9:16
• Rotate and flip
• Save as JPEG, PNG or WEBP, choose the quality and the album
• Remember your favourite save settings
• Your original photo is never changed — edits are saved as a new copy

🗂️ ORGANISE IN SECONDS
• Long-press to select, tap to add more, or Select all / Select a whole day
• Move photos and videos to any album, or create a new one
• Delete one or many items at once

📤 SHARE ANYWHERE
• Share one or many photos and videos to WhatsApp, Gmail, Instagram, Drive or any app

🔍 BEAUTIFUL VIEWER
• Swipe through your memories
• Pinch or double-tap to zoom
• Play videos and see details like date, size and resolution

🔒 PRIVATE BY DESIGN
• No internet permission, no ads, no tracking
• Your photos and profile never leave your phone
```

**Graphics**
| Asset | File | Requirement |
|---|---|---|
| App icon | `graphics/app-icon-512.png` | 512×512 PNG |
| Feature graphic | `graphics/feature-graphic-1024x500.png` | 1024×500 PNG/JPG |
| Phone screenshots | `screenshots/*.png` | 2–8 images, 1080×1920 |

Play allows up to 8 phone screenshots. Suggested, in order: `02_gallery`, `03_sort_options`,
`04_multi_select`, `05_move_to_album`, `07_viewer`, `08_crop`, `09_save_preferences`, `10_settings`.
(`01_registration` and `06_select_all` are spares.) All were captured from the real app on an
Android 14 emulator.
Tablet screenshots are optional; you can skip them.

**Contact details:** your support email (required) — website optional.

## 4. App content (Policy → App content)

### Privacy policy
URL:
```
https://github.com/naddanki87/gallery/blob/claude/stoic-gates-whjy7t/play-store/PRIVACY_POLICY.md
```
⚠️ First edit that file on GitHub and replace `YOUR_SUPPORT_EMAIL@example.com` with your
support email. If you merge the branch into `main`, update the URL to use `main`.

### Ads
`No, my app does not contain ads`

### App access
`All functionality is available without special access`
(Registration only stores a name/email on the device; there is no login or server account.)

### Content rating
Start questionnaire → Email for IARC → Category: **All other app types**
- Violence, sexuality, language, controlled substances, gambling: **No** to all
- Does the app allow users to interact or exchange content? **No** (sharing goes through other apps)
- Does the app share the user's location? **No**
- Does the app allow purchases of digital goods? **No**
→ Expected rating: **Everyone / PEGI 3**

### Target audience and content
- Target age groups: **18 and over** (choose 13+ age groups too if you like; avoid under-13
  to stay out of the Families policy)
- Appeal to children: **No**

### Data safety
- Does your app collect or share any of the required user data types? **No**
  - Reason: Data that stays only on the device is not "collected" under Play's definition. The
    app has no INTERNET permission, so name/email/phone and photos never leave the device.
- Is all user data encrypted in transit? *(not shown once you answer No)*
- Account creation: **No account creation** (the on-device profile is not an account)

### Photo and video permissions declaration
Play asks this because the app uses `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`.
- Core functionality: **Gallery / photo management**
- Description to paste:
  ```
  PixelPocket is a gallery app. Its core purpose is to display all photos and videos stored
  on the device, sorted by date, and to let the user crop, move between albums, delete and
  share them. This requires broad, persistent access to the user's photo and video library;
  the one-time system photo picker cannot provide a browsable, organisable gallery.
  ```

### Government apps / Financial features / Health / News
Answer **No / Not applicable** to each.

## 5. Send for review

Publishing overview → **Send changes for review**. New developer accounts may first need
to run a **closed test with at least 12 testers for 14 days** before production access
is granted — Play Console will tell you if this applies to your account.

---

## Signing future updates yourself

```bash
./gradlew bundleRelease -PunsignedRelease=true -PversionCode=<higher number>
jarsigner -keystore pixelpocket-upload.jks -signedjar PixelPocket-release.aab \
  app/build/outputs/bundle/release/app-release.aab upload
```
(or download `PixelPocket-unsigned.aab` from the latest GitHub release and run the
`jarsigner` line on it). Password: see `UPLOAD_KEY_PASSWORD.txt`.
