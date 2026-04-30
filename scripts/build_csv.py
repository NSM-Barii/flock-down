#!/usr/bin/env python3
"""
Converts alpr_cameras.json (Overpass API dump) to the lean CSV
used by the Android app. Run from the repo root.
"""
import json
import csv
import os
import sys

SRC = "alpr_cameras.json"
DST = "app/src/main/res/raw/alpr_cameras.csv"

def is_flock(tags):
    mfr = tags.get("manufacturer", "").lower()
    op  = tags.get("operator", "").lower()
    desc = tags.get("description", "").lower()
    return "flock" in mfr or "flock" in op or "flock" in desc

if not os.path.exists(SRC):
    print(f"ERROR: {SRC} not found. Run the curl download first.")
    sys.exit(1)

with open(SRC) as f:
    data = json.load(f)

os.makedirs(os.path.dirname(DST), exist_ok=True)

total, flock_count = 0, 0
with open(DST, "w", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["id", "lat", "lon", "flock"])
    for node in data["elements"]:
        tags = node.get("tags", {})
        flag = 1 if is_flock(tags) else 0
        writer.writerow([node["id"], node["lat"], node["lon"], flag])
        total += 1
        flock_count += flag

size = os.path.getsize(DST) / 1024 / 1024
print(f"Done. {total} cameras ({flock_count} Flock) -> {DST} ({size:.2f} MB)")
