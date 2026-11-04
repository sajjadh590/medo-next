# Medo Next — Cloud Build, Local Signing & User Guide (`docs/android-next/README.md`)

## 1. Architecture & Highlights
- **100% Offline Clinical Point-of-Care App**: Built with Kotlin, Jetpack Compose, Material 3, and Room SQLite.
- **11 Clinical Glass Themes**: Dark, AMOLED Black, Light, Sepia, Blue-Grey, Midnight («نیمه‌شب»), Forest («جنگل»), Night Purple («بنفش شب»), Blue Paper («کاغذ آبی»), Mint («نعنا»), and Sunset («غروب»), all verified by automated WCAG 2.1 contrast tests (`>= 4.5:1`).
- **Clinical Drug Safety Profiles**: Structured JSON drug cards with Pregnancy Category, Normal Dose, and GFR/CrCl Renal Adjustments.
- **Core Modules**:
  - **خانه (Home)**: Resume card («ادامه از همان‌جا»), SM-2 due counter, pack banner, global `< 300ms` normalized Persian search, and two-tab Category Tree («بانک من» / «همه دسته‌ها») with full-path disambiguation.
  - **تمرین (Practice)**: Immediate feedback or reveal-at-end, option vote percentages, trap badges, personal notes, full-screen Lesson Reader, and Tiled/Sub-sampled Mind-Map Viewer.
  - **آزمون (Exam Simulator)**: Configurable scope, time limit, and **1/3 negative marking** (`ExamScorer`).
  - **مرور (Spaced Repetition)**: SM-2 scheduler + runtime **Cloze flashcard generator** from bold/colored lesson phrases.
  - **خروجی PDF (A4 Print Engine)**: Exam mode (strict no-leak guarantee) and Study mode, compact multi-column lab values, 5-pair Answer Key table, compressed missing explanation ranges, and `BidiGuard`.

## 2. Signing Locally in Two Minutes
1. Download the build APK artifact to your PC.
2. Run:
   - **Windows**: `tools\sign_local.bat app-release-unsigned.apk medo-next-signed.apk`
   - **Linux / macOS**: `./tools/sign_local.sh app-release-unsigned.apk medo-next-signed.apk`
3. Install on your phone and open **تنظیمات > گزارش و عیب‌یابی** to copy the one-tap system & bank diagnostics report («کپی گزارش»).
