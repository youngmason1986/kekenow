# KekeNow – Product Steering

## What This Product Is

KekeNow is a voice-first keke (tricycle taxi) ride-hailing app for Nigerian cities, beginning with Asaba, Delta State.  
It is built for the **Kiroween 2026 hackathon**, Frankenstein category — a lovable, working monster stitched together from open parts.

## Prior Art — ORide (Inspiration Only)

ORide was OPay's bike-hailing app in Lagos (launched 2019, shut down around 2020 after the Lagos okada/keke ban). George remembers it as the model for what a ride app should feel like. Lessons carried into KekeNow:

- **Simple booking** — a ride request should take seconds, not a form.
- **Fare shown upfront** — the passenger sees the price before committing.
- **Rider verification** — riders are known, named operators, not anonymous vehicles.

How KekeNow differs: **voice booking** (speak the destination, no typing at all), **keke not okada** (tricycle taxis, the Asaba workhorse), and **Asaba-first** (built for one Nigerian city, not Lagos scale). Nothing is copied from ORide — no code, branding, or assets. It is a reference point for the experience, not a source.

## Who Uses It

- **Passengers** — residents of Asaba who want to hail a keke quickly without typing, navigating menus, or creating an account. Many users have limited data, shared devices, and prefer spoken interaction over screens.
- **Riders** — keke operators who want to receive jobs on a simple screen with a voice alert. No smartphone expertise assumed.

## Core Value

1. **Voice-first** — a passenger speaks a destination; the app does the rest. No typing required.
2. **Zero friction** — no signup, no account, no payment setup. Open the app, speak, ride.
3. **Free to operate** — no API keys, no paid services, no cloud bills. Nominatim + OSRM + OpenStreetMap + Android built-ins only.
4. **Transparent pricing** — fare shown before the ride, based on real Asaba keke rates, with an honest disclaimer.

## What It Is Not

- Not a full marketplace (no driver ratings, payment processing, or surge pricing in scope).
- Not a navigation app (no turn-by-turn for riders; map is for context only).
- Not a data-collection platform (no PII, no analytics).

## Success Criteria for Hackathon

- A passenger can speak a destination in Asaba, see the route on a map, see the fare, and request a keke — in under 60 seconds from app open.
- A rider receives a voice alert, sees the job, and can accept it — all without an internet-dependent backend.
- The dashboard shows live ride state from a static file.
- The APK installs and runs on a stock Android 7.0 (API 24) device with no additional setup.

## Principles

- **Offline-resilient** — degrade gracefully when the network is poor or absent; never show a blank screen with no explanation.
- **Accessible** — TTS on every important action so a rider can keep their eyes on the road.
- **Minimal surface** — fewer screens, fewer taps, fewer decisions. Complexity is the enemy.
- **Respect Nominatim** — always send the required `User-Agent` header; never hammer the server. This is a demo; throttle calls.

## Rider Identity & Safety (added Nov 24, 2026 — George)

- First launch is a role choice: **Rider** or **Customer**. Sign-up
  follows the role — riders give the full profile, customers give
  name + phone only.
- A rider's identity (photo, name, phone, FRSC plate, keke code /
  union tag) is shown to the passenger once a ride is accepted, so the
  passenger can match the keke that arrives. Two numbers, not one:
  the FRSC plate (`ABC-123-XY`) and the tag painted on the keke
  (Delta `DT/CT/…`, Enugu `ENS/104/1/…`, Kano KNT series).
- **Honest boundary:** the app checks the SHAPE of a plate/tag only.
  There is no public government register of keke numbers, so the app
  never claims a number is verified. Say so in the UI.
