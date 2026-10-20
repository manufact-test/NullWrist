# Red Girl

Pixel-art watchface for Pebble Time 2 / emery based on the approved red-orange design.

## Dynamic data
- Time and date: native Pebble clock (28px LECO numerals with a second 1px-offset pass for extra weight)
- Watch battery: native Pebble battery service
- Heart rate: Pebble Health API when available
- Phone battery: PebbleKit JS bridge
- Weather: message key `2` is reserved and already rendered; wire any preferred provider into `src/pkjs/index.js`

## Artwork
- `resources/images/red-girl-base.png`: approved source artwork / native face background
- `resources/images/red-girl-menu-icon.png`: generated 25×25 Pebble **watch launcher** icon, bundled into the PBW with `menuIcon: true`
- `previews/red-girl-cover.png`: generated 200×228 face preview
- `previews/red-girl-cover-2x.png`: 400×456 version of the preview for phone/app-store use

Generate icons/previews before building (requires Python + Pillow):

```bash
cd watchfaces/red-girl
python3 -m pip install Pillow
python3 scripts/make_preview.py
pebble build
```

GitHub Actions regenerates the images, builds `Red-Girl-emery.pbw` and uploads the images alongside it.

**Pebble mobile app caveat:** A sideloaded PBW can include the 25×25 watch launcher icon, but the thumbnail in the **phone's Pebble app gallery** is controlled by the store/listing metadata, not by the PBW icon. Use the included `red-girl-cover-2x.png` when publishing/uploading the watchface to the relevant app store. Some phone clients display a generic placeholder until the watchface is listed there.
