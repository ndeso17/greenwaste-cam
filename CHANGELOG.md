# Changelog

## v0.1.1 — 27 Oktober 2026

Rilis uji coba tahap kamera, Models, Uji Model dan Galeri. Checklist di bawah
mencatat implementasi yang tersedia; tidak menyatakan seluruh kebutuhan mesin
atau akurasi tujuh kelas telah terverifikasi.

### Checklist yang sudah diimplementasikan pada tahap ini

- [x] Models: impor berkas ONNX, baca metadata kelas/tensor, validasi kompatibilitas, simpan profil/model, pilih model aktif, atur ambang keyakinan dan hapus model dengan konfirmasi.
- [x] Inferensi ONNX lokal selama pemantauan; proposal lokasi YOLOv10n COCO diklasifikasi oleh ResNet18 aktif per objek, termasuk beberapa proposal dalam satu frame.
- [x] Overlay kotak, kelas, keyakinan, warna per kelas, nomor dan titik bantu objek; hasil terlambat dari sesi yang dihentikan ditolak.
- [x] Uji Model dengan Mulai Uji/Stop Uji, penyimpanan gambar sumber dan prediksi semua objek; penyimpanan dihentikan saat sesi berakhir.
- [x] Deduplikasi sampel uji berdasarkan posisi dan ciri visual, toleran variasi crop kamera; penanda tersimpan baru disahkan setelah penulisan berhasil.
- [x] Galeri: buka gambar/anotasi, koreksi per objek, pilih gambar/objek jamak langsung dari daftar, labeli massal dan hapus dengan konfirmasi/laporan hasil sebagian.
- [x] Border card hijau ketika seluruh objek sudah dikoreksi; merah ketika masih ada objek yang belum dikoreksi.
- [x] Foto detail Galeri mengikuti rasio sumber dan tampil utuh tanpa menimpa teks; titik/kotak tetap mengikuti bingkai foto.
- [x] Referensi koreksi lokal dimuat saat pemantauan dimulai; label dari referensi ditandai dengan kemiripan terpisah dari probabilitas model.
- [x] Pratinjau kamera memenuhi area tampilan dengan transformasi overlay yang sesuai dan garis bantu bidik yang dapat diatur.
- [x] Ringkasan sesi lebih transparan, blur lebih ringan, dapat disembunyikan dan ditampilkan kembali; pilihan bertahan saat rotasi tanpa menghentikan scan.

### Validasi

- Build debug, 369 unit test dan Android lint lulus; lint masih memiliki peringatan yang dicatat pada bukti rilis.
- Pada moto g57 power Android 16: detail foto diperiksa pada foto yang sama sebelum/sesudah, replay 14 foto QC melalui runtime model asli menghasilkan satu sampel/encoding, serta ringkasan diuji tampil/sembunyi/tampil dan rotasi.
- Hasil tersebut membuktikan kasus uji yang dijalankan, bukan jaminan seluruh kondisi pencahayaan, identitas objek, atau akurasi lapangan.

### Batasan dan checklist lanjutan

- [ ] Latih ulang model deteksi/klasifikasi dari koreksi Galeri. Implementasi sekarang hanya memakai referensi warna crop; bobot ONNX tetap sama dan pencocokan awal 97% sensitif perubahan tampilan.
- [ ] Tingkatkan serta evaluasi detektor khusus sampah. YOLOv10n COCO belum menjamin seluruh sampah terdeteksi; koreksi label tidak menambahkan kotak yang dilewatkan detektor.
- [ ] Tampilkan hasil dan alasan penyaringan setiap objek pada panel Operasi. Pipeline memproses multiobjek, tetapi panel saat ini menampilkan hasil teratas dan overlay menyaring keyakinan di bawah ambang profil kecuali cocok referensi.
- [ ] Hubungkan pelacakan identitas ke pipeline kamera dan area kerja terkalibrasi; heuristik deduplikasi Galeri bukan bukti pelacakan fisik.
- [ ] Lengkapi Bot, Settings, Kalibrasi dan Logs, serta pemetaan kelas/bak dan riwayat mesin.
- [ ] Integrasikan dan uji USB ESP32-S3, encoder, target, gerak robot dan pengambilan fisik; tombol aplikasi bukan emergency stop perangkat keras.
- [ ] Verifikasi tablet fisik, kinerja/akurasi pada data evaluasi terpisah dan ambang kelulusan yang disepakati.
- [ ] Siapkan signing dan build distribusi produksi sebelum distribusi umum.
- [ ] Lanjutkan kelompok objek dan dua robot setelah prototipe satu robot terverifikasi.

### Unduhan

`greenwaste-cam-v0.1.1-debug.apk` adalah APK debug bertanda tangan untuk uji coba,
Android 8.0/API 26 atau lebih baru. Model klasifikasi ONNX diimpor melalui
Models; model dan Galeri pribadi pengguna tidak disertakan dalam APK.

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

### Rencana berikutnya (status diperbarui pada v0.1.1)

Daftar ini merupakan pekerjaan lanjutan, bukan fitur yang sudah tersedia atau janji jadwal rilis.

- [x] Lengkapi menu Models: impor/validasi profil ONNX, label kelas dan pemilihan profil sesuai kontrak model yang disepakati (tersedia pada v0.1.1).
- [x] Hubungkan inferensi kamera dan overlay deteksi untuk pemantauan (tersedia pada v0.1.1).
- [ ] Hubungkan pelacakan objek dan area kerja mesin setelah geometri tersedia; bagian ini belum selesai.
- [ ] Lengkapi Settings, Bot dan Kalibrasi, termasuk area kerja, konfigurasi mesin dan pemetaan kelas ke bak.
- [ ] Hubungkan USB ESP32-S3 dan status controller sesuai protokol yang disepakati; uji sambungan ulang dan gangguan pada perangkat nyata.
- [ ] Hubungkan pemilihan target dan siklus pemilahan satu robot setelah dependensi firmware/mekanik terpenuhi.
- [ ] Lengkapi Logs dan riwayat sesi, dengan pemisahan gerak selesai dan keberhasilan pengambilan terverifikasi.
- [ ] Uji rotasi fisik bolak-balik, tablet fisik, aksesibilitas serta kinerja kamera pada perangkat sasaran; tetapkan ambang kinerja bersama setelah perangkat dipilih.
- [ ] Siapkan build distribusi produksi beserta pengelolaan signing sebelum distribusi umum.
- [ ] Evaluasi kelompok objek dan dua robot sesuai urutan tahap PRD setelah prototipe satu robot terverifikasi.
