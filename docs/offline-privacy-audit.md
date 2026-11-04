# Offline & Privacy Security Audit (`docs/offline-privacy-audit.md`)

## 1. AndroidManifest.xml Permissions Audit
| Permission | Type | Exact Purpose in Medo Next |
| :--- | :--- | :--- |
| `android.permission.CAMERA` | Runtime (`required="false"`) | Scanning local LAN QR codes displayed by the desktop `medo` companion (`QrScannerModalScreen`). Zero images are stored or transmitted. |
| `android.permission.INTERNET` | Install-time | Connecting strictly to private LAN IPv4 addresses (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`) and `localhost` for `.medo` v2/v3 pack transfer. |
| `android.permission.ACCESS_NETWORK_STATE` | Install-time | Binding the app process to the active `TRANSPORT_WIFI` network (`ConnectivityManager.bindProcessToNetwork`) so cellular data routes do not hijack local LAN transfers. |
| `android.permission.VIBRATE` | Install-time | Haptic confirmation pulse (70 ms) upon QR code lock and option interaction. |

* **Storage Permissions**: Zero (`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` are NOT requested). Gallery QR selection uses the zero-permission Android Photo Picker (`ActivityResultContracts.PickVisualMedia`), and file sharing uses `FileProvider`.
* **Backup Flag**: `android:allowBackup="false"` is enforced in `AndroidManifest.xml`; user progress is backed up via explicit JSON export and optional daily auto-export to `Documents/MedoNext/`.

## 2. Network Call Sites & Strict Private LAN Enforcement
1. **Network Security Configuration (`app/src/main/res/xml/network_security_config.xml`)**:
   - `<base-config cleartextTrafficPermitted="false" />` blocks arbitrary cleartext HTTP traffic.
   - Explicit domain exceptions are limited to local private addresses (`localhost`, `127.0.0.1`, `10.0.0.1`, `192.168.1.1`, `192.168.1.100`, `medo.local`).
2. **Application Validator (`MedoQrAndLanEngine.validateLanTransferUrl`)**:
   - Inspects every scanned QR URL, clipboard paste, and manual address entry.
   - Rejects any non-RFC1918 public IP or external domain before any socket connection can be attempted.

## 3. Embedded API Keys, AI Clients, Analytics & Telemetry Audit
- **AI / LLM Clients**: **None**. `firebase-ai`, `firebase-bom`, `firebase-firestore`, `firebase-auth`, `retrofit`, and `converter-moshi` are all commented out in `app/build.gradle.kts`.
- **Google Play Services / ML Kit**: **None**. QR scanning is powered 100% by local open-source `com.google.zxing:core:3.5.3` + AndroidX `CameraX`.
- **Analytics / Crash Reporting SDKs**: **None**. Crash traces are captured purely locally in SharedPreferences by `DiagnosticsLogger` and never leave the device unless the user taps «کپی گزارش».

## 4. Bundled Local Fonts & Assets (Zero CDN / Remote Requests)
All 6 Persian and English fonts are bundled statically as `.ttf` files inside `app/src/main/res/font/`:
- `vazirmatn.ttf` (Vazirmatn)
- `markazi_text.ttf` (B Nazanin / Traditional Persian Naskh style)
- `amiri.ttf` (Amiri Classical Naskh)
- `inter.ttf` (Inter)
- `roboto.ttf` (Roboto)
- `jetbrains_mono.ttf` (JetBrains Mono)

All backgrounds and mind-map previews are rendered procedurally via Jetpack Compose `Canvas` with zero external image URLs.
