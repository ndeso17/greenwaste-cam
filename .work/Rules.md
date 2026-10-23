# Rules — Camera

**Repo:** `Camera/`
**Status:** Draf kerja

## 1. Dokumen wajib dibaca sebelum bekerja

1. `Camera/.work/PRD.md`
2. `Camera/.work/Architecture.md`
3. `Camera/.work/Styleguide.md`
4. `Camera/.work/Design.md`
5. `Camera/.work/Schema.md`
6. `Camera/.work/Rules.md`
7. `AGENTS.md` di root repo
8. `PRD Sistem Pemilah Objek.md` di root repo
9. `.work/Plan_AndroidPemilah_23102026-124815.md` di root repo (rencana eksekusi)
10. `.work/FINDINGS.md` di root repo (penghambat)

Baca `.work/INDEX.md` sebelum membaca berkas source satu per satu setelah kode
ada. `INDEX.md` dihasilkan dengan:

```
node ~/.config/opencode/skills/init-work/scripts/index-project.mjs Camera
```

Jalankan ulang setelah perubahan kode besar, sebelum memulai fitur berikutnya.

## 2. Batas keras

1. **Dilarang memilih atau memasang stack sebelum B-01 ditutup.** Usulan stack
   pada `Architecture.md` bukan izin implementasi.
2. Dilarang menetapkan angka mesin (dimensi, kecepatan, jangkauan, ambang
   toleransi) di kode. Semua berasal dari konfigurasi.
3. Dilarang menetapkan ambang kelulusan FPS atau latensi sebelum B-06.
4. Dilarang mengklaim objek berhasil diambil berdasarkan status gerakan.
5. Dilarang menambah kelas di luar tujuh kelas resmi PRD induk bagian 5.
6. Dilarang menerima profil model yang menggabungkan `Kaleng` dan `Besi`.
7. Dilarang mengubah `PRD Sistem Pemilah Objek.md` atau `AGENTS.md` dari repo ini.
8. Dilarang menjadikan backend sebagai syarat operasi.
9. Dilarang menaruh logika kendali aktuator di aplikasi.

## 3. Aturan requirement

- Gunakan ID PRD asli: `CAM-`, `MOD-`, `CON-`, `ARM-`, `COM-`, `CAL-`, dan alias
  `NFR-`, `SC-`, `UI-`, `CFG-`, `OUT-` sesuai `AGENTS.md` bagian 6.
- Kebutuhan baru yang belum ber-ID di PRD induk ditandai `COM-07`–`COM-09`
  (antrean telemetri) dan dicatat di `.work/FINDINGS.md` root sebelum dipakai.
- Satu kebutuhan tanpa work package adalah cacat perencanaan, bukan kebebasan.

## 4. Aturan kerja per work package

- Satu WP menyentuh maksimal lima berkas dan selesai di bawah 30 menit.
- Lifecycle `todo → wip → qc → done`; QC menolak berarti `rework → qc`.
- Setiap WP harus menjaga build tetap berjalan setelah skeleton ada.
- Build, test, dan lint memakai perintah yang tercatat di `.work/DECISIONS.md`.
- Bukti disimpan di `.work/evidence/`; klaim tanpa bukti ditolak.

## 5. Konvensi kode

Ikuti `Styleguide.md`. Ringkasnya:

- Kode berbahasa Inggris, teks UI dan dokumen berbahasa Indonesia.
- Dilarang `!!`, dilarang `catch (e: Exception) {}` kosong.
- Satu berkas satu tanggung jawab; test berpasangan `XTest.kt`.
- Tidak ada logika domain di dalam Composable.

## 6. Konvensi dokumen

- Dokumen `.work/` repo ini memakai awalan nama berkas kapital: `PRD.md`,
  `Architecture.md`, `Styleguide.md`, `Design.md`, `Schema.md`, `Rules.md`,
  `INDEX.md`.
- Dokumen pipeline tetap di `.work/` root dengan pola `AGENTS.md` bagian 3.
- Jangan menyalin isi PRD induk ke sini; rujuk bagiannya.

## 7. Larangan dokumentasi

- Dilarang menulis `TODO`, `TBD`, atau janji tanpa kriteria terima di dokumen
  maupun rencana.
- Dilarang menandai sesuatu "sudah diuji" tanpa perangkat, prosedur, hasil, dan
  tanggal pada `.work/evidence/`.
- Dilarang menghapus atau merenumber work package yang sudah `done`.

## 8. Penghambat yang menahan repo ini

| ID | Menahan |
|---|---|
| B-01 | Seluruh implementasi (stack dan perangkat) |
| B-02 | Detector, decoder, validator profil (model tujuh kelas belum ada) |
| B-03 | Kalibrasi kamera, blok, filter area |
| B-04 | CommandCodec, ControllerLink |
| B-06 | Kriteria terima berangka, verifikasi pengambilan |
| B-08 | TelemetryQueue tujuan backend |
| B-14 | Batas perhitungan lintasan |
| B-15 | Definisi operasional kelas |
| B-16 | Alokasi tujuh kelas ke enam bak |

Rincian: `.work/FINDINGS.md` root.
