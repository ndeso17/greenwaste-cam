# Design — Camera

**Repo:** `Camera/`
**Status:** Layout operasi landscape disetujui 24 Oktober 2026; detail ukuran masih perlu verifikasi
**Dokumen terkait:** `PRD.md`, `Styleguide.md`, `Schema.md`, [OperationScreen.xml](OperationScreen.xml)

## Acuan struktur layar

[OperationScreen.xml](OperationScreen.xml) adalah rujukan struktur layar operasi
pendamping dokumen ini. Agen wajib membaca keduanya sebelum mengimplementasikan
layar: Markdown menjelaskan tata letak, alasan, dan aturan perilaku; XML merinci
hierarki komponen, ID, binding semantik, mode, serta kondisi kontrol.

Perubahan desain layar harus diselaraskan pada kedua berkas. Bila ada perbedaan,
ikuti urutan sumber kebenaran pada `AGENTS.md` dan keputusan D-UI-01; jangan
menganggap XML sebagai izin mengubah requirement. XML bersifat deskriptif,
bukan layout Android siap kompilasi.

## 1. Prinsip desain

1. HP dipasang tetap menghadap conveyor. Tata letak harus menjaga kamera dan
   kontrol operasi terbaca pada pemasangan perangkat sasaran; jangan mengasumsikan
   HP dipegang atau dioperasikan dengan satu tangan.
2. Kejujuran status lebih penting daripada tampilan rapi. "Belum terverifikasi"
   tidak boleh terlihat seperti "berhasil".
3. Pratinjau kamera adalah elemen utama layar operasi, bukan hiasan.
4. Warna tidak menjadi satu-satunya pembeda status.
5. Tidak ada kontrol yang dapat menggerakkan aktuator dari layar yang salah.

## 2. Palet

Belum ada identitas merek. Palet di bawah adalah usulan; nilai hex mengikat
setelah disetujui dan harus dipindahkan ke token.

| Token | Nilai usulan | Pemakaian |
|---|---|---|
| `surface` | `#0F1115` | Latar layar operasi (gelap agar pratinjau menonjol) |
| `surfaceRaised` | `#171A21` | Kartu, panel |
| `onSurface` | `#E6E8EC` | Teks utama |
| `onSurfaceMuted` | `#9AA0AB` | Teks sekunder |
| `accent` | `#2F6FED` | Aksi utama |
| `success` | `#2E9E5B` | Siklus terverifikasi |
| `warning` | `#C98A12` | Belum terverifikasi, data usang |
| `danger` | `#D64545` | Gangguan, gagal |
| `neutralClass` | `#7C8595` | Objek tak dikenal |

Aturan warna status:

- `success` hanya untuk siklus yang punya verifikasi pengambilan.
- `warning` wajib untuk belum terverifikasi, kalibrasi perlu diperiksa, dan
  overlay usang.
- `danger` untuk gangguan terbuka dan penolakan start.

## 3. Tipografi

| Peran | Ukuran | Bobot | Catatan |
|---|---|---|---|
| Judul layar | 20 sp | 600 | Nama menu |
| Angka ringkasan | 32 sp | 700 | Tabular figures |
| Label status | 14 sp | 600 | Selalu disertai ikon |
| Teks isi | 14 sp | 400 | |
| Teks bantuan | 12 sp | 400 | `onSurfaceMuted` |

Font sistem. Tidak ada font kustom sampai keputusan merek ada.

## 4. Tata letak layar operasi

### 4.1 Dasar dan batas rancangan

Isi layar mengikuti PRD induk bagian 9 (UI-01), brainstorming pada chat
“Rancang aplikasi klasifikasi sampah” tanggal 23 Oktober 2026 (pembahasan
pengaturan model, dimensi, dan kecepatan), serta baseline
`../../.work/Plan_AndroidPemilah_23102026-124815.md` WP-21–24 dan WP-35.
Persetujuan pengguna pada 24 Oktober 2026: “oke setuju dengan mockup, sekarang
terjemahkan mockup tersebut menjadi markdown/xml supaya agent dapat membacanya.”
Keputusan visual dicatat sebagai D-UI-01 di `../../.work/DECISIONS.md`.

Acuan visual lokal: [OperationMockup-v1.png](assets/OperationMockup-v1.png).
Spesifikasi terstruktur: [OperationScreen.xml](OperationScreen.xml).
XML tersebut adalah spesifikasi UI deskriptif, bukan layout Android yang dapat
dikompilasi dan bukan keputusan memakai Android Views atau Compose.

Layout acuan adalah **landscape**, kamera di kiri dan panel mesin di kanan.
Orientasi sensor kamera tetap mengikuti konfigurasi/kalibrasi; landscape UI
bukan alasan memutar atau meregangkan frame. Perangkat, resolusi, dimensi dp,
dan kebijakan rotasi sistem belum ditetapkan (B-01, B-03).

```text
┌──────────┬─────────────────────────────────────────────────────────────┐
│          │ Header: Pemilah Objek · profil · kamera · kalibrasi · USB     │
│ Navigasi ├──────────────────────────────────────┬──────────────────────┤
│ 6 menu   │ Pratinjau kamera langsung             │ Target aktif         │
│          │ + area kerja dan blok putus-putus     │ ID · kelas · blok    │
│ Kamera / │ + kotak objek, kelas, keyakinan        │ Bak · robot tujuan*  │
│ Operasi  │ + sudut tebal dan label TARGET        ├──────────────────────┤
│ aktif    │                                      │ Robot kiri / kanan*  │
│          │                                      ├──────────────────────┤
│          │                                      │ Conveyor*            │
│          │                                      │ Diminta · Terapkan   │
│          ├──────────────────────────────────────┤ Aktual (encoder)     │
│          │ Ringkasan sesi · backend · Riwayat    │                      │
│          ├──────────────────────────────────────┴──────────────────────┤
│          │ Alasan terblokir / gangguan (bila ada)                        │
│          │ Mulai mode · Hentikan mode · status operasi                  │
└──────────┴─────────────────────────────────────────────────────────────┘
```

`*` Informasi/kendali mesin berlaku pada integrasi tahap 4 dan seterusnya.
Tahap 3 menampilkan “Belum terintegrasi” atau “Tidak tersedia pada tahap 3”
untuk robot, tujuan operasional, dan kecepatan; tidak menampilkan nilai atau
status siap palsu. Mulai pemantauan tidak mengirim perintah aktuator dan tidak
memerlukan USB/robot. Lihat PRD tahap 3 bagian 2, 3, dan 4 di
`../../.work/PRD-aplikasi_kamera_android_tahap_3-24102026-014245.md`.

### 4.2 Aturan penyajian dan interaksi

- Pratinjau adalah bidang utama. Status, ringkasan, dan kontrol ditempatkan di
  luar bidang gambar agar tidak menutupi deteksi atau blok. Tidak ada batas
  tinggi 55% yang belum disepakati.
- Transformasi pratinjau dan seluruh overlay memakai rotasi, rasio aspek,
  crop/letterbox, serta kalibrasi yang sama; jangan meregangkan gambar agar
  memenuhi panel (CAM-02, CAM-03, CAL-01; WP-17, WP-23).
- Overlay blok memakai garis putus-putus dan label blok; kotak deteksi memakai
  garis penuh dengan kelas dan keyakinan. Jumlah/ukuran blok berasal dari
  konfigurasi terkalibrasi, bukan grid contoh yang ditetapkan permanen.
- Target aktif memakai sudut tebal dan label “TARGET”. Detail ID, kelas, blok/
  posisi dan, saat terintegrasi, bak/robot tujuan dapat dibaca di panel target.
  Ketika tidak ada target, tulis “Belum ada target” (CAM-04; WP-35).
- Status setiap robot aktif berasal dari telemetri: siap, persiapan, mengambil,
  meletakkan, atau gangguan. Robot kedua yang belum diaktifkan ditulis
  “Nonaktif”; kamera tetap berjalan saat robot sibuk (ARM-05, CAM-01; WP-35).
- Kecepatan diminta dan aktual diberi label terpisah serta satuan sesuai kontrak
  konfigurasi. Nilai diminta bukan bukti kecepatan aktual. Aktual berasal dari
  encoder; telemetri terlambat diberi penanda usang dan saat belum tersedia
  ditulis “Belum ada data” (CON-01–02, COM-04; WP-28, WP-35).
- Masukan kecepatan divalidasi terhadap rentang konfigurasi sebelum diterapkan
  melalui ESP32-S3. Kontrol dinonaktifkan jika integrasi, koneksi, atau validasi
  tidak memungkinkan; alasannya ditampilkan. Siklus pengambilan normal tidak
  memerintahkan conveyor berhenti (CON-01, CON-05).
- Tombol menyebut mode secara eksplisit: “Mulai pemantauan” pada tahap 3 dan
  “Mulai pemilahan” pada tahap integrasi. Guard pemantauan memeriksa kamera,
  profil, kalibrasi kamera, dan gangguan terkait. Guard pemilahan juga memeriksa
  koneksi, konfigurasi mesin, pemetaan kelas aktif, serta kalibrasi terkait.
  Tombol Mulai nonaktif ketika guard gagal; alasan tampil tepat di atas kontrol.
- “Hentikan” menghentikan mode yang aktif menurut kontrak operasi; tombol ini
  tidak dinyatakan sebagai emergency stop perangkat keras. Perilaku aktuator
  saat berhenti/gangguan tetap menunggu B-05 (WP-06, WP-32–33).
- Ringkasan sesi hanya menyajikan data dan akses ke Status & Riwayat, tanpa
  kontrol mesin. Gerak selesai, pengambilan terverifikasi, belum terverifikasi,
  dan gagal dibedakan. Backend tidak tersedia tidak memblokir operasi dan
  tidak menutupi pratinjau/kontrol (OUT-01; PRD frontend FE-04, FE-N04).
- Profil aktif terlihat pada layar; impor/pergantian tetap melalui Model &
  Kelas dan mengikuti guard pergantian profil (MOD-03; WP-16).

### 4.3 Pemeriksaan dokumen dan pembuktian perangkat

Pemeriksaan sendiri, 24 Oktober 2026: isi layar ditelusuri ke UI-01, CAM-01–04,
CON-01–02, ARM-05, MOD-03, COM-04, dan OUT-01 melalui sumber/WP di atas.
Koreksi ini mengganti asumsi pemakaian satu tangan, skema yang menghilangkan
status robot/kecepatan, serta ketentuan tinggi kamera 55% tanpa dasar.
Ini koreksi dokumen desain; tidak mengubah status WP baseline atau menyatakan
QC independen telah dilakukan.

MANUAL-VERIFY setelah UI dan perangkat tersedia: periksa ruang sempit/lebar,
rotasi/crop dan keselarasan overlay, keterbacaan pada dudukan HP, target kosong/
aktif, robot sibuk/nonaktif, data encoder usang, guard kedua mode, dan akses
Mulai/Hentikan ketika backend tidak tersedia. Catat perangkat/versi, konfigurasi,
prosedur, hasil, tanggal, dan bukti di `.work/evidence/` root. Seluruh pembuktian
perangkat tersebut belum dilakukan.

### 4.4 Spesifikasi implementasi untuk agen

Urutan baca: PRD/kontrak → keputusan D-UI-01 → bagian 4 dokumen ini →
`OperationScreen.xml` → gambar referensi. Jika teks pada render berbeda dari
PRD atau aturan keselamatan/status, gunakan spesifikasi tertulis. Persetujuan
mockup tidak mengubah enam menu PRD atau mengesahkan data contoh sebagai nilai mesin.

| ID stabil | Posisi dan ukuran relatif | Isi / perilaku |
|---|---|---|
| `navigationRail` | Kiri, sepanjang tinggi konten | Enam tujuan (label D-UI-02); dapat diperkecil 232 dp ⇄ 72 dp; berikon; Operasi aktif |
| `statusHeader` | Atas, setelah rail | Nama aplikasi, profil aktif, lalu satu kartu status ringkas berisi kamera, kalibrasi, dan USB |
| `cameraStage` | Kolom utama kiri, area terbesar | Frame, area kerja, grid blok, deteksi, target |
| `targetPanel` | Kanan atas | ID target, kelas, blok/posisi, bak dan robot tujuan |
| `robotPanel` | Kanan tengah | Status kiri dan kanan secara terpisah |
| `conveyorPanel` | Kanan bawah | Input diminta, Terapkan, aktual dari encoder |
| `sessionSummary` | Di bawah kamera, selebar kolom kamera | Hasil gerak/jepit terpisah, backend, antrean, Riwayat |
| `guardBanner` | Di atas kontrol bawah, tampil bila diperlukan | Penyebab penolakan/gangguan dan tindakan berikut |
| `operationControls` | Bawah, selebar konten setelah rail | Mulai, Hentikan, status mode |

Proporsi visual referensi: rail sempit, kolom kamera sekitar dua pertiga area
utama, panel kanan sekitar sepertiga. Proporsi ini panduan komposisi, bukan
batas numerik perangkat; jangan menambahkan breakpoint atau ukuran piksel tetap
sebelum perangkat tersedia. Pertahankan urutan panel dan akses kontrol saat
teks diperbesar; isi kartu boleh menggulir tanpa menggulir tombol Hentikan keluar
layar. Jangan menutup kamera dengan kartu mesin.

Pengecualian aturan di atas: perangkat uji sudah tersedia (B-01 sebagian selesai,
lihat `.work/evidence/device-requirements.md`), sehingga dua lebar rail pada
tabel keadaan rail di bawah ini ditetapkan sebagai nilai tetap sesuai D-UI-02,
dan ambang label ringkasan sesi diukur dari lebar barisnya sendiri. Keduanya
bukan angka mesin dan tidak berasal dari PRD.

Enam tujuan navigasi adalah Operasi, Models, Bot, Settings, Kalibrasi, Logs
(`D-UI-02`, 24 Oktober 2026). Isinya tetap enam menu PRD induk bagian 9 dalam
urutan yang sama: UI-01 Operasi (kamera), UI-02 Models (profil/kelas), UI-03 Bot
(pemetaan bak/robot), UI-04 Settings (konfigurasi mesin), UI-05 Kalibrasi,
UI-06 Logs (status/riwayat). Label tampilan tidak mengganti requirement: "Bot"
tetap berarti Bak dan Robot, "Logs" tetap mencakup Status & Riwayat. Render
memakai label Kamera/Operasi/Data/Pengaturan/Log/Tentang: **label tersebut bukan
daftar menu implementasi**. Akses Riwayat menuju Logs, bukan menu ketujuh.

Setiap tujuan wajib berikon (`Icons.Rounded`, `D-05`) dan rail wajib dapat
diperkecil (UI-07). Aturan keadaan rail:

| Keadaan | Lebar | Tampilan |
|---|---|---|
| Terbuka | 232 dp | Ikon 24 dp + label 14 sp; judul aplikasi tampil |
| Tertutup | 72 dp | Hanya ikon 24 dp; label dan judul aplikasi disembunyikan |

- Ikon kendali lipat berada di puncak rail, **sebaris dengan judul aplikasi**
  ("Pemilah Objek"), dan di luar daftar yang dapat digulir, sehingga selalu
  terjangkau tanpa menggulir. Saat rail tertutup, tombol itu berada di tengah
  rail agar sejajar dengan ikon tujuan di bawahnya; judul aplikasi disembunyikan.
- Target sentuh baris tujuan dan tombol lipat minimal 48 dp pada kedua keadaan.
- Saat tertutup, label penuh tetap menjadi `contentDescription` tiap tujuan dan
  tombol lipat menyebut aksinya ("Perkecil navigasi"/"Perbesar navigasi").
- Nama aksesibilitas memakai **label yang terlihat**, bukan label PRD induk pada
  `.work/PRD.md` §6.6. Alasannya WCAG 2.5.3 *Label in Name*: nama aksesibilitas
  wajib memuat teks yang terbaca, supaya pengguna Voice Access yang mengucap
  "tap Models" atau "tap Logs" tetap menemukan kontrolnya. Pemetaan label PRD →
  label tampilan tetap dicatat di §6.6 dan pada atribut `prdLabel`
  `OperationScreen.xml`, tetapi tidak dipakai sebagai nama aksesibilitas.
- Ikon memakai `tint` warna terpilih/tidak terpilih; keadaan terpilih juga
  dibedakan oleh latar, bukan warna ikon saja (Design.md §7).

Header profil adalah identitas dan pintasan ke Models; bentuk mirip
pemilih pada render tidak mengizinkan pergantian profil saat operasi berjalan.

Status kamera, kalibrasi, dan USB pada header dikelompokkan dalam **satu kartu
ringkas** di sisi kanan: setiap status memakai ikon, label kecil di atas, dan
nilai tebal di bawah, dipisahkan garis tipis. Pengelompokan ini menjaga label
dan nilai tidak bercampur menjadi satu baris teks serta tetap terbaca tanpa
mengandalkan warna (Design.md §7). Kartu tidak boleh menutupi pratinjau dan
harus tetap utuh saat teks diperbesar.

Gunakan permukaan gelap dan kartu bersudut membulat, pemisah tipis, ikon
sederhana, teks terang, biru untuk pilihan/aksi, merah untuk Hentikan/gangguan,
dan amber untuk belum terverifikasi/usang. Palet bagian 2 menjadi token acuan
D-UI-01; kontras tetap harus diperiksa. Indikator koneksi boleh berwarna hijau
dengan ikon dan teks; hijau koneksi tidak berarti jepit berhasil. Bentuk HP,
perspektif 3D, dudukan, bayangan studio, judul di luar HP, dan catatan data
ilustrasi bukan elemen layar aplikasi. Ukuran tipografi bagian 3 tetap panduan,
belum dibuktikan pada perangkat.

Contoh snapshot render (hanya fixture dokumentasi): profil Sampah v1,
T-024, Plastik, A2, Bak 2, robot kiri Persiapan, kanan Nonaktif, kecepatan diminta
0,10 m/s, aktual 0,09 m/s, gerak selesai 12, pengambilan belum terverifikasi 12,
backend terhubung. A1–B3, Plastik 94%, dan Kardus 91% juga hanya contoh.
Jangan menjadikan grid, nilai, label profil, tujuan bak, atau hasil tersebut
sebagai default operasional. Binding XML memakai nama semantik UI; bukan kontrak
wire, skema penyimpanan baru, atau penetapan pemilik state.

| Keadaan | Presentasi yang wajib |
|---|---|
| Kosong | Belum ada profil/target/data; angka tidak diganti nol yang menyesatkan |
| Memuat | Status memuat pada komponen terkait; kontrol mengikuti guard |
| Siap / dihentikan | Mulai aktif hanya setelah guard mode lolos; Hentikan nonaktif |
| Berjalan | Mulai nonaktif; Hentikan aktif; mode dan status robot terlihat |
| Robot sibuk | Kamera tetap aktif; tidak ada penugasan siklus baru ke robot itu |
| Gagal / terblokir | Penyebab spesifik dan tindakan berikut di banner; Mulai nonaktif |
| Telemetri usang | Nilai terakhir ditandai usang; tidak dianggap posisi/kecepatan terkini |
| Backend tidak tersedia | Status backend dan antrean ditampilkan; bukan guard operasi |
| Tahap 3 | Label pemantauan; kendali mesin nonaktif dengan alasan belum terintegrasi |

Peristiwa UI: buka menu, buka Model & Kelas, ubah draft kecepatan, terapkan
kecepatan, mulai mode, hentikan mode, buka Status & Riwayat. UI mengirim intent;
validasi dan hasil aktual datang dari logika aplikasi/controller. Setelah
Terapkan, tampilkan pending/ditolak/dikonfirmasi sesuai respons; jangan menyalin
input diminta ke pembacaan encoder. Gangguan atau pemulihan USB tidak otomatis
memulai kembali operasi. Kontrak berhenti/pemulihan aktuator menunggu B-05.

## 5. Komponen

| Komponen | Isi | Status |
|---|---|---|
| `StatusStrip` | Ikon dan label untuk kamera, profil, kalibrasi, USB, backend | wajib |
| `DetectionOverlay` | Kotak, label kelas, keyakinan, penanda usang | wajib |
| `BlockOverlay` | Batas blok, label blok | wajib |
| `ActiveTargetMarker` | Penanda sudut tebal + label "TARGET" | wajib |
| `ActiveTargetPanel` | ID, kelas, blok/posisi, bak/robot tujuan sesuai tahap | wajib |
| `RobotStatusPanel` | Status per robot, nonaktif/belum terintegrasi | wajib pada integrasi |
| `ConveyorSpeedControl` | Nilai diminta, validasi, aksi Terapkan | wajib pada integrasi |
| `ConveyorSpeedReadout` | Kecepatan aktual encoder, satuan, status data usang | wajib pada integrasi |
| `OperationControls` | Mulai/Hentikan berlabel mode, guard dan alasan | wajib |
| `SessionSummaryBar` | Siklus, terverifikasi, belum terverifikasi, gagal | wajib |
| `StartGuardBanner` | Alasan start ditolak | wajib |
| `ProfileCard` | Nama, versi, status validasi, alasan penolakan | wajib |
| `ThresholdSlider` | Ambang per profil, nilai ditampilkan | wajib |
| `BinMappingRow` | Kelas, bak, robot | wajib |
| `CalibrationPointList` | Titik acuan, galat | wajib |
| `TelemetryQueueBadge` | Jumlah event tertunda | wajib |

## 6. Keadaan layar yang wajib ada

Setiap layar wajib mendefinisikan dan menampilkan:

1. Kosong — belum ada data atau belum dikonfigurasi.
2. Memuat.
3. Siap.
4. Gagal — dengan penyebab dan langkah berikut.
5. Terblokir — dengan nama keputusan yang belum tersedia, bukan pesan generik.

Keadaan terblokir ditampilkan apa adanya, misalnya: "Pemetaan kelas ke bak belum
lengkap. Kelas Styrofoam belum memiliki bak tujuan."

## 7. Aksesibilitas

- Kontras teks minimal 4.5:1 terhadap latar.
- Setiap status memiliki teks dan ikon, tidak hanya warna.
- Target sentuh minimal 48 dp.
- Seluruh kontrol utama dapat dijangkau pembaca layar dengan label yang bermakna.
- Animasi overlay tidak berkedip lebih dari 3 kali per detik.

## 8. Yang belum diputuskan

Ukuran layar, kebijakan rotasi sistem, dan keterbacaan pada dudukan perangkat sasaran (B-01),
geometri/transformasi kamera (B-03), serta perilaku berhenti/gangguan mesin
(B-05). Layout landscape dan posisi panel disetujui melalui D-UI-01; ukuran
adaptif serta kelengkapan isi mengikuti bagian 4.1 dan 4.4. Tata letak harus diverifikasi pada perangkat nyata sebelum dianggap
final.

## Perbaikan panel kanan — 27 Oktober 2026

Sumber: permintaan pengguna sesi ini dan foto UI terpotong. Panel kanan memiliki
tombol “Sembunyikan panel” di atas area gulir. Target, Robot, dan Conveyor
berada dalam satu area gulir vertikal dengan tinggi sisa yang dibatasi. Tombol
tetap terjangkau pada semua posisi gulir. Saat ditutup, tombol “Panel” membuka
kembali panel dan kamera memakai ruang yang tersedia. Keadaan lipat dipulihkan
dengan rememberSaveable; posisi gulir dipertahankan selama lipat/buka. Kamera
tetap terpasang dan kontrol operasi berada di luar gulir (UI-01, CAM-01, NFR-02).

### Revisi lanjutan pengguna pada sesi yang sama
Ringkasan Sesi kini satu baris ringkas transparan, tetap di atas body setelah
header; metrik berupa label singkat dan nilai sebaris, detail makna tersedia
pada deskripsi aksesibilitas. Baris dapat digulir horizontal jika tidak muat.
Tidak ada kartu, latar berwarna, atau bingkai. Status/alasan start juga
transparan dan ditempatkan paling atas dalam area gulir panel kanan. Status
ikut disembunyikan bersama panel; kontrol Mulai/Hentikan tetap di bawah.
Ketentuan ini menggantikan penempatan ringkasan di bawah kamera dan banner
di atas kontrol pada §4, sesuai instruksi eksplisit pengguna 27 Oktober 2026.

### Revisi header dan keadaan awal navigasi — instruksi pengguna sesi ini
Hapus judul “Pemilah Objek” dan teks/pintasan profil dari header operasi.
Header kini hanya menampilkan kartu kamera, kalibrasi, USB. Model tetap
dapat diakses melalui tujuan Models pada navigasi. Rail awal tertutup
(72 dp, ikon saja); toggle dan pemulihan state sesi tetap berlaku. Ini
menggantikan ketentuan profil terlihat pada header dan default rail terbuka.

### Revisi status mengambang — instruksi pengguna sesi ini
Kamera, kalibrasi, dan USB kini berada dalam frame kamera, mengambang di bawah.
Kelompok status transparan sepenuhnya: tanpa kartu, latar, atau border. Header
atas dihapus. Status mempertahankan ikon, label, dan nilai terpisah; dapat
digulir horizontal jika sempit. Ringkasan tetap di atas frame. Ini menggantikan
seluruh penyajian status header pada §4 dan revisi sebelumnya.

### Revisi akhir ringkasan sesi — instruksi pengguna sesi ini
Ringkasan Sesi juga menjadi overlay transparan di bagian atas frame kamera,
bukan baris di luar kamera. Status kamera/kalibrasi/USB mengambang di bawah.
Kedua overlay tanpa latar; ruang kamera diperbesar. Batas atas menghormati
status bar Android agar jam dan ikon sistem tidak bertumpuk dengan ringkasan.
Ini menggantikan posisi ringkasan top-fixed-below-header pada revisi sebelumnya.

## Revisi shutter — 27 Oktober 2026 (instruksi pengguna terbaru)
Ketentuan ini menggantikan layout footer dan transparansi penuh sebelumnya.
Preview mengisi lebar/tinggi konten setelah navigasi kiri; panel mesin menjadi
overlay kanan gulir/lipat dan tidak mengecilkan kamera. Semua kontrol berada
di dalam preview bawah tengah dengan gradien hitam tipis. Shutter utama 80 dp
bercincin putih: idle hijau/putih dengan play, aktif kotak merah membulat.
Secondary Berhenti 48 dp hanya saat aktif; keduanya menggunakan onStop lama.
Tidak ada kendali aktuator/emergency stop baru. Guard StartGuardProjection
tetap menjadi sumber enabled; state aktif bukan diubah sendiri oleh tombol.
Animasi 180–220 ms untuk bentuk/warna dan tekanan; label aksi kecil, semantics
button serta disabled tersedia. Status berjalan/berhenti tetap dinyatakan.
Ringkasan di atas frame dan status kamera/kalibrasi/USB di atas kontrol memakai
permukaan glass hitam alpha 0.64 serta teks putih, warna status hanya ikon.
Backdrop blur API31+ pada sampel visual preview berukuran terbatas; teks
tidak diblur, preview utama tidak diblur. API26–30 memakai tint tanpa blur.
Kontras dihitung pada latar kamera putih sebagai kasus terburuk, minimal 4.5:1.
Orientasi mengikuti perangkat; hilangkan kunci landscape. Insets sistem
dihormati; teks overlay dapat digulir horizontal. Panel mesin overlay dibatasi
lebar dan tinggi sisa agar tidak menutup shutter, baik portrait maupun tablet.
Tidak ada perubahan alur deteksi, konfigurasi, protokol, atau data operasional.

### Amandemen ringkasan pada sesi shutter
Instruksi tambahan pengguna: hapus Backend dan Riwayat dari Ringkasan Sesi.
Ringkasan hanya menyajikan metrik sesi/antrean; menu Logs tetap tersedia dan
data/backend tidak diubah. Ketentuan ini menggantikan isi backend dan pintasan
Riwayat pada spesifikasi ringkasan sebelumnya.

Pada jendela sempit (<600 dp), rail kiri yang diperbesar mengambang di atas
konten tanpa mempersempit area kamera/shutter; slot awal tetap 72 dp. Pada
jendela lebar, rail tetap memakai lebar 72/232 dp. Insets vertikal rail
menghindari jam/status bar dan area gestur sistem.

### Amandemen panel default dan seluruh menu — instruksi tambahan pengguna
Panel kanan default hidden pada peluncuran baru. State sesi tetap saveable.
Root navigasi memakai safeDrawingPadding untuk seluruh tujuan (Operasi, Models,
Bot, Settings, Kalibrasi, Logs), bukan hanya operasi. Insets dikonsumsi pada
root sehingga anak tidak menggandakan jarak. Semua konten menghindari status
bar, cutout dan gesture/navigation bar, baik portrait maupun landscape.

Kontrol memakai slot keterangan yang sama pada idle/aktif agar shutter tidak
bergeser ketika berubah state. Line-height label 16 sp dan keterangan 12 sp;
status 14/16 sp. Ringkasan tetap memiliki tinggi sentuh minimal 48 dp. Panel
mesin menyisakan area bawah 224 dp untuk status/kontrol dan teks diperbesar.

### Amandemen status minimal dan sensor — 27 Oktober 2026
Status kamera, kalibrasi dan USB hanya label putih dengan titik bulat: hijau
untuk aktif/sah/terhubung, abu-abu untuk belum disiapkan/izin belum diberikan/
belum terhubung/menghubungkan, merah untuk kamera tidak tersedia/gagal,
kalibrasi perlu diperiksa dan koneksi gagal. Deskripsi lengkap tetap tersedia
bagi pembaca layar; tidak ada nilai status tambahan secara visual.
Ringkasan portrait vertikal memakai nama lengkap lima metrik; landscape memakai
singkatan horizontal. Surface transparan gelap/blur dan preview penuh dipertahankan.
Activity memakai FULL_SENSOR sehingga mengikuti posisi fisik HP, termasuk saat
rotasi sistem dikunci. State pemantauan dan alur deteksi tidak diubah.


## Layar Models — 27 Oktober 2026

Bagian ini mencatat tata letak dan keadaan layar Models sesuai implementasi
WP-01–WP-09 pada `.work/Plan_ModelsOnnxImport_27102026-123043.md` (D-09).

Layar ini **hanya** memuat tiga hal: impor berkas model ONNX, ambang keyakinan
per model, dan informasi kelas yang tertulis di dalam model. Tidak ada impor
berkas konfigurasi JSON, tidak ada sakelar kelompok, dan tidak ada tampilan bak.

### Komponen

| Komponen | Isi | Sumber state |
|---|---|---|
| `ModelsImportAction` | Tombol "Impor model" dengan pemilih dokumen tanpa filter MIME; alasan kegagalan impor tampil di atas daftar | `ModelsSession.importModel` |
| `ModelsProfileList` | Satu kartu per model: nama, versi (bila model menuliskannya), status validasi (ikon + teks), jumlah kelas, label berurutan beserta kelas resmi hasil pencocokan, alasan penolakan per bidang | `ModelsUiState.rows` |
| `ModelsThresholdEditor` | Nilai awal dari profil tersimpan, ditampilkan sebagai pecahan dan persen, tombol "Terapkan ambang"; ambang `0.0` ditampilkan sebagai "Belum ditetapkan" dengan warna peringatan | `ModelsUiState.thresholdDrafts` |
| `ModelsActivateAction` | Tombol "Jadikan model aktif" hanya pada model yang lolos validasi dan belum aktif | `ModelsSession.setActiveProfile` |
| `ModelsDeleteAction` | Tombol "Hapus model" pada setiap kartu; membuka `DeleteConfirmationDialog` yang menyebut nama model dan menyatakan berkas `.onnx` ikut terhapus serta tidak dapat dibatalkan. Hanya tombol "Hapus" di dialog yang menghapus | `ModelsSession.deleteProfile` |

### Keadaan wajib

| Keadaan | Tampilan |
|---|---|
| Kosong | Judul "Belum ada model." dan ajakan mengimpor berkas `.onnx` |
| Memuat | Teks "Memuat daftar model." tanpa daftar palsu |
| Siap | Daftar model dengan status masing-masing |
| Ambang belum ditetapkan | Keterangan "Belum ditetapkan" pada field ambang dan penolakan aktivasi `CONFIDENCE_THRESHOLD_UNSET` |
| Gagal | Alasan per bidang pada kartu model; impor gagal menampilkan kode alasannya (bukan `.onnx`, bukan model yang dapat dibuka, metadata kelas tidak ada, bentuk tensor tidak didukung, label tak dikenal, penyimpanan gagal) |
| Terblokir | Alasan pergantian model ditolak (`ProfileSwitchBlockCode`) |
| Konfirmasi hapus | Dialog menyebut nama model dan akibatnya (berkas `.onnx` terhapus, tidak dapat dibatalkan); "Batal" menutup tanpa perubahan |
| Selesai hapus | Snackbar `Model "<nama>" dihapus.`; kartu hilang dari daftar. Bila model aktif yang dihapus, penanda aktif hilang dan daftar menampilkan "Belum ada model aktif yang dipilih." |

Layar tidak boleh menampilkan model, label, ambang, atau bak contoh. Daftar
kosong berarti belum ada model di penyimpanan aplikasi.

### Aksesibilitas

- Status validasi selalu berupa ikon + teks, bukan warna saja (`Design.md` §7).
- Penanda model aktif berupa teks "Aktif" di samping ikon status.
- Setiap kontrol memakai teks dari `strings.xml`; tidak ada label yang hanya
  berupa ikon pada layar ini.
- Target sentuh 48 dp mengikuti `minimumInteractiveComponentSize` pada tombol
  Material yang dipakai.
- Alasan penolakan ditulis lengkap di dalam kartu, tidak dipotong menjadi
  singkatan.

### Catatan uji perangkat — 27 Oktober 2026

Catatan uji perangkat untuk tata letak kartu (portrait penuh, layar rendah perlu
digulir, teks 1,3×, target sentuh ≥48 dp) berasal dari
`.work/evidence/models-device.md` dan tetap berlaku karena struktur kartu tidak
berubah bentuk.

Bukti impor `.onnx` nyata pada `moto g57 power` (Android 16, 1080×2400 px, 330
dpi) dicatat pada `.work/evidence/models-onnx-import.md` bagian WP-11 beserta
tangkap layar di `.work/evidence/models-onnx-device/`:

1. Impor `resnet18_model-7kelas.onnx` (44,71 MB) berhasil; kartu menampilkan
   tujuh baris pemetaan label dan "Kelas di dalam model: 7".
2. Ambang `0.6` disimpan, lalu "Jadikan model aktif" ditekan; kartu menandai
   "Aktif".
3. Setelah `am force-stop` dan dibuka ulang, layar Operasi menampilkan
   `Profil: resnet18_model-7kelas resnet18-7kelas`.
4. Berkas JSON ditolak dengan "Berkas bukan model ONNX (.onnx). Model lama tidak
   diubah." dan daftar model tidak berubah.
5. Impor berulang berkas yang sama membuat kartu kedua
   `resnet18_model-7kelas (3)`; model lama beserta ambangnya tetap utuh.

Temuan uji perangkat yang diperbaiki: metadata `names` berbentuk `repr` Python
(kutip tunggal) ditolak pembaca metadata, sehingga impor pertama gagal walau
model sah. Perbaikan dan test-nya dicatat pada berkas bukti tersebut.

### Yang belum ditetapkan

- Perilaku pengelompokan objek menunggu B-07; logika domain sakelar masih ada
  tetapi tidak lagi tampil di layar ini (celah UI dicatat di
  `../.work/FINDINGS.md`).
- Nama bak sah dan alokasi tujuh kelas ke enam bak menunggu B-15/B-16; layar
  tidak lagi menampilkan bak.
- Model deteksi tujuh kelas menunggu B-17. Selama model itu tidak ada, kotak pada
  kamera adalah batas petak klasifikasi (D-14), bukan tepi objek.

## Operasi — garis bantu, panel klasifikasi, dan kotak — 27 Oktober 2026

Instruksi pengguna: kamera harus bisa dipakai menguji klasifikasi tanpa
kalibrasi dan USB, dengan garis bantu bidik, dan hasil klasifikasi tampil di
kamera. Perubahan yang mengikat:

| Bagian | Aturan |
|---|---|
| Guard Mulai (mode pemantauan) | Hanya kamera siap dan profil aktif sah yang menahan Mulai. Kalibrasi, konfigurasi, pemetaan bak, USB, dan kelas aktif hanya menahan pemilahan (D-11) |
| `CameraGuidesOverlay` | Garis pembagi tiga pada pratinjau, aktif secara bawaan, dapat dimatikan lewat sakelar "Tampilkan garis bantu" |
| `ClassificationPanel` | Menampilkan label teratas, keyakinan (persen bulat), ambang profil, nama model, pesaing terdekat, dan jumlah petak diperiksa; di bawah ambang diberi tanda, bukan disembunyikan |
| Kotak overlay | Kotak pada petak yang lolos ambang, berlabel kelas + persen keyakinan; kotak lebih tua dari 2,5 detik ditandai usang. Ringkasan kotak ditulis sebagai deskripsi aksesibilitas karena isi kanvas tidak terbaca pembaca layar |

Batas yang dinyatakan di UI dan dokumen: garis bantu bukan area kerja hasil
kalibrasi, dan kotak bukan batas objek.

Bukti perangkat: `.work/evidence/kamera-klasifikasi.md` beserta tangkap layar
`klasifikasi-kotak-overlay-27102026.png` dan
`klasifikasi-panel-kotak-27102026.png`.

## Uji Model dan Galeri — tambahan 27 Oktober 2026

Dasar: PRD.md §11 (U-01–U-06), instruksi pengguna sesi ini. Status:
spesifikasi layar; belum merupakan bukti layar telah diimplementasikan.

### Uji Model

Tujuan navigasi baru “Uji Model”. Gunakan susunan Operasi versi terbaru:
preview memenuhi konten, rail adaptif, panel kanan lipat, insets sistem,
kontrol bawah dalam preview dan dukungan portrait/landscape. Ganti aksi
pemantauan menjadi “Mulai Uji” dan “Stop Uji”; label aksesibilitas mengikuti
teks terlihat. Menekan Mulai dua kali tidak membuat dua sesi. Stop tetap
terjangkau saat panel disembunyikan dan tidak mengirim perintah mesin.

Kotak persegi panjang mengikuti lokasi objek dari detektor, dengan warna
tetap per kelas. Warna bukan satu-satunya pembeda: tiap objek menampilkan
label dan keyakinan. Beberapa objek pada gambar masing-masing memiliki
kotak/label sendiri. Tidak ada kontur dan batas petak tidak dipresentasikan
sebagai batas objek. Transformasi kotak harus sama dengan preview pada
rotasi dan rasio aspek yang berbeda (CAM-02).

| Keadaan | Tampilan dan perilaku |
|---|---|
| Belum siap | Alasan kamera/profil belum siap; Mulai Uji nonaktif. |
| Detektor belum tersedia | Alasan spesifik, kotak tidak dikarang; tidak menyamarkan klasifikasi petak sebagai deteksi. |
| Siap | Mulai Uji tersedia; Stop Uji nonaktif. |
| Berjalan | Status uji aktif, Stop Uji aktif; simpan gambar dan prediksi per objek yang terkait frame yang sama. |
| Tidak ada objek | Preview tetap aktif, tanpa kotak palsu. |
| Gagal simpan | Penyebab terlihat; jangan menambah jumlah tersimpan untuk sampel gagal. |
| Dihentikan | Tidak ada inferensi atau penyimpanan baru; hasil yang sedang diproses tidak diterbitkan sebagai sampel baru setelah Stop. |

### Galeri

Tujuan navigasi baru “Galeri”. Daftar gambar membuka detail dengan overlay
objek bernomor/beridentitas; memilih satu objek menunjukkan prediksi asli,
keyakinan, kelas koreksi dan status pemeriksaan. Operator memilih kelas
resmi untuk objek itu, lalu menyimpan koreksi. Koreksi tidak mengganti
prediksi asli. Menyimpan satu objek tidak mengonfirmasi objek lainnya.

| Keadaan | Tampilan dan perilaku |
|---|---|
| Kosong | Belum ada gambar; arahkan operator ke Uji Model. |
| Memuat | Indikator memuat tanpa thumbnail contoh. |
| Belum diperiksa | Penanda teks pada objek; tidak dianggap label kebenaran. |
| Dikonfirmasi | Label koreksi tersimpan terpisah dari prediksi awal. |
| Gagal baca/simpan | Alasan tampil; pertahankan draft koreksi jika penyimpanan gagal. |
| Hapus | Konfirmasi menyebut gambar dan anotasi yang ikut dihapus; pembatalan tidak mengubah data. |

Seluruh teks UI kelak berasal dari strings.xml (D-03). Terapkan target
sentuh dan label aksesibilitas §7, termasuk pemilihan objek selain melalui
kanvas. Referensi koreksi U-06 belum boleh ditampilkan sebagai “model sudah
dilatih”; mekanismenya masih B-UM-02.

### Pelaksanaan layar tambahan (D-15)
Galeri menyediakan thumbnail, detail gambar dengan nomor objek, pemilih label
kelas untuk masing-masing objek, Konfirmasi kelas dan Hapus gambar dengan
dialog konfirmasi. Ambang kemiripan referensi dapat disimpan di daftar Galeri.
Uji Model memakai komposisi OperationScreen dengan label Mulai Uji/Stop Uji
dan jumlah gambar tersimpan. Operasi memakai pipeline lokasi/klasifikasi sama
dan snapshot koreksi pada start; uji menyimpan prediksi asli tanpa otomatis
menerapkan atau mengonfirmasi referensi koreksi. Label overlay menyebut
“referensi” dan kemiripan bila kelas berasal dari pencocokan; persen model
tetap dilaporkan terpisah. Pemasok lokasi dan transformasi frame mengikuti
Architecture.md §8. Pemeriksaan perangkat/akurasi dicatat terpisah.

### Koreksi objek dan pilihan jamak (instruksi 27 Oktober 2026)
Setiap kotak pada pratinjau kamera dan gambar Galeri diberi titik bantu di
tengahnya dengan warna sama seperti kotaknya dan nomor objek; nomor itu sama
dengan nomor pada daftar koreksi. Titik hanya menandai kotak, bukan tiap benda
di dalamnya; bila satu kotak memuat beberapa benda, operator tetap mengoreksi
per kotak. Di Galeri, ketukan pada titik memilih objek dan memusatkannya.
Penandaan gambar dilakukan langsung dari daftar Galeri, tanpa masuk ke detail:
centang selalu tampil pada setiap kartu, tekan lama kartu menandai atau
membatalkan kartu itu dan sekaligus mengaktifkan mode pilih (ketuk biasa tetap
membuka detail), dan header selalu menyediakan "Tandai semua" serta "Batalkan
tandai". Detail gambar menyediakan centang per objek. Baris pilihan massal
menampilkan jumlah gambar dan objek, Kosongkan, "Labeli terpilih", dan "Hapus
terpilih". Kedua aksi massal memakai dialog konfirmasi yang menyebut jumlah
gambar dan objek; di dalam dialog label, seluruh baris label dapat diketuk.
`Galeri` melaporkan bila hanya sebagian berhasil. Pilihan tidak mengubah makna
koreksi: koreksi massal tetap tersimpan sebagai koreksi per objek dengan jam
yang sama.

### Warna status koreksi Galeri (27 Oktober 2026)
Border card 2 dp berwarna hijau (#2E7D32) hanya jika seluruh objek memiliki
label koreksi tersimpan. Card belum dikoreksi atau baru sebagian dikoreksi
berborder merah (#C62828). Teks status dan centang tetap tersedia; warna
border menunjukkan koreksi, sedangkan centang menunjukkan pilihan.

### Foto detail Galeri utuh (27 Oktober 2026)
Foto detail dibatasi tinggi maksimum 320 dp dan dipusatkan horizontal. Lebar
tidak dipaksa penuh: rasio sumber menentukan ukuran yang muat dalam batas
tinggi/lebar, sehingga foto potret tampil lebih sempit tanpa memotong atas
atau menimpa teks koreksi. Overlay dan sasaran ketuk mengikuti bingkai foto
yang sama; kebijakan pratinjau kamera memenuhi layar tidak diterapkan ke Galeri.
