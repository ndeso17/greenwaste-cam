# Schema — Camera

**Repo:** `Camera/`
**Status:** Draf kerja
**Format:** JSON untuk profil dan konfigurasi; tipe Kotlin sebagai rujukan lapisan

## 1. Prinsip

- Semua data persisten memiliki `schemaVersion` eksplisit.
- Versi skema tidak dikenal ditolak dengan alasan, bukan diabaikan.
- Tidak ada nilai default tersembunyi yang mengisi data hilang.
- Angka mesin tidak ditanam di kode; semuanya berasal dari berkas konfigurasi.

## 2. Kelas resmi (enum)

```
enum class WasteClass {
    ORGANIK, PLASTIK, KALENG, STYROFOAM, KACA, KARDUS, BESI
}
```

Tujuh nilai, urutan sesuai PRD induk bagian 5. Enum ini tidak boleh memuat
`KERTAS`, `LOGAM`, atau `UNKNOWN` sebagai kelas yang dapat dipetakan ke bak.
Objek tak dikenal diwakili `null` pada deteksi, bukan anggota enum.

## 3. ModelProfile

```json
{
  "schemaVersion": 1,
  "profileId": "sampah-v3",
  "name": "sampah-v3",
  "version": "8.3.0",
  "modelFile": "model-sampah-v3.onnx",
  "labels": ["Organik", "Plastik", "Kaleng", "Styrofoam", "Kaca", "Kardus", "Besi"],
  "classMapping": { "Organik": "ORGANIK" },
  "input": {
    "width": 640, "height": 640,
    "normalization": "scale_0_1",
    "layout": "nchw"
  },
  "output": {
    "format": "yolo_v8",
    "boxesNormalized": false,
    "hasObjectness": false
  },
  "confidenceThreshold": 0.0,
  "groupingEnabled": false,
  "binAssignment": { "ORGANIK": null }
}
```

**Asal nilai (D-09).** Berkas JSON ini tidak diimpor operator dan tidak
disunting tangan; ia dihasilkan `ModelImport` dari berkas `.onnx` yang dipilih:

| Field | Asal |
|---|---|
| `profileId` | nama berkas model tanpa akhiran `.onnx`, dijadikan bentuk aman berkas; bentrok nama diberi akhiran `-2`, `-3`, … sehingga impor tidak menimpa model lama |
| `name` | nama berkas model apa adanya; impor berulang dengan nama sama diberi akhiran pembeda (`sampah (2)`) supaya dua kartu tidak tampak identik |
| `version` | metadata `version` model; string kosong bila model tidak menuliskannya |
| `modelFile` | `model-<profileId>.onnx`, salinan biner di penyimpanan internal aplikasi |
| `labels` | metadata `names` model, urut indeks kelas |
| `classMapping` | pencocokan nama label model ke tujuh kelas resmi (tidak peka huruf besar, hanya huruf/angka); label yang tidak cocok tidak dipetakan dan ditolak validator |
| `input` | bentuk tensor masukan; `normalization` `scale_0_1` dan layout mengikuti posisi sumbu kanal |
| `output` | bentuk tensor keluaran: `4 + jumlah kelas` → `yolo_v8`, `5 + jumlah kelas` → `yolo_v5`, `[batch, jumlah kelas]` → `classification_only` (D-10); `boxesNormalized` `false` karena keluaran mentah YOLO memakai koordinat piksel |
| `confidenceThreshold` | `0.0` = belum ditetapkan; operator yang menetapkan |
| `groupingEnabled`, `binAssignment` | nilai netral saat impor; belum ada kendali pada layar Models |

Aturan validasi:

1. `labels` wajib berurutan sesuai keluaran model.
2. `classMapping` wajib memetakan setiap label ke satu `WasteClass`.
3. Satu `WasteClass` boleh menerima lebih dari satu label (mis. dua label kaca).
4. Satu label tidak boleh dipetakan ke lebih dari satu `WasteClass`.
5. Label yang memetakan ke dua kelas sekaligus (`KALENG` + `BESI`) ditolak.
6. `input.width`/`height` wajib sama dengan tensor model.
7. `confidenceThreshold` pada rentang `0.0 <= x <= 1.0`, dengan `0.0` berarti
   **belum ditetapkan**: profil sah dan tersimpan, tetapi aktivasi ditolak
   `ProfileSwitchGuard` sampai operator mengisi ambang.
8. `binAssignment` boleh kosong pada impor, tetapi kelas `null` memblokir start.
9. `modelFile` wajib menunjuk berkas yang ada di penyimpanan aplikasi; profil
   yang berkas modelnya hilang dilaporkan `MODEL_FILE_MISSING` dan tidak dapat
   diaktifkan.
10. `output.format` `classification_only` diterima (D-10); kebutuhan lokasi
    objek menjadi tanggung jawab pemasok kotak objek (deteksi) yang belum ada,
    sehingga model klasifikasi dapat diaktifkan tetapi belum menghasilkan
    kandidat pada kamera.

Nilai angka di atas adalah placeholder; nilai sah berasal dari model dan
keputusan B-02.

## 4. MachineConfig

```json
{
  "schemaVersion": 1,
  "conveyor": {
    "speedMin": null, "speedMax": null,
    "encoderTicksPerMeter": null,
    "direction": null
  },
  "workArea": {
    "units": "millimeter",
    "blocks": [{ "blockId": "B1", "polygon": [] }]
  },
  "robots": [
    {
      "robotId": "left",
      "bins": ["B1", "B2", "B3"],
      "enabled": true
    }
  ]
}
```

Aturan:

- Bak 1–3 milik robot `left`, 4–6 milik `right` (`AGENTS.md` invarian 9).
- `null` berarti belum dikonfigurasi, bukan nol.
- Perubahan geometri memicu penanda kalibrasi (CAL-06).

## 5. CameraCalibration

```json
{
  "schemaVersion": 1,
  "captureResolution": { "width": 0, "height": 0 },
  "rotationDegrees": 0,
  "referencePoints": [{ "image": { "x": 0, "y": 0 }, "world": { "x": 0, "y": 0 } }],
  "mappingMethod": null,
  "reprojectionError": null,
  "validity": {
    "state": "needs_check",
    "reason": null,
    "checkedAt": null,
    "checkedNote": null
  }
}
```

Aturan:

- `mappingMethod` `null` berarti metode belum diputuskan (B-03), sehingga
  kalibrasi tidak boleh dianggap sah.
- `state` bernilai `valid` atau `needs_check`. Tidak ada pembersihan otomatis.
- Perubahan `captureResolution` atau `rotationDegrees` memaksa `needs_check`.

## 6. Detection (runtime, tidak persisten)

| Field | Tipe | Catatan |
|---|---|---|
| `detectionId` | string | Unik per frame |
| `frameId` | long | Nomor frame |
| `capturedAt` | epoch ms | Waktu ambil frame |
| `analyzedAt` | epoch ms | Waktu analisis selesai |
| `box` | Rect | Koordinat frame asal |
| `label` | string | Label dari profil |
| `wasteClass` | WasteClass? | `null` bila tak dikenal |
| `confidence` | float | |
| `blockId` | string? | Blok hasil klasifikasi titik acuan |

## 7. TrackedObject

| Field | Tipe | Catatan |
|---|---|---|
| `trackId` | string | Stabil selama objek di area |
| `wasteClass` | WasteClass? | |
| `firstSeen` / `lastSeen` | epoch ms | |
| `history` | list of `{capturedAt, center}` | Untuk tahap 4 |
| `state` | enum | `tracked`, `released` |

## 8. ActiveTarget (kontrak record untuk tahap 4)

```json
{
  "targetId": "string",
  "trackId": "string",
  "wasteClass": "KALENG",
  "confidence": 0.0,
  "boxInFrame": { "x": 0, "y": 0, "w": 0, "h": 0 },
  "capturedAt": 0,
  "observationAgeMs": 0,
  "worldPosition": { "x": 0, "y": 0 },
  "blockId": "string",
  "targetRobot": "left",
  "targetBin": "B2",
  "graspEligibility": "unverified"
}
```

`graspEligibility` bernilai `unverified` sampai ada metode verifikasi yang
disepakati (B-06). Nilai `eligible` dilarang diisi berdasarkan ukuran kotak saja.

## 9. TelemetryEvent (ke backend)

```json
{
  "eventId": "uuid",
  "schemaVersion": 1,
  "sessionId": "uuid",
  "eventType": "cycle",
  "capturedAt": 0,
  "payload": {
    "targetId": "string",
    "wasteClass": "KALENG",
    "confidence": 0.0,
    "blockId": "B2",
    "robotId": "left",
    "binId": "B2",
    "motionStatus": "completed",
    "graspStatus": "unverified",
    "verificationMethod": "none"
  }
}
```

Aturan:

- `eventId` dipakai untuk idempotensi di backend.
- `motionStatus` dan `graspStatus` adalah dua field terpisah (OUT-01).
- `graspStatus` bernilai `verified`, `unverified`, atau `failed`; tidak ada
  nilai `success` yang boleh diisi tanpa `verificationMethod` selain `none`.

## 10. Yang belum diputuskan

Bentuk keluaran model (B-02), nilai numerik konfigurasi mesin (B-03), format
pesan serial (B-04), dan definisi operasional kelas (B-15).

## 11. Kontrak data sampel Uji Model dan anotasi Galeri

Sumber: PRD.md §11 U-02/U-05/U-06; instruksi pengguna 27 Oktober 2026.
Status: kontrak data produk untuk WP-01; format serialisasi, lokasi file,
representasi fitur, retensi dan mekanisme pencocokan bukan keputusan final
di bagian ini. Detail teknis ditetapkan WP-02 sesuai kewenangan arsitektur.

### Sampel gambar

| Data wajib | Makna / aturan |
|---|---|
| Versi skema | Eksplisit; versi tidak dikenal dilaporkan. |
| Identitas sampel dan sesi uji | Membedakan gambar dan sesi tanpa bergantung nama label. |
| Identitas gambar sumber | Merujuk gambar yang benar-benar tersimpan dan dapat dibaca. |
| Waktu pengambilan | Waktu frame, berbeda dari waktu selesai analisis/simpan. |
| Ukuran dan orientasi gambar tersimpan | Menentukan koordinat anotasi; tidak menganggap frame dan preview identik. |
| Identitas/versi profil klasifikasi dan detektor | Asal hasil dapat ditelusuri; koreksi profil berbeda tidak otomatis dicampur. |
| Daftar objek | Semua objek pada sampel memiliki identitas sendiri; bukan satu label untuk seluruh gambar. |

### Anotasi per objek

| Data wajib | Makna / aturan |
|---|---|
| Identitas objek dalam sampel | Stabil saat pengguna mengedit kelas; tidak mengandalkan urutan daftar. |
| Kotak persegi panjang | Mengacu ke gambar sumber tersimpan; area positif, berada dalam batas gambar. Konvensi angka/normalisasi ditetapkan WP-02. |
| Prediksi kelas asli dan keyakinan | Hasil awal model, tidak ditimpa koreksi pengguna. |
| Kelas koreksi | Salah satu tujuh kelas resmi §2; belum diisi saat otomatis tersimpan. |
| Status pemeriksaan | Belum diperiksa atau dikonfirmasi; prediksi otomatis bukan konfirmasi. |
| Waktu koreksi | Diisi setelah koreksi berhasil disimpan. |

Koreksi satu objek tidak mengubah objek lainnya. Mengonfirmasi kelas yang
sama dengan prediksi tetap memerlukan tindakan pengguna. Status dikonfirmasi
memerlukan kelas koreksi yang valid. Gagal simpan tidak boleh mengubah
status menjadi berhasil; gambar dan anotasi tidak boleh tertukar antar frame.
Penghapusan sampel menghapus gambar, anotasi dan pengaruh referensinya pada
sesi berikut, bila U-06 sudah diimplementasikan.

Data referensi U-06 harus dapat ditelusuri ke anotasi terkonfirmasi dan
profil asal. Tidak ada field yang menyatakan bobot ONNX telah terlatih
sebagai akibat menyimpan anotasi. Bentuk referensi dan aturan aktivasi
menunggu B-UM-02; inferensi tidak boleh menganggap semua sampel sebagai
label kebenaran. Kontur/mask segmentasi tidak termasuk kontrak ini.
