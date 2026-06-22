# flock-down

> Android car head unit app that maps Flock Safety ALPR cameras and alerts you in real time when you drive past one.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green)
![Cameras](https://img.shields.io/badge/ALPR%20cameras-110k-red)
![Offline](https://img.shields.io/badge/works-offline-blue)

---

## Seen in the wild

[Watch the original reel (2.5M views)](https://www.instagram.com/p/DZvbp4dSuSg/)

---

## Disclaimer

This project is **experimental** and provided for educational and privacy-awareness purposes only. It is not intended to facilitate illegal activity. Camera location data is sourced from OpenStreetMap and may be incomplete, inaccurate, or out of date. Use at your own risk.

This app does not record, transmit, or store any data outside of your device.

---

## Part of the NSM toolkit

| Tool | Description |
|------|-------------|
| [dooku](https://github.com/nsm-barii/dooku) | Portable wardriving rig — Raspberry Pi 5, 4× WiFi adapters, BLE, GPS, RTL-SDR in a hardened case |
| [flock-back](https://github.com/nsm-barii/flock-back) | Detects Flock Safety cameras via BLE/WiFi |
| **flock-down** | In-car map with real-time Flock camera proximity alerts |

---

## Features

- **110,924 ALPR cameras** mapped from OpenStreetMap — Flock Safety + all other brands
- **Full-screen red alert** when driving past a Flock camera
- **Two-tier beep system** — warning at ~1600ft, urgent alert at ~500ft
- **"Flock detected" voice alert** through car speakers
- **Live speed display** from GPS
- **Session + lifetime counter** — tracks how many Flock cameras you've passed
- **Camera log** — browse every Flock camera you've driven past with coordinates
- **Tap any camera dot** on the map to see its ID, coordinates, and open in Google Maps
- **Recenter button** — snaps the map back to your location
- **Offline tile caching** — tap CACHE while on WiFi to save map tiles for offline driving
- **100% offline camera data** — all camera coordinates bundled in the APK, no internet needed
- Landscape, always-on display built for car screens

---

## Install

1. Enable Unknown Sources on your head unit — Settings → Security → Unknown Sources → ON
2. Download [`releases/flock-down.apk`](releases/flock-down.apk) directly onto your head unit or transfer via USB
3. Open it to install

---

## How it works

When the app launches it loads 110k+ ALPR camera coordinates from a bundled CSV into memory. A foreground location service checks your GPS position every second against the camera list using haversine distance. When you enter the alert radius:

- **~1600ft** — short beep, distance shown in corner HUD
- **~500ft** — urgent beep + "Flock detected" voice alert + full-screen **FLOCK SAFETY** overlay

The map follows your location with red dots for Flock cameras and orange dots for other ALPRs. Each unique Flock camera you drive past gets counted and logged.

---

## Offline map tiles

The camera data is always available offline (bundled in the APK). For the actual map background (roads, buildings):

1. **While on WiFi** — open the app, navigate to your city or route, tap **CACHE**
2. The app downloads zoom levels 10–17 for the visible area to local storage
3. **While driving** — the map renders fully from the local cache, no internet needed

Or go to **Settings → Download State** to cache your entire state at highway level in one shot.

---

## Settings

Tap **SETTINGS** to:
- Show/hide session counter
- Show/hide lifetime counter
- Download your state map for offline use
- Reset lifetime count
- Clear camera log

---

## Contributing

Bug reports and pull requests are welcome. If something isn't working right or you want to add a feature:

- **Found a bug?** [Open an issue](https://github.com/nsm-barii/flock-down/issues)
- **Want to contribute?** Fork the repo, make your changes, and open a pull request

---

## Camera database

**Last updated: 2026-05-07 — 110,924 cameras (83,203 Flock Safety)**

The database auto-updates every Friday via GitHub Actions. You can also trigger it manually from the [Actions tab](https://github.com/nsm-barii/flock-down/actions).

### Update it yourself

```bash
# 1. Download fresh data from OpenStreetMap
curl -X POST "https://overpass.kumi.systems/api/interpreter" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode 'data=[out:json][timeout:300];node["surveillance:type"="ALPR"];out body;' \
  -o alpr_cameras.json

# 2. Rebuild the CSV
python3 scripts/build_csv.py

# 3. Rebuild the APK
./gradlew assembleDebug
```

Camera data comes from OpenStreetMap — if you know of a camera that's missing or mislabeled, [add it to OSM](https://openstreetmap.org) and it'll show up in the next update.

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
