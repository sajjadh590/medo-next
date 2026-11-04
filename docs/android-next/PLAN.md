# Medo Next — Architecture & Implementation Plan (Stage 0)

## 1. Overview & Objectives
**Medo Next** is a complete, cloud-built replacement Android application for offline medical board exam preparation (`دستیاری پزشکی`, `پره انترنی`, `تالیفی`), spaced repetition (SM-2), exam simulation, rich clinical lesson reading, high-resolution mind-map viewing, incremental pack management, and print-ready A4 PDF export.

## 2. Formats & Data Schemas Learned
1. **`.medo` v2 Format**:
   - ZIP archive containing `manifest.json` (or `bank.json`), chunked JSON arrays of questions (`chunks/chunk_000.json`), category tree definitions (`categories.json`), and shared media blobs (`media/<sha256>`).
   - Questions are keyed by `siteQuestionId` (fallback to SHA-256 content hash of normalized stem + options) and mapped many-to-many to category nodes (`nodeIds`).
2. **`.medo` v3 Format (Content-Addressed & Incremental Packs)**:
   - Designed for 50,000+ questions and multi-gigabyte media sets.
   - `manifest.json` defines `bankVersion`, `packs` (top-level category/topic/subtopic + content level: `text`, `lessons`, `media`), `chunkHashes`, `questionRevisions`, and `tombstones`.
   - Delta planner computes only missing/changed chunks and missing media hashes, applying updates atomically while preserving user progress, notes, bookmarks, and SM-2 state.
3. **Progress & Legacy Migration**:
   - Progress is keyed by `siteQuestionId` so re-imports and delta updates never overwrite user history.
   - Legacy migration imports preferences, answered states, bookmarks, and theme settings from the previous app's SharedPreferences/SQLite store (`medo_prefs` / `medo_legacy_store`).

## 3. Design System & Accessibility
- **11 Clinical Glass Themes** (sourced from `design/themes.json`):
  1. `dark` (تیره)
  2. `amoled` (تاریک مطلق AMOLED)
  3. `light` (روشن)
  4. `sepia` (سپیا)
  5. `blue_grey` (خاکستری آبی)
  6. `midnight` (نیمه‌شب)
  7. `forest` (جنگل)
  8. `night_purple` (بنفش شب)
  9. `blue_paper` (کاغذ آبی)
  10. `mint` (نعنا)
  11. `sunset` (غروب)
- **WCAG Contrast Enforcement**:
  - Reading surfaces (question stems, options, lessons, explanations) use high surface alpha (`0.90–0.96`) so composited body text achieves **>= 4.5:1** contrast against both the darkest and lightest stops of every theme's procedural gradient.
  - Correct/wrong states always combine color with explicit `✓` and `✗` glyphs.
  - RTL-first layout with bundled `Vazirmatn` font, configurable Persian/Latin numerals, and font scale support up to `2.0x`.

## 4. Conflict Resolutions & Safety Priorities
Following `data safety > correctness > legibility > beauty > extra features`:
- **Backdrop Blur vs. Legibility**: Real RenderEffect blur is supported on API 31+ for chrome/headers, while reading cards enforce near-opaque composited surfaces with automated unit tests verifying worst-case WCAG contrast.
- **Tombstones in v3 Delta Sync**: Default delta update mode is non-destructive (`keep local + mark removedUpstream = true`); destructive deletion only occurs when explicit `mirror` mode is toggled.
- **Exam PDF Export**: Enforces strict `no-leak` separation so Exam mode never accesses or renders `correctIndex`, `userChoice`, or explanations on question pages.
