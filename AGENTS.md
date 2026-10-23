# Kontrak Proses — Camera

**Repo:** `Camera/`
**Status:** Berlaku untuk seluruh pekerjaan di direktori ini
**Kontrak induk:** `../AGENTS.md` (root `RPL/`)
**Tanggal:** 24 Oktober 2026

## 1. Tujuan dan cakupan

Kontrak ini mengikat setiap agen yang bekerja di `Camera/`: aplikasi Android
pemilah objek (kamera, inferensi ONNX lokal, pelacakan, pemilihan target,
pemetaan bak, profil model, kalibrasi, kontrak pesan controller, antrean
telemetri). Kontrak ini menambah batasan operasional di atas kontrak induk dan
tidak menggantikannya.

Pekerjaan firmware (`Controller/`), backend (`Backend/`), dashboard
(`Frontend/`), dan pelatihan model berada di luar repo ini. Jangan mengerjakannya
dari sini.

## 2. Sumber kebenaran dan konflik

| Urutan | Sumber | Wewenang |
|---|---|---|
| 1 | Instruksi eksplisit pengguna dalam sesi aktif | Perubahan lingkup yang diotorisasi |
| 2 | `../PRD Sistem Pemilah Objek.md` | Kebutuhan produk dan batas lingkup |
| 3 | `../AGENTS.md` | Proses kerja, artefak, lifecycle, pembuktian |
| 4 | `AGENTS.md` (berkas ini) | Batasan operasional khusus repo Camera |
| 5 | `../.work/DECISIONS.md` | Keputusan teknis yang disetujui beserta sumbernya |
| 6 | `.work/PRD.md`, `.work/Architecture.md`, `.work/Schema.md`, `.work/Styleguide.md`, `.work/Design.md`, `.work/Rules.md` | Turunan teknis repo ini |
| 7 | `../.work/Plan_AndroidPemilah_23102026-124815.md` | Urutan eksekusi work package |
| 8 | Kode dan test | Implementasi; bukan dasar mengganti kebutuhan |

Konflik dengan sumber lebih tinggi dicatat sebagai `BLOCKED:` di
`../.work/FINDINGS.md` (bagian 12). Jangan mengubah PRD induk, kontrak induk,
atau dokumen `.work/` untuk "menyelesaikan" konflik tanpa otorisasi pengguna.
Dokumen yang belum ada tidak dianggap telah disepakati.

## 3. Wajib membaca `.work/` sebelum bekerja

Aturan ini tidak boleh dilewati, termasuk untuk tugas yang tampak sepele
(perbaikan satu baris, rename, menjawab pertanyaan teknis, atau membuat rencana).

### 3.1 Bacaan wajib sebelum tindakan pertama

Urutan baca:

1. `AGENTS.md` (berkas ini)
2. `.work/PRD.md`
3. `.work/Architecture.md`
4. `.work/Schema.md`
5. `.work/Styleguide.md`
6. `.work/Design.md` dan `.work/OperationScreen.xml` (wajib bila menyentuh UI)
7. `.work/Rules.md`
8. `../PRD Sistem Pemilah Objek.md`
9. `../.work/DECISIONS.md`
10. `../.work/FINDINGS.md`
11. `../.work/Plan_AndroidPemilah_23102026-124815.md` (WP yang relevan)

Berkas tambahan yang relevan: `.work/assets/OperationMockup-v1.png` untuk acuan
visual, dan `.work/INDEX.md` sebagai peta kode (lihat 3.3).

### 3.2 Kewajiban selama bekerja

- Sebutkan di awal jawaban dokumen `.work/` mana yang dibaca dan bagian mana
  yang menjadi dasar tindakan. Tanpa itu, hasil dianggap belum dapat diperiksa.
- Setiap klaim kepatuhan requirement harus menunjuk ID requirement dan bagian
  dokumen `.work/` atau PRD induk yang mendasarinya.
- Angka mesin (dimensi, kecepatan, jangkauan, ambang toleransi) hanya boleh
  berasal dari konfigurasi; nilainya dibaca dari dokumen `.work/`, bukan dikarang.
- Bila dokumen `.work/` saling bertentangan, hentikan bagian yang terdampak,
  catat temuan, dan lanjutkan hanya pekerjaan yang tidak bergantung padanya.
- Setelah pekerjaan mengubah keputusan, struktur, atau perilaku yang diatur
  `.work/`, perbarui dokumen `.work/` yang terkena pada pekerjaan yang sama.
  Jangan biarkan dokumen tertinggal dari kode.

### 3.3 Peta kode

Setelah kode ada, baca `.work/INDEX.md` sebelum membuka berkas sumber satu per
satu. Hasilkan/pembarui dengan:

```
node ~/.config/opencode/skills/init-work/scripts/index-project.mjs Camera
```

### 3.4 Larangan

- Dilarang memulai implementasi, membuat rencana, atau menyatakan sesuatu
  "sesuai spesifikasi" tanpa membaca himpunan di 3.1.
- Dilarang mengarang isi dokumen `.work/` yang belum ada, lalu memakainya
  sebagai dasar keputusan.
- Dilarang menyalin isi PRD induk ke `.work/` repo ini; rujuk bagiannya.
- Dilarang menulis `TODO`, `TBD`, atau janji tanpa kriteria terima di dokumen
  maupun rencana.

## 4. Larangan multi-agen dan sub-agen

Pekerjaan di repo ini dijalankan oleh **satu agen dalam satu sesi**. Aturan ini
bersifat mutlak.

Dilarang:

1. Membuat, memanggil, atau menjadwalkan sub-agen, agen paralel, agen latar
   belakang, atau delegasi ke sesi lain.
2. Memecah WP menjadi beberapa agen yang berjalan bersamaan, termasuk untuk
   pekerjaan yang tampak independen.
3. Menyerahkan penulisan kode, test, dokumen, atau bukti kepada agen lain lalu
   mengklaim hasilnya sebagai hasil kerja sesi ini.
4. Menyatakan telah melewati QC independen berdasarkan hasil sub-agen. QC
   independen hanya sah bila dilakukan pengguna atau sesi terpisah yang
   disetujui pengguna.
5. Melaporkan bukti yang tidak dihasilkan sendiri dalam sesi ini. Setiap bukti
   yang dicatat harus berasal dari perintah yang benar-benar dijalankan di sini.

Bila pekerjaan terasa terlalu besar: pecah menjadi work package yang lebih kecil
lewat Planner (bagian 10), bukan dengan menambah agen. Bila waktu atau konteks
habis, hentikan pada batas WP yang aman dan catat status sebenarnya.

## 5. Artefak kerja

Lokasi artefak mengikuti kontrak induk bagian 3:

| Artefak | Lokasi | Isi |
|---|---|---|
| Rencana fitur | `../.work/Plan_<NamaFitur>_<ddMMyyyy-HHmmss>.md` | Work package, traceability, dependensi |
| Temuan/penghambat | `../.work/FINDINGS.md` | `BLOCKED:` beserta dampak dan bukti penutupan |
| Keputusan teknis | `../.work/DECISIONS.md` | Usulan/disetujui, alasan, dampak, sumber persetujuan |
| Spesifikasi repo | `.work/*.md` | `PRD`, `Architecture`, `Schema`, `Styleguide`, `Design`, `Rules` |
| Bukti | `../.work/evidence/` | Keluaran perintah uji, catatan perangkat, bukti manual tanpa rahasia |

Konvensi nama dokumen repo ini memakai awalan kapital dan tidak diubah:
`PRD.md`, `Architecture.md`, `Styleguide.md`, `Design.md`, `Schema.md`,
`Rules.md`, `INDEX.md`. Rencana dan bukti tetap di `../.work/`.

## 6. Batas arsitektur

- Lapisan dan arah dependensi: `ui` → `domain` → `data` → `platform`. Tidak ada
  dependensi balik; `platform` tidak mengimpor `ui`.
- Satu state satu pemilik sesuai `.work/Architecture.md` bagian 4. UI tidak
  menulis state domain; UI mengirim peristiwa ke pemilik state.
- Aplikasi tidak menghitung timing aktuator dan tidak mengakses GPIO; seluruh
  kendali aktuator milik `Controller/`.
- Backend tidak pernah berada di jalur kritis; `OperationGuard` tidak memeriksa
  ketersediaan backend.
- Stack (bahasa, toolkit UI, library kamera/ONNX/USB, versi) masih usulan.
  Implementasi kode menunggu B-01 ditutup; usulan di `Architecture.md` bukan
  izin memasang dependensi.

## 7. Invarian produk yang mengikat repo ini

1. Start hanya setelah kamera, profil, konfigurasi, pemetaan kelas aktif, dan
   kalibrasi lolos validasi.
2. Profil wajib menghasilkan lokasi objek; profil tidak kompatibel ditolak
   dengan alasan spesifik, termasuk label gabungan `Kaleng`+`Besi`.
3. Pergantian profil hanya saat pemantauan berhenti dan target kosong.
4. Kamera tetap aktif saat robot sibuk; paling banyak satu target aktif per
   siklus; target sama tidak dipilih ulang.
5. Deteksi di luar area kerja tidak menjadi kandidat.
6. Objek tak dikenal, keyakinan rendah, atau terlewat dibiarkan bersirkulasi.
7. Prediksi memakai encoder aktual dan usia pengamatan; pesan terlambat tidak
   dianggap posisi terkini.
8. Identitas perintah dan target tertelusur; pengiriman ulang tidak memicu
   siklus ganda.
9. Kelas aktif tanpa tujuan bak valid memblokir start.
10. Perubahan geometri menandai kalibrasi perlu diperiksa; start ditolak selama
    tanda itu ada.
11. Gerakan selesai bukan bukti objek terambil. Tanpa verifikasi, hasil ditulis
    "belum terverifikasi", bukan sukses.
12. Penghentian darurat perangkat keras dan catu daya aktuator terpisah adalah
    dependensi fisik; tombol aplikasi tidak membuktikannya.

## 8. Identitas requirement

Gunakan ID asli PRD induk: `CAM-01–06`, `MOD-01–06`, `CON-01–05`, `ARM-01–06`,
`COM-01–06`, `CAL-01–06`, ditambah alias `NFR-01–07`, `SC-01–11`, `UI-01–06`,
`CFG-01`, `CFG-02`, `OUT-01` sesuai kontrak induk bagian 6. Jangan mengganti
dengan ID baru.

Kebutuhan tambahan repo ini yang belum ber-ID induk: `COM-07`–`COM-09` (antrean
telemetri ke backend). Setiap kebutuhan tanpa work package adalah cacat
perencanaan.

## 9. Kontrak work package

Format dan lifecycle mengikuti kontrak induk bagian 7:

```markdown
## WP-NN — Judul
Status: todo
File: daftar path eksplisit, maksimal lima file
Dependensi: WP prasyarat, requirement, dan ID penghambat bila ada
Tugas:
- [ ] T-NNN Aksi konkret (ID requirement)
Kriteria terima:
- Perintah/prosedur, hasil yang diharapkan, dan lokasi bukti
Risiko: risiko konkret dan mitigasinya

### Log WP-NN (Executor)
### Log WP-NN (QC)
```

- Satu WP menyentuh maksimal lima berkas, selesai di bawah 30 menit, dan menjaga
  build tetap berjalan setelah skeleton ada.
- Lifecycle `todo → wip → qc → done`; QC menolak berarti `rework → qc`. Hanya
  satu WP dikerjakan dalam satu waktu.
- WP yang `done` tidak boleh dihapus atau dinomori ulang; WP baru memakai nomor
  lanjutan.
- Executor mencatat berkas yang disentuh, hasil pemeriksaan, dan keterbatasan
  sebelum menyerahkan ke QC. QC mencatat bukti dan temuan.

## 10. Perencanaan ulang

Planner dijalankan sebelum WP pertama, setiap tiga WP `done`, dan setiap kali
PRD, kontrak, keputusan teknis, arsitektur, atau struktur berubah — dijalankan
oleh agen yang sama dalam sesi ini, bukan didelegasikan. Saat perencanaan ulang:
pertahankan riwayat WP selesai, evaluasi ulang dependensi dan traceability, dan
pastikan seluruh requirement (fungsional, nonfungsional, UI, konfigurasi,
skenario) masih memiliki WP dan metode pembuktian.

## 11. Gerbang mutu dan pembuktian

Perintah baseline setelah scaffold Gradle disetujui dan nama task diverifikasi
pada proyek aktual, dijalankan dari `android/`:

- Build: `./gradlew assembleDebug`
- Unit test: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Uji perangkat bila tersedia: `./gradlew connectedDebugAndroidTest`

Selama proyek atau perintah belum ada, jangan mengklaim build/test lulus. Build
wajib untuk perubahan kode atau konfigurasi build; test dan lint dipilih menurut
dampak perubahan dan kriteria WP. Jangan membuat test yang menyalin implementasi.

Bukti bermakna wajib ada untuk: validasi profil, filter area dan aturan target,
target aktif tunggal, deduplikasi perintah, pengamatan terlambat, perubahan
encoder/kecepatan, koneksi putus, pemetaan bak, dan invalidasi kalibrasi.
Simulator boleh membuktikan logika dan kontrak pesan; simulator tidak
membuktikan kompatibilitas USB atau gerak fisik.

Tandai `MANUAL-VERIFY` untuk: kamera/USB pada HP sasaran, encoder dan jarak
nyata, jangkauan/batas robot, pengambilan bergerak, ukuran kelompok, resirkulasi
conveyor, emergency stop, catu daya terpisah, dan verifikasi keberhasilan jepit.
Catat perangkat/versi, konfigurasi, prosedur, hasil, tanggal, dan bukti di
`../.work/evidence/`. Status belum diuji dinyatakan jelas.

Ambang FPS, latensi, toleransi posisi, rentang kecepatan, dan tingkat keberhasilan
numerik menunggu B-06. WP yang membutuhkannya tidak boleh `done` atas dasar
pengamatan subjektif.

## 12. Penghambat dan temuan

Format: `BLOCKED: <ID> — <masalah>` disertai sumber PRD, WP terdampak, keputusan
atau bukti yang dibutuhkan, dan status terbuka/selesai, dicatat di
`../.work/FINDINGS.md`.

Penghambat yang menahan repo ini: B-01 (stack dan perangkat), B-02 (model tujuh
kelas), B-03 (geometri kamera/area kerja), B-04 (protokol controller), B-06
(ambang numerik), B-08 (backend), B-14 (batas perhitungan lintasan), B-15
(definisi kelas), B-16 (alokasi kelas ke bak).

Agen hanya menahan WP yang bergantung pada penghambat tersebut; pekerjaan
independen boleh berlanjut. Agen tidak menutup penghambat dengan menetapkan
pilihannya sendiri; penutupan memerlukan keputusan atau bukti yang disetujui
pengguna dan dicatat di `../.work/DECISIONS.md`.

## 13. Aturan commit

Setiap perubahan apa pun di repo ini **wajib langsung di-commit**. Berlaku untuk
semua jenis perubahan: satu baris kode, test, dokumen `.work/`, `AGENTS.md`,
berkas konfigurasi, aset, berkas yang dihapus, maupun perbaikan salah ketik.

Aturan:

1. **Hanya commit.** Jangan `push`, jangan buat tag, jangan buat/merge/pull
   branch, jangan rebase, jangan amend commit yang sudah ada, jangan menulis
   ulang riwayat, dan jangan mengubah konfigurasi remote. Sinkronisasi ke remote
   hanya dilakukan bila pengguna meminta secara eksplisit.
2. Satu perubahan logis satu commit. Jangan menggabungkan perubahan yang tidak
   berhubungan, dan jangan meninggalkan perubahan yang belum di-commit di akhir
   jawaban.
3. Jalankan `git status` sebelum commit dan hanya stage berkas yang memang
   diubah pada pekerjaan ini. Jangan memakai `git add -A`, `git add .`, atau
   `git commit -a`; berkas asing, artefak build, dan berkas rahasia tidak boleh
   ikut masuk.
4. Format pesan: baris pertama `<tipe>(<lingkup>): <ringkasan>` dengan tipe
   `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, atau `build`; ringkasan
   imperatif, bahasa Indonesia, maksimal 72 karakter; boleh ada badan pesan satu
   paragraf bila perlu alasan. Contoh:
   `docs(camera): tambah kontrak agen dan aturan commit`.
5. Sebutkan hash commit pada jawaban akhir, bersama daftar berkas yang berubah.
   Tanpa hash, pekerjaan dianggap belum selesai.
6. Jangan commit bila pemeriksaan pada bagian 11 gagal karena kesalahan yang
   disebabkan perubahan ini. Jangan pula commit perubahan yang belum diperiksa.
   Bila commit tidak dapat dilakukan (repo belum diinisialisasi, hook menolak,
   identitas git belum diatur, atau berkas terkunci), hentikan dan laporkan
   alasannya — jangan mengakalinya dengan `--no-verify`, `--force`, atau
   mengubah konfigurasi git.
7. Repo ini saat ini belum memiliki `.git` (belum diinisialisasi). Aturan ini
   berlaku sejak repo git tersedia; jangan menginisialisasi repo, membuat
   `.gitignore`, atau mengubah identitas git tanpa persetujuan pengguna.

## 14. Checklist sesi

Sebelum tindakan pertama:

1. Konfirmasi direktori kerja `Camera/` dan bukan home directory.
2. Baca himpunan wajib pada bagian 3.1.
3. Tentukan WP yang dikerjakan beserta requirement dan penghambatnya.
4. Pastikan pekerjaan dapat diselesaikan satu agen dalam satu WP.

Sebelum menutup sesi:

1. Catat berkas yang berubah, perintah yang dijalankan, dan hasil sebenarnya.
2. Perbarui `.work/` yang terkena dan log WP bila ada.
3. Nyatakan bagian yang belum terverifikasi atau masih terblokir.
4. Jangan mengklaim `done` tanpa kriteria terima terpenuhi dan bukti tersimpan.
5. Pastikan setiap perubahan sudah di-commit sesuai bagian 13 dan laporkan
   hash-nya; jangan biarkan ada perubahan yang menggantung.
