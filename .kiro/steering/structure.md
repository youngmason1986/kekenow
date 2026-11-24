# KekeNow – Structure Steering

## Repository Layout

```
kekenow/
├── .kiro/
│   ├── specs/kekenow/
│   │   ├── requirements.md      # EARS-style functional requirements
│   │   ├── design.md            # Architecture, components, fare table, data flow
│   │   └── tasks.md             # Incremental checkbox task list
│   └── steering/
│       ├── product.md           # What, who, why, success criteria
│       ├── tech.md              # Stack, constraints, rules
│       └── structure.md         # This file — layout and conventions
├── app/
│   ├── build.gradle             # App-level Gradle config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── map.html         # Leaflet map shell (loads from file:///)
│       ├── res/
│       │   ├── layout/          # XML layouts for activities and fragments
│       │   ├── values/
│       │   │   ├── strings.xml  # All user-visible strings
│       │   │   ├── colors.xml
│       │   │   └── themes.xml
│       │   └── drawable/        # Icons and vector assets
│       └── java/com/blackempire/kekenow/
│           ├── MainActivity.java
│           ├── fragment/
│           │   ├── ModeSelectFragment.java
│           │   ├── PassengerFragment.java
│           │   ├── RiderFragment.java
│           │   └── RideDetailFragment.java
│           ├── service/
│           │   ├── VoiceService.java    # SpeechRecognizer + TextToSpeech wrapper
│           │   └── GeoService.java      # Nominatim geocode + OSRM route + Haversine
│           ├── model/
│           │   ├── RideRecord.java      # Ride data POJO
│           │   └── FareBand.java        # min/max fare pair
│           ├── store/
│           │   └── RideStore.java       # JSON file CRUD for rides
│           └── util/
│               ├── FareCalculator.java  # Distance → FareBand
│               ├── MapBridge.java       # @JavascriptInterface WebView bridge
│               ├── HashUtil.java        # SHA-256 device ID hashing
│               └── LocationHelper.java  # FusedLocationProviderClient wrapper
├── dashboard/
│   ├── index.html               # Static live-ride dashboard
│   └── README.md                # How to run + rides.json format
├── build.gradle                 # Root Gradle config
├── settings.gradle              # Module includes
└── CONTRIBUTING.md              # Build instructions + Nominatim reminder
```

## Package Conventions

| Package | Contents |
|---|---|
| `com.blackempire.kekenow` | `MainActivity` only |
| `com.blackempire.kekenow.fragment` | All `Fragment` subclasses |
| `com.blackempire.kekenow.service` | `VoiceService`, `GeoService` |
| `com.blackempire.kekenow.model` | Plain data classes (`RideRecord`, `FareBand`) |
| `com.blackempire.kekenow.store` | Persistence layer (`RideStore`) |
| `com.blackempire.kekenow.util` | Stateless helpers (`FareCalculator`, `MapBridge`, `HashUtil`, `LocationHelper`) |

## File Naming Rules

- One public Java class per file; file name matches class name exactly.
- XML layout files mirror their Java counterpart: `fragment_passenger.xml` for `PassengerFragment`.
- XML IDs use `snake_case` prefixed by type: `btn_request_keke`, `tv_fare_band`, `wv_map`.

## Ride Data Files

- Written to `Context.getFilesDir()/rides/<uuid>.json`.
- File name is the ride UUID (lowercase, hyphenated, e.g. `a1b2c3d4-e5f6-...json`).
- Atomic write: write to `<uuid>.tmp`, then `File.renameTo(<uuid>.json)`.
- Never delete files during the session; `FileObserver` relies on file presence.

## Assets

- `assets/map.html` is the only asset file. Keep it self-contained (all JS inline or from CDN with a graceful load failure).
- Do not add binary assets (images, fonts) to `assets/`; use `res/drawable` for vector drawables.

## Dashboard

- `dashboard/` is a sibling of `app/`, not inside it. It is a standalone static site.
- `dashboard/rides.json` is the data feed; it is not checked in (generated at runtime or demo time).
- No build tools, no `node_modules`, no `package.json` in `dashboard/`.

## What Goes Where — Decision Guide

| I need to… | File to edit |
|---|---|
| Add a UI string | `res/values/strings.xml` |
| Add a colour | `res/values/colors.xml` |
| Add a screen | New `Fragment` in `fragment/`, new layout XML in `res/layout/` |
| Add a network call | `GeoService.java` |
| Change fare bands | `FareCalculator.java` |
| Change how rides are stored | `RideStore.java` |
| Change the map behaviour | `assets/map.html` and/or `MapBridge.java` |
| Change voice behaviour | `VoiceService.java` |
| Update the dashboard | `dashboard/index.html` |
