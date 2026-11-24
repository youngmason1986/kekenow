# KekeNow – Design

## 1. Architecture Overview

KekeNow is a single Android APK written in native Java (minSdk 24, compileSdk/targetSdk 36).  
It uses a simple **single-Activity + Fragment** architecture with no external DI framework.

```
┌─────────────────────────────────────────────────────────┐
│                        MainActivity                      │
│  - Mode gate: ModeSelectFragment                        │
│  - Passenger flow: PassengerFragment                    │
│  - Rider flow:     RiderFragment                        │
└───────────────┬────────────────────────┬────────────────┘
                │                        │
        ┌───────▼──────┐        ┌────────▼───────┐
        │  Passenger   │        │     Rider      │
        │  Fragment    │        │    Fragment    │
        └──────┬───────┘        └────────┬───────┘
               │                         │
     ┌─────────▼──────────────────────────▼─────────┐
     │              Shared Services                  │
     │  VoiceService  │  GeoService  │  RideStore   │
     └────────────────────────────────────────────────┘
```

**Navigation:** Android `FragmentManager` back-stack; no Jetpack Navigation (keep deps minimal).  
**Threading:** `AsyncTask` is deprecated; use `java.util.concurrent.ExecutorService` + main-thread `Handler` for all background work.  
**State:** Minimal — UI state lives in Fragments; persistent state in SharedPreferences (mode) and JSON files (rides).

---

## 2. Component Inventory

| Component | Class | Responsibility |
|---|---|---|
| Entry point | `MainActivity` | Hosts all fragments, handles permission results |
| Mode gate | `ModeSelectFragment` | First-launch mode chooser |
| Passenger screen | `PassengerFragment` | Voice entry, geocode, map, fare, request |
| Rider screen | `RiderFragment` | Job list, FileObserver, TTS alerts |
| Job detail | `RideDetailFragment` | Route map + accept/decline/complete |
| Voice helper | `VoiceService` | Wraps `SpeechRecognizer` + `TextToSpeech` |
| Geo helper | `GeoService` | Nominatim geocode + OSRM route + Haversine fallback |
| Fare calculator | `FareCalculator` | Distance → fare band |
| Ride store | `RideStore` | CRUD on JSON ride files in `files/rides/` |
| Map bridge | `MapBridge` | JavaScript interface between WebView Leaflet and Java |
| Dashboard | `dashboard/index.html` | Static HTML/JS live table |

---

## 3. Data Flow – Passenger Happy Path

```
1. Launch → ModeSelectFragment → user picks "Passenger"
2. PassengerFragment opens → FusedLocationProviderClient → origin lat/lon
3. User taps mic → VoiceService.startListening()
4. VoiceService callback → recognised text → PassengerFragment
5. TTS reads back text
6. User confirms → GeoService.geocode(text) → Nominatim HTTP GET
7. Nominatim returns lat/lon → GeoService.route(origin, dest) → OSRM HTTP GET
8. OSRM returns GeoJSON + distance + duration
9. FareCalculator.estimate(distanceMetres) → fare band
10. PassengerFragment loads WebView with assets/map.html
11. MapBridge.showRoute(geoJson, originMarker, destMarker) via evaluateJavascript()
12. Fare card shown + TTS speaks fare
13. User taps "Request Keke" → RideStore.create(rideRecord) → writes JSON file
14. "Waiting for rider" screen shown
15. FileObserver in RiderFragment detects new file → job list refreshes
16. Rider taps job → RideDetailFragment → accepts → status updated → both sides notified via TTS
```

---

## 4. Data Flow – Rider Path

```
1. Launch → user picks "Rider" (or persisted)
2. RiderFragment → RideStore.listPending() → shows job cards
3. FileObserver watches files/rides/ → triggers list refresh on new file
4. Rider taps card → RideDetailFragment
5. MapBridge renders pickup + destination on WebView map
6. Rider taps "Accept" → RideStore.updateStatus(id, "accepted")
7. Rider taps "Complete" → RideStore.updateStatus(id, "completed")
```

---

## 5. Fare Table – Asaba Keke Rates

Based on typical Asaba keke fares (2024–2025 reference).

| Distance band | Minimum fare | Maximum fare |
|---|---|---|
| 0 – 1 km | ₦100 | ₦150 |
| 1.01 – 2 km | ₦150 | ₦200 |
| 2.01 – 3 km | ₦200 | ₦250 |
| 3.01 – 5 km | ₦250 | ₦350 |
| 5.01 – 8 km | ₦350 | ₦500 |
| 8.01 – 12 km | ₦500 | ₦700 |
| > 12 km | ₦700 | ₦1000 |

`FareCalculator.estimate(double distanceMetres)` returns a `FareBand(int min, int max)`.  
The displayed string is `"₦" + min + " – ₦" + max`.

---

## 6. Ride Record Schema (JSON)

```json
{
  "id": "uuid-v4",
  "createdAt": "2026-10-31T14:22:00Z",
  "passengerId": "sha256-of-android-id",
  "status": "pending | accepted | completed | declined",
  "origin": { "lat": 6.1944, "lon": 6.7357 },
  "destination": { "lat": 6.2010, "lon": 6.7421, "name": "Asaba Main Market" },
  "distanceMetres": 1234,
  "durationSeconds": 420,
  "fareMin": 150,
  "fareMax": 200,
  "routeGeoJson": "{ ... }"
}
```

Files are written to `Context.getFilesDir()/rides/<id>.json`.  
`routeGeoJson` is stored as an escaped JSON string to keep the outer structure simple.

---

## 7. Networking

| Call | URL pattern | Library |
|---|---|---|
| Geocode | `https://nominatim.openstreetmap.org/search?q={query}&format=json&countrycodes=ng&viewbox=6.65,6.15,6.80,6.00&bounded=1&limit=1` | `java.net.HttpURLConnection` |
| Route | `https://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson` | `java.net.HttpURLConnection` |
| Map tiles | Loaded by Leaflet inside WebView | WebView / Leaflet |

No third-party HTTP library (OkHttp, Retrofit) is introduced to keep the APK lightweight.  
All HTTP calls run on a background thread via `ExecutorService`; results are posted to the main thread via `Handler.post()`.

---

## 8. WebView / Leaflet Map

- `assets/map.html` bundles Leaflet JS + CSS from CDN with a local fallback comment.  
- On load, the page exposes `window.showRoute(geoJsonStr, originLat, originLon, destLat, destLon)` and `window.clearMap()`.  
- `MapBridge` (annotated with `@JavascriptInterface`) lets Java call into JS via `webView.evaluateJavascript(...)` and lets JS call back into Java for tap events if needed.  
- WebView settings: `setJavaScriptEnabled(true)`, `setDomStorageEnabled(true)`, `setBuiltInZoomControls(true)`, `setDisplayZoomControls(false)`.  
- Mixed-content mode is **not** required because all external resources (tiles, OSRM) use HTTPS.

---

## 9. Voice Architecture

### Speech Recognition
- `android.speech.SpeechRecognizer` — `createSpeechRecognizer(context)`.
- Intent: `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`, `EXTRA_LANGUAGE = "en-NG"` falling back to `"en-US"`.
- Timeout: `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS = 3000`, outer watchdog timer of 8 s cancels if no `onResults`.

### Text-to-Speech
- `android.speech.tts.TextToSpeech` — initialised in `VoiceService.init()`.
- Locale preference order: `en_NG` → `en_GB` → `Locale.ENGLISH`.
- Queued with `QUEUE_FLUSH` for fare announcements; `QUEUE_ADD` for rider alerts.

---

## 10. FileObserver (Rider Mode)

`android.os.FileObserver` watches `files/rides/` for `CREATE` and `MODIFY` events.  
On event, the observer posts a `Runnable` to the main thread via `Handler`; `RiderFragment.refreshJobs()` re-reads all JSON files in the directory.  
The observer is started in `onResume()` and stopped in `onPause()` to avoid leaks.

---

## 11. Error Handling

| Scenario | Handling |
|---|---|
| `RECORD_AUDIO` denied | Hide mic button; show text field |
| `ACCESS_FINE_LOCATION` denied | Fallback to coarse; if both denied show settings link |
| Nominatim 0 results | TTS "Could not find that location"; re-enable mic |
| Nominatim network error | Snackbar; re-enable mic |
| OSRM network error | Haversine straight-line fallback; label fare "Approximate" |
| WebView asset load error | Show error card "Map unavailable" |
| No rides in rider mode | Show empty-state illustration and "Waiting for jobs…" |
| `SpeechRecognizer` unavailable | Show text input; suppress mic |
| JSON parse error (ride file) | Log warning; skip corrupted file; don't crash |

---

## 12. Dashboard (Static HTML)

`dashboard/index.html` is a standalone HTML5 file with no build step.

- Uses `fetch('./rides.json')` (relative path) every 10 seconds.
- Renders a `<table>` with columns: Time, Passenger ID (truncated hash), From, To, Distance, Fare, Status.
- Status badges: inline `<span>` with class `status-pending`, `status-accepted`, `status-completed`, `status-declined`.
- New rows (detected by comparing ride IDs against previous render) get class `row-new`, which CSS transitions from `background: #fff3cd` to transparent over 5 s.
- No JavaScript framework — vanilla JS only.

A developer running the demo can copy ride JSON files to the same folder as `index.html` and open it in a browser, or serve it with `python3 -m http.server`.

---

## 13. Project Structure (Target)

```
kekenow/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/
│   │   │   └── map.html                  # Leaflet map shell
│   │   └── java/com/blackempire/kekenow/
│   │       ├── MainActivity.java
│   │       ├── fragment/
│   │       │   ├── ModeSelectFragment.java
│   │       │   ├── PassengerFragment.java
│   │       │   ├── RiderFragment.java
│   │       │   └── RideDetailFragment.java
│   │       ├── service/
│   │       │   ├── VoiceService.java
│   │       │   └── GeoService.java
│   │       ├── model/
│   │       │   ├── RideRecord.java
│   │       │   └── FareBand.java
│   │       ├── store/
│   │       │   └── RideStore.java
│   │       └── util/
│   │           ├── FareCalculator.java
│   │           ├── MapBridge.java
│   │           └── HashUtil.java
│   └── build.gradle
├── dashboard/
│   └── index.html
├── build.gradle
└── settings.gradle
```

## 14. Prior Art — ORide Lessons (Inspiration Only)

ORide (OPay's bike-hailing app, Lagos 2019, shut down ~2020 after the Lagos okada/keke ban) is the experience model George remembers. Lessons adopted:

- **Simple booking** — request a ride in seconds; no account, no form.
- **Fare shown upfront** — price is visible before the passenger commits.
- **Rider verification** — riders are known, named operators.

KekeNow differs in three deliberate ways: **voice booking** instead of typed booking, **keke (tricycle) instead of okada (motorbike)**, and **Asaba-first** instead of Lagos scale. ORide is inspiration only — no code, branding, or assets are copied or reused.

## 15. Role Choice, Auth & Rider Identity (added Nov 24, 2026)

Entry point by state (MainActivity): no saved profile → role-choice
screen (ModeSelectFragment: Rider / Customer); profile saved but signed
out → phone sign-in (AuthFragment); signed in → saved mode, or the
role-choice screen as a mode switcher.

- `model/UserProfile` — id, role (`rider` / `passenger`), name, phone,
  photoPath, state, plateNumber, stateTag, createdAt. JSON via
  SharedPreferences. `hasRiderIdentity()` = rider + name + phone +
  valid plate shape + tag present when the state requires it
  (Kano exempt — KNT plate is enough).
- `store/ProfileStore` — one profile in SharedPreferences
  (`kekenow_profile`); phone number IS the account; `signOut()` keeps
  the profile; `savePhoto(Uri)` copies a picked image to internal
  storage (`profile_photo.jpg`, max 512px, JPEG 85).
- `fragment/AuthFragment` — phone entry; matching number signs back in,
  otherwise opens registration.
- `fragment/ProfileRegisterFragment` — branched by role. Rider: photo,
  name, phone, state Spinner (all 36 + FCT, default Delta), plate,
  tag. Customer: name + phone only. State selection drives the plate
  example and tag hint/requirement via `util/TricycleCodes`. After
  save, enters the matching mode directly (`MainActivity.setMode`).
- `util/TricycleCodes` — states list, per-state plate examples (real
  keke plates where the research found them, e.g. Delta ASB 991 VC),
  tag hints (Delta DT/CT/…, Enugu ENS/104/1/…, Kano none, Lagos NURTW,
  Anambra ATMC …), `isValidPlate` (ABC-123-XY / AB123-CDE / KNT-123-A),
  `formatPlate`, `tagRequiredFor`. Shape check ONLY — no verification
  against government records (none is public); the UI says so.
- Ride record gains: `riderName`, `riderPhone`, `riderPhotoPath`,
  `riderPlateNumber`, `riderStateTag` (see §6 schema — new fields).
  `RideStore.update(record, cb)` rewrites a ride file in full;
  `RideDetailFragment.acceptJob` fills the rider fields from the
  current profile, then `update()`s the record with status `accepted`.
- `PassengerWaitingFragment` polls `RideStore.get(rideId)` every 2s;
  on `accepted` it shows the rider card (photo, name, phone, plate,
  keke code), a Call button (ACTION_DIAL) and the check-before-boarding
  note, and speaks name + keke code. Polling stops on cancel/timeout/
  destroy. `RiderFragment` shows a complete-profile banner until
  `hasRiderIdentity()` is true.
