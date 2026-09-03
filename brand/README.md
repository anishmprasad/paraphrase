# Paraphrase brand kit

Vector sources here, rendered PNGs in `exports/`. Re-render everything with:

```bash
./render.sh
```

## Palette

| Token | Light | Dark | Used for |
|---|---|---|---|
| Brand | `#4F46E5` | `#A5B4FC` | primary UI, links, buttons |
| Gradient | `#6366F1` → `#7C5CF5` → `#9333EA` | same | icon tile, banners |
| Ink | `#1E1B4B` | `#E0E7FF` | wordmark, headings |
| Surface | `#FBFAFF` | `#0B0B12` | page background |
| Accent | `#0EA5E9` | `#7DD3FC` | secondary highlights |

The mark is two lines of text inside a rewrite cycle: the arcs say "rewritten",
the lines say "text". It is stroked, never filled, with round caps — at small
sizes the round caps are what keep it from turning to mush.

## Files

| Source | What it is |
|---|---|
| `mark.svg` | glyph alone, `stroke="currentColor"` so it takes the colour of its context |
| `icon.svg` | the app icon: glyph on the gradient, full-bleed square |
| `icon-rounded.svg` | same with rounded corners, for surfaces that won't mask it |
| `lockup-horizontal.svg` | mark + wordmark, dark text for light backgrounds |
| `lockup-horizontal-light.svg` | the same in white, for dark backgrounds |
| `banner-1024x500.svg` | Play feature graphic |
| `social-1280x640.svg` | GitHub/OG social preview |

The wordmark uses live `<text>` with a system font stack, so it renders with
whatever sans-serif the viewer has. That is fine for docs and stores, where the
PNG exports are what actually ship. If this ever becomes a real logo, convert
the text to outlines so it can't reflow.

## Where each file goes

| File | Size | Destination |
|---|---|---|
| `exports/icon-512.png` | 512×512 | Play Console → Store listing → **App icon** |
| `exports/feature-1024x500.png` | 1024×500 | Play Console → Store listing → **Feature graphic** |
| `../play/screenshots/*.png` | 1080×2400 | Play Console → Store listing → **Phone screenshots** (min 2). Use `9x16/` if the tall originals are rejected. |
| `exports/social-1280x640.png` | 1280×640 | GitHub → repo **Settings → General → Social preview → Upload** (this one can't be done from the CLI) |
| `exports/lockup-1280.png` | 1280×320 | README header, slides, anywhere the name is needed |
| `exports/icon-1024.png` | 1024×1024 | spare master for any store or press use |
| `app/src/main/res/mipmap-anydpi-v26/` | vector | already in the app — the adaptive icon, including the monochrome layer for Android 13+ themed icons |

Play also offers, all optional: a promo video (a YouTube URL, not a file),
tablet screenshots (only if you list tablet support), and a TV banner (only for
Android TV). None apply here.
