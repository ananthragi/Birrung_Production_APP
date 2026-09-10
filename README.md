# Birrung TV — Digital Signage Player (Android TV)

An offline‑first digital‑signage player for Android TV. Screens download their assigned
content (video / image / PDF) to local storage and play it on a schedule, continuing to work
even without an internet connection. The internet is used only to receive new content and
schedule updates.

---

## 1. Core Principle

> **Download once → Store locally → Play locally → Internet only for updates.**

Playback priority the player follows:

1. Valid scheduled content available locally → **play it**
2. New content downloading → **keep playing existing valid content**
3. Internet unavailable → **continue using local content**
4. No local content **and** no internet → **show the "I'M OFFLINE" screen**
5. New content fully downloaded & validated → **atomically switch** to it

---

## 2. Tech Stack

| Concern | Technology |
| --- | --- |
| Language / Build | Java 8, Gradle (AGP 8.5), `minSdk 26`, `targetSdk 35` |
| Video playback | ExoPlayer `2.19.1` (TextureView surface) |
| Image / GIF rendering | Glide `5.0.5` |
| PDF rendering | Android `PdfRenderer` (first page → bitmap) |
| Downloads | OkHttp `4.7.2` (custom resumable downloader) |
| Realtime updates | Eclipse Paho MQTT `1.2.5` |
| REST calls | Volley (single shared `RequestQueue`) |
| Crash reporting | Firebase Crashlytics |
| Release build | R8 (code + resource shrinking), signed APK |

---

## 3. Project Structure & Code Locations

All packages are under `app/src/main/java/com/cilpl/Cynergytx_admin/`.

```
Activites/
  SplashActivity.java          → splash screen, routes to Login or Player
  LoginActivity.java           → licence login, routes to the Player
  SignagePlayerActivity.java   → MAIN player screen + orchestrator
  MqttHelper.java              → MQTT connect / subscribe (realtime updates)
  MySingleton.java             → shared Volley RequestQueue (one per app)

signage/
  model/
    ContentSchedule.java       → parses the schedule, picks the active session
    Playlist.java              → one session (start/end time) + its files
    MediaFile.java             → one media item; type detection (video/image/pdf)
  player/
    PlaylistController.java     → the playback engine (video/image/pdf, switching, fallback)
  storage/
    LocalStorageManager.java   → local content folder, 8 GB budget, LRU eviction
    MediaDownloadManager.java  → resumable download (.part → atomic rename)
    MediaValidator.java        → verifies a downloaded file is complete & playable
  sync/
    ContentSyncManager.java    → download-then-play orchestration (background thread)
    SyncReport.java            → per-sync summary (downloaded / failed / shortfall)
  net/
    NetworkSpeedMonitor.java   → continuous internet speed probe (low-bandwidth indicator)
  StorageStatusReporter.java   → reports storage/status back to the backend

Services/ApiDataSync.java      → REST calls (login / schedule refresh / status)
NetworkCalling/ServerURL.java  → all backend endpoints
NetworkCalling/NetworkServiceCall.java → Volley request wrapper
utils/StripedProgressView.java → custom green download-progress bar
```

---

## 4. App Flow

```
SplashActivity ──▶ LoginActivity ──▶ SignagePlayerActivity
   (logo)            (licence)          (content playback)
```

1. **Splash** (`SplashActivity`) shows the branded logo, then routes based on saved session.
2. **Login** (`LoginActivity`) — the user enters the licence key; on success the app stores
   the session and schedule, then opens the player. The submit button turns green ("Done").
3. **Player** (`SignagePlayerActivity`) is the heart of the app. On start it:
   - shows a single **download-progress loader**,
   - plays already-downloaded content immediately (offline-first),
   - starts the **1-second schedule ticker**,
   - connects **MQTT** for realtime updates,
   - registers the **network listener** and **speed monitor**,
   - checks the backend once for newer content.

---

## 5. Content Download & Local Storage

**Entry point:** `SignagePlayerActivity.runSync()` → `ContentSyncManager.sync(...)`

- `ContentSyncManager` runs on a **single background thread**. It downloads the **active
  session first**, then the rest, so the current screen becomes ready fastest.
- `MediaDownloadManager.download(...)` writes to a `<file>.part` and only **atomically renames**
  it to the final file once complete. If interrupted, the `.part` is kept and the next attempt
  **resumes via HTTP Range**.
- `MediaValidator.isComplete(...)` verifies each downloaded file:
  - videos must be playable (`MediaMetadataRetriever`, duration > 0),
  - images must decode (`BitmapFactory`),
  - and the byte size must match the backend's `fileSize`.
  A partially-downloaded/corrupt file is **never treated as complete**.
- `LocalStorageManager` keeps content in the app's private folder, enforces an **8 GB budget**
  with a 1 GB safety margin, and evicts **least-recently-used** files when space is tight.
- The **currently-playing file is protected** from cleanup/eviction until its replacement is
  fully downloaded and validated (`ContentSyncManager` — `protectedFileName`).

The single loader shows the **real download percentage** (bytes ÷ `fileSize`) via
`SignagePlayerActivity.showDownloadProgress(...)` and `utils/StripedProgressView`.

---

## 6. Session / Schedule Playback

**Files:** `ContentSchedule.java`, `Playlist.java`, `SignagePlayerActivity.startScheduleTicker()`

- The player re-evaluates the active session **every second** (`SCHEDULE_TICK_MS = 1000`).
- `ContentSchedule.getActivePlaylist()` returns the session whose `[startTime, endTime]`
  window contains the current time:
  - a **specific timed session always wins** over a full-day / "forever" slot,
  - windows that **cross midnight** are handled (`Playlist.isActiveAt`),
  - if no session matches the current time it returns `null`.
- On a session boundary the player **switches content automatically** — no restart/refresh.
- **Outside any session** the player stops and shows *"Oops! No content has been configured at
  this time."* (`SignagePlayerActivity.showNoSession()`).
- Sessions are driven by `Playlist.nowSeconds()` (the **device's local clock**) and the
  **cached schedule**, so scheduling works fully **offline**.

> **Dependency:** because sessions use the TV's local clock, the device's **date, time and
> timezone must be correct** (ideally "Automatic date & time" ON).

---

## 7. Playback Engine

**File:** `signage/player/PlaylistController.java`

- `setPlaylist(Playlist)` decides what to show:
  - content downloaded → **play it** (video / image / PDF),
  - content not yet downloaded but something is already playing → **keep the current content
    playing** and show a small *"New content loading…"* indicator (update case),
  - nothing playing yet → the host shows the full-screen loader (first-time) or the offline
    screen (offline).
- **Type detection** (`MediaFile.isVideo/isImage/isPdf`) uses the backend `type` field **and**
  the file extension as a fallback, so a PDF is always rendered as a PDF, etc.
- Video → video / image → video transitions keep the last frame on screen until the new video's
  first frame renders (no black/white flash).
- PDFs are rendered first-page-to-bitmap (`renderPdfFirstPage`) with a size cap to avoid OOM.

---

## 8. Offline Playback & Fallback

**Files:** `SignagePlayerActivity.tryPlayOfflineFallback()`, `PlaylistController.playLocalFallback()`,
`LocalStorageManager.mostRecentPlayableFile()`

- On an offline restart, downloaded content plays immediately from local storage.
- If the *newly-scheduled* content isn't downloaded yet **and** there's no internet, the player
  falls back to **any valid content already on disk** (most recent) instead of showing the
  offline screen.
- A **transient playback error while offline never deletes** the downloaded file — it retries,
  so a momentary cold-start decoder hiccup can't destroy the only content on the device
  (`PlaylistController` — `onPlayerError`).
- The **"I'M OFFLINE" screen** (`showNoInternet()`, `res/drawable/offline_image.png`) appears
  **only** when there is no internet **and** no playable content on disk.

---

## 9. Network Awareness

**File:** `signage/net/NetworkSpeedMonitor.java`

- Continuously probes internet throughput against a fast CDN reference (~every 3 s).
- A small, semi-transparent **"no wifi" icon** blinks (~2×/minute) when speed is ≤ 5 Mbps or
  offline.
- On reconnect (`SignagePlayerActivity` network callback) the app checks the backend for the
  latest content, reconnects MQTT, and reports the screen online.

---

## 10. Realtime Updates (MQTT)

**File:** `Activites/MqttHelper.java`

- Subscribes to `mysign/chat/<sessionId>`.
- When the backend publishes a change, the player refreshes the schedule and downloads the new
  content in the background while the current content keeps playing.
- `setAutomaticReconnect(true)` plus an explicit reconnect on network-available keep the channel
  alive.

---

## 11. Backend APIs

Base URL is configured in `NetworkCalling/ServerURL.java`.

| Endpoint | Method / params | Purpose |
| --- | --- | --- |
| `/apklogin` | POST (licence) | Login/activation; returns the schedule (`data`), `MQTT_URL` |
| `/getMQTTscreens` | POST `userid, time, deviceName, availableMem, totalMem, mystatus` | Fetch the latest schedule / report the screen online |
| `/getScreenStatus` | POST | Screen / storage status reporting |
| Media file URLs | GET (+ HTTP Range) | Download the actual content to local storage |
| MQTT topic `mysign/chat/<sessionId>` | subscribe | Realtime "content changed" notifications |

Each item in the schedule `data` array includes `type`, `url`, `startTime`, `endTime`, and
`fileSize` (bytes, used to verify a complete download).

---

## 12. Building

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (R8 + resource shrinking + signed)
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Release signing reads from `keystore.properties` (kept out of version control). R8 keep rules
live in `app/proguard-rules.pro`; resources loaded dynamically (`getIdentifier`) are protected
by `app/src/main/res/raw/keep.xml`.

---

## 13. State Screens Summary

| State | What the screen shows |
| --- | --- |
| First-time download | Full-screen loader with real download % |
| Content playing | The video / image / PDF |
| Content update (online) | Current content keeps playing + small "New content loading…" chip |
| Low bandwidth / offline (playing) | Content keeps playing + small "no wifi" icon |
| Outside a session | "Oops! No content has been configured at this time." |
| Offline + no local content | "I'M OFFLINE" full-screen image |
| No content configured | "No content" screen |
