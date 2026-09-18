# LOKASI SAYA + ADMIN — 2 APK

Project ini menghasilkan dua APK terpisah melalui GitHub Actions:

- `lokasi-saya-target.apk` → 📱 LOKASI SAYA
- `lokasi-saya-admin.apk` → 🖥️ ADMIN

## Fitur

LOKASI SAYA:
- 🔐 Nomor/kode pengguna
- 📍 GPS realtime
- 🎯 Akurasi GPS
- 🔋 Baterai
- 🚗 Bergerak / parkir
- ⏱️ Waktu update terakhir
- 🔄 Update sekitar setiap 10 detik
- 🔔 Foreground location service dengan notifikasi Android
- 🔥 Firebase Realtime Database

ADMIN:
- 👥 Tambah banyak target
- 🗺️ Google Maps
- 🎯 Marker per target
- 🔋 Baterai
- 🚗 Bergerak / parkir
- 🎯 Akurasi GPS
- ⏱️ Waktu update terakhir + umur data
- 🗺️ Tombol buka lokasi di Google Maps
- 💾 Daftar target disimpan di HP Admin

## Firebase

Project ID:
`meme-project-cab7f`

Database:
`https://meme-project-cab7f-default-rtdb.asia-southeast1.firebasedatabase.app/`

Aktifkan:
Firebase Console → Authentication → Sign-in method → Anonymous → Enable

Rules ada di:
`firebase-rules.json`

## Google Maps

Admin menggunakan Google Maps SDK for Android.

Simpan API key di GitHub:
Repository → Settings → Secrets and variables → Actions → New repository secret

Nama:
`MAPS_API_KEY`

Workflow menggunakan secret tersebut saat build.

## Build tanpa AIDE

Tidak memerlukan AIDE atau AndroidIDE.

1. Buat repository GitHub baru.
2. Upload seluruh isi project ini ke repository.
3. Tambahkan secret `MAPS_API_KEY`.
4. Buka tab Actions.
5. Jalankan `Build 2 APK`.
6. Setelah selesai, download artifact `lokasi-saya-apks`.
7. Artifact berisi:
   - `lokasi-saya-target.apk`
   - `lokasi-saya-admin.apk`

## Cara pakai

### HP Target
1. Install `lokasi-saya-target.apk`.
2. Buka **LOKASI SAYA**.
3. Isi nomor/kode, misalnya `12345`.
4. Tekan `MULAI BAGIKAN LOKASI`.
5. Izinkan lokasi.
6. Izinkan notifikasi jika diminta.
7. Selama layanan aktif, lokasi dikirim ke Firebase sekitar setiap 10 detik.

### HP Admin
1. Install `lokasi-saya-admin.apk`.
2. Buka **ADMIN**.
3. Isi kode yang sama (`12345`).
4. Tekan `TAMBAH`.
5. Tambahkan kode lain untuk memantau beberapa target.
6. Marker target akan diperbarui otomatis saat data baru masuk.
7. Tekan kartu target untuk memusatkan peta.
8. Tombol `BUKA DI GOOGLE MAPS` membuka lokasi di aplikasi Maps.

## Struktur

```
LokasiSaya/
├── target-app/
│   ├── build.gradle.kts
│   └── src/main/...
├── admin-app/
│   ├── build.gradle.kts
│   └── src/main/...
├── .github/workflows/build-apks.yml
├── firebase-rules.json
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Catatan keamanan

Versi ini adalah prototype berbagi lokasi dengan persetujuan pengguna.

- Android tetap meminta izin lokasi.
- Saat layanan lokasi berjalan, Android menampilkan foreground-service notification.
- Tidak ada perekaman kamera/mikrofon secara diam-diam.
- Tidak ada perekaman panggilan.
- Firebase Anonymous Auth membuat identitas backend; rules prototype di atas belum membedakan role Admin dan Target.
- Untuk pemakaian produksi, buat Firebase Authentication/authorization khusus Admin dan Target serta perketat rules.


## Jika GitHub menampilkan error

Pastikan:
1. Seluruh isi ZIP di-upload ke repository, bukan file ZIP-nya sebagai satu file.
2. `MAPS_API_KEY` dibuat di:
   Settings → Secrets and variables → Actions.
3. Nama secret harus tepat: `MAPS_API_KEY`.
4. Firebase Authentication → Anonymous harus aktif.
5. Firebase Rules di `firebase-rules.json` sudah diterapkan ke Realtime Database.

Workflow sekarang membangun Target lebih dahulu, lalu Admin. Jika Target gagal, baca bagian `Build Target APK` pada log Actions.
