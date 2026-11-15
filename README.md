<a id="top"></a>

# 🖨️ ohmycapT

[![GitHub stars](https://img.shields.io/github/stars/tereyagburak/ohmycapT?style=social)](https://github.com/tereyagburak/ohmycapT)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

**🌐 Language / Dil:** [🇹🇷 Türkçe](#-türkçe) · [🇬🇧 English](#-english)

---

<a id="-türkçe"></a>

# 🇹🇷 Türkçe

> 🤖 **Vibecoded:** Bu proje tamamen **vibecoded**'dır! Yani yapay zekâyla sohbet ede ede, "bakalım olacak mı?", "hadi şunu da ekleyelim" denilerek yazıldı. Kod kalitesi "çalışıyorsa dokunma" seviyesindedir :) Bir yıldızınızı (⭐) eksik etmeyin!
>
> 🖨️ **Donanım Durumu:** Uygulama **gerçek Canon yazıcı ile bizzat test edilmiş ve doğrulanmıştır**.
>
> ⭐ **Projeyi Destekleyin:** Projeyi beğendiyseniz GitHub üzerinden repoyu yıldızlamayı unutmayın: [github.com/tereyagburak/ohmycapT](https://github.com/tereyagburak/ohmycapT)
>
> ⚠️ **Önemli / Bilinen Sorun:** Android **PrintService** (sistem genelindeki "Paylaş -> Yazdır" menüsü) kısmı **şu an ÇALIŞMIYOR / bozuk**. Kod projede duruyor ama güvenilir şekilde çalışmaz. Yazdırma işlemlerini doğrudan uygulamanın kendi arayüzü üzerinden yapmanız gerekiyor.

---

## ⚡ Basitçe Nedir? (TL;DR)

Eski ama taş gibi Canon lazer yazıcınız (özellikle meşhur **Canon LBP2900 / LBP3000**) var ama telefonunuzdan veya tabletinizden çıktı alamıyor musunuz? Çünkü Canon bu emektar yazıcılar için Android sürücüsü çıkarmadı. 

**ohmycapT** tam olarak bunu çözüyor:
1. Bir ucu telefonunuza (OTG dönüştürücü ile), diğer ucu yazıcıya bağlı olan USB kablosunu takıyorsunuz.
2. Yazıcının gücünü açıp ekrana gelen USB iznini onaylıyorsunuz.
3. Uygulama içerisinden belgenizi (PDF, resim vb.) seçip **"Yazdır"** diyorsunuz.
4. Telefonunuz yazıcının dilinden (CAPT protokolü) konuşup çıktınızı şakır şakır alıyor!

Bilgisayara, sürücü CD'sine veya harici print server cihazlarına gerek yok; sadece telefonunuz, kablonuz ve emektar Canon yazıcınız!

---

## İçindekiler

- [Basitçe Nedir? (TL;DR)](#-basitçe-nedir-tldr)
- [Hakkında](#hakkında)
- [Durum Tablosu](#durum-tablosu)
- [Özellikler](#özellikler)
- [Desteklenen Yazıcılar](#desteklenen-yazıcılar)
- [Nasıl Çalışır?](#nasıl-çalışır)
- [Mimari](#mimari)
- [Proje Yapısı](#proje-yapısı)
- [Ekranlar](#ekranlar)
- [Yazdırma Ayarları](#yazdırma-ayarları)
- [Yazıcı Durumları](#yazıcı-durumları)
- [Teknoloji Yığını](#teknoloji-yığını)
- [Gereksinimler](#gereksinimler)
- [Derleme](#derleme)
- [Kullanım](#kullanım)
- [Bilinen Sorunlar](#bilinen-sorunlar)
- [Vibecoded Üzerine](#vibecoded-üzerine)
- [Teşekkür ve Kaynaklar](#teşekkür-ve-kaynaklar)
- [Lisans](#lisans)

## Hakkında

**ohmycapT**, Canon **CAPT** protokolünü kullanan lazer yazıcıları (ör. **LBP2900**) Android cihazlardan **USB OTG** üzerinden doğrudan kullanabilmeniz için geliştirilmiş açık kaynaklı bir Android uygulamasıdır. Belgeyi işler, bitmap'e dönüştürür, dither algoritmalarıyla 1-bit monokrom hale getirir, Canon'un tescilli HiScoA algoritması ile sıkıştırır ve USB üzerinden yazıcıya ham komutları gönderir.

## Durum Tablosu

| Bileşen | Durum |
|---|---|
| Uygulama İçi Yazdırma Arayüzü | ✅ Çalışan ana akış (Gerçek donanımla test edildi) |
| USB OTG Bağlantısı & İzin Yönetimi | ✅ Mevcut ve çalışıyor |
| Yazdırma Geçmişi (Room) & Ayarlar | ✅ Mevcut |
| **Android PrintService (`CaptPrintService`)** | ❌ **Çalışmıyor / Bozuk (Sistem menüsünden yazdırmaz)** |

## Özellikler

- 🔌 **USB Host / OTG Doğrudan Bağlantı:** Cihaz takıldığı an algılama (`USB_DEVICE_ATTACHED`).
- 🔐 Güvenli USB izin yönetimi ve otomatik bağlantı sağlığı denetimi.
- 📄 **Belge Hazırlama Motoru:** PDF ve resim dosyalarını otomatik boyutlandırıp A4 standartlarında rasterize etme.
- 🎨 **Dithering Motoru:** Eşikleme (Threshold), **Floyd-Steinberg** ve Ordered dithering seçenekleri.
- 🧠 **C / NDK Çekirdeği:** CAPT komut üretimi, gerçek zamanlı durum okuma ve **HiScoA** sıkıştırma.
- 🧱 Bant (band) tabanlı akış: 592 bayt satır genişliği, 70 satırlık dinamik bantlar, 600 DPI baskı.
- ⚙️ **Kapsamlı Ayarlar:** Kopya sayısı, sayfa aralığı, kağıt boyutu, ölçekleme, toner yoğunluğu (1–7), kontrast ve negatif baskı.
- 📄 **Elle Besleme & Duplex:** Kağıt sıkışmasını önleyen "Adım Adım Onaylı Besleme" ve manuel çift taraflı yazdırma.
- ⏸️ Yazdırma esnasında duraklatma / devam ettirme.
- 🕘 **Geçmiş:** Room tabanlı yazdırma kuyruğu ve geçmiş logları.

## Desteklenen Yazıcılar

Uygulama, Canon Üretici Kimliği (VID: `0x04A9`) altındaki modelleri hedefler:

| Model | Ürün Kimliği (PID) | Durum |
|---|---|---|
| **Canon LBP2900** | `0x2676` | 🎯 Gerçek cihazla test edildi ve onaylandı |
| Canon LBP3000 | `0x26B0` | Kodda tanımlı (Uyumlu komut seti) |
| Canon LBP3100 | `0x26CE` | Kodda tanımlı (Uyumlu komut seti) |
| Canon LBP6000 | `0x271A` | Kodda tanımlı (Uyumlu komut seti) |

## Nasıl Çalışır?

```
Belge (PDF / Resim)
   │  DocumentPreparationEngine (PdfRenderer / Bitmap)
   ▼
Sayfa Bitmap'i  →  Dithering (1-bit Siyah/Beyaz)
   │  CaptNative (JNI)
   ▼
HiScoA Sıkıştırma  →  CAPT Komut Paketleri (capt-command.c)
   │  CaptUsbTransport (UsbDeviceConnection / Bulk Transfer)
   ▼
Canon LBP Yazıcı (Fiziksel Kağıt Çıktısı 📄)
```

## Mimari

- **UI:** Jetpack Compose (Material 3), Compose Navigation, tek yönlü veri akışı (UDF).
- **Veri & İş Mantığı:** `RealPrinterRepository` üzerinden doğrudan USB ve donanım kontrolü.
- **Veritabanı:** Room SQLite (Yazdırma geçmişi ve durumları).
- **USB & Donanım:** Android `UsbManager` + JNI C Köprüsü (`app/src/main/cpp`).
- **Sistem Yazdırma Servisi:** `CaptPrintService` (**Bozuk/Deneysel**).

## Proje Yapısı

```
ohmycapT/
├── app/
│   └── src/main/
│       ├── cpp/                      # C: Düşük Seviye CAPT Sürücüsü
│       │   ├── native-lib.c          # JNI Bağlantıları, Dither & Çizim
│       │   ├── capt_io.c/.h          # G/Ç Soyutlama Katmanı
│       │   ├── capt-command.c/.h     # Canon Komut Setleri
│       │   ├── capt-status.c/.h      # Durum Paketi Ayrıştırıcı
│       │   ├── hiscoa-*.c/.h         # HiScoA Sıkıştırma Algoritması
│       │   ├── prn_lbp2900.c/.h      # LBP2900 Motor Kontrolü
│       │   ├── dither.c/.h           # Dithering Algoritmaları
│       │   └── CMakeLists.txt
│       └── java/com/example/captprint/
│           ├── data/                 # Room DB, Repository, Belge İşleme
│           ├── domain/model/         # PrintSettings, PrinterStatus modelleri
│           ├── feature/              # Ekranlar (Home, Printer, Config, Progress, History, Settings)
│           ├── ui/                   # Tema, Bileşenler ve Navigasyon
│           ├── CaptPrintService.kt   # (BOZUK) PrintService Katmanı
│           ├── CaptUsbTransport.kt   # Android USB Host Haberleşmesi
│           └── MainActivity.kt
└── LICENSE
```

## Ekranlar

| Ekran | Açıklama |
|---|---|
| **Karşılama (Onboarding)** | İlk kurulum rehberi ve USB OTG bağlama adımları |
| **Yazdır (Home)** | Dosya seçimi, önizleme ve yazdırma başlatma |
| **Yazıcı (Printer)** | Gerçek zamanlı bağlantı durumu, donanım hazır bilgisi, hata uyarıları |
| **Yazdırma Ayarları** | Kağıt boyutu, kopya, toner yoğunluğu, dither seçimi |
| **İlerleme (Progress)** | Sayfa ilerlemesi, bant gönderimi, duraklatma ve elle besleme onayı |
| **Geçmiş (History)** | Tamamlanan ve geçmiş yazdırma işleri |
| **Ayarlar (Settings)** | Genel tercihler ve GitHub proje bağlantısı |

## Yazdırma Ayarları

| Ayar | Değerler | Varsayılan |
|---|---|---|
| Kopya Sayısı | Sayısal giriş | `1` |
| Sayfa Aralığı | `ALL` veya `1-3, 5` formatı | `ALL` |
| Kağıt Boyutu | A4, US Letter, A5, Legal | `A4` |
| Ölçekleme | Sayfaya Sığdır, Gerçek Boyut (%100), Doldur | `Sayfaya Sığdır` |
| Toner Yoğunluğu | 1 (Açık) .. 7 (Koyu) | `7` |
| Kontrast | 0.5 .. 2.0 | `1.0` |
| Dither Yöntemi | Floyd-Steinberg, Threshold, Ordered | `Floyd-Steinberg` |
| Renkleri Ters Çevir | Açık / Kapalı | `Kapalı` |
| Elle Besleme Modu | Açık / Kapalı (Her sayfada ekrandan onay ister) | `Kapalı` |
| Elle Çift Taraflı | Açık / Kapalı | `Kapalı` |

## Teknoloji Yığını

- **Dil:** Kotlin, C11
- **Arayüz:** Jetpack Compose, Material 3, Material Icons Extended
- **Asenkron Yapı:** Coroutines & Kotlin Flow
- **Veri Saklama:** Room DB, DataStore Preferences
- **NDK & Build:** CMake 3.22+, Android NDK (16 KB page-size uyumlu bayraklar)
- **Görsel:** Coil Compose

## Gereksinimler

- Android 8.0+ (API 26+) yüklü telefon/tablet (Hedef: Android 15 / API 35)
- USB Host (OTG) donanım desteği ve OTG çevirici kablo
- `arm64-v8a` veya `armeabi-v7a` mimarili cihaz
- Canon CAPT uyumlu lazer yazıcı (Örn: LBP2900)

## Derleme

Projeyi Android Studio ile açabilir veya terminalden derleyebilirsiniz:

```bash
git clone https://github.com/tereyagburak/ohmycapT.git
cd ohmycapT
./gradlew assembleDebug      # Windows: gradlew.bat assembleDebug
```

Çıktı APK dosyası: `app/build/outputs/apk/debug/app-debug.apk`

## Kullanım

1. Yazıcınızı USB kablosu ve OTG adaptörü ile telefonunuza bağlayın.
2. Yazıcının güç düğmesine basarak açın.
3. Ekrana gelen "ohmycapT uygulamasının bu cihaza erişmesine izin verilsin mi?" uyarısını onaylayın.
4. Uygulamayı açın, **Yazıcı** sekmesinden yazıcının "Hazır" olduğunu görün.
5. **Yazdır** sekmesinden bir PDF veya resim seçin, ayarlarınızı kontrol edin ve yazdırın!

## Bilinen Sorunlar

- ❌ **PrintService Bozuk:** Android'in kendi yazdırma çerçevesi üzerinden (örneğin Chrome'dan "Yazdır" diyerek) yazdırma işlemi şu anda **çalışmamaktadır**. Çıktıları doğrudan ohmycapT uygulaması içerisinden vermelisiniz.

## Vibecoded Üzerine

Bu proje mimari planlar, devasa şartnameler veya kurumsal kod kalitesi gözetilerek değil; yapay zekâ asistanlarıyla birlikte, bir merakın ve ihtiyacın peşinden gidilerek **tamamen vibecoded** şeklinde hayata geçirildi. Hatalar veya ilginç çözümler görürseniz gülüp geçebilir ya da bir Pull Request gönderebilirsiniz! 

Projeye destek olmak için GitHub üzerinden yıldız vermeyi unutmayın: [tereyagburak/ohmycapT](https://github.com/tereyagburak/ohmycapT) ⭐

## Teşekkür ve Kaynaklar

Bu projenin C tabanlı düşük seviye sürücü mantığı, Alexey Galakhov'un efsanevi Linux açık kaynak sürücüsü olan **[captdriver](https://github.com/agalakhov/captdriver)** projesinden esinlenilerek Android platformuna taşınmıştır. Kendisine teşekkür ederiz.

## Lisans

Bu proje [GNU General Public License v3.0](LICENSE) kapsamında lisanslanmıştır.

[⬆️ Başa dön](#top)

---

<a id="-english"></a>

# 🇬🇧 English

> 🤖 **Vibecoded:** This project is 100% **vibecoded**! Built entirely through chats with AI, driven by "let's see if this works" and pure curiosity. Code quality is at the "if it works, don't touch it" level :) Please consider leaving a star (⭐)!
>
> 🖨️ **Hardware Status:** The app has been **personally tested and verified on a real Canon printer**.
>
> ⭐ **Support the Project:** If you find this helpful, don't forget to star the repo on GitHub: [github.com/tereyagburak/ohmycapT](https://github.com/tereyagburak/ohmycapT)
>
> ⚠️ **Important / Known Issue:** The Android **PrintService** (printing from the system "Share -> Print" dialog) is **currently BROKEN / NOT WORKING**. The code remains in the repository, but it does not function reliably. Please use the app's internal UI to select and print your documents.

---

## ⚡ Simply Put: What is this? (TL;DR)

Do you own an old, rock-solid Canon laser printer (like the iconic **Canon LBP2900 / LBP3000**), but can't print from your Android phone or tablet? That's because Canon never released an official Android driver for these legacy CAPT printers.

**ohmycapT** solves this exact problem:
1. Connect your printer's USB cable to your phone using an OTG adapter.
2. Power on the printer and grant USB permission when prompted.
3. Pick a document (PDF, image, etc.) inside the app and tap **"Print"**.
4. Your phone speaks the printer's native language (CAPT protocol) and prints your document directly!

No PC, no driver discs, and no wireless print server hardware required—just your phone, a USB cable, and your trusty old Canon printer!

---

## Table of Contents

- [Simply Put: What is this? (TL;DR)](#-simply-put-what-is-this-tldr)
- [About](#about)
- [Status Table](#status-table)
- [Features](#features)
- [Supported Printers](#supported-printers)
- [How It Works](#how-it-works)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Screens](#screens)
- [Print Settings](#print-settings)
- [Tech Stack](#tech-stack)
- [Requirements](#requirements)
- [Building](#building)
- [Usage](#usage)
- [Known Issues](#known-issues)
- [On Being Vibecoded](#on-being-vibecoded)
- [Credits](#credits)
- [License](#license)

## About

**ohmycapT** is an open-source Android driver and printing utility for Canon laser printers utilizing the proprietary **CAPT** protocol (such as the **LBP2900**). Because no official Android driver exists, this app handles document rasterization, converts pages into 1-bit monochrome data using advanced dithering, compresses streams via Canon's HiScoA algorithm, and sends raw commands directly to the printer over USB OTG.

## Status Table

| Component | Status |
|---|---|
| In-App Printing Flow | ✅ Working main path (Tested with physical hardware) |
| USB OTG Connection & Permissions | ✅ Working |
| Print History (Room DB) & Settings | ✅ Working |
| **Android PrintService (`CaptPrintService`)** | ❌ **Broken / Does not work via system menu** |

## Features

- 🔌 **Direct USB Host / OTG:** Plug-and-play detection (`USB_DEVICE_ATTACHED`).
- 🔐 Robust USB permission handling and status monitoring.
- 📄 **Document Preparation Engine:** Rasterizes PDFs and images directly to printable A4/letter format.
- 🎨 **Dithering Engine:** Threshold, **Floyd-Steinberg**, and Ordered dithering algorithms.
- 🧠 **C / NDK Engine:** CAPT command generation, status parsing, and **HiScoA** compression.
- 🧱 Banded streaming: 592-byte lines, 70-line dynamic bands, 600 DPI printing.
- ⚙️ **Detailed Settings:** Copies, page ranges, paper formats, scaling, toner density (1–7), contrast, and inversion.
- 📄 **Manual Feed & Duplex:** Step-by-step confirmation mode to avoid paper jams, plus manual duplex.
- ⏸️ Pause and resume support during print execution.
- 🕘 **History:** Room-backed print job history and logging.

## Supported Printers

Recognized Canon models (Vendor ID: `0x04A9`):

| Model | Product ID (PID) | Status |
|---|---|---|
| **Canon LBP2900** | `0x2676` | 🎯 Tested and verified on real hardware |
| Canon LBP3000 | `0x26B0` | Defined in code (Compatible command set) |
| Canon LBP3100 | `0x26CE` | Defined in code (Compatible command set) |
| Canon LBP6000 | `0x271A` | Defined in code (Compatible command set) |

## How It Works

```
Document (PDF / Image)
   │  DocumentPreparationEngine (PdfRenderer / Bitmap)
   ▼
Page Bitmap  →  Dithering (1-bit Black/White)
   │  CaptNative (JNI)
   ▼
HiScoA Compression  →  CAPT Command Packets (capt-command.c)
   │  CaptUsbTransport (UsbDeviceConnection / Bulk Transfer)
   ▼
Canon LBP Printer (Physical Paper Output 📄)
```

## Architecture

- **UI:** Jetpack Compose (Material 3), Compose Navigation, UDF architecture.
- **Data & Business Logic:** Direct printer hardware integration via `RealPrinterRepository`.
- **Database:** Room SQLite (Print history and states).
- **USB & Driver:** Android `UsbManager` + JNI C Bridge (`app/src/main/cpp`).
- **Print Service:** `CaptPrintService` (**Broken/WIP**).

## Project Structure

```
ohmycapT/
├── app/
│   └── src/main/
│       ├── cpp/                      # C: Low-level CAPT Driver
│       │   ├── native-lib.c          # JNI Bridge, Dithering & Bitmap rendering
│       │   ├── capt_io.c/.h          # I/O Abstraction Layer
│       │   ├── capt-command.c/.h     # Canon Command Sets
│       │   ├── capt-status.c/.h      # Status Packet Parser
│       │   ├── hiscoa-*.c/.h         # HiScoA Compression Engine
│       │   ├── prn_lbp2900.c/.h      # LBP2900 Engine Controller
│       │   ├── dither.c/.h           # Dithering Algorithms
│       │   └── CMakeLists.txt
│       └── java/com/example/captprint/
│           ├── data/                 # Room DB, Repository, Document Preparation
│           ├── domain/model/         # PrintSettings, PrinterStatus models
│           ├── feature/              # Screens (Home, Printer, Config, Progress, History, Settings)
│           ├── ui/                   # Theme, Components, Navigation
│           ├── CaptPrintService.kt   # (BROKEN) System PrintService
│           ├── CaptUsbTransport.kt   # Android USB Host Transport
│           └── MainActivity.kt
└── LICENSE
```

## Screens

| Screen | Description |
|---|---|
| **Onboarding** | First-run walkthrough and OTG setup instructions |
| **Print (Home)** | Document picker, preview, and print actions |
| **Printer** | Device connection status, real-time hardware ready state, and diagnostics |
| **Print Settings** | Paper size, copies, toner density, dithering selector |
| **Progress** | Real-time page counter, band transmission, pause/resume |
| **History** | Log of previous completed and failed print jobs |
| **Settings** | Preferences and GitHub repository link |

## Print Settings

| Setting | Options | Default |
|---|---|---|
| Copies | Number input | `1` |
| Page Range | `ALL` or `1-3, 5` | `ALL` |
| Paper Size | A4, US Letter, A5, Legal | `A4` |
| Page Scaling | Fit Page, Actual Size (100%), Fill Page | `Fit Page` |
| Toner Density | 1 (Light) .. 7 (Dark) | `7` |
| Contrast | 0.5 .. 2.0 | `1.0` |
| Dither Method | Floyd-Steinberg, Threshold, Ordered | `Floyd-Steinberg` |
| Invert Colors | On / Off | `Off` |
| Step-by-Step Manual Feed | On / Off (Prompts before each sheet) | `Off` |
| Manual Duplex | On / Off | `Off` |

## Tech Stack

- **Language:** Kotlin, C11
- **UI:** Jetpack Compose, Material 3, Material Icons Extended
- **Async:** Kotlin Coroutines & Flow
- **Persistence:** Room DB, DataStore Preferences
- **NDK & Build:** CMake 3.22+, Android NDK (with 16 KB page-size alignment)
- **Image:** Coil Compose

## Requirements

- Android 8.0+ (API 26+) device (Target: Android 15 / API 35)
- USB Host (OTG) hardware support and an OTG adapter cable
- `arm64-v8a` or `armeabi-v7a` CPU architecture
- CAPT-compatible Canon printer (e.g. LBP2900)

## Building

Open the project in Android Studio or compile via command line:

```bash
git clone https://github.com/tereyagburak/ohmycapT.git
cd ohmycapT
./gradlew assembleDebug      # Windows: gradlew.bat assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Usage

1. Connect the printer to your phone via USB cable and OTG adapter.
2. Turn on the printer power switch.
3. Accept the USB permission prompt on your Android screen.
4. Open **ohmycapT**, verify that the **Printer** tab reports "Ready".
5. Go to the **Print** tab, select your PDF or image, configure options, and start printing!

## Known Issues

- ❌ **PrintService is Broken:** Printing from system dialogs (e.g. Chrome -> Print) is **non-functional**. Please open files and print directly inside ohmycapT.

## On Being Vibecoded

This application was created without extensive architectural blueprints or corporate rigor—it was **100% vibecoded** in flow state with AI assistants. If you stumble upon strange code patterns or funny comments, enjoy the ride or send a Pull Request!

Don't forget to star the project on GitHub: [tereyagburak/ohmycapT](https://github.com/tereyagburak/ohmycapT) ⭐

## Credits

Low-level CAPT protocol handling in C is inspired by and ported from Alexey Galakhov's open-source Linux driver **[captdriver](https://github.com/agalakhov/captdriver)**.

## License

Licensed under the [GNU General Public License v3.0](LICENSE).

[⬆️ Back to top](#top)
