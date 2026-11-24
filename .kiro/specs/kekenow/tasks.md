# KekeNow – Tasks

## Phase 1 – Project Scaffold

- [ ] 1.1 Create Android project with package `com.blackempire.kekenow`, minSdk 24, compileSdk 36, targetSdk 36, native Java (no Kotlin).
- [ ] 1.2 Add `app/build.gradle` with no third-party dependencies (stdlib + AndroidX core + AppCompat + Material only).
- [ ] 1.3 Configure `AndroidManifest.xml` with permissions: `INTERNET`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`.
- [ ] 1.4 Set `MainActivity` as the launcher activity; configure it for `singleTop` launch mode.
- [ ] 1.5 Add `res/values/strings.xml` with all UI strings (no hard-coded strings in Java).
- [ ] 1.6 Add `res/values/colors.xml` and `res/values/themes.xml` with a green-and-yellow KekeNow theme.
- [ ] 1.7 Verify the project builds and installs an empty-screen APK on a minSdk 24 emulator.

## Phase 2 – Mode Selection

- [ ] 2.1 Create `ModeSelectFragment` with two large `CardView` buttons: "I Need a Keke" and "I Am a Rider".
- [ ] 2.2 On card tap, write the selected mode (`"passenger"` or `"rider"`) to `SharedPreferences` key `mode`.
- [ ] 2.3 On app launch in `MainActivity.onCreate()`, read the saved mode and navigate directly to `PassengerFragment` or `RiderFragment`, skipping `ModeSelectFragment`.
- [ ] 2.4 Implement long-press (2 s) on the mode label in both main fragments to show a `AlertDialog` "Switch mode?" and, on confirm, clear the preference and replace with `ModeSelectFragment`.

## Phase 3 – Location

- [ ] 3.1 Create a `LocationHelper` utility class that requests `ACCESS_FINE_LOCATION` using `ActivityCompat.requestPermissions()`.
- [ ] 3.2 In `LocationHelper`, implement `getCurrentLocation(callback)` using `FusedLocationProviderClient`; if last-known location is null or older than 60 s, request a single fresh update.
- [ ] 3.3 Handle the case where both fine and coarse location are denied: show a `Snackbar` with an "Open Settings" action linking to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
- [ ] 3.4 Call `LocationHelper.getCurrentLocation()` from `PassengerFragment.onResume()` and store the result as `originLatLon`.

## Phase 4 – Voice Input

- [ ] 4.1 Create `VoiceService` with `init(Context)`, `startListening(VoiceListener)`, `speak(String)`, and `shutdown()` methods.
- [ ] 4.2 Implement `startListening()` using `SpeechRecognizer.createSpeechRecognizer()`; configure the `RecognizerIntent` with `en-NG` locale, free-form model, and 3 s silence timeout.
- [ ] 4.3 Add an 8-second watchdog `Handler.postDelayed()` that calls `recognizer.cancel()` and fires `onTimeout()` on the listener if `onResults` hasn't fired.
- [ ] 4.4 Implement `speak(String text)` using `TextToSpeech`; prefer locale `en_NG`, fall back to `en_GB`, then `Locale.ENGLISH`.
- [ ] 4.5 In `PassengerFragment`, add a `FloatingActionButton` mic icon; on tap, check `RECORD_AUDIO` permission and start listening.
- [ ] 4.6 On `SpeechRecognizer.onResults()`, populate the destination `EditText` with the top result and call `VoiceService.speak(result)`.
- [ ] 4.7 On timeout or `onError()`, show a `Toast` "Didn't catch that – try again" and re-enable the FAB.
- [ ] 4.8 If `SpeechRecognizer.isRecognitionAvailable(context)` returns false, hide the FAB and show a plain `EditText` for manual destination entry.

## Phase 5 – Geocoding (Nominatim)

- [ ] 5.1 Create `GeoService` with method `geocode(String query, GeoCallback callback)`.
- [ ] 5.2 Build the Nominatim URL with `countrycodes=ng`, `viewbox=6.65,6.15,6.80,6.00&bounded=1`, `limit=1`, `format=json`.
- [ ] 5.3 Add the `User-Agent: KekeNow-App/1.0` header to the `HttpURLConnection` request.
- [ ] 5.4 Run the HTTP call on a background `ExecutorService` thread; post results back via `new Handler(Looper.getMainLooper()).post(...)`.
- [ ] 5.5 Parse the JSON array response; if length > 0 extract `lat` and `lon` from index 0 and return a `LatLon` object via callback.
- [ ] 5.6 If the array is empty, return `null` via callback; `PassengerFragment` speaks "I could not find that location".
- [ ] 5.7 Catch `IOException` and return an error code via callback; `PassengerFragment` shows a `Snackbar` "No internet – check your connection".

## Phase 6 – Routing (OSRM)

- [ ] 6.1 Add `GeoService.route(LatLon origin, LatLon dest, RouteCallback callback)`.
- [ ] 6.2 Build the OSRM URL `https://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson`.
- [ ] 6.3 Parse the response JSON: extract `routes[0].distance`, `routes[0].duration`, and `routes[0].geometry` (GeoJSON LineString).
- [ ] 6.4 Return a `RouteResult(double distanceMetres, double durationSeconds, String geoJsonGeometry)` via callback.
- [ ] 6.5 Implement `GeoService.haversineMetres(LatLon a, LatLon b)` as a straight-line fallback using the Haversine formula.
- [ ] 6.6 If the OSRM call fails with `IOException`, fall back to Haversine and return `RouteResult` with `geoJsonGeometry = null` and a `fallback = true` flag.

## Phase 7 – Fare Calculator

- [ ] 7.1 Create `FareBand` model: `int min, max`.
- [ ] 7.2 Create `FareCalculator.estimate(double distanceMetres)` implementing the seven-row fare table from the design doc.
- [ ] 7.3 Unit-test `FareCalculator` with boundary values: 0 m, 999 m, 1000 m, 1010 m, 2000 m, 5000 m, 8000 m, 15000 m.
- [ ] 7.4 In `PassengerFragment`, call `FareCalculator.estimate()` after receiving `RouteResult` and display the band in a `CardView` fare card: `"₦ X – ₦ Y"`.
- [ ] 7.5 Display the disclaimer text: "Fare is an estimate. Agree with your rider before boarding."
- [ ] 7.6 Call `VoiceService.speak()` with the fare string after the fare card is shown.
- [ ] 7.7 If OSRM fallback was used, append " (Approximate – routing unavailable)" to the fare card subtitle.

## Phase 8 – Leaflet Map (WebView)

- [ ] 8.1 Create `assets/map.html` with a full-viewport Leaflet map, loading Leaflet from CDN (`https://unpkg.com/leaflet@1.9.4/`).
- [ ] 8.2 Define `window.showRoute(geoJsonStr, oLat, oLon, dLat, dLon)` in the HTML: adds a blue polyline for the route GeoJSON, a green marker for origin, a red marker for destination, and fits bounds.
- [ ] 8.3 Define `window.clearMap()` to remove all layers except the base tile layer.
- [ ] 8.4 Create `MapBridge` Java class with `@JavascriptInterface` annotation; add it to the WebView with `addJavascriptInterface(mapBridge, "Android")`.
- [ ] 8.5 In `PassengerFragment` and `RideDetailFragment`, add a `WebView`, enable JS and DOM storage, load `file:///android_asset/map.html`.
- [ ] 8.6 After `WebView.onPageFinished()`, call `webView.evaluateJavascript("showRoute(...)", null)` with the route data.
- [ ] 8.7 Handle `WebViewClient.onReceivedError()` by hiding the WebView and showing an error `TextView` "Map unavailable".

## Phase 9 – Ride Request & Store

- [ ] 9.1 Create `RideRecord` POJO with all fields from the schema in the design doc.
- [ ] 9.2 Create `HashUtil.sha256(String input)` using `MessageDigest`.
- [ ] 9.3 Create `RideStore` with `create(RideRecord)`, `listPending()`, `updateStatus(String id, String status)`, and `get(String id)` methods.
- [ ] 9.4 `RideStore.create()` serialises the `RideRecord` to JSON using `org.json.JSONObject` and writes it to `{filesDir}/rides/{id}.json`; create the directory if absent.
- [ ] 9.5 `RideStore.listPending()` reads all `.json` files in the rides directory, parses them, filters for `status = "pending"`, and returns a `List<RideRecord>` sorted by `createdAt` descending.
- [ ] 9.6 `RideStore.updateStatus()` reads the file, updates the `status` field, and writes it back atomically (write to a `.tmp` file, then rename).
- [ ] 9.7 Add a "Request Keke" `Button` in `PassengerFragment`; on tap, call `RideStore.create()` and navigate to a waiting screen.
- [ ] 9.8 The waiting screen shows the map, fare card, and a `ProgressBar` spinner. After 3 minutes with no status change, cancel and navigate back.

## Phase 10 – Rider Mode

- [ ] 10.1 Create `RiderFragment` with a `RecyclerView` job list and an empty-state view "Waiting for jobs…".
- [ ] 10.2 Create `JobCardAdapter` displaying: destination name, distance, fare band, time received, status badge.
- [ ] 10.3 In `RiderFragment.onResume()`, start a `FileObserver` on `{filesDir}/rides/` for `CREATE | MODIFY` events; stop it in `onPause()`.
- [ ] 10.4 On `FileObserver.onEvent()`, post a `refreshJobs()` runnable to the main thread; `refreshJobs()` calls `RideStore.listPending()` and updates the adapter.
- [ ] 10.5 When a new job appears (list size increases), call `VoiceService.speak("New job available")`.
- [ ] 10.6 On job card tap, navigate to `RideDetailFragment` passing the ride ID as a fragment argument.
- [ ] 10.7 In `RideDetailFragment`, load the ride record, render the WebView map with pickup + destination, show fare band and distance.
- [ ] 10.8 Wire "Accept Job" button: call `RideStore.updateStatus(id, "accepted")`, speak "Job accepted. Head to pickup.", and show a navigation-hint card.
- [ ] 10.9 Wire "Complete Job" button: call `RideStore.updateStatus(id, "completed")`, speak "Job done. Well done!", pop back to `RiderFragment`.
- [ ] 10.10 Wire "Decline Job" button: call `RideStore.updateStatus(id, "declined")`, pop back without announcement.

## Phase 11 – Dashboard

- [ ] 11.1 Create `dashboard/index.html` with a semantic HTML5 table: Time, Passenger, From, To, Distance, Fare, Status.
- [ ] 11.2 Add inline CSS: status badge colours (orange/blue/green/grey), new-row yellow-flash transition, responsive table layout.
- [ ] 11.3 Add a vanilla JS `loadRides()` function that `fetch('./rides.json')`, parses the JSON array, and renders table rows.
- [ ] 11.4 On first load call `loadRides()`; then `setInterval(loadRides, 10000)` for auto-refresh.
- [ ] 11.5 Track rendered ride IDs in a `Set`; on refresh, add class `row-new` to rows with IDs not in the previous set.
- [ ] 11.6 CSS: `.row-new` starts with `background-color: #fff3cd`, transitions to transparent over 5 s using a CSS animation.
- [ ] 11.7 Add a header with the KekeNow logo text and a "Last updated: HH:MM:SS" timestamp.
- [ ] 11.8 Add a `README.md` in `dashboard/` explaining how to run with `python3 -m http.server` and the expected `rides.json` format.

## Phase 12 – Polish & Hardening

- [ ] 12.1 Audit all `catch` blocks; ensure no silent swallows — log every exception with `Log.e("KekeNow", ...)`.
- [ ] 12.2 Rotate `ExecutorService` threads properly: shut down in `onDestroy()` of any class that owns one.
- [ ] 12.3 Ensure `VoiceService.shutdown()` calls `tts.stop()`, `tts.shutdown()`, and `recognizer.destroy()`.
- [ ] 12.4 Add `android:usesCleartextTraffic="false"` to the manifest (all external services use HTTPS already).
- [ ] 12.5 Test on minSdk 24 emulator: verify all flows work without crash.
- [ ] 12.6 Test offline: disable network; verify text entry fallback, Haversine fallback fare, and "Offline" banner appear.
- [ ] 12.7 Test with `RECORD_AUDIO` denied: verify mic button hides and text input appears.
- [ ] 12.8 Test with location denied: verify settings link appears.
- [ ] 12.9 Add a `CONTRIBUTING.md` with build instructions, emulator setup, and Nominatim usage-policy reminder.
- [ ] 12.10 Final APK build: `./gradlew assembleDebug`; confirm APK size is under 10 MB.

## Phase 13 – Role Choice, Sign-Up & Rider Identity (Nov 24, 2026)

- [x] 13.1 Role-choice screen as entry point (ModeSelectFragment: Rider / Customer; first launch -> matching sign-up; signed in -> mode switcher).
- [x] 13.2 AuthFragment: phone sign-in (matching number signs back in, new number -> registration); MainActivity branches by profile state.
- [x] 13.3 UserProfile + ProfileStore (local only; photo copied to internal storage; sign-out keeps profile).
- [x] 13.4 ProfileRegisterFragment branched by role: Rider full profile (photo, name, phone, state picker, FRSC plate, keke code/tag); Customer name + phone only.
- [x] 13.5 TricycleCodes util: 36 states + FCT, per-state plate examples + tag hints, plate shape check (ABC-123-XY / AB123-CDE / KNT-123-A), no-verification boundary in UI copy.
- [x] 13.6 RideRecord rider fields (riderName/Phone/PhotoPath/PlateNumber/StateTag) + RideStore.update(); accept attaches the rider profile.
- [x] 13.7 Passenger waiting screen: 2s poll for acceptance, rider card (photo, name, phone, plate, keke code) + Call button + check-before-boarding note + spoken name/code.
- [x] 13.8 Rider mode banner until rider identity is complete; accept warns when identity is incomplete.
- [ ] 13.9 On-device test: fresh install -> pick Rider -> register (Delta, plate ASB-991-VC, tag DT/CT/10550B) -> request as Customer -> accept as Rider -> passenger sees rider card + both numbers.
- [ ] 13.10 On-device test: fresh install -> pick Customer -> register (name + phone only) -> request a ride.
