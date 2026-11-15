<a id="top"></a>

# 🖨️ ohmycapT

**🌐 Language / Dil:** [🇹🇷 Türkçe](#-türkçe) · [🇬🇧 English](#-english)

---

<a id="-türkçe"></a>

# 🇹🇷 Türkçe

> 🤖 **Vibecoded:** Bu proje tamamen **vibecoded**'dır. Yani yapay zekâyla sohbet ede ede, "bakalım olacak mı?" diyerek yazıldı. Kod kalitesi "çalışıyorsa dokunma" seviyesindedir :)
>
> ⚠️ **Bilinen sorun:** Android **PrintService** (sistem genelinde "Yazdır" menüsünden yazdırma) kısmı **şu an ÇALIŞMIYOR / bozuk**. Kod projede duruyor ama güvenilir şekilde çalışmaz. Yazdırma için şimdilik uygulamanın kendi arayüzünü kullanın. Ayrıntı: [Bilinen Sorunlar](#bilinen-sorunlar).

## İçindekiler

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

**ohmycapT**, Canon **CAPT** protokolünü kullanan lazer yazıcıları (ör. **LBP2900**) Android telefon/tabletten **USB OTG** ile doğrudan kullanmanızı sağlayan bir Android uygulamasıdır. Bu yazıcıların Android için resmi sürücüsü yoktur; uygulama belgeyi bitmap'e çevirir, 1 bit'e indirger (dithering), sıkıştırır ve CAPT komutlarıyla USB üzerinden yazıcıya gönderir.

## Durum Tablosu

| Bileşen | Durum |
|---|---|
| Uygulama içi yazdırma arayüzü | ✅ Çalışması hedeflenen ana yol |
| USB bağlantı / izin akışı | ✅ Mevcut |
| Yazdırma geçmişi, ayarlar, ekranlar | ✅ Mevcut |
| Sahte (Fake) yazıcı ile test | ✅ Mevcut |
| **Android PrintService (`CaptPrintService`)** | ❌ **Çalışmıyor / bozuk** |

> Not: Donanım testleri sınırlıdır; yazıcı modeline göre sonuç değişebilir.

## Özellikler

- 🔌 **USB Host** ile yazıcıya doğrudan bağlantı; cihaz takılınca uygulama tetiklenir (`USB_DEVICE_ATTACHED`)
- 🔐 USB izin isteme akışı ve bağlantı durumu takibi
- 📄 Belge hazırlama: PDF, görüntü ve metin gibi girdileri yazdırılabilir PDF/bitmap'e dönüştürme (A4 = 595×842 pt)
- 🎨 Dithering: Eşikleme, **Floyd-Steinberg**, Ordered
- 🧠 Native (C / NDK) CAPT komutları, durum okuma, LBP2900 yazdırma akışı ve **HiScoA** sıkıştırma
- 🧱 Bant (band) tabanlı gönderim: 592 bayt satır, 70 satırlık bantlar, 600 DPI
- ⚙️ Kopya, sayfa aralığı, kağıt boyutu, ölçekleme, toner yoğunluğu, kontrast, ters çevirme
- 📄 **Elle besleme** (her sayfa için onaylı) ve **elle çift taraflı** yazdırma
- ⏸️ Duraklatma / devam etme ve sonraki sayfa bekleme durumları
- 🕘 Yazdırma geçmişi (Room)
- 📊 Animasyonlu yazıcı çizimi, ilerleme ekranı, genel durum çubuğu
- 🧪 Gerçek yazıcı olmadan denemek için `FakePrinterRepository`

## Desteklenen Yazıcılar

Kod, Canon (VID `0x04A9`) için şu USB ürün kimliklerini tanır:

| Model | PID |
|---|---|
| LBP2900 | `0x2676` |
| LBP3000 | `0x26B0` |
| LBP3100 | `0x26CE` |
| LBP6000 | `0x271A` |

Asıl hedef ve test edilen model **LBP2900**'dür; diğerleri kodda tanımlıdır ancak doğrulanmamıştır.

## Nasıl Çalışır?

```
Belge (PDF/görüntü/metin)
   │  DocumentPreparationEngine
   ▼
Sayfa bitmap'i  →  Dithering (1 bit)  →  Bantlara bölme
   │  CaptNative (JNI)
   ▼
HiScoA sıkıştırma  →  CAPT komutları (capt-command.c)
   │  CaptUsbTransport (UsbManager / bulk transfer)
   ▼
Canon LBP2900
```

Yazıcıdan gelen durum paketleri (`capt-status.c`) okunur ve arayüzdeki `PrinterStatus` modeline çevrilir.

## Mimari

- **Kotlin + Jetpack Compose** (Material 3) arayüz
- **Repository** deseni: `PrinterRepository` → `RealPrinterRepository` / `FakePrinterRepository`
- **Room** ile yazdırma geçmişi
- **JNI / C** ile CAPT protokolü (`app/src/main/cpp`)
- `CaptUsbTransport`: USB uç noktaları, izin ve veri aktarımı
- `CaptNative`: Kotlin ↔ C köprüsü
- `CaptPrintService`: Android PrintService denemesi (**bozuk**)

## Proje Yapısı

```
ohmycapT/
├── app/
│   └── src/main/
│       ├── cpp/                      # C: CAPT protokolü
│       │   ├── native-lib.c          # JNI köprüsü, dither + bitmap yazdırma
│       │   ├── capt_io.c/.h          # G/Ç soyutlaması
│       │   ├── capt-command.c/.h     # CAPT komutları
│       │   ├── capt-status.c/.h      # Durum çözümleme
│       │   ├── hiscoa-*.c/.h         # HiScoA sıkıştırma
│       │   ├── prn_lbp2900.c/.h      # LBP2900 yazdırma akışı
│       │   ├── dither.c/.h           # Dithering algoritmaları
│       │   └── CMakeLists.txt
│       └── java/com/example/captprint/
│           ├── data/                 # Room DB, repository'ler, belge hazırlama
│           ├── domain/model/         # PrintSettings, PrinterStatus
│           ├── feature/              # home, printer, printconfig, progress, history, settings, onboarding
│           ├── ui/                   # tema, bileşenler, navigasyon
│           ├── CaptPrintService.kt   # (bozuk)
│           ├── CaptUsbTransport.kt
│           ├── CaptNative.kt
│           ├── CaptProbe.kt
│           ├── CaptApplication.kt
│           └── MainActivity.kt
├── build.gradle.kts / settings.gradle.kts
└── LICENSE
```

## Ekranlar

| Ekran | Açıklama |
|---|---|
| Karşılama (Onboarding) | İlk açılış tanıtımı |
| Yazdır (Home) | Belge seçimi ve yazdırmaya başlama |
| Yazıcı | Bağlantı, izin ve yazıcı durumu |
| Yazdırma Ayarları | Kopya, kağıt, ölçekleme, kalite ayarları |
| İlerleme | Sayfa sayfa ilerleme, duraklat/devam, elle besleme onayı |
| Geçmiş | Önceki yazdırma işleri |
| Ayarlar | Uygulama ayarları |

## Yazdırma Ayarları

| Ayar | Değerler / Varsayılan |
|---|---|
| Kopya sayısı | 1 |
| Sayfa aralığı | `ALL` veya `1-3, 5` |
| Kağıt boyutu | A4 (varsayılan), Letter, A5, Legal |
| Ölçekleme | Sayfaya sığdır (varsayılan), Gerçek boyut %100, Sayfayı doldur |
| Toner yoğunluğu | 1–7 (varsayılan 7) |
| Kontrast | 1.0 |
| Dithering | Floyd-Steinberg (varsayılan), Eşikleme, Ordered |
| Renkleri ters çevir | Kapalı |
| Elle besleme (adım adım) | Kapalı – kağıt sıkışmasını önlemek için |
| Elle çift taraflı | Kapalı |

## Yazıcı Durumları

`Disconnected`, `PermissionNeeded`, `Connecting`, `Ready`, `WarmingUp`, `Printing`, `Paused`, `WaitingForNextPage`, `PaperOut`, `PaperJam`, `CoverOpen`, `TonerLow`, `Error`.

## Teknoloji Yığını

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coil
- Coroutines, Lifecycle / ViewModel
- Room, DataStore Preferences
- C11, Android NDK, CMake (16 KB sayfa boyutu uyumlu bağlama)
- Android `UsbManager`, `PdfRenderer`

## Gereksinimler

- Android Studio (güncel), Android NDK + CMake
- Android 8.0+ (minSdk 26, targetSdk 35)
- USB Host (OTG) destekli cihaz + OTG kablosu
- ABI: `arm64-v8a`, `armeabi-v7a`
- CAPT uyumlu Canon yazıcı

## Derleme

```bash
git clone <repo-url>
cd ohmycapT
./gradlew assembleDebug      # Windows: gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/`. Paket adı: `oh.my.capt`.

## Kullanım

1. Yazıcıyı OTG kablosuyla telefona bağlayın, USB iznini verin.
2. Uygulamayı açın, karşılama ekranını geçin.
3. **Yazıcı** ekranında durumun `Ready` olduğunu doğrulayın.
4. **Yazdır** ekranından belgeyi seçin, ayarları yapın ve yazdırın.
5. Elle besleme açıksa her sayfadan sonra onay verin.

> ⚠️ Sistem "Yazdır" menüsünden (PrintService) yazdırma **çalışmaz**; uygulama içinden yazdırın.

## Bilinen Sorunlar

- ❌ **PrintService bozuk:** `CaptPrintService` ile diğer uygulamalardan ("Paylaş → Yazdır") yazdırma şu an çalışmıyor. Düzeltilmedi; katkıya açık.
- Deneysel proje; yazıcıda beklenmedik davranış, sıkışma veya yanlış baskı olabilir.
- Test kapsamı sınırlı; LBP2900 dışındaki modeller doğrulanmadı.
- Kod tabanında vibecoding kaynaklı tutarsızlıklar olabilir.

## Vibecoded Üzerine

Kodun büyük kısmı yapay zekâ yardımıyla, derin bir ön planlama olmadan "akışına bırak" yaklaşımıyla yazıldı. Hatalar, tuhaf yorumlar ve garip kararlar normaldir. PR'lar memnuniyetle karşılanır. 🙌

## Teşekkür ve Kaynaklar

C tarafındaki CAPT protokolü kodu, Alexey Galakhov'un GPLv3 lisanslı Linux sürücüsü **[captdriver](https://github.com/agalakhov/captdriver)** projesinden uyarlanmıştır.

## Lisans

[GNU GPL v3](LICENSE)

[⬆️ Başa dön](#top)

---

<a id="-english"></a>

# 🇬🇧 English

> 🤖 **Vibecoded:** This project is fully **vibecoded**. It was built by chatting with an AI and saying "let's see if this works" over and over. Code quality is at the "if it works, don't touch it" level :)
>
> ⚠️ **Known issue:** The Android **PrintService** (printing system-wide from the "Print" menu) is **currently NOT WORKING / broken**. The code is in the repo but doesn't work reliably. For now, print using the app's own UI. Details: [Known Issues](#known-issues).

## Table of Contents

- [About](#about)
- [Status Table](#status-table)
- [Features](#features)
- [Supported Printers](#supported-printers)
- [How It Works](#how-it-works)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Screens](#screens)
- [Print Settings](#print-settings)
- [Printer States](#printer-states)
- [Tech Stack](#tech-stack)
- [Requirements](#requirements)
- [Building](#building)
- [Usage](#usage)
- [Known Issues](#known-issues)
- [On Being Vibecoded](#on-being-vibecoded)
- [Credits](#credits)
- [License](#license)

## About

**ohmycapT** is an Android app that lets you use Canon laser printers speaking the **CAPT** protocol (e.g. **LBP2900**) directly from an Android phone or tablet over **USB OTG**. These printers have no official Android driver, so the app renders the document to a bitmap, reduces it to 1-bit (dithering), compresses it, and sends it to the printer over USB using CAPT commands.

## Status Table

| Component | Status |
|---|---|
| In-app printing UI | ✅ Main intended path |
| USB connection / permission flow | ✅ Present |
| Print history, settings, screens | ✅ Present |
| Testing with the Fake printer | ✅ Present |
| **Android PrintService (`CaptPrintService`)** | ❌ **Not working / broken** |

> Note: Hardware testing is limited; results may vary by printer model.

## Features

- 🔌 **USB Host** connection; app is triggered when the device is attached (`USB_DEVICE_ATTACHED`)
- 🔐 USB permission request flow and connection status tracking
- 📄 Document preparation: converts PDF, image and text inputs into a printable PDF/bitmap (A4 = 595×842 pt)
- 🎨 Dithering: Threshold, **Floyd-Steinberg**, Ordered
- 🧠 Native (C / NDK) CAPT commands, status reading, LBP2900 print flow and **HiScoA** compression
- 🧱 Band-based transfer: 592-byte lines, 70-line bands, 600 DPI
- ⚙️ Copies, page range, paper size, scaling, toner density, contrast, inversion
- 📄 **Manual feed** (confirmation per page) and **manual duplex** printing
- ⏸️ Pause / resume and waiting-for-next-page states
- 🕘 Print history (Room)
- 📊 Animated printer illustration, progress screen, global status bar
- 🧪 `FakePrinterRepository` for testing without a real printer

## Supported Printers

The code recognizes these USB product IDs for Canon (VID `0x04A9`):

| Model | PID |
|---|---|
| LBP2900 | `0x2676` |
| LBP3000 | `0x26B0` |
| LBP3100 | `0x26CE` |
| LBP6000 | `0x271A` |

The primary target and tested model is the **LBP2900**; the others are defined in code but unverified.

## How It Works

```
Document (PDF/image/text)
   │  DocumentPreparationEngine
   ▼
Page bitmap  →  Dithering (1 bit)  →  Split into bands
   │  CaptNative (JNI)
   ▼
HiScoA compression  →  CAPT commands (capt-command.c)
   │  CaptUsbTransport (UsbManager / bulk transfer)
   ▼
Canon LBP2900
```

Status packets from the printer (`capt-status.c`) are read and mapped to the `PrinterStatus` model used by the UI.

## Architecture

- **Kotlin + Jetpack Compose** (Material 3) UI
- **Repository** pattern: `PrinterRepository` → `RealPrinterRepository` / `FakePrinterRepository`
- **Room** for print history
- **JNI / C** for the CAPT protocol (`app/src/main/cpp`)
- `CaptUsbTransport`: USB endpoints, permission and data transfer
- `CaptNative`: Kotlin ↔ C bridge
- `CaptPrintService`: Android PrintService attempt (**broken**)

## Project Structure

```
ohmycapT/
├── app/
│   └── src/main/
│       ├── cpp/                      # C: CAPT protocol
│       │   ├── native-lib.c          # JNI bridge, dither + bitmap printing
│       │   ├── capt_io.c/.h          # I/O abstraction
│       │   ├── capt-command.c/.h     # CAPT commands
│       │   ├── capt-status.c/.h      # Status parsing
│       │   ├── hiscoa-*.c/.h         # HiScoA compression
│       │   ├── prn_lbp2900.c/.h      # LBP2900 print flow
│       │   ├── dither.c/.h           # Dithering algorithms
│       │   └── CMakeLists.txt
│       └── java/com/example/captprint/
│           ├── data/                 # Room DB, repositories, document preparation
│           ├── domain/model/         # PrintSettings, PrinterStatus
│           ├── feature/              # home, printer, printconfig, progress, history, settings, onboarding
│           ├── ui/                   # theme, components, navigation
│           ├── CaptPrintService.kt   # (broken)
│           ├── CaptUsbTransport.kt
│           ├── CaptNative.kt
│           ├── CaptProbe.kt
│           ├── CaptApplication.kt
│           └── MainActivity.kt
├── build.gradle.kts / settings.gradle.kts
└── LICENSE
```

## Screens

| Screen | Description |
|---|---|
| Onboarding | First-launch introduction |
| Print (Home) | Pick a document and start printing |
| Printer | Connection, permission and printer status |
| Print Settings | Copies, paper, scaling, quality options |
| Progress | Page-by-page progress, pause/resume, manual feed confirmation |
| History | Previous print jobs |
| Settings | App settings |

## Print Settings

| Setting | Values / Default |
|---|---|
| Copies | 1 |
| Page range | `ALL` or `1-3, 5` |
| Paper size | A4 (default), Letter, A5, Legal |
| Scaling | Fit page (default), Actual size 100%, Fill page |
| Toner density | 1–7 (default 7) |
| Contrast | 1.0 |
| Dithering | Floyd-Steinberg (default), Threshold, Ordered |
| Invert colors | Off |
| Manual feed (step-by-step) | Off – helps prevent paper jams |
| Manual duplex | Off |

## Printer States

`Disconnected`, `PermissionNeeded`, `Connecting`, `Ready`, `WarmingUp`, `Printing`, `Paused`, `WaitingForNextPage`, `PaperOut`, `PaperJam`, `CoverOpen`, `TonerLow`, `Error`.

## Tech Stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coil
- Coroutines, Lifecycle / ViewModel
- Room, DataStore Preferences
- C11, Android NDK, CMake (16 KB page-size compatible linking)
- Android `UsbManager`, `PdfRenderer`

## Requirements

- Recent Android Studio, Android NDK + CMake
- Android 8.0+ (minSdk 26, targetSdk 35)
- USB Host (OTG) capable device + OTG cable
- ABIs: `arm64-v8a`, `armeabi-v7a`
- A CAPT-compatible Canon printer

## Building

```bash
git clone <repo-url>
cd ohmycapT
./gradlew assembleDebug      # Windows: gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/`. Package name: `oh.my.capt`.

## Usage

1. Connect the printer to your phone with an OTG cable and grant USB permission.
2. Open the app and go through onboarding.
3. On the **Printer** screen, confirm the status is `Ready`.
4. On the **Print** screen, pick a document, adjust settings and print.
5. If manual feed is on, confirm after each page.

> ⚠️ Printing from the system "Print" menu (PrintService) **does not work**; print from inside the app.

## Known Issues

- ❌ **PrintService is broken:** Printing from other apps ("Share → Print") via `CaptPrintService` currently doesn't work. Not fixed yet; contributions welcome.
- Experimental project; unexpected printer behavior, jams or misprints are possible.
- Limited test coverage; models other than LBP2900 are unverified.
- Expect vibecoding-induced inconsistencies throughout the codebase.

## On Being Vibecoded

Most of the code was written with AI assistance in a "go with the flow" style without deep upfront planning. Bugs, odd comments and strange decisions are normal. PRs are very welcome. 🙌

## Credits

The CAPT protocol code in the C layer is adapted from Alexey Galakhov's GPLv3 Linux driver **[captdriver](https://github.com/agalakhov/captdriver)**.

## License

[GNU GPL v3](LICENSE)

[⬆️ Back to top](#top)
