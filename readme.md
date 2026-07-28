Berikut draf file **`README.md`** yang sudah disesuaikan dengan seluruh fitur dan struktur aplikasi Bel Sekolah kamu. Tinggal kamu salin ke dalam file `README.md` di repositori GitHub-mu.

```markdown
# 🔔 Bel Sekolah - SDI Miftahul Ulum Klemunan

Aplikasi manajemen bel otomatis berbasis desktop yang dibuat menggunakan **JavaFX**. Aplikasi ini dirancang untuk memutar bel jadwal pelajaran, lagu harian, serta audio kustom secara otomatis dan presisi, lengkap dengan dukungan berjalan di *background* (*System Tray*).

---

## ✨ Fitur Utama

- **Pemutaran Otomatis Terjadwal**: Memutar bel sekolah secara presisi berdasarkan hari dan jam yang dikonfigurasi.
- **Rangkaian Nada Bel**: Otomatis memutar audio pembuka (`pembuka.wav`), lagu/suara utama, dan audio penutup (`penutup.wav`).
- **Auto Pause & Resume**: Jika bel jadwal berbunyi saat lagu harian atau audio kustom sedang diputar, audio lain akan otomatis di-*pause* dan dilanjutkan kembali setelah bel selesai.
- **Manajemen Jadwal (CRUD)**: Tambah, edit, dan hapus jadwal bel secara interaktif langsung dari aplikasi.
- **Lagu Hari Ini**: Pemutar lagu otomatis sesuai daftar lagu harian yang telah dikonfigurasi.
- **Pemutar Audio Kustom**: Memutar audio manual dari folder `mp3/custom/` dengan kontrol simbol intuitif (`▶`, `⏸`, `⏹`).
- **File Watcher**: Otomatis memperbarui data jadwal/lagu secara *real-time* jika ada perubahan pada file konfigurasi `.dat`.
- **System Tray Support**: Saat jendela dikelip (*close*), aplikasi tetap berjalan secara tersembunyi di *system tray* agar jadwal bel tetap aktif.

---

## 📁 Struktur Folder & File Konfigurasi

Agar aplikasi dapat berjalan dengan baik, pastikan struktur folder dan file berikut tersedia di direktori kerja aplikasi:

```text
.
├── mp3/
│   ├── pembuka.wav       # Audio pembuka bel (opsional)
│   ├── penutup.wav       # Audio penutup bel (opsional)
│   ├── BEL.mp3           # Suara bel bawaan
│   └── custom/           # Folder penyimpanan audio kustom
├── jadwal.dat            # File basis data jadwal bel (format teks)
└── lagu.dat              # File basis data lagu harian (format teks)

```

### Format File Konfigurasi

1. **`jadwal.dat`**
```text
Label Jadwal|Hari (1-7)|HHmm|Relative Path File MP3
Contoh:
Bel Masuk|1|0700|BEL.mp3
Jam Ke-1|1|0715|jam1.mp3

```


2. **`lagu.dat`**
```text
Label Lagu|Daftar Hari (pisahkan koma)|Relative Path File MP3
Contoh:
Indonesia Raya|1,2,3,4,5,6|indonesia_raya.mp3

```



---

## 🛠️ Prasyarat Sistem

Sebelum melakukan kompilasi dan menjalankan aplikasi, pastikan sistem kamu sudah terpasang:

* **Java Development Kit (JDK)**: Versi 17 atau yang lebih baru.
* **Apache Maven**: Versi 3.8+ (jika build dilakukan secara manual via CLI).
* **JavaFX SDK**: Versi 17+ (jika tidak dimuat otomatis oleh Maven).

---

## 🚀 Cara Kompilasi & Jalankan

### 1. Clone Repositori

```bash
git clone [https://github.com/amuadib/bel-sekolah.git](https://github.com/amuadib/bel-sekolah.git)
cd bel-sekolah

```

### 2. Jalankan Aplikasi (Mode Pengembang / Maven)

Gunakan plugin JavaFX Maven untuk menjalankan aplikasi secara langsung:

```bash
mvn clean javafx:run

```

### 3. Kompilasi ke JAR (Build Executable)

Untuk mengemas aplikasi menjadi file `.jar` yang dapat didistribusikan:

```bash
mvn clean package

```

File JAR hasil build akan tersimpan di dalam folder `target/`.

---

## 💻 Penggunaan Aplikasi

1. **Menjalankan Bel Otomatis**: Cukup biarkan aplikasi terbuka atau di-*minimize* ke *System Tray*.
2. **Menambah/Mengedit Jadwal**:
* Klik tombol **Tampilkan Jadwal**.
* Gunakan tombol **Tambah**, **Edit**, atau **Hapus** untuk mengelola jadwal harian.


3. **Memutar Suara Manual**:
* Pilih file dari *dropdown* pada kartu **Putar Suara Manual**.
* Gunakan tombol `▶` (Play), `⏸` (Pause), dan `⏹` (Stop) untuk mengontrol pemutaran.



---

## 📄 Lisensi & Kredit

Dikembangkan untuk **SDI Miftahul Ulum Klemunan**.

Dikembangkan oleh [amuadib](https://github.com/amuadib). Lisensi bersifat terbuka untuk penggunaan dan pengembangan edukasional.
