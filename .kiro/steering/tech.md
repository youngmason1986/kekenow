# KekeNow – Tech Steering

## Stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java (native Android) | Required by brief; broader device support; no Kotlin toolchain setup |
| Min SDK | 24 (Android 7.0) | Covers ~95 % of Nigerian Android devices in active use |
| Compile / Target SDK | 36 | Latest stable; required for Play Store compliance |
| Architecture | Single-Activity + Fragments | Minimal boilerplate; no Jetpack Navigation or Compose overhead |
| HTTP | `java.net.HttpURLConnection` | Zero dependency; sufficient for two JSON endpoints |
| Threading | `ExecutorService` + `Handler` | Replaces deprecated `AsyncTask`; no RxJava or coroutines |
| Maps | Leaflet 1.9.x in `WebView` | Renders OSM tiles; GeoJSON route overlay; no native map SDK |
| Geocoding | Nominatim (`nominatim.openstreetmap.org`) | Free, no key, `ng` country filter, Asaba viewbox bias |
| Routing | OSRM demo (`router.project-osrm.org`) | Free, no key, returns GeoJSON geometry + distance + duration |
| Speech input | `android.speech.SpeechRecognizer` | On-device; no cloud API key |
| Speech output | `android.speech.tts.TextToSpeech` | On-device; Nigerian English locale preferred |
| JSON | `org.json` (bundled in Android) | No extra dependency |
| Persistence | Flat JSON files in `Context.getFilesDir()` | Simple; FileObserver-compatible; human-readable |
| Dashboard | Vanilla HTML5 + CSS + JS | No build step; no framework; runs with `python3 -m http.server` |

## Hard Constraints

- **No Kotlin.** All `.java` files only.
- **No third-party HTTP libraries** (OkHttp, Retrofit, Volley).
- **No Firebase, Google Maps SDK, Google Places, or any paid/key-required API.**
- **No analytics, crash-reporting SDKs, or ad networks.**
- **No Room, LiveData, ViewModel, or Hilt** — keep the dependency footprint minimal for a hackathon APK.
- APK must remain under 10 MB (debug build).

## Threading Rules

- All network calls must run off the main thread (use `executor.execute(runnable)`).
- All UI updates must run on the main thread (`handler.post(runnable)` or `activity.runOnUiThread()`).
- Never block the main thread for I/O.
- `ExecutorService` instances owned by long-lived objects (services, stores) must be shut down in the owner's `destroy()` / `onDestroy()` lifecycle method.

## WebView Rules

- Load the Leaflet shell from `file:///android_asset/map.html` to avoid a network round-trip.
- Enable JS (`setJavaScriptEnabled(true)`) and DOM storage (`setDomStorageEnabled(true)`).
- Do NOT enable `setAllowFileAccessFromFileURLs` or `setAllowUniversalAccessFromFileURLs` — these are security risks.
- Mixed content is not needed; all external resources (tile servers, OSRM) use HTTPS.
- Always set a `WebViewClient` to intercept errors and avoid default Android error pages.

## Security Rules

- `android:usesCleartextTraffic="false"` in the manifest (all services are HTTPS).
- Device ID stored only as SHA-256 hash (`HashUtil.sha256(Settings.Secure.ANDROID_ID)`).
- No plaintext secrets, keys, or passwords anywhere in the codebase.
- Quote and validate all user-supplied strings before interpolating into URLs (`URLEncoder.encode()`).

## Nominatim Usage Policy

- Always include `User-Agent: KekeNow-App/1.0 (Android; contact@blackempire.com)`.
- Do not send more than one geocode request per second.
- This is a hackathon demo — do not deploy to production without reading the full Nominatim usage policy.

## Code Style

- Class names: `PascalCase`.
- Method and variable names: `camelCase`.
- Constants: `UPPER_SNAKE_CASE`.
- One public class per file; inner classes allowed for callbacks.
- No wildcard imports.
- All log tags use the string `"KekeNow"`.
- Every `catch` block must either re-throw, log with `Log.e("KekeNow", ...)`, or handle explicitly — no silent swallows.
