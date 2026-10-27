# Structure — Camera

**Repo:** `Camera/`
**Status:** Struktur aktual per 24 Oktober 2026 (WP-03, WP-08; stack D-01, JDK D-03)
**Dokumen terkait:** `Architecture.md` §2, `Styleguide.md` §2, `Rules.md` §2

## 1. Pohon direktori

```text
Camera/
├── AGENTS.md
├── README.md                   gambaran proyek, build, status (WP-66)
├── .gitignore
├── .work/                      spesifikasi repo (dokumen, bukan kode)
│   ├── PRD.md  Architecture.md  Schema.md  Styleguide.md
│   ├── PRD-Breakdown.md        peta requirement → implementasi (WP-66)
│   ├── Design.md  OperationScreen.xml  Rules.md  Structure.md
│   ├── spec-machine.md          parameter konfigurasi & kalibrasi (WP-05)
│   └── assets/OperationMockup-v1.png
└── android/                    proyek Gradle (satu modul: app)
    ├── settings.gradle.kts     (WP-07)
    ├── build.gradle.kts        (WP-07)
    ├── gradle.properties       (WP-07)
    ├── gradlew, gradlew.bat    (WP-07)
    ├── gradle/wrapper/         (WP-08)
    └── app/
        ├── build.gradle.kts    (WP-08)
        └── src/
            ├── main/
            │   ├── AndroidManifest.xml   (WP-08)
            │   └── java/id/pemilah/      (lihat bagian 2)
            └── test/java/id/pemilah/     unit test JVM
```

Rencana, temuan, keputusan, dan bukti tetap di `../.work/` root sesuai
`AGENTS.md` §5; tidak disalin ke repo ini.

## 2. Paket dan kepemilikan berkas

Arah dependensi: `ui` → `domain` → `data` → `platform`
(`Architecture.md` §2). Berkas baru wajib masuk paket sesuai lapisannya.

| Paket | Isi | Komponen `Architecture.md` §3 | WP |
|---|---|---|---|
| `id.pemilah` | `MainActivity`, titik masuk aplikasi | — | WP-08 |
| `id.pemilah.ui` | Compose screen, navigasi, overlay, `OperationUiState` | — | WP-09, 12, 13, 16, 20, 23, 35, WP-57 |
| `id.pemilah.domain.target` | `TargetSelector` | `TargetSelector` | WP-30, 31 |
| `id.pemilah.domain.waste` | `WasteClass` (tujuh kelas resmi) | — | WP-50 |
| `id.pemilah.domain.detection` | `Detection`, `Box` | `Detector` (keluaran) | WP-24, WP-54 |
| `id.pemilah.domain.tracking` | `Tracker`, `TrackedObject` | `Tracker` | WP-24, WP-54 |
| `id.pemilah.domain.config` | `BinMapping`, `BinId` | `BinMapping` | WP-13, WP-50 |
| `id.pemilah.domain.operation` | `OperationGuard`, `StartDecision`, `MachineState` | `OperationGuard` | WP-32, 33, WP-51 |
| `id.pemilah.domain.calibration` | `CalibrationValidity` | `CalibrationValidity` | WP-19 |
| `id.pemilah.config` | `MachineConfig` (model + validasi) | — | WP-10 |
| `id.pemilah.storage` | `ConfigStore`, `ConfigCodec`, `FileConfigStorage` | `ProfileRepository` (sebagian) | WP-11 |
| `id.pemilah.model` | `ProfileValidator`, `ModelProfile`, `ProfileImporter`, `ModelSession` | `ProfileValidator`, `ProfileRepository` | WP-14, 15, 16, WP-53 |
| `id.pemilah.calibration` | `CameraCalibration` | `CameraCalibration` | WP-17, 18 |
| `id.pemilah.camera` | `CameraSource`, `FramePipeline` | `CameraSource`, `FramePipeline` | WP-21 |
| `id.pemilah.inference` | `Detector`, `OutputDecoder` | `Detector`, `OutputDecoder` | WP-22 |
| `id.pemilah.area` | `WorkAreaFilter` | `WorkAreaFilter` | WP-17, 23 |
| `id.pemilah.protocol` | `CommandCodec` | `CommandCodec` | WP-25, 27 |
| `id.pemilah.link` | `ControllerLink` (USB) | `ControllerLink` | WP-26, 28 |
| `id.pemilah.usb` | `SerialChannel`, `ControllerConnection`, `UsbCdcChannelOpener` | `ControllerLink` (transport) | WP-26, WP-55, WP-56 |
| `id.pemilah.telemetry` | `TelemetryQueue`, `TelemetryEvent`, `CyclePayloadFactory` | `TelemetryQueue` | WP-47, WP-52 |

Nama berkas mengikuti deklarasi utama (`Styleguide.md` §1). Test berpasangan
`X.kt` → `XTest.kt` pada `src/test/java/id/pemilah/...` dengan paket sama.

Keadaan per 27 Oktober 2026: paket yang **belum** memiliki berkas adalah
`domain.target`, `domain.tracking` (`Tracker` berada di `domain.detection`),
`domain.calibration` (logika kalibrasi di `id.pemilah.calibration`), `area`,
`inference`, `protocol`, dan `link`. Peta requirement → berkas aktual ada di
`.work/PRD-Breakdown.md`.

## 3. Perintah build dan pemeriksaan

Dijalankan dari `Camera/android/` (`AGENTS.md` §11):

| Gerbang | Perintah | Catatan |
|---|---|---|
| Build | `./gradlew assembleDebug` | wajib untuk perubahan kode/konfigurasi build |
| Unit test | `./gradlew testDebugUnitTest` | wajib untuk perubahan logika domain |
| Lint | `./gradlew lintDebug` | dipilih menurut dampak |
| Perangkat | `./gradlew connectedDebugAndroidTest` | hanya bila perangkat tersedia |

Prasyarat lingkungan (dicatat di `../.work/evidence/device-requirements.md`):

- `android/local.properties` berisi `sdk.dir=/home/mrksvt/Expo` (tidak di-commit).
- Build memakai `JAVA_HOME=/home/mrksvt/android-studio/jbr` (JBR 21.0.8, D-03).
- Mirror Central `maven-central.storage-download.googleapis.com` dipakai karena
  `repo1.maven.org` mengembalikan HTTP 403.
- Seluruh teks UI berasal dari `res/values/strings.xml` (D-03).

## 4. Strategi test

1. Logika domain diuji sebagai unit JVM murni tanpa Android framework; kelas
   domain tidak boleh bergantung pada `android.*`.
2. Kontrak pesan dan antrean telemetri diuji dengan simulator/fake, bukan
   perangkat (batas `AGENTS.md` §11).
3. Test tidak menyalin implementasi; asersi pada perilaku teramati
   (`Styleguide.md` §6).
4. Nilai ambang pada test berasal dari fixture bernama, bukan angka telanjang.
5. Bukti perintah disimpan di `../.work/evidence/` dan dirujuk pada Log WP.

## 5. Konvensi repo

- Setiap WP menyentuh maksimal lima berkas (`AGENTS.md` §9).
- Setiap perubahan di-commit lokal (`AGENTS.md` §13, D-02); tidak ada `push`.
- Berkas hasil build (`android/build/`, `android/app/build/`,
  `android/.gradle/`, `android/local.properties`) tidak di-commit; daftarnya di
  `.gitignore`.

## Komponen overlay shutter (27 Oktober 2026)
- ui/CameraShutterControls.kt: kontrol presentasi state-driven, callback lama, animasi dan semantics.
- ui/CameraOverlaySurface.kt: tint/blur latar visual tanpa blur teks; konstanta kontras.
- src/debug/java/id/pemilah/ui/ShutterVerificationActivity.kt: fixture UI terkontrol; tidak ada pada release/aplikasi utama.
- src/test/java/id/pemilah/ui/CameraOverlaySurfaceTest.kt: bukti kontras tint kasus terburuk.
