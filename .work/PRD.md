# PRD — Camera (Aplikasi Android: Kamera, Deteksi, Target)

**Repo:** `Camera/`
**Status:** Draf kerja
**Tanggal:** 24 Oktober 2026
**Dokumen induk:** `PRD Sistem Pemilah Objek.md`
**Kontrak proses:** `AGENTS.md` (root)

## 1. Tujuan

Aplikasi Android yang membaca aliran kamera, menjalankan inferensi ONNX lokal,
menampilkan kelas/keyakinan/lokasi objek, menandai target aktif, dan menjadi
mesin logika pemilahan: memilih target, memetakan kelas ke bak, serta
mengirimkan perintah ke controller.

Aplikasi ini adalah satu-satunya antarmuka operator ke mesin.

## 2. Kedudukan dokumen

1. Turunan PRD induk; tidak menggantikan PRD induk.
2. Wewenang mengikuti `AGENTS.md` bagian 2. Konflik dicatat di `.work/FINDINGS.md` root.
3. Dokumen milik repo ini: PRD, Architecture, Styleguide, Design, Schema, Implement.
   Rencana kerja (work package) tetap di `.work/` root sesuai `AGENTS.md` bagian 3.

## 3. Ruang lingkup

### 3.1 Termasuk

Kamera dan pratinjau, inferensi ONNX lokal, decoding deteksi, pelacakan identitas
objek, pemilihan target, pemetaan kelas ke bak, profil model, kalibrasi kamera,
konfigurasi mesin, kontrak pesan ke controller, antrean telemetri ke backend,
enam menu aplikasi, dan kelengkapan antarmuka navigasi (ikon dan perilaku lipat,
UI-07).

### 3.2 Tidak termasuk

Firmware controller, layanan backend, dashboard web, pelatihan model, dan
kendali aktuator langsung (dilakukan controller).

## 4. Kelas resmi

Tujuh kelas dari PRD induk bagian 5:

| # | Kelas | Cakupan |
|---|---|---|
| 1 | Organik | Sisa makanan, daun, sisa organik lain |
| 2 | Plastik | Botol plastik, cup plastik, kresek |
| 3 | Kaleng | Kemasan makanan/minuman berbahan logam |
| 4 | Styrofoam | Wadah styrofoam |
| 5 | Kaca | Botol kaca, pecahan kaca |
| 6 | Kardus/karton | Kardus dan karton |
| 7 | Besi | Benda besi selain kemasan kaleng |

Definisi operasional tiap kelas wajib disepakati sebelum dataset dibekukan.
Tanpa definisi, dataset akan tumpang tindih dan akurasi tidak bermakna.

## 5. Kondisi model yang ada

`CV/best.onnx` (YOLOv8, 13 label) **tidak memenuhi** daftar tujuh kelas:

| Kelas model yang ada | Status terhadap 7 kelas |
|---|---|
| Kaca Coklat_Brownglass, Kaca_Glass | Dapat dipetakan ke `Kaca` |
| Kardus_Cardboard | Dapat dipetakan ke `Kardus` |
| Kertas_paper | Tidak ada padanan; perlu keputusan |
| Kresek_Plasticbag, Plastik_Plastic | Dapat dipetakan ke `Plastik` |
| Logam_metal | Digabung; harus **dipisah** ke `Kaleng` dan `Besi` |
| Sampah kering, Sisa makanan | Dapat dipetakan ke `Organik` |
| Manusia, Pakaian, Sepatu, label `0` | Bukan sampah; dibuang |
| — | **Styrofoam tidak ada** |
| — | **Kaleng dan Besi belum terpisah** |

Konsekuensi: aplikasi tidak dapat mengklaim mendukung tujuh kelas sampai model
dilatih ulang. Aplikasi tetap wajib menolak profil model yang tidak lolos
validasi, termasuk yang kelasnya tidak memuat tujuh kelas resmi.

## 6. Kebutuhan fungsional

ID `CAM-*`, `MOD-*`, `CAL-*`, `UI-*`, `COM-*` mengikuti alias `AGENTS.md` bagian 6.

### 6.1 Kamera dan deteksi (CAM-01–06)

| ID | Kebutuhan | Kriteria terima |
|---|---|---|
| CAM-01 | Pratinjau real time dengan overlay hasil analisis terakhir | Pratinjau tampil; overlay diperbarui; frame usang diberi penanda |
| CAM-02 | Setiap deteksi lolos ambang memiliki kelas, keyakinan, dan kotak | Label dari daftar kelas profil, bukan indeks mentah |
| CAM-03 | Pembagian blok sesuai area conveyor terkalibrasi | Batas blok dari konfigurasi, digambar terpisah dari kotak deteksi |
| CAM-04 | Paling banyak satu target aktif ditandai berbeda | Target sama tidak dipilih berulang dalam satu siklus |
| CAM-05 | Identitas objek bertahan antar frame | Dua objek berdekatan/silang tidak digabung |
| CAM-06 | Deteksi di luar area kerja tidak menjadi kandidat | Berdasarkan titik acuan yang dinyatakan eksplisit |

Penjabaran sub-klausa: lihat PRD kamera tahap 3
`.work/PRD-aplikasi_kamera_android_tahap_3-24102026-014245.md` bagian 6.

### 6.2 Profil model (MOD-01–06)

| ID | Kebutuhan | Kriteria terima |
|---|---|---|
| MOD-01 | Impor profil ONNX beserta identitas, label, konfigurasi inferensi | Profil tersimpan dan dimuat ulang setelah aplikasi ditutup |
| MOD-02 | Validasi kompatibilitas; tolak dengan alasan spesifik | Model klasifikasi saja ditolak; jumlah label harus cocok dengan keluaran |
| MOD-03 | Pergantian profil hanya saat pemantauan berhenti dan target kosong | Kegagalan muat mempertahankan profil lama |
| MOD-04 | Ambang keyakinan per profil | Nilai tersimpan; perubahan berlaku pada frame berikutnya |
| MOD-05 | Pemetaan kelas aktif ke bak tujuan | Kelas aktif tanpa tujuan valid memblokir start |
| MOD-06 | Aturan pengambilan kelompok per profil | Sakelar tersimpan; perilaku kelompok mengikuti tahap 5 PRD induk |

**Tambahan wajib dari koreksi kelas:** MOD-02 harus menolak profil yang daftar
kelas aktifnya tidak dapat dipetakan ke tujuh kelas resmi, atau yang memakai
gabungan `Kaleng`+`Besi` sebagai satu label, karena kebutuhan pemisahan bak
tidak dapat dipenuhi.

### 6.3 Kalibrasi kamera (CAL-01, CAL-06)

| ID | Kebutuhan | Kriteria terima |
|---|---|---|
| CAL-01 | Pemetaan titik gambar ke koordinat area kerja | Galat pemetaan dihitung dan ditampilkan |
| CAL-06 | Perubahan geometri menandai kalibrasi perlu diperiksa | Start ditolak selama kalibrasi bertanda perlu diperiksa |

### 6.4 Komunikasi ke controller (COM-01–04)

| ID | Kebutuhan | Kriteria terima |
|---|---|---|
| COM-01 | Dua arah melalui USB OTG | Perintah terkirim, telemetri diterima |
| COM-02 | Identitas perintah dan target dapat dicocokkan | Setiap respons dapat ditelusuri ke perintah asal |
| COM-03 | Pengiriman ulang tidak mengeksekusi siklus ganda | Satu perintah, satu siklus |
| COM-04 | Pengamatan dicocokkan dengan encoder, bukan dianggap terkini | Pesan terlambat ditandai usang |

### 6.5 Antrean telemetri ke backend

| ID | Kebutuhan | Kriteria terima |
|---|---|---|
| COM-07 | Aplikasi mengirim event siklus dan deteksi ke backend statistik | Event tersimpan di backend sesuai identitas |
| COM-08 | Event disimpan lokal sampai dikonfirmasi backend | Antrean bertahan setelah aplikasi ditutup |
| COM-09 | Backend tidak tersedia tidak menghentikan pemilahan | Operasi berjalan; antrean bertambah; status ditampilkan |

COM-07–09 adalah kebutuhan tambahan dari perluasan lingkup backend
(`.work/PRD-backend_statistik_dan_kinerja_mesin-24102026-020511.md`).

### 6.6 Menu aplikasi

Enam menu PRD induk bagian 9 tetap menjadi enam tujuan, dengan **label tampilan**
mengikuti `D-UI-02` (instruksi pengguna 24 Oktober 2026). Isi tiap tujuan tidak
berubah; hanya teks label, ikon, dan perilaku lipat rail yang ditambahkan.

| Requirement | Menu PRD induk bagian 9 | Label tampilan (D-UI-02) | Isi utama |
|---|---|---|---|
| UI-01 | Kamera / Operasi | Operasi | Pratinjau, deteksi, blok, target aktif, status robot, kecepatan, mulai/hentikan |
| UI-02 | Model & Kelas | Models | Impor profil, pilih model, ambang keyakinan, aturan kelompok |
| UI-03 | Bak & Robot | Bot | Pemetaan kelas ke bak, robot aktif, bak yang dapat dilayani |
| UI-04 | Pengaturan Mesin | Settings | Dimensi lintasan, panjang ruas, posisi robot, gripper, batas kecepatan |
| UI-05 | Kalibrasi | Kalibrasi | Kamera, encoder, base, sendi, area pengambilan, posisi bak |
| UI-06 | Status & Riwayat | Logs | Koneksi, gangguan, perintah, hasil siklus, ringkasan sesi, riwayat dari backend |

Label tampilan boleh berubah tanpa mengubah requirement: `UI-03` tetap
mewajibkan pemetaan kelas ke bak dan status robot, meskipun labelnya "Bot".
PRD induk tidak diubah oleh keputusan ini.

### 6.7 Kebutuhan tambahan repo ini

| ID | Kebutuhan | Kriteria terima |
|---|---|---|
| UI-07 | Rail navigasi berikon dan dapat diperkecil/diperbesar; saat diperkecil hanya ikon yang tampil | Rail terbuka menampilkan ikon + label; rail tertutup hanya ikon; label penuh tetap tersedia sebagai deskripsi aksesibilitas; perpindahan tujuan berfungsi pada kedua keadaan |

UI-07 adalah kebutuhan tambahan repo seperti preseden COM-07–COM-09; bukan ID
PRD induk dan tidak menggantikannya. Sumber: instruksi pengguna 24 Oktober 2026,
dicatat sebagai `D-UI-02` dan `D-05`.

## 7. Kebutuhan nonfungsional

- Inferensi lokal; tidak ada pengiriman frame atau model ke jaringan.
- UI responsif; kamera dan inferensi tidak berjalan di thread UI.
- Konfigurasi, profil, ambang, dan status kalibrasi bertahan setelah aplikasi ditutup.
- Setiap perintah gerak memiliki identitas dan dapat ditelusuri.
- Gangguan kamera, model, USB, encoder, dan backend ditampilkan dengan penyebab.
- Aplikasi tidak menjadi jalur kendali tunggal: penghentian darurat perangkat
  keras tetap berlaku tanpa aplikasi.

## 8. Skenario penerimaan

| Skenario | Hasil |
|---|---|
| Satu objek dikenali | Kelas, lokasi, tujuan bak benar |
| Objek berkelas gabungan Logam dideteksi | Tidak dipaksakan menjadi Kaleng atau Besi |
| Objek di luar tujuh kelas | Tidak menjadi target |
| Model tidak kompatibel | Impor ditolak dengan alasan |
| USB terputus | Perilaku gangguan dijalankan |
| Backend mati | Operasi berjalan; antrean terkirim setelah backend hidup |
| Kalibrasi berubah | Pemeriksaan diminta sebelum operasi |

## 9. Keputusan terbuka

| ID | Masalah | Status |
|---|---|---|
| B-01 | Perangkat sasaran, versi Android, dan stack aplikasi | terbuka |
| B-02 | Model tujuh kelas: dataset, retraining, kontrak keluaran | terbuka |
| B-03 | Geometri kamera dan area kerja | terbuka |
| B-04 | Kontrak pesan Android–controller | terbuka |
| B-06 | Ambang numerik FPS, latensi, toleransi posisi | terbuka |
| B-08 | Lokasi backend dan antarmuka baca | terbuka |
| B-15 | Definisi operasional tiap kelas dan nasib kelas Kertas pada model lama | terbuka |
| B-16 | Alokasi tujuh kelas ke enam bak pada tahap satu robot | terbuka |

## 10. Di luar lingkup repo ini

Firmware controller, server backend, dashboard web, pelatihan model (dicatat
sebagai pekerjaan retraining di `Controller/` tidak; lihat `Backend/` juga tidak;
retraining berada di luar keempat repo sampai diputuskan pemiliknya).
