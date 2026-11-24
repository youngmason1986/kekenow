# KekeNow

Voice-first keke (tricycle taxi) ride-hailing for Nigerian cities, starting with
Asaba, Delta State. Built with Kiro for the **Kiroween 2026** hackathon,
Frankenstein category: an Android app + voice AI + maps/GPS + web dashboard
stitched into one working product.

## What it does

- **Passenger mode** — speak a destination (no typing), see the route on a
  Leaflet/OpenStreetMap map, see the fare before the ride, request a keke.
- **Rider mode** — keke operators see incoming jobs on a simple screen with a
  voice alert, and can accept them.
- **Dashboard** — `dashboard/index.html`, a static page showing live ride state.

## How it works (no keys, no signup, no paid services)

- Geocoding: Nominatim (OpenStreetMap)
- Routing: OSRM demo server
- Map: Leaflet + OpenStreetMap tiles in a WebView
- Voice: Android on-device SpeechRecognizer + TextToSpeech (en-NG)
- Fare: distance-based Asaba keke rate table (`FareCalculator`)

## Build

```sh
export ANDROID_HOME=$HOME/workspace/android-sdk
export JAVA_HOME=$HOME/workspace/jdk-17
export GRADLE_USER_HOME=$HOME/.gradle-kekenow
$HOME/workspace/gradle-8.9/bin/gradle assembleDebug --offline \
  -Djava.net.preferIPv4Stack=true \
  -Dorg.gradle.jvmargs="-Djava.net.preferIPv4Stack=true"
```

Debug APK signs with the permanent `~/workspace/debug.keystore`.

## Kiro usage

Spec and steering live in `.kiro/` — `specs/kekenow/` (requirements, design,
tasks) and `steering/` (product, tech, structure). The `/.kiro` directory is
part of the submission and is never gitignored.
