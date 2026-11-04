# Map snapshots

The Photos step offers **Add map snapshot**: a real street-map image of the
incident location with a car marker and a 50 m accident-radius ring, stored
as ordinary evidence (encrypted, hashed, captioned, rendered in the PDF).
Snapshots count toward the 10-photograph limit.

## How it works

1. The app reads the current position once (`DeviceLocation`, platform GPS —
   no Play Services), reusing the existing location permission.
2. MapLibre Native (`org.maplibre.gl:android-sdk`, pinned in
   `gradle/libs.versions.toml`) renders a 1024 px snapshot at zoom 17 from
   the keyless OpenFreeMap Liberty style
   (`https://tiles.openfreemap.org/styles/liberty`). No API key, no account.
3. The app draws the overlay on the snapshot: translucent 50 m radius ring
   (sized through the snapshot's own projection), teal car-marker pin using
   the Material Symbols `directions_car` glyph (`ic_map_car.xml`), and
   top labels naming the radius, coordinates and capture time. Tile
   attribution stays visible along the bottom edge.
4. The PNG is imported through the normal evidence path with source
   "Map snapshot" and an auto-caption
   ("Map snapshot · lat, lon · accident radius 50 m · timestamp"), which
   the user can edit like any caption.

## Offline fallback

Any failure — no network, tile timeout (30 s), tile error — stores a
labelled **offline location diagram** instead (source "Location diagram
(map unavailable)"): a schematic grid with the car marker, a diagrammatic
radius ring, coordinates, GPS accuracy and timestamp, clearly marked
"Schematic — not to scale". Evidence capture never depends on
connectivity. Only a missing location fix blocks the action, with a
neutral error.

## Privacy and permissions

- `INTERNET` is used solely for map tile downloads on this explicit
  action. Tile requests reveal the approximate location to the
  OpenFreeMap public service; no incident details, photographs or
  identifiers are sent. The Privacy screen states this.
- The Data safety declaration must cover approximate-location
  transmission to the tile service (folded into the existing redo item
  in `docs/design/ROADMAP.md`).
- Attribution is a licence obligation, not decoration: map data
  © OpenStreetMap contributors (ODbL), tiles via © OpenMapTiles /
  OpenFreeMap. It is drawn on every real snapshot and credited in-app.
  Commercial use of the public instance is allowed; keep the style URL
  and attribution intact when upgrading.

## Cost and size notes

- OpenFreeMap's public instance is free with no key or published cap;
  each snapshot fetches a handful of vector tiles once — negligible.
- The MapLibre SDK adds native libraries (all ABIs in a debug APK);
  release AABs split per ABI. Re-check bundle size at release.
- This SDK is also the foundation for the planned driver-map use; the
  snapshotter shares its style and init with future interactive maps.

## Verification

- Unit: `location/LocationSnapshotTest` (radius math, captions, size limits).
- Instrumented: `MapSnapshotTest` renders the offline diagram without network.
- Device QA: with network, snapshot shows real streets with attribution;
  in airplane mode, the offline diagram is stored instead; both appear in
  the PDF with captions.
