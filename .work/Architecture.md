# Architecture — Camera

**Repo:** `Camera/`
**Status:** Draf kerja; stack **disetujui** 24 Oktober 2026 (D-01)
**Dokumen terkait:** `PRD.md`, `Design.md`, `Schema.md`, `Rules.md`, `Structure.md`

## 1. Status stack

Stack di bawah **disetujui pengguna** sebagai D-01 di `../.work/DECISIONS.md`
tanggal 24 Oktober 2026. Keputusan ini menutup bagian stack dari B-01; bagian
**perangkat sasaran tetap terbuka** (`.work/evidence/device-requirements.md`).

| Lapisan | Keputusan (D-01) | Versi | Alternatif yang tidak dipakai |
|---|---|---|---|
| Bahasa | Kotlin | 2.0.21 | Java |
| UI | Jetpack Compose + Material3 | BOM 2024.10.01 | XML + View |
| Kamera | CameraX | 1.4.0 | Camera2 langsung |
| Inferensi | ONNX Runtime Android | 1.19.2 | TFLite (perlu konversi) |
| USB | usb-serial-for-android | 3.8.1 (mulai WP-25) | Android USB host API langsung |
| Penyimpanan | DataStore Preferences + berkas JSON profil | 1.1.1 | Room |
| Build | Gradle wrapper, Kotlin DSL, AGP | 8.11.1 / 8.7.3 | Bazel |
| SDK | compileSdk 35, targetSdk 35, minSdk 26 | — | — |
| JDK | Toolchain 17 | — | JDK 25 terpasang (tidak didukung AGP 8.7.3) |

Larangan yang tetap berlaku: `usb-serial-for-android` tidak dipakai sebelum
B-04 menetapkan protokol; angka mesin tidak masuk kode; `ControllerLink`
tingkat lanjut menunggu B-04 dan B-14.

## 2. Lapisan

```
id.pemilah.ui          Compose screen + overlay
  ↓
id.pemilah.domain      Target selector, tracker, class→bin mapping, guards
  ↓
id.pemilah.data        Profile, config, calibration, telemetry queue
  ↓
id.pemilah.platform    Camera, ONNX runtime, USB serial, storage
```

Aturan arah dependensi: `ui` → `domain` → `data` → `platform`. Tidak ada
dependensi balik. `platform` tidak boleh mengimpor `ui`. Pemetaan paket ke
berkas aktual ada di `Structure.md`.

## 3. Komponen

| Komponen | Tanggung jawab | Tidak boleh |
|---|---|---|
| `CameraSource` | Membuka kamera, menghasilkan frame, menangani izin | Menjalankan inferensi |
| `FramePipeline` | Antrean frame, membuang frame usang, menandai usia | Menyimpan riwayat panjang |
| `Detector` | Memuat model, menjalankan inferensi, melepas sumber daya | Menentukan target |
| `OutputDecoder` | Tensor → daftar deteksi berlabel | Memfilter area kerja |
| `WorkAreaFilter` | Menolak deteksi di luar area terkalibrasi | Mengubah kalibrasi |
| `Tracker` | Identitas objek antar frame | Memilih target |
| `TargetSelector` | Memilih satu target aktif | Mengirim perintah |
| `CameraCalibration` | Pemetaan gambar ke area kerja | Menentukan area kerja |
| `CalibrationValidity` | Menandai kalibrasi perlu diperiksa | Membersihkan status otomatis |
| `ProfileRepository` | Simpan/muat profil dan ambang | Memvalidasi model |
| `ProfileValidator` | Validasi kompatibilitas profil | Memuat model |
| `BinMapping` | Kelas → bak dan robot | Menentukan jangkauan mekanik |
| `CommandCodec` | Serialisasi perintah dan telemetri | Menentukan timing aktuator |
| `ControllerLink` | Transport USB, deteksi putus | Menyimpan status siklus |
| `TelemetryQueue` | Antrean event ke backend, retry, idempotensi | Menghentikan operasi |
| `OperationGuard` | Syarat start: kamera, profil, kalibrasi, koneksi | Menggantikan emergency stop |

Pemetaan komponen ke berkas aktual (paket, nama kelas, berkas test) ada di
`Structure.md` bagian 2. Setiap komponen baru wajib ditambahkan ke tabel itu
pada WP yang sama.

## 4. Kepemilikan state

| State | Pemilik tunggal | Pembaca |
|---|---|---|
| Profil aktif dan ambang | `ProfileRepository` | Detector, UI |
| Kalibrasi dan status validitas | `CameraCalibration`, `CalibrationValidity` | WorkAreaFilter, TargetSelector, UI |
| Hasil deteksi terakhir | `FramePipeline` | UI overlay, Tracker |
| Identitas pelacakan | `Tracker` | TargetSelector, TelemetryQueue |
| Target aktif | `TargetSelector` | CommandCodec, UI |
| Status koneksi controller | `ControllerLink` | OperationGuard, UI |
| Antrean telemetri | `TelemetryQueue` | UI status |
| Pemetaan kelas ke bak | `BinMapping` | TargetSelector, OperationGuard |

Aturan: satu state satu pemilik. UI tidak menulis state domain; UI mengirim
peristiwa ke pemilik state.

## 5. Konkurensi

- Frame diproses pada satu worker khusus. Tidak ada dua inferensi bersamaan pada
  satu model.
- Antrean frame berkapasitas terbatas; saat penuh, frame tertua dibuang dan
  jumlah pembuangan dicatat.
- Perubahan profil hanya pada keadaan idle, dijaga `OperationGuard`.
- Pembacaan telemetri controller pada worker terpisah dari inferensi.
- Pengiriman backend pada worker terpisah; kegagalan tidak memblokir operasi.

## 6. Kontrak profil model

Profil memuat field berikut; nama field mengikat di `Schema.md`.

Identitas (nama, versi, versi skema), berkas model, daftar label berurutan,
ukuran input, normalisasi, format keluaran, ambang keyakinan, aturan kelompok,
pemetaan kelas ke bak.

Validasi wajib menolak:

1. Model tanpa keluaran lokasi objek.
2. Jumlah label tidak sama dengan jumlah kelas keluaran.
3. Ukuran input deklarasi tidak sama dengan tensor model.
4. Versi skema tidak dikenal.
5. Daftar kelas tidak dapat dipetakan ke tujuh kelas resmi PRD induk bagian 5.
6. Label yang menggabungkan `Kaleng` dan `Besi` menjadi satu kelas.

Butir 5 dan 6 berasal dari koreksi kelas 24 Oktober 2026; tanpanya aplikasi akan
menerima profil yang tidak dapat memenuhi pemisahan bak.

## 7. Kontrak pesan ke controller

Belum ditetapkan (B-04). Yang mengikat pada sisi aplikasi:

- Setiap perintah membawa identitas unik dan identitas target.
- Setiap respons dapat dicocokkan ke perintah asal.
- Telemetri membawa nilai encoder dan waktu pembacaan.
- Pesan dengan identitas yang sudah dieksekusi tidak memicu siklus baru.
- Versi protokol diperiksa; versi tidak dikenal ditolak.

Aplikasi tidak menghitung timing aktuator. Batas perhitungan lintasan belum
diputuskan (B-14) dan menahan implementasi `CommandCodec` tingkat lanjut.

## 8. Alur data ke backend

```
TargetSelector → siklus selesai → TelemetryQueue → POST event → Backend
                                        ↓ gagal
                                   retry dengan identitas sama
```

Backend tidak pernah berada di jalur kritis. `OperationGuard` tidak memeriksa
ketersediaan backend.

## 9. Batas dengan repo lain

| Repo | Batas |
|---|---|
| `Controller/` | Kontrak pesan serial; aplikasi tidak mengakses GPIO atau aktuator |
| `Backend/` | Kontrak event statistik; aplikasi tidak membaca database backend |
| `Frontend/` | Dashboard web terpisah; tidak berbagi kode dengan aplikasi |

## 10. Yang belum diputuskan

B-02 (model tujuh kelas), B-03 (geometri), B-04 (protokol), B-05 (gangguan dan
pemulihan), B-06 (ambang numerik), B-08 (backend), B-14 (pemilik perhitungan
lintasan), B-15 (definisi kelas), B-16 (alokasi kelas ke bak), serta bagian
**perangkat sasaran** dari B-01 (stack sudah disetujui D-01).

Komponen yang bergantung pada butir di atas tetap boleh dibuat kerangkanya,
tetapi tidak boleh diisi angka, protokol, atau klaim hasil tanpa keputusan.
