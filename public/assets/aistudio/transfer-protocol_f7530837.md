# Medo desktop → Android transfer protocol

Source of truth: `medo/share.py`, `medo/server.py` (`PhoneBridge` and the
`/api/phone/*` routes), `medo/web/app.js` (QR payload), and the existing tests
`tests/test_android_bundle.py` / `tests/test_phone_bridge.py`.

This document describes the code at commit `65beb7b` (branch `master`).
Nothing here is a proposed protocol: it is what the desktop serves today.

## 1. Current format status

- The current bank format is **`.medo` v2**: `FORMAT_NAME = "medo-bank"`,
  `FORMAT_VERSION = 2`, `FORMAT_NAME`/`version` are written into the bundle.
- **`medo/packs.py` does not exist on master.** `git cat-file -e
  HEAD:medo/packs.py` exits 128.
- **There is no v3 pack server and no pack route on master.** `medo/share.py`,
  `medo/server.py` and the transfer server expose no pack/manifest endpoint.
  A new Android client must use the v2 ZIP described below, not a v3 pack.
- A plain JSON bank from older builds is still *readable* by
  `medo.share.read_bundle`, but the current desktop server always serves the
  v2 ZIP (magic bytes `PK`).

## 2. Transport, port, QR and authentication

### Server and default port

The phone-transfer server is a separate, **read-only** server from the desktop
practice server.

- Default bind/port: `0.0.0.0:8765`.
- CLI: `medo share --port 8765 --bind 0.0.0.0 --db ...`.
- Desktop UI: `PhoneBridge(port=8765)`; it does **not** automatically choose a
  different port when 8765 is busy. The start action returns an error instead.
  The status response reports the actual bound port.
- USB alternative: `adb reverse tcp:8765 tcp:8765`, then use
  `http://127.0.0.1:8765`.
- The desktop practice server is unrelated: it listens on
  `127.0.0.1:8080` by default and must not be used by the phone.

### URLs and QR payload

`share_urls(port)` returns LAN URLs first and loopback last:

```text
http://<lan-ipv4>:<port>
...
http://127.0.0.1:<port>
```

LAN address ranking puts `192.168.x.x` first, then `10.x.x.x`, then other
addresses; Docker/WSL (`172.16/12`), CGNAT (`100/8`) and link-local
(`169.254/16`) addresses rank last. The first URL is the recommended one.

The QR contents are a **plain HTTP URL**, with **no token, PIN, query string,
path or custom scheme**:

- Bank QR: `http://<ip>:<port>` (normally the first LAN URL; loopback only if
  no LAN address is available). Scanning it opens `/`, the HTML install/import
  page. It is not a direct file URL.
- APK QR: `http://<ip>:<port>/medo.apk`.
- There is no `?token=`, no pairing code, no login and no per-session secret.
  Anyone who can reach the bound port can read the bank and APK.

The QR itself is generated in the browser by `qrcode` with error-correction
level **M**, 4-module cells and a 16-module margin, rendered as an SVG. The
desktop does not generate or sign QR content on the server.

### Security boundary

- The transfer server has **no authentication**, **no cookies**, **no CORS
  headers**, **no TLS** and **no write routes**. It answers only `GET` and
  `HEAD`.
- The desktop app's `/api/phone/*` routes are a different surface: they are
  loopback-only and the practice server rejects foreign `Host` headers. The
  phone must call the share server, not those `/api/phone/*` routes.
- Binding `0.0.0.0` makes the server reachable by the LAN, subject to the
  Windows firewall. Stop sharing when finished.

## 3. Share-server endpoints

The server uses `ThreadingHTTPServer`, HTTP/1.1 and
`Server: MedoShare/1.0`. Every response here has an explicit
`Content-Length`; there is no chunked encoding, gzip, ETag, Last-Modified,
`Accept-Ranges` or `Content-Range`.

| Path | Methods | Response |
|---|---|---|
| `/` or `/index.html` | GET, HEAD | HTML install/import page, `text/html; charset=utf-8` |
| `/medo.medo` | GET, HEAD | The v2 ZIP, `application/zip` |
| `/bank.json` | GET, HEAD | Same bytes as `/medo.medo` (legacy alias) |
| `/medo.json` | GET, HEAD | Same bytes as `/medo.medo` (legacy alias) |
| `/medo.apk` | GET, HEAD | APK bytes, `application/vnd.android.package-archive` |
| `/app.apk` | GET, HEAD | Same APK alias |
| `/health` | GET, HEAD | Exactly `{"ok":true}`, `application/json` |
| anything else | GET, HEAD | HTTP 404, exactly `{"ok":false}`, `application/json` |

All responses include `Cache-Control: no-store`. Bank and APK responses also
include:

```http
Content-Disposition: attachment; filename="medo.medo"
Content-Disposition: attachment; filename="medo.apk"
```

There is **no Range/resume support**. A request with
`Range: bytes=0-9` is ignored and the server returns the whole file with
`200 OK`; there is no `206`, `Accept-Ranges` or `Content-Range`. A dropped
download must be restarted from byte zero.

### Missing APK

If `dist/medo.apk` is absent, `/medo.apk` and `/app.apk` return:

```http
HTTP/1.1 404 Not Found
Content-Type: application/json
Content-Length: 65
```

```json
{"ok":false,"error":"build the APK first: bash android/build.sh"}
```

The desktop app can build it from `android/build.sh`; a phone client must not
pretend the APK exists.

### Timeouts and connection behavior

The share handler sets **no socket or request timeout**. HTTP/1.1 keep-alive is
used. A phone client should set its own connect/read timeout and should not
hold connections open indefinitely. Use one GET per file; there is no
heartbeat, progress endpoint or upload endpoint.

### Authentication / CORS

- No `Authorization` header is required or checked.
- No `Access-Control-Allow-Origin` header is emitted. A native Android HTTP
  client is unaffected; a browser page from another origin cannot read these
  responses.
- `Host` is not validated by this server. It is used only by the HTML page to
  print the URL.

## 4. The `.medo` v2 file

The `.medo` extension is misleading: the current file is a ZIP
(`application/zip`), not JSON. It contains:

```text
index.json
chunks/<root-category-id>.json
```

### `index.json`

Top-level keys:

```text
format, version, generated_at, stats, tree, questions, chunks
```

- `format`: `"medo-bank"`.
- `version`: `2`.
- `generated_at`: UTC ISO-8601 string.
- `stats`: `{total, with_answer, missing_answer, with_explanation,
  bookmarked, with_images}`.
- `tree`: recursive cleaned category/topic nodes. Node keys:
  `id, parent_id, kind, title, source_url, position, question_count,
  progress, partial, children`.
- `questions`: one light entry per question. Keys:
  `id, topic_id, tag_id, nodes, page, ordinal, stem_text, correct,
  answer_source, explanation_status, has_images, path, bookmarked, selected,
  options_count, has_explanation, chunk`.
- `chunks`: `[{id, title, count, file}]`; `file` is
  `"chunks/<id>.json"`. A question is assigned to the chunk of its top-level
  category. Questions with no matching root category use chunk id `0`.

The light index deliberately has **no lesson HTML**. Read the chunk entry for
the full question.

### Chunk files

Each chunk is:

```json
{
  "format": "medo-bank",
  "version": 2,
  "chunk": 1,
  "questions": [ ... ]
}
```

A full question has:

```text
id, topic_id, tag_id, nodes, source_url, page, ordinal, stem_html, stem_text,
options, correct, answer_source, votes, explanation_html, explanation_text,
explanation_status, has_images, path, bookmarked, selected
```

`options` is `[{"index": n, "text": "..."}]`. `nodes` is
`[{"topic_id": n, "tag_id": n}]`; `tag_id` may be `0`. `correct` may be
`null` when the site did not reveal an answer; such a question must not be
graded wrong. Empty lesson fields are valid.

ZIP entries are deflated. The phone must verify ZIP CRC/structure before
replacing its current bank; there is no separate checksum manifest. Embedded
images are **not** included by the share server's default export
(`include_images=False`). Lesson HTML may therefore reference
`assets/...` paths that the phone cannot resolve unless a separately built
bundle embedded them.

### Measured current artifact sizes

From the current working tree (not a protocol guarantee):

```text
dist/medo.medo            501,712 bytes
  index.json            1,354,740 uncompressed / 163,438 compressed
  chunks/1.json           490,072 uncompressed /  79,750 compressed
  chunks/2.json         1,658,997 uncompressed / 250,587 compressed
  chunks/6.json            38,672 uncompressed /   7,513 compressed
  index stats            877 questions, 3 chunks
dist/medo.apk             311,761 bytes
```

`Content-Length` is always the actual response body length; sizes change when
the database changes.

## 5. Desktop `/api/phone/*` routes (do not use from the phone)

These belong to the desktop UI at `http://127.0.0.1:8080`. They are guarded by
the loopback `Host` allowlist (`127.0.0.1`, `localhost`, `::1`, `[::1]`) and
are not reachable through the LAN share server.

- `GET /api/phone` → status JSON:
  `running, port, lan[], urls[], suggested_url, questions, started_at,
  bank{exists,bytes,mtime}, apk{exists,bytes,mtime}, building, build_ok,
  build_log, build_finished_at`.
- `POST /api/phone/start` → `{ok, message, phone}`; starts the share server.
- `POST /api/phone/stop` → `{ok, message, phone}`.
- `POST /api/phone/bundle` → `{ok, message, phone}`; accepts optional
  `{"include_archived": bool}`; writes `dist/medo.medo` and restarts sharing.
- `POST /api/phone/build-apk` → `{ok, message, phone}`; starts the APK build
  in the background.
- `GET /api/phone/apk` → raw APK attachment if present; if missing, HTTP
  **200** with `{"ok":false,"message":"..."}` (different from the share-server
  404 above).
- `GET /api/phone/bank` → `dist/medo.medo` attachment. Its current
  `Content-Type` is `application/json; charset=utf-8` even though the body is
  a ZIP; the share server's `/medo.medo` correctly uses `application/zip`.

## 6. What the phone must do

1. Obtain the base URL from the bank QR or the desktop URL list:
   `http://<host>:<port>`. Strip a trailing `/`. There is no token to add.
2. Optionally `GET /health` with a short timeout and require
   `{"ok":true}`. A failing health check means wrong host/port/firewall, not
   bad credentials.
3. `GET /medo.medo` (or `/bank.json`/`/medo.json`) with
   `Accept: application/zip`. Do not send Range; expect the full body and a
   `Content-Length`.
4. Write to a temporary `.part` file. Verify the ZIP opens, `index.json` has
   `format="medo-bank"` and `version=2`, every `chunks[].file` exists, and
   `stats.total` is numeric. Do not replace the live bank until all checks
   pass. On a dropped connection, discard the part and retry from byte zero.
5. Use the light `index.questions` for list/search/tree, then read the chunk
   named by `chunk` for lesson HTML. Merge all chunk questions after import.
6. For an APK update, `GET /medo.apk` (or scan the APK QR) and handle it as an
   Android package (`application/vnd.android.package-archive`); the file is
   not streamed and has no Range support. If the server returns 404, the
   desktop has no built APK.
7. For USB, run `adb reverse tcp:8765 tcp:8765` on the desktop and use
   `http://127.0.0.1:8765`.
8. Do not call `/api/phone/*`, `/api/*`, or the desktop practice server from
   the phone. Do not POST anything to the share server: only GET/HEAD exist.

Recommended client behavior, not enforced by the server: connect timeout
around 5–10 s, read timeout at least several minutes for the bank, one request
at a time, and a full retry after any interrupted transfer.

## 7. Synthetic test server

`scripts/dev/fake_share_server.py` serves a one-question synthetic v2 bank
through the **real** `medo.share._make_handler`, so its routes and headers are
the production transfer behavior. It never reads the real database.

```bat
cd /d E:\MedoOffline
.venv\Scripts\python scripts\dev\fake_share_server.py --bind 127.0.0.1 --port 8765
```

Useful options:

```text
--port 8765       default; 0 chooses an ephemeral port
--bind 0.0.0.0    default (LAN); use 127.0.0.1 for loopback/USB tests
--apk PATH        optional APK to serve at /medo.apk
```

The console prints only structure and numbers (`FAKE_BANK`, `BIND`,
`BASE_URL`, `BANK`, `APK`, `AUTH/CORS/RANGE`). The synthetic bank has one
question, one root category, one topic and one chunk. Missing `--apk` exercises
the real 404 response. This server is a fixture, not a production server and
not a source of real content.
