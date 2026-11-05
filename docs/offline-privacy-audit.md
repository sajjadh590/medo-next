# Medo Next — Offline & Privacy Security Audit (`docs/offline-privacy-audit.md`)

## 1. Executive Summary
**Medo Next** operates **100% offline**. It contains:
- **Zero** cloud AI / LLM client libraries or API keys.
- **Zero** analytics, telemetry, crash-reporting, or advertising SDKs.
- **Zero** Google Play Services dependencies (`play-services-*` is absent from the dependency graph).
- **Zero** remote font, image, or CDN requests (all 6 Persian and Latin fonts are bundled locally in `app/src/main/res/font/`).
- **Strict Private-LAN-Only Networking**: The `INTERNET` permission is used exclusively by `MedoQrAndLanEngine.openValidatedLanConnection()` to download question banks from the user's own desktop PC over a local Wi-Fi/hotspot network (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `localhost`).

---

## 2. Manifest Permissions Audit (`app/src/main/AndroidManifest.xml`)

| Permission | Classification | Exact Purpose | Runtime Guard |
| :--- | :--- | :--- | :--- |
| `android.permission.CAMERA` | Dangerous (Runtime) | Scanning the local LAN QR code displayed by `medo share` on the user's PC | Requested only when opening `QrScannerModalScreen`; camera is immediately unbound (`cameraProvider.unbindAll()`) when the QR screen closes or freezes |
| `android.permission.INTERNET` | Normal (Install-time) | Downloading `.medo` question banks from the user's desktop PC over local Wi-Fi (`http://192.168.x.x:8765`) | Guarded by `MedoQrAndLanEngine.validateLanTransferUrl()` which rejects any non-RFC1918 / non-localhost host |
| `android.permission.ACCESS_NETWORK_STATE` | Normal (Install-time) | Binding the process to the active `TRANSPORT_WIFI` network (`ConnectivityManager.bindProcessToNetwork`) so mobile data does not hijack local LAN routing | Used only in `MedoQrAndLanEngine.bindProcessToWifiIfAvailable()` |
| `android.permission.VIBRATE` | Normal (Install-time) | Short 70ms haptic confirmation when a QR code is detected | Used only in `QrScannerScreen.kt` |

*Note*: `<uses-feature android:name="android.hardware.camera" android:required="false" />` is declared so devices without a back camera can still install the app and use manual IP entry, clipboard paste, or gallery QR image picking (`ActivityResultContracts.PickVisualMedia` / `ActivityResultContracts.OpenDocument`).

---

## 3. Cleartext HTTP & Network Call-Site Audit (Item 6)

### Why Cleartext HTTP is Enabled in `network_security_config.xml`
The desktop `medo share` server (`medo/share.py`) runs a local HTTP server on port `8765` without TLS certificates (`http://<LAN_IP>:8765`). Because Android's `<domain-config>` XML tag only matches exact hostname/IP strings and does not support CIDR subnets (`192.168.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`), `<base-config cleartextTrafficPermitted="true" />` and `android:usesCleartextTraffic="true"` are configured at the manifest level so any private LAN IP assigned by the user's router or phone hotspot can connect.

### Code-Level Private-LAN-Only Enforcement
Every network connection in the entire codebase is opened exclusively through `MedoQrAndLanEngine.openValidatedLanConnection(urlString)`:
1. Before calling `URL(url).openConnection()`, `openValidatedLanConnection` invokes `validateLanTransferUrl(urlString)`.
2. `isPrivateLanOrLocalhost(host)` parses the IPv4 octets and verifies that the target is strictly within:
   - `10.0.0.0/8` (`10.x.x.x`)
   - `172.16.0.0/12` (`172.16.x.x` – `172.31.x.x`)
   - `192.168.0.0/16` (`192.168.x.x`)
   - `127.0.0.0/8` / `localhost` / `.local`
3. Any public IP address (e.g., `8.8.8.8`, `172.32.0.1`) or external domain name (e.g., `example.com`, `medofast.ir`) is rejected with a `SecurityException` before any DNS lookup or TCP socket is created.
4. No other file in `app/src/main/` imports `java.net.HttpURLConnection`, `java.net.Socket`, `okhttp3`, `retrofit2`, or `android.webkit.WebView`.

---

## 4. Backup & Local Storage Privacy
- `android:allowBackup="false"` is set in `AndroidManifest.xml` so Android Auto Backup never uploads the user's SQLite database or study history to cloud storage.
- Daily automatic progress backups are written locally on-device to `Documents/MedoNext/` via Android 10+ `MediaStore.Files` (or SAF) and never leave the device unless explicitly shared by the user.
