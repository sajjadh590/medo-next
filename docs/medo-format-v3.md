# Medo Format v3 Specification (`docs/medo-format-v3.md`)

## 1. Purpose
Format v3 enables incremental, content-addressed transfers and selective pack installation (by category and content level: `text`, `lessons`, `media`) for banks scaling to 50,000+ questions and multi-gigabyte media sets while preserving `.medo` v2 compatibility.

## 2. Directory / Archive Structure
```text
medo-v3-pack/
├── manifest.json
├── packs/
│   ├── pack_internal_text.json
│   ├── pack_internal_lessons.json
│   └── pack_internal_media.json
├── chunks/
│   ├── <sha256_chunk_1>.json.gz
│   └── <sha256_chunk_2>.json.gz
└── media/
    ├── <sha256_media_1>.webp
    └── <sha256_media_2>.png
```

## 3. `manifest.json` Schema
```json
{
  "formatVersion": 3,
  "bankVersion": "2026.11.01",
  "generatedAt": 1762250000000,
  "categories": [
    { "id": "cat_dastyari", "parentId": null, "name": "دستیاری پزشکی", "order": 1 }
  ],
  "packs": [
    {
      "id": "pack_dastyari_text",
      "categoryId": "cat_dastyari",
      "categoryPath": "دستیاری پزشکی",
      "level": "text",
      "questionCount": 200,
      "sizeBytes": 458200,
      "chunkHashes": ["a1b2c3d4..."],
      "mediaHashes": []
    }
  ],
  "questionRevisions": {
    "q_1001": "rev_9f8e7d",
    "q_1002": "rev_3c4b5a"
  },
  "tombstones": ["q_0999"]
}
```

## 4. Delta Update Rules
1. **Chunk Diffing**: The phone compares incoming `chunkHashes` against installed chunks and downloads only missing or modified chunk files.
2. **Media Deduplication**: Media files are stored by content SHA-256 in internal storage; shared mind maps are downloaded once and reference-counted across packs.
3. **Tombstone Handling**:
   - **Standard Mode (Default)**: Questions listed in `tombstones` are kept in SQLite with `removedUpstream = true` so user notes and SM-2 history are never lost.
   - **Mirror Mode**: Questions in `tombstones` are removed from node memberships while still retaining `UserProgress` rows keyed by `siteQuestionId`.
4. **Desktop Companion (`medo/packs.py`)**:
   - CLI: `python -m medo.packs build-packs --db <sqlite_path> --out <dir> [--categories ...] [--levels text,lessons,media]`
   - Server: `python -m medo.packs serve <dir> --port 8765`
