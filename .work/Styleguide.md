# Styleguide — Camera

**Repo:** `Camera/`
**Status:** Draf kerja
**Berlaku untuk:** kode Kotlin, Compose UI, dan berkas konfigurasi di repo ini

## 1. Bahasa dan penamaan

- Kode, komentar kode, nama variabel, dan nama berkas memakai bahasa Inggris.
- Dokumen `.work/*.md`, teks UI untuk operator, dan pesan galat ke pengguna memakai bahasa Indonesia.
- Nama kelas `PascalCase`, fungsi dan variabel `camelCase`, konstanta `UPPER_SNAKE_CASE`.
- Berkas Kotlin mengikuti nama deklarasi utama: `TargetSelector.kt`.
- Paket: `id.pemilah.<lapisan>.<fitur>`, misalnya `id.pemilah.domain.target`.
- Nama state dan peristiwa memakai kata benda dan kata kerja lampau: `ActiveTarget`, `ProfileSwitched`.

## 2. Struktur berkas

- Satu berkas satu tanggung jawab utama. Berkas lebih dari ~250 baris dipecah.
- Berkas test berpasangan satu-satu: `X.kt` → `XTest.kt`.
- Tidak ada logika domain di dalam Composable; Composable menerima state dan lambda.
- Konstanta ajaib dilarang. Nilai yang dapat berubah adalah konfigurasi.

## 3. Gaya Kotlin

- Hentikan baris pada 100 kolom.
- Ekspresi tunggal memakai `=` untuk fungsi pendek.
- `val` diutamakan; `var` hanya untuk state lokal yang jelas.
- Type eksplisit pada tanda tangan publik.
- Dilarang `!!`. Gunakan `requireNotNull`, `?: error("alasan")`, atau tipe nullable eksplisit.
- Eksepsi untuk kesalahan pemrograman; kegagalan yang diprediksi memakai tipe hasil
  (`sealed interface ValidationResult`), bukan `throw`.
- Tidak ada `catch (e: Exception) { }` kosong. Setiap tangkapan harus dilaporkan
  atau diubah menjadi status yang terlihat pengguna.

## 4. Compose

- Satu screen satu file `*Screen.kt`; state hoisted ke `*ViewModel`.
- Composable tidak melakukan I/O. I/O lewat ViewModel atau UseCase.
- Overlay deteksi menggambar di koordinat kanvas setelah transformasi
  eksplisit; tidak ada asumsi rasio aspek di dalam Composable.
- Pratinjau kamera tidak pernah diblokir oleh overlay atau dialog.

## 5. Penanganan galat

- Setiap kegagalan platform (izin kamera, model gagal muat, USB putus, storage
  penuh) menghasilkan nilai status dengan penyebab yang dapat ditampilkan.
- Pesan galat ke operator menyebut apa yang gagal, apa dampaknya, dan apa yang
  harus dilakukan. Contoh: "Model tidak dapat dimuat. Pemilahan tidak dapat
  dimulai. Periksa berkas profil di menu Model & Kelas."
- Log tidak boleh memuat rahasia; tidak ada rahasia pada repo ini.

## 6. Testing

- Nama test memakai pola `fungsi_kondisi_harapan` dalam bahasa Inggris.
- Unit test untuk semua aturan domain: validator profil, seleksi target,
  tracker, filter area, invalidaasi kalibrasi, antrean telemetri.
- Test tidak menyalin implementasi. Asersi pada perilaku yang teramati.
- Bukti command disimpan di `.work/evidence/` sesuai `AGENTS.md` bagian 8.
- Nilai ambang numerik pada test harus berasal dari fixture bernama, bukan angka
  telanjang di dalam asersi.

## 7. Larangan

- Dilarang menulis angka mesin (dimensi, kecepatan, jangkauan) di kode.
- Dilarang menetapkan ambang kelulusan FPS atau latensi tanpa keputusan B-06.
- Dilarang mengklaim "berhasil terambil" dari status gerakan controller.
- Dilarang menambah library baru tanpa entri keputusan.
- Dilarang mengubah `PRD Sistem Pemilah Objek.md` atau `AGENTS.md` dari repo ini.

## 8. Dokumentasi

- Setiap keputusan teknis non-trivial dicatat di `.work/DECISIONS.md` root.
- Setiap penyimpangan dari dokumen ini dicatat di log WP, bukan di komentar kode.
