# Third-Party Libraries & Licences (`docs/third-party.md`)

All dependencies used in **Medo Next** are open-source, free of proprietary Google Play Services or ML Kit requirements, and free of analytics or advertising SDKs.

| Component / Library | Coordinates / Source | Licence | Purpose |
| :--- | :--- | :--- | :--- |
| **AndroidX Core / Activity / Lifecycle** | `androidx.core:core-ktx`, `androidx.activity:activity-compose`, `androidx.lifecycle:*` | Apache-2.0 | Core Android & Compose lifecycle integration |
| **Jetpack Compose & Material 3** | `androidx.compose.*`, `androidx.compose.material3:material3` | Apache-2.0 | UI toolkit and base accessible components |
| **AndroidX CameraX** | `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` (1.5.0) | Apache-2.0 | Hardware camera preview, torch, autofocus, and YUV frame analysis |
| **ZXing Core** | `com.google.zxing:core:3.5.3` | Apache-2.0 | Pure offline QR code decoding (`PlanarYUVLuminanceSource`, `HybridBinarizer`) |
| **AndroidX Room (SQLite)** | `androidx.room:room-runtime`, `androidx.room:room-ktx` (2.7.0) | Apache-2.0 | Local offline relational database & normalized search |
| **Kotlin Coroutines** | `org.jetbrains.kotlinx:kotlinx-coroutines-android` (1.10.2) | Apache-2.0 | Asynchronous background database & import tasks |
| **OkHttp** | `com.squareup.okhttp3:okhttp` (4.10.0) | Apache-2.0 | Local LAN HTTP pack transfer |
| **Bundled Persian & English Fonts** | `vazirmatn.ttf`, `markazi_text.ttf`, `amiri.ttf`, `inter.ttf`, `roboto.ttf`, `jetbrains_mono.ttf` | SIL OFL-1.1 / Apache-2.0 | Offline Persian (`Vazirmatn`, `B Nazanin`/`Markazi`, `Amiri`) and English (`Inter`, `Roboto`, `JetBrains Mono`) typography |
| **JUnit 4 & Robolectric** | `junit:junit`, `org.robolectric:robolectric` (4.16.1) | EPL-1.0 / MIT | Local JVM unit & Compose UI verification tests |
