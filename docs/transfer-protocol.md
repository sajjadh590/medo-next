# Desktop Phone-Transfer Protocol (`docs/transfer-protocol.md`)

## 1. Overview
The desktop `medo share` server (`medo/share.py` and `scripts/dev/fake_share_server.py`) runs a read-only local-network HTTP server on the user's PC so the phone can download the `.medo` v2 question bank bundle over Wi-Fi/hotspot without internet access.

- **Default Port**: `8765` (configurable via CLI flag; ephemeral port supported in tests).
- **Authentication**: None required by `medo share` on `master` (no token or PIN required; optional `?token=` / `PIN` parameter is tolerated by the Android client if a custom wrapper provides one).
- **Range Requests / Resume**: Not supported by the current desktop `medo share` handler (`Accept-Ranges` is not advertised; full stream download is used for `.medo` v2 bundles, with fallback Range support only if a v3 server advertises `206 Partial Content`).
- **CORS Headers**: None sent by the desktop server (not needed by native Android `HttpURLConnection`).
- **v3 Pack Server Status on `master`**: `medo/packs.py` and v3 delta routes do not exist on `master`; the desktop server on `master` serves the `.medo` v2 ZIP bundle.

---

## 2. QR Payload Format
The QR code displayed by `medo share` encodes a plain HTTP URL pointing to the desktop machine's private LAN IPv4 address:

```text
http://<LAN_IP>:8765/
```
or direct bundle path:
```text
http://<LAN_IP>:8765/bank.medo
```
- Only RFC1918 private LAN addresses (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`) and `localhost` / `127.0.0.1` are accepted by the Android client.

---

## 3. HTTP Endpoints

| Method | Path | Content-Type | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | `text/html; charset=utf-8` or `application/zip` | Landing page with links to download the `.medo` v2 bundle (and APK if present), or direct bundle stream |
| `GET` | `/bank.medo` (or `/*.medo`) | `application/zip` / `application/octet-stream` | Streams the `.medo` v2 ZIP archive containing `manifest.json`, `categories.json` / `bank.json`, `chunks/*.json`, and optional media |
| `GET` | `/apk` (or `/medo.apk`) | `application/vnd.android.package-archive` | Optional APK download route; returns `404 Not Found` when no APK was passed to the server |

---

## 4. Step-by-Step Phone Behavior
1. **Scan or Enter Address**: Decode the QR code (`http://<LAN_IP>:8765/...`) via CameraX + ZXing, gallery image picker, clipboard paste, or manual `IP:port` entry.
2. **Validate Private LAN Host**: Verify via `MedoQrAndLanEngine.validateLanTransferUrl` that the host is in `10/8`, `172.16/12`, `192.168/16`, or `localhost`. Reject all public internet hosts immediately.
3. **Bind Process to Wi-Fi**: Call `ConnectivityManager.bindProcessToNetwork(wifiNetwork)` so Android does not route private LAN traffic over mobile data when the Wi-Fi router has no internet uplink.
4. **Download `.medo` v2 Bundle**:
   - Connect via `MedoQrAndLanEngine.downloadBankFromLanServer(validatedUrl)` with `connectTimeout = 8000ms`, `readTimeout = 30000ms`.
   - If the scanned URL points directly to a `.medo`/ZIP stream (magic bytes `PK\x03\x04` or JSON), read it directly; if it returns the HTML landing page (`GET /`), follow `/bank.medo` (or the `.medo` href in the page).
5. **Atomic Validation & SQLite Import**:
   - Parse and validate the `.medo` archive in memory/temp storage (`SyntheticBankSeedAndImporter.parseMedoV2ZipBytes`).
   - If corrupt or truncated, abort before modifying Room SQLite so the existing bank and user progress remain untouched.
   - Commit categories, questions, cross-references, and Cloze cards in a single `@Transaction` (`dao.atomicImportBankChunk`) while preserving all `user_progress` rows.
