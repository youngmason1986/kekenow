# KekeNow Dashboard

Static HTML5 live-ride viewer. No build step, no dependencies.

## Running locally

```bash
cd dashboard/
python3 -m http.server 8080
```

Then open `http://localhost:8080` in your browser.

## rides.json format

The dashboard reads `rides.json` from the same directory.  
Create this file by aggregating the JSON files written by the app to `{filesDir}/rides/`.

**Expected format** — either a plain array:

```json
[
  {
    "id": "uuid-v4",
    "createdAt": "2026-10-31T14:22:00Z",
    "passengerId": "sha256hex...",
    "status": "pending | accepted | completed | declined",
    "origin": { "lat": 6.1944, "lon": 6.7357 },
    "destination": { "lat": 6.2010, "lon": 6.7421, "name": "Asaba Main Market" },
    "distanceMetres": 1234,
    "durationSeconds": 420,
    "fareMin": 150,
    "fareMax": 200,
    "routeGeoJson": "",
    "isFallback": false
  }
]
```

…or a wrapper object:

```json
{ "rides": [ ... ] }
```

## Demo rides.json (paste for testing)

```json
[
  {
    "id": "demo-001",
    "createdAt": "2026-10-31T10:00:00Z",
    "passengerId": "abc123",
    "status": "completed",
    "origin": { "lat": 6.1944, "lon": 6.7357 },
    "destination": { "lat": 6.2010, "lon": 6.7421, "name": "Asaba Main Market" },
    "distanceMetres": 1200,
    "durationSeconds": 360,
    "fareMin": 150,
    "fareMax": 200,
    "routeGeoJson": "",
    "isFallback": false
  },
  {
    "id": "demo-002",
    "createdAt": "2026-10-31T11:30:00Z",
    "passengerId": "def456",
    "status": "pending",
    "origin": { "lat": 6.1950, "lon": 6.7370 },
    "destination": { "lat": 6.2200, "lon": 6.7500, "name": "Asaba Government House" },
    "distanceMetres": 3800,
    "durationSeconds": 720,
    "fareMin": 250,
    "fareMax": 350,
    "routeGeoJson": "",
    "isFallback": false
  }
]
```

## Notes

- `rides.json` is **not** committed to version control (it is runtime data).
- The dashboard auto-refreshes every 10 seconds.
- New rows flash yellow for 5 seconds on each refresh cycle.
- Status badge colours: Pending = orange, Accepted = blue, Completed = green, Declined = grey.
