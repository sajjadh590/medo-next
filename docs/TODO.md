# Medo Next — Stage Status (`docs/TODO.md`)

- [x] **S0**: Study & `docs/android-next/PLAN.md`
- [x] **S1**: Gradle configuration, custom adaptive icon, bundled Persian (`Vazirmatn`, `Markazi Text` / `B Nazanin` style, `Amiri`) & English (`Inter`, `Roboto`, `JetBrains Mono`) fonts, 11 clinical themes (`design/themes.json`), glass design system, WCAG contrast math
- [x] **S2**: Room SQLite data layer, `.medo` v2 ZIP round-trip importer, normalized Persian search (`PersianTextUtils`), legacy progress migration, privacy-safe diagnostics logger
- [x] **S3**: Home category tree («بانک من» / «همه دسته‌ها»), node filters, Practice screen, Lesson Reader, deterministic lesson source extraction (`DeterministicLessonExtractor`), and sub-sampled Mind-Map viewer
- [x] **S4**: A4 PDF Export engine (`MedoPdfEngine`: Exam vs Study mode, 5-pair key table, compressed missing explanations, compact lab blocks, `BidiGuard`, exam no-leak check, and generated sample PDFs `docs/samples/sample-exam-mode.pdf` & `docs/samples/sample-study-mode.pdf`)
- [x] **S5**: Format v3 specification (`docs/medo-format-v3.md`), delta update planner (`PackDeltaPlanner`), Pack Manager UI, and additive `medo/packs.py` module
- [x] **S6**: Progress & notes JSON export/import (`docs/progress-format.md`), daily automatic backup to `Documents/MedoNext/`, SM-2 scheduler, runtime Cloze flashcard generator, Exam simulator with 1/3 negative marking, Analytics & trap-question filter
- [x] **S7**: Settings live preview card, 6 offline fonts, accessibility semantics, large font scale support (`2.0x`), Persian/Latin digits toggle
- [x] **S8**: Local signing scripts (`tools/sign_local.sh`, `tools/sign_local.bat`), documentation (`docs/offline-privacy-audit.md`, `docs/third-party.md`), and full automated JUnit + Robolectric test suite
- [x] **Follow-up A (QR + LAN)**: CameraX + ZXing (`com.google.zxing:core:3.5.3`) multi-pass scanner (`MedoQrAndLanEngine`, `QrScannerModalScreen`), private LAN validator, Wi-Fi network binding (`bindProcessToNetwork`), fallbacks (manual IP:port+PIN, gallery picker, clipboard paste), and Persian error diagnostics
- [x] **Follow-up B (Medical Data Safety)**: Removed model-written drug safety cards; added `DeterministicLessonExtractor` citing verbatim imported lesson text; marked synthetic demo items `[نمونه آزمایشی]` and excluded them from release builds (`BuildConfig.DEBUG`)
- [x] **Follow-up C–E (Audit, Benchmarks, Samples & Release)**: Offline/privacy audit (`docs/offline-privacy-audit.md`), third-party license table (`docs/third-party.md`), 20,000-question benchmark (`genMs=51`, `searchMs=8`, `usedMemMb=125`), and sample PDFs in `docs/samples/`
