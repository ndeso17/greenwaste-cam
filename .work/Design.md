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
