# Architecture — Camera

**Repo:** `Camera/`
**Status:** Draf kerja; stack masih usulan
**Dokumen terkait:** `PRD.md`, `Design.md`, `Schema.md`, `Rules.md`

## 1. Status stack

Stack di bawah adalah **usulan**, bukan keputusan yang disetujui. Keputusan
terbuka B-01 pada `.work/FINDINGS.md` root. Implementasi tidak boleh dimulai
sebelum B-01 ditutup.

| Lapisan | Usulan | Alasan | Alternatif |
|---|---|---|---|
| Bahasa | Kotlin | Dukungan ONNX Runtime dan CameraX terbaik di Android | Java |
| UI | Jetpack Compose | Pratinjau + overlay butuh komposisi dinamis | XML + View |
| Kamera | CameraX | Menangani rotasi, resolusi, dan lifecycle | Camera2 langsung |
| Inferensi | ONNX Runtime Mobile | Model induk sudah ONNX | TFLite (perlu konversi) |
| USB | usb-serial-for-android | CDC/ACM ke ESP32-S3 | Android USB host API langsung |
| Penyimpanan | DataStore + berkas JSON profil | Konfigurasi kecil, tidak butuh relasi | Room |
| Build | Gradle wrapper | Baseline `AGENTS.md` bagian 8 | Bazel |

## 2. Lapisan

```
ui/          Compose screen + overlay
  ↓
domain/      Target selector, tracker, class→bin mapping, guards
  ↓
data/        Profile, config, calibration, telemetry queue
  ↓
platform/    Camera, ONNX runtime, USB serial, storage
```

Aturan arah dependensi: `ui` → `domain` → `data` → `platform`. Tidak ada
dependensi balik. `platform` tidak boleh mengimpor `ui`.

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

B-01 (stack), B-02 (model tujuh kelas), B-03 (geometri), B-04 (protokol),
B-06 (ambang numerik), B-08 (backend), B-14 (pemilik perhitungan lintasan),
B-15 (definisi kelas), B-16 (alokasi kelas ke bak).
