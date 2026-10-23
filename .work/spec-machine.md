# Spec — Konfigurasi mesin dan kalibrasi (WP-05)

**Repo:** `Camera/`
**Status:** Spesifikasi parameter; **nilai numerik belum ada** (B-03 terbuka)
**Sumber:** PRD induk §4, §10, §11; `.work/Schema.md` §4–5; `.work/Architecture.md` §3, §6
**Dokumen terkait:** `MachineConfig` (WP-10), `ConfigStore` (WP-11),
`CameraCalibration` (WP-17), `CalibrationValidity` (WP-19)

## 1. Aturan dasar

1. Seluruh parameter pada dokumen ini adalah **konfigurasi**, bukan konstanta
   kode. Tidak ada angka mesin di dalam sumber Kotlin (`Rules.md` §2.2).
2. `null` berarti **belum dikonfigurasi**, bukan nol. Nilai `null` pada
   parameter yang dibutuhkan memblokir start, bukan diganti default.
3. Angka pada dokumen ini **tidak ada yang berasal dari mesin terpasang**.
   Semua nilai final menunggu pengukuran (B-03) dan dicatat di
   `../.work/evidence/`.
4. Konfigurasi contoh/uji wajib ditandai sebagai fixture dan tidak boleh
   menjadi nilai bawaan aplikasi.
5. Konfigurasi tidak membuktikan kemampuan mekanik (PRD induk §10 penutup,
   `AGENTS.md` invarian 10).

## 2. Parameter conveyor (CFG-01)

| Field | Tipe | Satuan | Validasi | Sumber PRD |
|---|---|---|---|---|
| `shape` | enum/daftar segmen | — | segmen tidak boleh kosong bila diisi | §10 Conveyor butir 1 |
| `dimensions` | panjang per segmen | milimeter | > 0, finite | §10 butir 1 |
| `surfaceWidth` | bilangan | milimeter | > 0, finite | §10 butir 2 |
| `turnRadius` | bilangan | milimeter | > 0, finite | §10 butir 3 |
| `direction` | enum (`clockwise`, `counterclockwise`) | — | wajib bila diisi | §10 butir 4 |
| `speedMin`, `speedMax` | bilangan | meter/detik | `0 < speedMin <= speedMax` | §10 butir 5 |
| `encoderTicksPerMeter` | bilangan | tick/meter | > 0, finite | §10 butir 6 |
| `detectionArea` | poligon/rentang | milimeter | titik ≥ 3, tidak degenerate | §10 butir 7 |
| `pickupArea` | poligon/rentang | milimeter | titik ≥ 3, tidak degenerate | §10 butir 7 |
| `speedUnit` | teks | — | dipakai label UI, bukan nilai | `Design.md` §4.2 |

Aturan tambahan:

- `speedMin`/`speedMax` tidak boleh ditetapkan sebelum B-06; nilai diisi
  operator setelah mesin diukur.
- `encoderTicksPerMeter` = 0 ditolak (pembagian nol pada konversi jarak).
- Perubahan `dimensions`, `surfaceWidth`, `turnRadius`, `detectionArea`, atau
  `pickupArea` memicu penanda kalibrasi `needs_check` (CAL-06).

## 3. Parameter setiap armbot (CFG-02)

| Field | Tipe | Satuan | Validasi | Sumber PRD |
|---|---|---|---|---|
| `robotId` | `left` \| `right` | — | unik per konfigurasi | §4, §10 |
| `enabled` | boolean | — | — | §10 |
| `basePosition` | titik | milimeter | finite | §10 armbot butir 1 |
| `segmentLengths` | daftar bilangan | milimeter | semua > 0; jumlah ruas = 3 | §10 butir 2 |
| `mountHeight` | bilangan | milimeter | ≥ 0 | §10 butir 2 |
| `zeroAngles` | daftar sudut | derajat | panjang = jumlah sendi | §10 butir 3 |
| `rotationDirections` | daftar enum | — | panjang = jumlah sendi | §10 butir 3 |
| `baseLimits` | rentang sudut | derajat | `min <= max`, di dalam batas kendali | §10 butir 3 |
| `jointLimits` | daftar rentang | derajat | tiga sendi; `min <= max` | §10 butir 4 |
| `gripperOpening` | rentang | milimeter | `0 < min <= max` | §10 butir 5 |
| `maxTargetSize` | dimensi | milimeter | > 0 | §10 butir 5 |
| `pickupArea` | poligon | milimeter | titik ≥ 3 | §10 butir 6 |
| `binPositions` | peta bak → titik | milimeter | bak harus milik robot ini | §10 butir 7 |
| `motionParameters` | peta nama → nilai | sesuai parameter | wajib bertanda "telah diuji" | §10 butir 8 |
| `bins` | daftar id bak | — | `left` → B1–B3, `right` → B4–B6 | §4, invarian 9 |

Aturan tambahan:

- Bak 1–3 hanya untuk `left`, 4–6 hanya untuk `right`; pelanggaran ditolak
  sebagai konfigurasi tidak sah (ARM-06, MOD-05).
- `motionParameters` tidak diisi dari perkiraan; hanya dari hasil uji mekanik
  (PRD induk §10 butir 8). Bila kosong, siklus pemilahan tetap terblokir.
- Aplikasi **tidak** memakai parameter ini untuk menghitung lintasan; batas
  perhitungan lintasan belum diputuskan (B-14).

## 4. Data kalibrasi kamera (CAL-01–CAL-06)

Mengikuti `.work/Schema.md` §5. Spesifikasi tambahan:

| Field | Validasi | Aturan |
|---|---|---|
| `captureResolution` | `width > 0`, `height > 0` | perubahan memaksa `needs_check` |
| `rotationDegrees` | salah satu dari `0/90/180/270` | perubahan memaksa `needs_check` |
| `referencePoints` | ≥ 3 titik, `image` dan `world` terisi | titik degenerate ditolak |
| `mappingMethod` | enum metode yang disetujui | `null` → kalibrasi tidak sah (B-03) |
| `reprojectionError` | bilangan ≥ 0 | ditampilkan ke operator (CAL-01) |
| `validity.state` | `valid` \| `needs_check` | tidak ada pembersihan otomatis |
| `validity.reason` | teks | wajib bila `needs_check` |

Aturan:

1. Status `valid` hanya dapat ditetapkan operator setelah pemeriksaan;
   aplikasi tidak pernah menaikkan status sendiri.
2. `mappingMethod` `null` membuat kalibrasi tidak sah meski titik acuan ada.
3. Galat pemetaan wajib ditampilkan pada menu Kalibrasi (CAL-01).
4. Kalibrasi encoder dan robot (CAL-02–CAL-05) menggunakan konfigurasi §2–§3
   sebagai acuan; nilai pembuktian tetap MANUAL-VERIFY.

## 5. Versi dan penyimpanan

- Setiap berkas konfigurasi dan kalibrasi membawa `schemaVersion`; versi tak
  dikenal ditolak dengan alasan (`.work/Schema.md` §1).
- Konfigurasi disimpan lewat `ConfigStore` (WP-11); kegagalan simpan
  dilaporkan ke operator, tidak ditelan.
- Konfigurasi dan kalibrasi bertahan setelah aplikasi ditutup (NFR-03).

## 6. Yang belum dapat diisi (dependensi)

| Butir | Menahan | Syarat pembukaan |
|---|---|---|
| Seluruh nilai numerik §2–§3 | WP-10 nilai bawaan, WP-12 editor, WP-18 | Pengukuran mesin di `../.work/evidence/` (B-03) |
| `mappingMethod` dan titik acuan §4 | WP-17, WP-20 | Keputusan metode kalibrasi (B-03) |
| `speedMin`/`speedMax` | WP-28, WP-32 | B-06 |
| Batas lintasan | WP-25–WP-31 | B-04, B-14 |

Tidak ada nilai pengganti yang dikarang untuk butir di atas.
