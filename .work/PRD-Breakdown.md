# PRD Breakdown — Camera

**Repo:** `Camera/`
**Status:** Peta implementasi per 27 Oktober 2026 (setelah WP-65)
**Sumber requirement:** `../PRD Sistem Pemilah Objek.md` (§5–§14) dan
`.work/PRD.md` §6–§8; ID memakai alias `AGENTS.md` §6.
**Fungsi dokumen:** menjawab satu pertanyaan — untuk setiap requirement, apa
yang sudah ada di kode, apa buktinya, dan apa yang masih menahan. Dokumen ini
**tidak** menggantikan PRD; ia memetakan requirement ke implementasi.

## 1. Cara membaca status

| Status | Arti |
|---|---|
| `selesai` | Kode ada, perilaku dijaga unit test, dan tidak bergantung keputusan terbuka untuk bagian yang diklaim |
| `sebagian` | Sebagian komponen atau sebagian jalur ada; sisa pekerjaan disebut di kolom keterangan |
| `belum` | Belum ada kode untuk requirement ini |
| `tertahan` | Tidak dapat dikerjakan sebelum penghambat ber-ID ditutup |

Gerbang terakhir yang dijalankan (27 Oktober 2026, `Camera/android/`):
`clean assembleDebug testDebugUnitTest lintDebug` → `BUILD SUCCESSFUL`,
**143 unit test**, lint **0 Error** / 11 Warning. Rincian bukti per WP ada di
`../.work/Plan_AndroidPemilah_23102026-124815.md`,
`../.work/Plan_NavigasiRail_24102026-125203.md`, dan `../.work/evidence/`.

Catatan kejujuran: sebagian besar WP berstatus `qc`, bukan `done`, karena
`Camera/AGENTS.md` §4 melarang sub-agen sehingga tidak ada QC independen.
Status di bawah adalah hasil pemeriksaan sendiri.

## 2. Kamera dan deteksi (CAM-01–06)

| ID | Kebutuhan ringkas (PRD §8.A) | Implementasi | Bukti | Status | Keterangan |
|---|---|---|---|---|---|
| CAM-01 | Pratinjau real time + overlay hasil analisis terakhir | `camera/CameraPreview.kt` (CameraX), `ui/OperationScreen.kt` `CameraStage` | Build + `uiautomator`: status **Kamera Aktif** pada perangkat `ZP2222SQLL` | sebagian | Pratinjau jalan; overlay belum menerima hasil analisis karena `Detector` belum ada (WP-21/22) |
| CAM-02 | Kelas, keyakinan, kotak per deteksi | `ui/DetectionOverlay.kt` (label kelas + keyakinan + penanda usang) | Kompilasi; overlay diuji secara visual = MANUAL-VERIFY | sebagian | Menggambar data yang ada; sumber data (decoder ONNX) belum ada |
| CAM-03 | Pembagian blok sesuai area terkalibrasi | `ui/DetectionOverlay.kt` (poligon area kerja + blok putus-putus + label blok) | — | sebagian | Gambar ada; geometri blok menunggu B-03 |
| CAM-04 | Menandai satu target aktif berbeda | `ui/DetectionOverlay.kt` (sudut tebal + label `TARGET`), `ui/OperationUiState.kt` `ActiveTargetUi` | `StartGuardProjectionTest` (16 test) menguji proyeksi state, bukan seleksi target | sebagian | `TargetSelector` (WP-30) belum ada |
| CAM-05 | Identitas objek bertahan antar frame | `domain/detection/Tracker.kt` | `TrackerTest` 12 test | selesai | Logika murni JVM; belum dijalankan di atas deteksi nyata |
| CAM-06 | Deteksi di luar area kerja tidak jadi kandidat | — | — | belum | `WorkAreaFilter` belum ada, tertahan B-03 |

## 3. Profil model (MOD-01–06)

| ID | Kebutuhan ringkas (§8.B) | Implementasi | Bukti | Status | Keterangan |
|---|---|---|---|---|---|
| MOD-01 | Impor profil ONNX | `model/ProfileJson.kt`, `model/ProfileRepository.kt`, `model/ProfileImport.kt`, `storage/ContentProfileReader.kt`, `ui/ModelsScreen.kt` | `ProfileJsonTest` 11, `ProfileRepositoryTest` 12, `ProfileImportTest` 10 test; 228 unit test total | sebagian | Impor **JSON** profil selesai dan diuji; impor berkas `.onnx` belum (WP-12, tertahan B-02/B-17). Pemilih dokumen pada perangkat belum diuji (WP-13) |
| MOD-02 | Validasi kompatibilitas, tolak dengan alasan | `model/ProfileValidator.kt` | `ProfileValidatorTest` 14 test, termasuk penolakan label gabungan `Kaleng`+`Besi` | selesai | Butir 1–6 `Architecture.md` §6 terjaga |
| MOD-03 | Pergantian profil hanya saat berhenti & target kosong | `model/ProfileSwitchGuard.kt`, `ui/ModelsSession.kt`, `ui/ModelsScreen.kt` | `ProfileSwitchGuardTest` 8, `ModelsSessionTest` 12 test | sebagian | Guard + aksi UI selesai; masukan status berjalan dari layar Operasi belum dibagikan sehingga penolakan "sedang berjalan" baru terbukti lewat test |
| MOD-04 | Ambang keyakinan per profil | `model/ProfileSettings.kt`, `ui/ModelsUiState.kt`, `ui/ModelsScreen.kt` | `ProfileSettingsTest` 11, `ModelsUiStateTest` 17, `ModelsSessionTest` 12 test | sebagian | Penyunting dan penyimpanan per profil selesai; nilai tetap terbaca setelah aplikasi ditutup baru diuji di repositori, bukan di perangkat (WP-13) |
| MOD-05 | Pemetaan kelas aktif ke bak | `domain/config/BinMapping.kt` | `BinMappingTest` 10 test (bak 1–3 kiri, 4–6 kanan, kelas tanpa bak memblokir) | selesai | Nilai alokasi kelas menunggu B-16 |
| MOD-06 | Aturan pengambilan kelompok per profil | `ModelProfile.groupingEnabled`, `model/ProfileSettings.kt`, `ui/ModelsScreen.kt` | `ProfileSettingsTest`, `ModelsUiStateTest` | sebagian | Sakelar tersimpan per profil dan diuji; perilaku pengelompokan tetap tertahan B-07 dan layar menyatakannya |

## 4. Conveyor (CON-01–05)

| ID | Kebutuhan ringkas (§8.C) | Implementasi | Bukti | Status | Keterangan |
|---|---|---|---|---|---|
| CON-01 | Mengatur kecepatan dari Android | `config/MachineConfig.kt` (validasi `speedMin`/`speedMax`), `ui/OperationScreen.kt` `ConveyorPanel` (input dinonaktifkan) | `MachineConfigTest` 13 test | belum | Tanpa `CommandCodec`/`ControllerLink`; tertahan B-04; nilai rentang menunggu B-06 |
| CON-02 | Menampilkan kecepatan aktual dari encoder | `ui/OperationScreen.kt` (`Aktual (encoder)`, teks `Belum ada data`, penanda usang) | — | sebagian | Hanya tampilan; telemetri encoder belum diterima |
| CON-03 | Perpindahan encoder → jarak | `MachineConfig` (`encoderTicksPerMeter`, tolak 0) | `MachineConfigTest` | belum | Konversi belum ada (WP-28) |
| CON-04 | Prediksi target mengikuti perubahan kecepatan | — | — | belum | WP-28; ambang usia pengamatan menunggu B-06 |
| CON-05 | Bersirkulasi tanpa berhenti untuk pengambilan | Teks aturan di panel Conveyor | — | belum | Tidak ada jalur perintah yang bisa membuktikan |

## 5. Armbot (ARM-01–06)

| ID | Kebutuhan ringkas (§8.D) | Implementasi | Bukti | Status | Keterangan |
|---|---|---|---|---|---|
| ARM-01 | Sudut base dengan umpan balik encoder | `config/MachineConfig.kt` (`zeroAngles`, `rotationDirections`, `baseLimits`) | `MachineConfigTest` | belum | Kendali base milik `Controller/`; aplikasi hanya menyimpan konfigurasi |
| ARM-02 | Tiga sendi + gripper sesuai konfigurasi | `MachineConfig` (`jointLimits`, `gripperOpening`) | `MachineConfigTest` | belum | Urutan gerak tidak dihitung aplikasi (B-14) |
| ARM-03 | Mengikuti target bergerak | — | — | tertahan | B-04, B-06 |
| ARM-04 | Membatasi gerakan di luar batas konfigurasi | Validasi batas pada `MachineConfig` | `MachineConfigTest` | belum | Penegakan runtime ada di controller |
| ARM-05 | Melaporkan status siap/persiapan/mengambil/meletakkan/gangguan | `ui/OperationUiState.kt` `RobotUiState`, `RobotPanel` | `StartGuardProjectionTest` (proyeksi state) | sebagian | UI siap; telemetri controller belum mengisi |
| ARM-06 | Konfigurasi dua robot | `MachineConfig.RobotId` (B1–B3 kiri, B4–B6 kanan) | `MachineConfigTest`, `BinMappingTest` | sebagian | Aturan bak terjaga; penjadwalan dua robot tertahan B-07 |

## 6. Komunikasi dan kontrol (COM-01–06, COM-07–09)

| ID | Kebutuhan ringkas (§8.E) | Implementasi | Bukti | Status | Keterangan |
|---|---|---|---|---|---|
| COM-01 | Dua arah lewat USB OTG | `usb/SerialChannel.kt`, `usb/ControllerConnection.kt`, `usb/UsbCdcChannelOpener.kt`, `AndroidManifest.xml` + `res/xml/device_filter.xml` | `ControllerConnectionTest` 10 test | sebagian | Transport diuji dengan kanal palsu; **MANUAL-VERIFY** dengan ESP32-S3 belum dijalankan; baud/framing menunggu B-04 |
| COM-02 | Identitas perintah/target dapat dicocokkan | — | — | belum | `CommandCodec` (WP-25) belum ada |
| COM-03 | Pengiriman ulang tidak membuat siklus ganda | — | — | belum | WP-25/27 |
| COM-04 | Pengamatan dicocokkan dengan encoder, bukan dianggap terkini | Penanda usang pada overlay dan pembacaan kecepatan | — | sebagian | Logika usia pengamatan belum ada (WP-28) |
| COM-05 | Menangani koneksi terputus | `ControllerConnection.markLost()`, `LinkState`, `LinkFailure` | `ControllerConnectionTest` | sebagian | Perilaku gangguan aktuator menunggu B-05 |
| COM-06 | Satu ESP32-S3 untuk conveyor + dua robot | Satu kanal serial, satu pembuka perangkat | `ControllerConnectionTest` | sebagian | Desain satu kanal; tidak dapat dibuktikan tanpa perangkat |
| COM-07 | Event siklus/deteksi ke backend | `telemetry/TelemetryEvent.kt` (`CyclePayloadFactory`) | `TelemetryQueueTest` 10 test | sebagian | Pengirim HTTP ke backend belum ada (B-08) |
| COM-08 | Event tersimpan sampai dikonfirmasi backend | `telemetry/TelemetryQueue.kt` | `TelemetryQueueTest` | sebagian | Penyimpanan masih `InMemoryTelemetryStore`; belum bertahan setelah aplikasi ditutup |
| COM-09 | Backend tidak tersedia tidak menghentikan pemilahan | `StartGuardProjection` tidak memeriksa backend | `StartGuardProjectionTest` (16 test) | selesai | Backend bukan guard operasi |

## 7. Kalibrasi (CAL-01–06)

| ID | Kebutuhan ringkas (§8.F) | Implementasi | Bukti | Status | Keterangan |
|---|---|---|---|---|---|
| CAL-01 | Pemetaan gambar → koordinat area kerja + galat | `calibration/CameraCalibration.kt` (model, `MappingMethod`, `reprojectionError`, `CameraCalibrationChecker`) | `CameraCalibrationTest` 9 test | sebagian | Model + validasi ada; perhitungan pemetaan dan tampilan galat belum; metode menunggu B-03 |
| CAL-02 | Kalibrasi encoder conveyor | Field konfigurasi saja | `MachineConfigTest` | belum | WP-18 |
| CAL-03 | Kalibrasi base robot | Field konfigurasi saja | `MachineConfigTest` | belum | WP-18 |
| CAL-04 | Kalibrasi sendi | Field konfigurasi saja | `MachineConfigTest` | belum | WP-18 |
| CAL-05 | Kalibrasi posisi bak | Field `binPositions` saja | `MachineConfigTest` | belum | WP-18 |
| CAL-06 | Perubahan geometri menandai kalibrasi perlu diperiksa | `calibration/CalibrationInvalidation.kt`, `CalibrationValidity` | `CalibrationInvalidationTest` 7 test | selesai | Status `valid` tidak pernah dinaikkan otomatis |

## 8. Antarmuka dan navigasi

| ID | Kebutuhan ringkas (`.work/PRD.md` §6.6–§6.7) | Implementasi | Bukti | Status | Keterangan |
|---|---|---|---|---|---|
| UI-01 | Operasi: pratinjau, deteksi, blok, target, status robot, kecepatan, mulai/hentikan | `ui/OperationScreen.kt` utuh (strip status termasuk profil, kamera, ringkasan, tiga panel, banner guard, kontrol bawah) | Build + `uiautomator` perangkat; `ModelsSessionTest` (label profil) | sebagian | Strip status kini menampilkan profil aktif; isi lain belum punya pemilik state nyata |
| UI-02 | Models: impor profil, pilih model, ambang, aturan kelompok | `ui/ModelsScreen.kt`, `ui/ModelsUiState.kt`, `ui/ModelsSession.kt`, `ui/AppNavigation.kt` | `ModelsUiStateTest` 17, `ModelsSessionTest` 12, `ModelsSessionTest` label Operasi; build + lint | sebagian | Layar terbuka dari rail dan menampilkan daftar, impor JSON, ambang, sakelar, aktivasi. Belum: impor `.onnx`, uji portrait/landscape/teks 1,3×/pembaca layar (WP-13) |
| UI-03 | Bot: pemetaan kelas → bak, robot aktif, bak terlayani | `BinMapping` (domain) ada; layar belum | `BinMappingTest` | belum | — |
| UI-04 | Settings: dimensi, ruas, posisi robot, gripper, batas kecepatan | `MachineConfig` (domain) ada; layar belum | `MachineConfigTest` | belum | — |
| UI-05 | Kalibrasi: kamera, encoder, base, sendi, area, posisi bak | Model kalibrasi ada; layar belum | `CameraCalibrationTest` | belum | — |
| UI-06 | Logs: koneksi, gangguan, perintah, hasil siklus, riwayat | Ringkasan sesi + tombol Riwayat pada layar operasi | `uiautomator` | sebagian | Layar Logs belum ada |
| UI-07 | Rail berikon, dapat diperkecil, label tetap untuk aksesibilitas | `ui/AppNavigation.kt`, `ui/NavigationIcons.kt`, `ui/NavigationRailState.kt` | `NavigationRailStateTest` 13 test + `uiautomator` 72 dp/232 dp | selesai | `contentDescription` memakai label yang terlihat (WCAG 2.5.3), bukan label PRD induk |

## 9. Konfigurasi (CFG-01, CFG-02) dan hasil (OUT-01)

| ID | Kebutuhan ringkas (`AGENTS.md` §6) | Implementasi | Bukti | Status | Keterangan |
|---|---|---|---|---|---|
| CFG-01 | Seluruh data conveyor (PRD §10) | `config/MachineConfig.kt` `ConveyorConfig` | `MachineConfigTest` 13 test | selesai | Validasi lengkap; **nilai** belum ada (B-03) |
| CFG-02 | Seluruh data setiap armbot (PRD §10) | `config/MachineConfig.kt` `RobotConfig` | `MachineConfigTest` | selesai | Idem; `motionParameters` wajib bertanda "telah diuji" |
| OUT-01 | Hasil gerak ≠ hasil pengambilan | `telemetry/TelemetryEvent.kt` (`MotionStatus` / `GraspStatus` / `VerificationMethod`), `CyclePayloadFactory`; metrik terpisah di ringkasan sesi | `TelemetryQueueTest` | selesai | `success` tanpa `verificationMethod` ditolak |

## 10. Nonfungsional (NFR-01–07, urutan PRD §13)

| ID | Kebutuhan ringkas | Implementasi | Bukti | Status | Keterangan |
|---|---|---|---|---|---|
| NFR-01 | Inferensi lokal di Android | — | — | belum | ONNX Runtime sudah menjadi dependensi (D-01) tetapi belum dipakai |
| NFR-02 | UI responsif saat kamera + inferensi aktif | CameraX `lifecycle`, komponen Compose | — | sebagian | Inferensi belum ada; ambang FPS/latensi menunggu B-06 |
| NFR-03 | Konfigurasi bertahan setelah aplikasi ditutup | `storage/ConfigStore.kt`, `storage/FileConfigStorage.kt`, `model/ProfileRepository.kt` | `ConfigStoreTest` 16, `ProfileRepositoryTest` 12 test (termasuk sesi dibangun ulang) | sebagian | Profil dan profil aktif bertahan pada penyimpanan; tutup paksa/buka ulang pada perangkat belum diverifikasi (WP-13) |
| NFR-04 | Perintah gerak punya identitas dan status tertelusur | — | — | belum | Bergantung COM-02/COM-03 |
| NFR-05 | Gangguan kamera/model/USB/encoder ditampilkan dengan penyebab | Status kamera (`CameraStatusOverlay`), USB (`LinkState`), kalibrasi (`CalibrationValidity`) | `ControllerConnectionTest`, `CameraCalibrationTest` | sebagian | Model dan encoder belum punya jalur status |
| NFR-06 | Penghentian darurat lewat perangkat keras | — (tombol aplikasi dinyatakan bukan E-stop) | — | tertahan | Dependensi fisik; aplikasi tidak dapat membuktikan |
| NFR-07 | Catu daya aktuator terpisah dari USB Android | — | — | tertahan | Dependensi kelistrikan |

## 11. Skenario penerimaan (SC-01–11, urutan PRD §14)

| ID | Skenario | Status | Keterangan |
|---|---|---|---|
| SC-01 | Satu objek dikenali; kelas, posisi, tujuan bak benar | belum | Butuh model (B-02) + `TargetSelector` |
| SC-02 | Beberapa objek sejenis berdekatan: hanya dikelompokkan bila memenuhi ukuran | tertahan | B-07 |
| SC-03 | Objek berbeda kelas berdekatan tidak dijadikan satu kelompok | tertahan | B-07 |
| SC-04 | Objek di luar tujuh kelas tidak dipaksa jadi target | sebagian | `WasteClass` = tujuh nilai, `null` untuk tak dikenal; filter kandidat belum ada |
| SC-05 | Kecepatan berubah: pelacakan mengikuti encoder | belum | WP-28; B-04/B-06 |
| SC-06 | Robot sibuk tidak menerima siklus baru | tertahan | B-04/B-07 |
| SC-07 | Objek terlewat bersirkulasi tanpa intervensi | belum | Tidak ada jalur siklus |
| SC-08 | USB terputus menjalankan perilaku gangguan | sebagian | `markLost()` diuji; prosedur gangguan menunggu B-05 |
| SC-09 | Model tidak kompatibel ditolak dengan penjelasan | selesai | `ProfileValidatorTest` 14 test, `ProfileJsonTest` 11 test, `ProfileImportTest` 10 test |
| SC-10 | Dua robot aktif: tidak ada penugasan ganda | tertahan | B-07 |
| SC-11 | Kalibrasi berubah: konfigurasi terkait diperiksa sebelum operasi | selesai | `CalibrationInvalidationTest`, guard `CALIBRATION_NEEDS_CHECK` |

## 12. Ringkasan cakupan

| Kelompok | Total | selesai | sebagian | belum | tertahan |
|---|---|---|---|---|---|
| CAM | 6 | 1 | 4 | 1 | 0 |
| MOD | 6 | 2 | 3 | 1 | 0 |
| CON | 5 | 0 | 1 | 4 | 0 |
| ARM | 6 | 0 | 2 | 3 | 1 |
| COM (01–09) | 9 | 1 | 6 | 2 | 0 |
| CAL | 6 | 1 | 1 | 4 | 0 |
| UI (01–07) | 7 | 1 | 2 | 4 | 0 |
| CFG + OUT | 3 | 3 | 0 | 0 | 0 |
| NFR | 7 | 0 | 3 | 2 | 2 |
| SC | 11 | 2 | 2 | 3 | 4 |
| **Jumlah** | **66** | **11** | **24** | **24** | **7** |

Angka di atas hasil hitung manual atas tabel §2–§11; bukan metrik otomatis.

## 13. Penghambat yang menentukan sisa pekerjaan

| ID | Menahan | Syarat pembukaan |
|---|---|---|
| B-02 / B-17 | CAM-01–04, NFR-01, SC-01 | Model tujuh kelas hasil retraining + kontrak keluaran |
| B-03 | CAM-03, CAM-06, CAL-01–05, CFG-01/02 nilai | Pengukuran geometri dan area kerja |
| B-04 | CON-01–05, COM-02/03, ARM-01–04, NFR-04 | Kontrak pesan serial + batas perhitungan |
| B-05 | COM-05, SC-08 | Prosedur gangguan/pemulihan |
| B-06 | CON-04, NFR-02 | Ambang FPS, latensi, toleransi posisi, usia pengamatan |
| B-07 | MOD-06, ARM-06, SC-02/03/06/10 | Penerimaan satu robot lolos lebih dulu |
| B-08 | COM-07, COM-08 | Lokasi dan antarmuka backend |
| B-14 | ARM-02, ARM-03 | Pemilik perhitungan lintasan |
| B-15 / B-16 | MOD-05 nilai, SC-01 | Definisi kelas + alokasi tujuh kelas ke enam bak |

B-01 sebagian tertutup: stack = D-01, perangkat sasaran `ZP2222SQLL` dipakai
untuk verifikasi UI (`../.work/evidence/device-requirements.md`), tetapi
kompatibilitas USB/inferensi pada HP sasaran belum dibuktikan.

## 14. Cara memperbarui dokumen ini

1. Perbarui baris requirement yang tersentuh WP pada pekerjaan yang sama.
2. Status hanya naik bila ada bukti: test yang dijalankan, keluaran perangkat,
   atau catatan di `../.work/evidence/`.
3. `MANUAL-VERIFY` tetap ditulis apa adanya; jangan diubah menjadi `selesai`
   sebelum prosedur, hasil, tanggal, dan bukti dicatat.
4. Sinkronkan §12 setelah tabel berubah.
