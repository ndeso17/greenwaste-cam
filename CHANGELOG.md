# Changelog

## Belum dirilis — 27 Oktober 2026

- Tambahkan Uji Model dengan Mulai Uji/Stop Uji serta penyimpanan gambar dan prediksi per objek.
- Tambahkan Galeri untuk koreksi kelas dan penghapusan gambar/anotasi lokal.
- Ganti jalur aplikasi dari kotak petak menjadi proposal kotak YOLO, lalu klasifikasi ResNet18 per crop; dukung beberapa kotak dan warna tetap per kelas.
- Muat referensi visual terkonfirmasi saat mulai pemantauan; tampilkan kemiripan terpisah dari probabilitas model, tanpa mengubah bobot ONNX.
- Selaraskan rotasi/aspek frame kamera dan overlay; tolak hasil inferensi dari sesi yang sudah dihentikan.
- Tambahkan pemeriksaan decoder, multiobjek, penyimpanan, koreksi, kegagalan simpan dan pergantian sesi.
- Tambahkan titik bantu bernomor berwarna sama dengan kotak pada pratinjau kamera dan gambar Galeri; ketukan titik memilih objek.
- Tambahkan pilihan jamak di Galeri: pilih gambar/objek, labeli terpilih, dan hapus terpilih dengan konfirmasi serta laporan kegagalan sebagian.
- Kembalikan pratinjau kamera memenuhi layar (FILL_CENTER) dan selaraskan overlay dengan transformasi isi, bukan letterbox.
- Batas: detektor lama belum menjamin cakupan tujuh kelas; pencocokan referensi peka perubahan tampilan dan belum membuktikan peningkatan akurasi lapangan.

## v0.1.0 — 27 Oktober 2026

Rilis pertama GreenWaste Cam berfokus pada **UI menu kamera (Operasi)**.
Rilis ini menyediakan pratinjau kamera dan antarmuka pemantauan awal;
integrasi deteksi ONNX serta kendali robot belum selesai.

### Perubahan UI kamera

- Pratinjau kamera memenuhi area konten dengan kontrol mengambang di bawah tengah.
- Tombol utama bergaya shutter: lingkaran play saat idle dan kotak merah saat pemantauan aktif, dilengkapi animasi tekan/transisi dan label aksesibel.
- Tombol sekunder **Berhenti** muncul selama pemantauan aktif.
- Ringkasan Sesi mengambang di atas kamera: vertikal dengan nama lengkap saat portrait, ringkas horizontal saat landscape. Backend dan Riwayat dihapus dari ringkasan.
- Status Kamera, Kalibrasi dan USB mengambang di bawah dengan label dan titik: hijau untuk aktif/sah/terhubung, abu-abu untuk belum disiapkan/terhubung, merah untuk error. Detail tetap tersedia untuk pembaca layar.
- Overlay memakai latar gelap semi-transparan dengan blur pada Android yang mendukung, serta teks putih untuk meningkatkan keterbacaan.
- Panel kanan dapat digulir dan disembunyikan, dengan keadaan awal tersembunyi. Alasan operasi belum dapat dimulai berada di panel kanan.
- Menu kiri awalnya diperkecil; header judul/profil dihilangkan untuk memperluas kamera.
- Orientasi mengikuti sensor HP untuk portrait/landscape, termasuk ketika rotasi sistem dikunci.
- Area aman status bar dan navigasi sistem diterapkan pada semua menu; tata letak menyesuaikan ponsel dan tablet.

### Validasi

- Build debug berhasil; 145 unit test lulus.
- Android lint: 0 error, 22 warning yang masih tercatat.
- Pemeriksaan perangkat terakhir: 9 pemeriksaan UI/orientasi lulus pada moto g57 power, Android 16, termasuk portrait, landscape dan pembesaran teks 1,3×.
- Tablet diperiksa melalui simulasi ukuran layar; belum diuji pada tablet fisik.

### Unduhan dan batasan rilis

- `greenwaste-cam-v0.1.0-debug.apk` adalah APK **debug bertanda tangan untuk uji coba**, Android 8.0/API 26 atau lebih baru. Bukan build produksi; fixture pengujian UI disertakan hanya pada build debug.
- Menu Models, Bot, Settings, Kalibrasi dan Logs belum menyediakan alur konfigurasi lengkap.
- Logika domain dan transport tersedia, tetapi jalur inferensi, deteksi serta pengendalian controller belum tersambung menyeluruh. USB dan gerak robot fisik belum terverifikasi.
- Tombol aplikasi bukan emergency stop perangkat keras. Nilai/status yang belum tersedia tidak dianggap siap.

### Rencana berikutnya

Daftar ini merupakan pekerjaan lanjutan, bukan fitur yang sudah tersedia atau janji jadwal rilis.

- [ ] Lengkapi menu Models: impor/validasi profil ONNX, label kelas dan pemilihan profil sesuai kontrak model yang disepakati.
- [ ] Hubungkan inferensi kamera, overlay deteksi dan pelacakan setelah model serta geometri tersedia.
- [ ] Lengkapi Settings, Bot dan Kalibrasi, termasuk area kerja, konfigurasi mesin dan pemetaan kelas ke bak.
- [ ] Hubungkan USB ESP32-S3 dan status controller sesuai protokol yang disepakati; uji sambungan ulang dan gangguan pada perangkat nyata.
- [ ] Hubungkan pemilihan target dan siklus pemilahan satu robot setelah dependensi firmware/mekanik terpenuhi.
- [ ] Lengkapi Logs dan riwayat sesi, dengan pemisahan gerak selesai dan keberhasilan pengambilan terverifikasi.
- [ ] Uji rotasi fisik bolak-balik, tablet fisik, aksesibilitas serta kinerja kamera pada perangkat sasaran; tetapkan ambang kinerja bersama setelah perangkat dipilih.
- [ ] Siapkan build distribusi produksi beserta pengelolaan signing sebelum distribusi umum.
- [ ] Evaluasi kelompok objek dan dua robot sesuai urutan tahap PRD setelah prototipe satu robot terverifikasi.
