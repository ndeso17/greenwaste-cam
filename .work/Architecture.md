# Architecture — Camera

**Repo:** `Camera/`
**Status:** Draf kerja; stack disetujui 24 Oktober 2026 (D-01)
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
| USB | usb-serial-for-android (`CdcAcmSerialDriver`) | 3.8.1, repo JitPack (D-04) | Android USB host API langsung |
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
| Hasil klasifikasi dan kotak petak | `OperationSession` | `ClassificationPanel`, `DetectionOverlay` |
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

1. Jumlah label tidak sama dengan jumlah kelas keluaran.
2. Ukuran input deklarasi tidak sama dengan tensor model.
3. Versi skema tidak dikenal.
4. Daftar kelas tidak dapat dipetakan ke tujuh kelas resmi PRD induk bagian 5.
5. Label yang menggabungkan `Kaleng` dan `Besi` menjadi satu kelas.

Butir 4 dan 5 berasal dari koreksi kelas 24 Oktober 2026; tanpanya aplikasi akan
menerima profil yang tidak dapat memenuhi pemisahan bak.

Bekas butir "model tanpa keluaran lokasi objek" **dicabut** oleh D-10 (27 Oktober
2026): model utama prototipe adalah ResNet18 klasifikasi tujuh kelas. Keluaran
`classification_only` diterima, dan kebutuhan kotak objek menjadi tanggung jawab
pemasok deteksi (`Detector`) yang belum ada. Sampai pemasok itu tersedia, model
klasifikasi aktif belum dapat menghasilkan kandidat; batas ini berlaku sebagai
hambatan terbuka, bukan implementasi selesai.

### 6.1 Jalur klasifikasi kamera (D-12, D-13, D-14)

```
CameraPreview (ImageAnalysis, STRATEGY_KEEP_ONLY_LATEST, jeda 400 ms)
  └─ RgbaConversion → ArgbFrame
       └─ OperationSession.onFrame
            ├─ FrameTiles.split(3×3)                → sembilan petak berwilayah
            ├─ Classifier.classifyAll               → satu eksekusi batch [N,3,H,W]
            │    └─ OrtClassifier: sesi ORT per berkas model + ImageNetNormalization
            ├─ TileDetectionFilter.select(ambang)   → kotak yang lolos ambang
            └─ ClassificationUi.Result + detections
                 └─ ClassificationPanel + DetectionOverlay
```

Aturan yang mengikat:

1. Frame hanya diminta saat `OperationSession.isRunning`; berhenti berarti tidak
   ada inferensi sama sekali.
2. Batch gagal (model dengan batch tetap 1) mundur ke eksekusi satu per satu;
   hasil yang dilaporkan sama, hanya jumlah eksekusi berbeda.
3. Ambang `0.0` berarti belum ditetapkan (D-09) dan tidak menghasilkan kotak.
4. Kotak adalah batas petak yang lolos ambang, bukan tepi objek. Overlay tidak
   boleh menyebutnya deteksi objek.
5. Ringkasan kotak diekspos sebagai deskripsi aksesibilitas karena isi kanvas
   tidak terbaca pembaca layar.

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

Setelah D-10, terbuka juga: sumber kotak objek untuk model klasifikasi
(pemasok deteksi dan kontrak `OutputDecoder`, B-02), normalisasi masukan model
klasifikasi (D-12: ImageNet mean `0.485/0.456/0.406`, std `0.229/0.224/0.225`,
satu sumber `ImageNetNormalization`; akurasi terhadap data latih belum diuji),
dan keakuratan kotak overlay (D-14: batas petak 3×3, belum ada NMS atau
penyaringan latar karena model tidak punya kelas latar).

Hambatan B-17 (model deteksi YOLO tujuh kelas) tetap terbuka: selama model itu
tidak ada, kotak pada kamera hanya menandai petak yang dinilai, bukan objek.

Komponen yang bergantung pada butir di atas tetap boleh dibuat kerangkanya,
tetapi tidak boleh diisi angka, protokol, atau klaim hasil tanpa keputusan.

## 8. Uji Model, Galeri dan referensi koreksi (27 Oktober 2026)

Sumber: mandat pengguna menyelesaikan semua WP dan memeriksa di akhir.
Jalur baru: frame tegak → letterbox YOLO CV/best.onnx → decode/NMS kotak →
crop objek → ResNet18 aktif → overlay. Label detektor lama hanya memasok
lokasi; tidak dipetakan menjadi tujuh kelas secara paksa. Semua kelas
detektor dapat memasok proposal; kemampuan menemukan objek terbatas model.

OperationSession memiliki state inferensi; dua instance terpisah untuk
pemantauan dan uji. Keluar layar menghentikan sesi; satu analyzer memproses
frame. Generation token menolak hasil terlambat setelah stop/start.
ModelTestSession mengoordinasi penyimpanan hanya pada uji. SampleStore
menyimpan gambar dan anotasi JSON lokal dengan publikasi atomik; pembacaan
galeri dan perubahan anotasi berjalan di worker IO. Tidak ada dependensi baru.

Referensi koreksi berupa descriptor warna spasial crop 8×8 RGB. Pencocokan
memakai jarak absolut rata-rata; kemiripan = 1 − jarak. Ambang dapat diubah
operator di Galeri, nilai awal konservatif 0.97; bukan ambang kemampuan mesin
atau jaminan akurasi. Referensi lintas label yang sama dekat ditolak. Hanya
sampel terkonfirmasi dari SHA-256 isi model klasifikasi yang sama dimuat saat start.
Skor referensi ditampilkan sebagai kemiripan, terpisah dari probabilitas model.
Bobot model tidak berubah. Descriptor sederhana sensitif pencahayaan, sudut,
dan latar; evaluasi data terpisah tetap diperlukan.

Detektor dibundel sebagai assets/object-detector.onnx dari CV/best.onnx.
Input/output aktual diverifikasi sebelum runtime. Proposal confidence 0.25
dan NMS IoU 0.45 mengikuti parameter script CV/infer.py, nilai awal teknis
yang dapat diperbarui, bukan angka mesin.

Pemasok runtime menolak bentuk tensor di luar [1,3,640,640] → [1,17,8400].
Bukti inspeksi aktual: onnxruntime Python pada CV/best.onnx, 27 Oktober 2026.
NMS lintas kelas dipakai karena seluruh label lama hanya proposal lokasi.
Maksimum 32 proposal per frame membatasi biaya klasifikasi; bukan batas robot.
Preview FILL_CENTER dan ImageAnalysis memakai aspek 4:3 serta rotasi display
yang sama. Pratinjau memenuhi bingkai (dipotong di tengah) supaya layar tidak
menyempit; overlay memakai transformasi isi (`fillRect`) dari aspek frame
tegak, terpisah dari letterbox input YOLO. Setiap kotak deteksi mendapat titik
bantu bernomor berwarna sama di tengahnya; nomor sama dengan urutan objek pada
ringkasan dan Galeri. Titik hanya menandai kotak, bukan tiap benda di dalamnya.
Keselarasan pada HP tetap memerlukan pembuktian perangkat.

Koreksi Galeri dapat dilakukan per objek atau massal: `SampleStore.correctMany`
menulis satu gambar sekali sehingga tidak ada koreksi separuh dan melaporkan
gambar yang gagal tanpa menghentikan sisanya; `deleteMany` melakukan hal sama
untuk penghapusan. Label yang tidak dimiliki model terlampir dilewati dengan
jumlah yang dilaporkan ke operator.

### Deduplikasi sampel Uji Model (27 Oktober 2026)
Instruksi pengguna: objek sama di posisi sama tidak terus disimpan.
`ModelTestSession` memiliki `TestSampleDeduplicator`, terpisah dari tracker
pemilahan robot. Setiap proposal dipasangkan satu-ke-satu dengan anchor
posisi/descriptor yang telah diamati; label dan probabilitas tidak menjadi
identitas. IoU minimum 0.5 dan kemiripan descriptor warna minimum 0.8
serta pergeseran titik tengah maksimum seperempat sisi kotak yang lebih kecil
adalah heuristik gambar awal, bukan toleransi mesin atau bukti identitas fisik.
Anchor tetap agar perpindahan kecil berurutan akhirnya menghasilkan sampel baru.
Dua frame tanpa deteksi ditoleransi; setelah tiga frame hilang, kemunculan
berikutnya dianggap baru. Frame hanya disimpan jika ada objek belum tersimpan;
seluruh objek frame itu tetap dicatat bersama gambar sumber yang sama.
Penanda tersimpan hanya disahkan setelah SampleStore.save berhasil sehingga
kegagalan encoding/I/O dapat dicoba ulang pada frame berikutnya. Tidak ada
encoding JPEG untuk frame duplikat. State direset saat sesi baru atau perubahan
model/resolusi; tidak membaca atau menghapus sampel Galeri lama.
Batas: objek berbeda dengan warna/posisi sangat mirip dapat dianggap sama;
perubahan pencahayaan besar dapat menghasilkan sampel baru. Pembuktian kamera
HP tetap MANUAL-VERIFY; mekanisme ini bukan jaminan pelacakan fisik (CAM-05).

Rework QC HP 27 Oktober 2026: rangkaian 14 sampel objek diam memiliki
IoU antar-sampel 0.594–0.986 dan kemiripan 0.847–0.969. Syarat kemiripan
0.97 sebelumnya gagal pada seluruh pasangan tersimpan. Regresi menyimpan
ulang 14 kali; toleransi posisi/visual diperlebar untuk variasi crop/kamera.
Nilai ini heuristik deduplikasi gambar dan belum merupakan evaluasi identitas
objek atau ambang mesin. Anchor tetap, pencocokan satu-ke-satu, reset sesi,
dan percobaan ulang I/O dipertahankan. Data anotasi tanpa JPEG masuk fixture
unit test; replay JPEG melalui debug ModelVerificationActivity memakai
penyimpanan verifikasi tersendiri tanpa mengubah Galeri pengguna.
