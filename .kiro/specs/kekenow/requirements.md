# KekeNow – Requirements

## Overview
KekeNow is a voice-first keke (tricycle taxi) ride-hailing app for Nigerian cities, starting with Asaba.  
A single Android APK (package `com.blackempire.kekenow`) contains two modes: **Passenger** and **Rider**.  
No API keys, no user accounts, no paid services. All maps, geocoding, and routing are open-source/free.

---

## Functional Requirements

### Mode Selection

- **REQ-01** WHEN the app is launched for the first time, THE SYSTEM SHALL display a full-screen mode selector offering "I Need a Keke (Passenger)" and "I Am a Rider".
- **REQ-02** WHEN the user selects a mode, THE SYSTEM SHALL persist the choice in SharedPreferences and go directly to that mode's main screen on subsequent launches.
- **REQ-03** WHEN the user long-presses the mode label on any main screen for 2 seconds, THE SYSTEM SHALL present a confirmation dialog and, upon confirmation, reset the mode and return to the mode selector.

---

### Passenger – Voice Destination Entry

- **REQ-10** WHEN the passenger taps the microphone button, THE SYSTEM SHALL request the `RECORD_AUDIO` permission if not already granted and, upon grant, start Android `SpeechRecognizer` in offline-preferred mode.
- **REQ-11** WHEN `SpeechRecognizer` returns a result, THE SYSTEM SHALL display the recognised text in a destination field and read it back via Android `TextToSpeech` in a Nigerian English voice if available, falling back to the default locale.
- **REQ-12** WHEN no speech is detected within 8 seconds, THE SYSTEM SHALL dismiss the recogniser, show a toast "Didn't catch that – try again", and re-enable the microphone button.
- **REQ-13** WHEN `SpeechRecognizer` is unavailable on the device, THE SYSTEM SHALL fall back to a plain text input field and suppress the microphone button.

---

### Passenger – Geocoding

- **REQ-20** WHEN the passenger confirms a destination (voice or text), THE SYSTEM SHALL geocode the destination string by querying the Nominatim Search API (`https://nominatim.openstreetmap.org/search`) with parameter `countrycodes=ng` and a bounding-box bias for Asaba (`viewbox=6.65,6.15,6.80,6.00`).
- **REQ-21** WHEN Nominatim returns one or more results, THE SYSTEM SHALL use the first result's `lat`/`lon` as the destination coordinate and proceed to routing.
- **REQ-22** WHEN Nominatim returns no results, THE SYSTEM SHALL speak "I could not find that location" and re-enable the microphone button.
- **REQ-23** WHEN the Nominatim request fails due to a network error, THE SYSTEM SHALL show a snackbar "No internet – check your connection" and re-enable the microphone button.
- **REQ-24** THE SYSTEM SHALL add the HTTP header `User-Agent: KekeNow-App/1.0 (Android; contact@blackempire.com)` to every Nominatim request to comply with the Nominatim usage policy.

---

### Passenger – Location

- **REQ-30** WHEN the passenger screen opens, THE SYSTEM SHALL request `ACCESS_FINE_LOCATION` permission; if denied, it SHALL fall back to `ACCESS_COARSE_LOCATION`.
- **REQ-31** WHEN location permission is granted, THE SYSTEM SHALL obtain the last-known location via `FusedLocationProviderClient` and, if stale (>60 s), request a fresh single update before proceeding.
- **REQ-32** WHEN location permission is permanently denied, THE SYSTEM SHALL show an in-app explanation and a button that opens the system app settings.

---

### Passenger – Routing & Map

- **REQ-40** WHEN both origin and destination coordinates are available, THE SYSTEM SHALL request a driving route from the OSRM demo server (`https://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson`).
- **REQ-41** WHEN the OSRM response contains a route, THE SYSTEM SHALL extract the GeoJSON geometry, total distance in metres, and duration in seconds.
- **REQ-42** WHEN route data is ready, THE SYSTEM SHALL load a Leaflet/OpenStreetMap map in a `WebView`, draw the passenger marker, the destination marker, and the route polyline, and fit the map bounds to the route.
- **REQ-43** THE SYSTEM SHALL serve the Leaflet HTML from local `assets/` so the map shell loads without internet; only the tile images and route call require connectivity.
- **REQ-44** WHEN the WebView map fails to load the local asset, THE SYSTEM SHALL log the error and display an error card with "Map unavailable".

---

### Passenger – Fare Estimate

- **REQ-50** WHEN route distance is known, THE SYSTEM SHALL compute the fare estimate using the Asaba keke fare table (see Design) and display it on a fare card before the passenger requests a ride.
- **REQ-51** THE SYSTEM SHALL display the fare as "₦ X – ₦ Y" (minimum–maximum band) derived from the distance tier.
- **REQ-52** WHEN the fare is computed, THE SYSTEM SHALL speak the fare string via `TextToSpeech`, e.g. "Estimated fare: two hundred to two hundred and fifty naira".
- **REQ-53** THE SYSTEM SHALL display a disclaimer: "Fare is an estimate. Agree with your rider before boarding."

---

### Passenger – Ride Request

- **REQ-60** WHEN the passenger taps "Request Keke", THE SYSTEM SHALL create a ride record with a generated UUID, timestamp (UTC), passenger device ID (Android `Settings.Secure.ANDROID_ID`, hashed with SHA-256), origin lat/lon, destination lat/lon, destination name, distance (m), and estimated fare band.
- **REQ-61** THE SYSTEM SHALL write the ride record as a JSON file to the app's `files/rides/` directory and attempt to POST it to the dashboard relay endpoint if configured (see REQ-80).
- **REQ-62** WHEN the ride record is written, THE SYSTEM SHALL transition to a "Waiting for rider" screen showing the route map and estimated fare, and speak "Looking for a keke near you".
- **REQ-63** WHEN no rider accepts within 3 minutes, THE SYSTEM SHALL speak "No rider found. Please try again." and return to the destination entry screen.

---

### Rider – Job Screen

- **REQ-70** WHEN the app is in Rider mode, THE SYSTEM SHALL display a job list showing all pending ride requests read from the shared `files/rides/` directory, ordered newest-first.
- **REQ-71** WHEN a new ride file appears in `files/rides/`, THE SYSTEM SHALL detect it via a `FileObserver` and refresh the list within 2 seconds, announcing "New job available" via `TextToSpeech`.
- **REQ-72** WHEN the rider taps a job card, THE SYSTEM SHALL show the pickup location, destination, distance, and fare band on a detail screen with the route drawn on a Leaflet map.
- **REQ-73** WHEN the rider taps "Accept Job", THE SYSTEM SHALL mark the ride as accepted (update the JSON file status to `"accepted"`), speak "Job accepted. Head to pickup.", and show a navigation-hint screen with the passenger's pickup pin on the map.
- **REQ-74** WHEN the rider taps "Complete Job", THE SYSTEM SHALL mark the ride status as `"completed"`, speak "Job done. Well done!", and return to the job list.
- **REQ-75** WHEN the rider taps "Decline Job", THE SYSTEM SHALL mark the ride status as `"declined"` and remove it from the list.

---

### Dashboard Relay (Optional Static Backend)

- **REQ-80** THE SYSTEM SHALL include a static web dashboard at `dashboard/index.html` that reads a local `rides.json` file and renders a live table of rides (ride ID, time, origin, destination, distance, fare, status).
- **REQ-81** THE SYSTEM SHALL auto-refresh the dashboard every 10 seconds by re-fetching `rides.json` via `fetch()`.
- **REQ-82** WHEN a new ride row arrives, THE SYSTEM SHALL highlight it in the table for 5 seconds with a yellow background.
- **REQ-83** THE SYSTEM SHALL provide a legend and status badge colouring: `pending` = orange, `accepted` = blue, `completed` = green, `declined` = grey.

---

### Permissions & Privacy

- **REQ-90** THE SYSTEM SHALL declare only the minimum required permissions: `INTERNET`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`.
- **REQ-91** THE SYSTEM SHALL NOT collect names, phone numbers, or any personally identifiable information. Device ID SHALL be stored only as a one-way SHA-256 hash.
- **REQ-92** THE SYSTEM SHALL NOT transmit any data to third-party analytics, advertising, or tracking services.

---

### Offline Resilience

- **REQ-100** WHEN the device has no internet, THE SYSTEM SHALL allow the passenger to type a destination manually (REQ-13 fallback), show a cached last map tile if available, and display the message "Offline – map may be incomplete".
- **REQ-101** WHEN the OSRM routing call fails, THE SYSTEM SHALL fall back to a straight-line distance estimate (Haversine formula) and label the fare card "Approximate – routing unavailable".

## Role Choice, Sign-Up & Rider Identity (added Nov 24, 2026 — George)

### Role Choice (First Launch)
- WHEN the app is opened with no saved profile, THE SYSTEM SHALL show a
  role-choice screen first: **Rider** or **Customer**.
- WHEN the user picks a role, THE SYSTEM SHALL open the sign-up that
  matches the role (see below); after sign-up it SHALL enter that role's
  mode directly. WHEN a profile is already saved but the user is signed
  out, THE SYSTEM SHALL show phone sign-in instead of the role choice.
- WHEN the user is signed in, THE SYSTEM SHALL let the same screen act
  as a mode switcher (Rider / Customer cards switch modes).

### Sign-Up / Sign-In (Local Only, Demo Scope)
- WHEN a new user signs up, THE SYSTEM SHALL store the profile locally
  on the device (SharedPreferences + internal storage for the photo);
  no server, no password.
- WHEN a returning user enters the phone number of a saved profile,
  THE SYSTEM SHALL sign them straight back in; any other number SHALL
  start registration.
- WHEN the user signs out, THE SYSTEM SHALL keep the saved profile on
  the device but clear the signed-in flag.

### Rider Profile (Full)
- WHEN a rider signs up, THE SYSTEM SHALL collect: profile photo
  (gallery pick, copied to internal storage), name, phone number,
  state of registration (picker: all 36 states + FCT, default Delta),
  FRSC plate number and keke code / union tag.
- WHEN the rider picks a state, THE SYSTEM SHALL show that state's
  plate example and tag format from docs/nigeria-tricycle-codes.md
  (Delta `DT/CT/10550B`, Enugu `ENS/104/1/6653`, Kano KNT series with
  no separate tag, Lagos NURTW tag, Anambra `ATMC 420525`, others
  "union tag number on the keke (if any)").
- WHEN a rider enters a plate number, THE SYSTEM SHALL accept only the
  shapes `ABC-123-XY` (current), `AB123-CDE` (pre-2011) or `KNT-123-A`
  (Kano tricycle series) and SHALL store it in dashed display form.
- WHEN the rider's state is Delta, Enugu, Lagos or Anambra, THE SYSTEM
  SHALL require the keke code / union tag; in other states it SHALL be
  optional; in Kano THE SYSTEM SHALL hide the tag field (the KNT plate
  series is the identifier).
- THE SYSTEM SHALL NEVER claim a plate or tag is verified against
  government records. The registration screen SHALL state that only
  the shape of the numbers is checked.

### Customer Profile
- WHEN a customer signs up, THE SYSTEM SHALL collect name and phone
  number ONLY (no photo, no state, no plate, no tag).

### Rider Identity Shown to the Passenger (Security)
- WHEN a rider accepts a job, THE SYSTEM SHALL attach the rider's
  photo, name, phone, FRSC plate number and keke code / union tag to
  the ride record.
- WHEN the ride becomes accepted, THE SYSTEM SHALL show the rider card
  on the passenger's waiting screen (photo, name, phone, plate, keke
  code) with a Call button and the note "Check the plate and keke code
  on the keke before you board", and SHALL speak the rider's name and
  keke code aloud.
- WHEN a rider opens Rider mode without a complete rider identity,
  THE SYSTEM SHALL show a banner that opens the profile form.
- WHEN a rider accepts a job without a complete rider identity,
  THE SYSTEM SHALL still accept the job but SHALL warn the rider to
  complete the profile so passengers can see who they are.
