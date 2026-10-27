# GreenWaste Cam Aplikasi Android Pemilah Objek

Aplikasi Android untuk sistem pemilah objek berbasis kamera: membaca aliran
kamera, menjalankan inferensi ONNX lokal, melacak objek di atas conveyor,
memilih satu target, memetakan kelas ke bak, lalu memerintahkan ESP32-S3
melalui USB OTG.

Repo ini hanya mencakup **aplikasi Android**. Firmware (`Controller/`),
backend statistik (`Backend/`), dashboard (`Frontend/`), dan pelatihan model
berada di luar repo ini.

- Requirement: [`../PRD Sistem Pemilah Objek.md`](../PRD%20Sistem%20Pemilah%20Objek.md)
  dan [`.work/PRD.md`](.work/PRD.md)
- Peta implementasi per requirement: [`.work/PRD-Breakdown.md`](.work/PRD-Breakdown.md)
- Kontrak kerja agen dan aturan commit: [`AGENTS.md`](AGENTS.md)

## Status saat ini

Tahap 3 PRD (aplikasi kamera): **UI berjalan, logika domain siap, jalur
inferensi dan controller belum tersambung.**

| Bagian | Keadaan |
|---|---|
| Layar operasi landscape (rail 6 menu, header status, pratinjau kamera, panel target/robot/conveyor, ringkasan sesi, banner guard, kontrol bawah) | Ada dan berjalan di perangkat |
| Rail navigasi berikon + dapat diperkecil (232 dp ⇄ 72 dp) | Ada, diuji unit test dan diukur di perangkat |
| Pratinjau kamera (CameraX) | Berjalan |
| Inferensi ONNX, decoder deteksi, filter area kerja | **Belum** menunggu model tujuh kelas (B-02/B-17) dan geometri (B-03) |
| Pelacakan objek, pemetaan kelas→bak, guard start, invalidasi kalibrasi, antrean telemetri, transport USB | Logika ada + unit test; sambungan ke deteksi/controller belum |
| Layar Models, Bot, Settings, Kalibrasi, Logs | **Belum** menu sudah ada, isinya menyatakan "Belum dikonfigurasi" |
| Uji dengan ESP32-S3 nyata | **Belum** (MANUAL-VERIFY) |

Aplikasi tidak menampilkan angka atau status contoh. Nilai yang belum ada
ditulis "Belum ada data", "Belum dikonfigurasi", atau "Belum terintegrasi pada
tahap 3", bukan nol atau status siap palsu.

Status rinci 66 requirement (CAM, MOD, CON, ARM, COM, CAL, UI, CFG, OUT, NFR,
SC): [`.work/PRD-Breakdown.md`](.work/PRD-Breakdown.md) §12.

## Stack (keputusan D-01)

| Lapisan | Nilai |
|---|---|
| Bahasa | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 (BOM 2024.10.01) |
| Kamera | CameraX 1.4.0 |
| Inferensi | ONNX Runtime Android 1.19.2 (dependensi sudah ada, belum dipakai) |
| USB | usb-serial-for-android 3.8.1 (CDC-ACM ke ESP32-S3) |
| Penyimpanan | Berkas JSON via `ConfigStore` |
| Build | Gradle 8.11.1 (wrapper) + AGP 8.7.3, Kotlin DSL |
| SDK | minSdk 26, target/compileSdk 35, JDK 17 toolchain |

## Build dan pemeriksaan

Dijalankan dari `android/`:

```bash
export JAVA_HOME=/home/mrksvt/android-studio/jbr   # D-03: JBR 21, AGP 8.7 belum mendukung JDK 25
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest                # hanya bila perangkat tersedia
```

Prasyarat lokal:

- `android/local.properties` berisi `sdk.dir=<path Android SDK>` (tidak
  di-commit; lihat `.gitignore`).
- Repositori memakai `google()` + mirror Central
  `maven-central.storage-download.googleapis.com` dan JitPack terbatas untuk
  `com.github.mik3y` (D-04), karena `repo1.maven.org` mengembalikan HTTP 403
  dari jaringan pengembangan.

Gerbang terakhir (27 Oktober 2026): `clean assembleDebug testDebugUnitTest
lintDebug` → `BUILD SUCCESSFUL`, 143 unit test, lint 0 Error / 11 Warning.

## Struktur

```text
Camera/
├── AGENTS.md                 kontrak proses repo ini
├── README.md
├── .work/                    spesifikasi (dokumen, bukan kode)
│   ├── PRD.md  PRD-Breakdown.md  Architecture.md  Structure.md
│   ├── Schema.md  Design.md  OperationScreen.xml  Styleguide.md  Rules.md
│   ├── spec-machine.md       parameter konfigurasi & kalibrasi
│   └── assets/
└── android/
    └── app/src/
        ├── main/java/id/pemilah/
        │   ├── ui/           Compose, navigasi, overlay, state tampilan
        │   ├── domain/       tracking, pemetaan bak, guard operasi, kelas
        │   ├── model/        profil ONNX + validator
        │   ├── config/       konfigurasi mesin + validasi
        │   ├── calibration/  kalibrasi kamera + invalidasi
        │   ├── storage/      penyimpanan JSON
        │   ├── telemetry/    event + antrean lokal
        │   ├── usb/          kanal serial, CDC-ACM, status link
        │   └── camera/       pratinjau CameraX
        └── test/java/id/pemilah/   unit test JVM (12 kelas, 143 test)
```

Aturan arah dependensi: `ui` → `domain` → `data` → `platform`; tidak ada
dependensi balik.

## Aturan produk yang mengikat

1. Start hanya setelah kamera, profil, konfigurasi, pemetaan kelas aktif, dan
   kalibrasi lolos validasi.
2. Profil wajib menghasilkan lokasi objek; label gabungan `Kaleng`+`Besi`
   ditolak.
3. Paling banyak satu target aktif per siklus; target sama tidak dipilih ulang.
4. Objek tak dikenal, keyakinan rendah, atau terlewat dibiarkan bersirkulasi.
5. Backend tidak pernah menjadi syarat operasi.
6. Gerakan selesai bukan bukti objek terambil — tanpa verifikasi, hasil ditulis
   "belum terverifikasi".
7. Angka mesin (dimensi, kecepatan, jangkauan, ambang) hanya berasal dari
   konfigurasi, tidak pernah ditanam di kode.
8. Tombol "Hentikan" di aplikasi bukan emergency stop perangkat keras.

## Penghambat terbuka

B-01 (perangkat sasaran), B-02/B-17 (model tujuh kelas), B-03 (geometri),
B-04 (protokol controller), B-05 (gangguan), B-06 (ambang numerik), B-07
(kelompok & dua robot), B-08 (backend), B-14 (perhitungan lintasan), B-15/B-16
(definisi kelas & alokasi bak). Rincian: `../.work/FINDINGS.md`.

## Kontribusi

- Setiap perubahan wajib dibaca bersama `.work/` yang relevan.
- Satu work package menyentuh maksimal lima berkas; lifecycle
  `todo → wip → qc → done`.
- Pekerjaan dijalankan satu agen dalam satu sesi; tidak ada sub-agen.
- Semua perubahan di-commit, dengan pesan
  `<tipe>(<lingkup>): <ringkasan>` (lihat `AGENTS.md`).
