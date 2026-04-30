# flock-down

> Android car head unit app that maps Flock Safety ALPR cameras and alerts you in real time when you drive past one.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green)
![Cameras](https://img.shields.io/badge/ALPR%20cameras-101k-red)
![Offline](https://img.shields.io/badge/works-offline-blue)

---

## Part of the NSM flock toolkit

| Tool | Description |
|------|-------------|
| [flock-back](https://github.com/nsm-barii/flock-back) | Detects Flock cameras via BLE/WiFi |
| **flock-down** | In-car map with real-time proximity alerts |

---

## Features

- **101,085 ALPR cameras** mapped from OpenStreetMap — Flock Safety + all other brands
- **Full-screen red alert** when driving past a Flock camera
- **Two-tier beep system** — warning at 500ft, urgent at 150ft
- **Filter toggle** — FLOCK ONLY or ALL CAMERAS
- **Live speed display** from GPS
- **Session + lifetime counter** — tracks how many Flock cameras you've passed
- **Camera log** — browse every Flock camera you've driven past with coordinates
- **Tap any camera dot** on the map to see its ID, coordinates, and open in Google Maps
- **Recenter button** — snaps the map back to your location
- **Offline tile caching** — tap CACHE while on WiFi to save map tiles for offline driving
- **100% offline camera data** — all 101k camera coordinates bundled in the APK, no internet needed
- Landscape, always-on display built for car screens

---

## Install

1. Enable Unknown Sources on your head unit — Settings → Security → Unknown Sources → ON
2. Download [`releases/flock-down.apk`](releases/flock-down.apk) directly onto your head unit or transfer via USB
3. Open it to install

---

## How it works

When the app launches it loads 101k ALPR camera coordinates from a bundled CSV into memory. A foreground location service checks your GPS position every second against the camera list using haversine distance. When you enter the alert radius:

- **500ft** — short beep, distance shown in corner HUD
- **150ft** — urgent beep, full-screen **FLOCK SAFETY** overlay with distance

The map follows your location with red dots for Flock cameras and gray dots for other ALPRs. Each unique Flock camera you drive past gets counted and logged.

---

## Offline map tiles

The camera data is always available offline (bundled in the APK). For the actual map background (roads, buildings):

1. **While on WiFi** — open the app, navigate to your city or route, tap **CACHE**
2. The app downloads zoom levels 10–17 for the visible area to local storage
3. **While driving** — the map renders fully from the local cache, no internet needed

Zoom in to street level before caching to get full detail. You can cache multiple areas — tiles accumulate.

---

## Settings

Tap **SETTINGS** to toggle:
- Show/hide session counter
- Show/hide lifetime counter

---

## Refresh camera data

The bundled CSV was pulled from OpenStreetMap via the Overpass API. To update it:

```bash
# Download fresh data
curl -X POST "https://overpass-api.de/api/interpreter" \
  --data '[out:json][timeout:300];node["surveillance:type"="ALPR"];out body;' \
  -o alpr_cameras.json

# Rebuild the CSV
python3 scripts/build_csv.py
```

Then rebuild the APK.

---

## Build from source

```bash
# Requires Android Studio + Android SDK
git clone https://github.com/nsm-barii/flock-down
cd flock-down
./gradlew assembleDebug
# APK -> app/build/outputs/apk/debug/app-debug.apk
```

Camera data is already bundled — no extra steps needed.

---

## Requirements

- Android 8.0+ (API 26)
- Google Play Services
- Location permission (fine location)

---

## Data sources

- Camera locations: [OpenStreetMap](https://openstreetmap.org) via [Overpass API](https://overpass-api.de)
- Flock identification: `manufacturer=Flock Safety` OSM tag
- Camera database: [deflock.org](https://deflock.org)
