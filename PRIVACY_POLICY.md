# 💧 Su Yolu - Gizlilik Politikası (Privacy Policy)

**Son Güncelleme / Effective Date:** 8 Kasım 2026 (November 8, 2026)  
**Uygulama Adı / App Name:** Su Yolu - GPS Boru & QR CNC  
**Paket Adı / Package Name:** `com.turga.suyolu`  
**Geliştirici / Developer:** Turgay Yüzen  
**İletişim / Contact:** turgayyuzen@gmail.com  

---

## 🇹🇷 TÜRKÇE GİZLİLİK POLİTİKASI (KVKK UYUMLU)

### 1. Giriş ve Genel Bakış
Su Yolu ("Uygulama"), Anadolu köylerindeki ve kırsal alanlardaki kaybolmaya yüz tutmuş su boru hatlarını, kaynakları, vanaları ve çeşmeleri GPS teknolojisiyle haritalandırmak; elde edilen rotaları çift QR kodlu mermer/pirinç tabela şablonları (CNC DXF ve Lazer SVG) ile kalıcı hale getirmek ve arıza durumunda canlı radarla boru hattını bulmak amacıyla geliştirilmiş bağımsız ve açık bir mühendislik/kültürel miras aracıdır.

Kullanıcılarımızın gizliliği en temel önceliğimizdir. **Su Yolu, kullanıcıların hiçbir kişisel verisini, kimlik bilgisini veya konum geçmişini harici sunuculara göndermez, kaydetmez ve üçüncü şahıslarla paylaşmaz.**

---

### 2. Erişilen İzinler ve Verilerin Kullanım Amaçları

#### A. Konum Verileri (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`)
* **Kullanım Amacı:** 
  1. *Saha Kayıt Modülü:* Boru hattı boyunca arazide yürürken her 10 metrede bir GPS koordinatını otomatik olarak kaydederek su hattının rotasını oluşturmak.
  2. *Saha Rehberi & Radar Modülü:* Toprağın altındaki boru hattına ve en yakın vanaya/maslağa olan anlık mesafeyi ve yönü pusula kılavuzuyla ("TAM BORU HATTININ ÜZERİNDESİNİZ" alarmı) göstermek.
* **Veri Saklama ve İletim:** Konum verileri yalnızca uygulamanın çalıştığı esnada cihazın kendi yerel hafızasında (SharedPreferences ve geçici bellek) tutulur. Konum verisi **hiçbir şekilde internet üzerinden bir sunucuya yüklenmez**. Rota, tamamen kullanıcının kontrolünde oluşturulan QR kodun içine veya DXF/SVG dosyasına işlenir.

#### B. Arka Plan & Ön Plan Servisi (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`)
* **Kullanım Amacı:** Kullanıcı arazide yürürken telefonun ekranı kapandığında veya telefon cebe konduğunda GPS kaydının kesilmemesini sağlamak. Kullanıcıya kayıt esnasında bildirim çubuğunda "GPS Kayıt Aktif - Kayıtlı Nokta ve Mesafe" bilgisi verilir. Kullanıcı dilediği an kaydı "Beklet" butonuna basarak duraklatabilir ve servisi kapatabilir.

#### C. Kamera İzni (`CAMERA`)
* **Kullanım Amacı:** Çeşme veya su yapısı üzerine monte edilmiş mermer/pirinç kitabe üzerindeki "Su Yolu Boru Hattı QR Kodunu" taramak için kullanılır (ZXing barkod tarayıcı altyapısı).
* **Veri Saklama:** Kamera yalnızca QR kodu anlık olarak çözmek için kullanılır. Hiçbir fotoğraf çekilmez, video kaydedilmez ve kamera görüntüsü hiçbir yere iletilmez.

#### D. Dosya ve Depolama Erişimi (`WRITE_EXTERNAL_STORAGE` - Android 9 ve öncesi)
* **Kullanım Amacı:** Kullanıcının dışa aktarmak istediği CNC freze dosyasını (`.dxf`), lazer plaka dosyasını (`.svg`), Google Earth 3D harita dosyasını (`.kml`), el GPS dosyasını (`.gpx`) ve mermer kitabe görselini (`.png`) cihazın `Downloads/SuYolu` veya `Pictures/SuYolu` klasörüne kaydetmek.

#### E. İnternet ve Ağ Durumu (`INTERNET`, `ACCESS_NETWORK_STATE`)
* **Kullanım Amacı:** Açık kaynaklı OpenStreetMap (OSM) harita karolarını yüklemek için kullanılır. Harita karosu indirme isteği sırasında hiçbir kullanıcı kimliği, konum geçmişi veya kişisel veri OSM sunucularına aktarılmaz. Uygulama internetsiz (çevrimdışı) ortamda da daha önce önbelleğe alınmış haritalarla ve QR kodlarla tam kapasite çalışabilir.

---

### 3. Veri Paylaşımı ve Üçüncü Taraflar
* **Reklam Yok:** Uygulama içerisinde reklam gösterilmez, reklam kimliği (GAID) toplanmaz.
* **Analitik Takipçi Yok:** Firebase Analytics, Adjust, AppsFlyer veya benzeri kullanıcı davranışı takip eden hiçbir üçüncü taraf SDK bulunmamaktadır.
* **Veri Satışı Yok:** Kullanıcı verileri hiçbir şart altında ticari amaçlarla satılmaz, kiralanmaz ve işlenmez.

---

### 4. Veri Saklama ve Kullanıcı Hakları (KVKK / GDPR)
Kullanıcı tüm verilerin tek sahibidir:
* Kaydedilen boru hatları kullanıcının kendi cihazında saklanır.
* Kullanıcı "Kayıtları Sil" seçeneğini kullanarak veya Android ayarlarından "Uygulama Verilerini Temizle" diyerek tüm kayıtları anında ve kalıcı olarak silebilir.
* Rota verileri paylaşıldığında (WhatsApp, E-posta vb.), bu işlem kullanıcının açık rızası ve tercihiyle standart Android paylaşım menüsü üzerinden gerçekleşir.

---

### 5. Çocukların Gizliliği
Uygulama 13 yaş altındaki çocuklara yönelik özel bir veri toplamaz veya çocukların gizliliğini ihlal edecek bir işlev barındırmaz.

---

### 6. Politika Güncellemeleri ve İletişim
Bu gizlilik politikası uygulamanın yeni sürümlerine veya yasal mevzuata göre güncellenebilir. Her türlü soru, geri bildirim veya hak talebiniz için geliştirici ile iletişime geçebilirsiniz:
* **E-posta:** turgayyuzen@gmail.com  
* **Geliştirici:** Turgay Yüzen  

---
---

## 🇬🇧 ENGLISH PRIVACY POLICY (GDPR & GOOGLE PLAY COMPLIANT)

### 1. Introduction
Su Yolu ("App") is an open-source engineering and cultural heritage tool designed to map underground water pipelines, natural springs, valves, and historic fountains using GPS technology. The app generates dual-QR marble/brass plaque templates for CNC (DXF) and Laser (SVG) engraving, and features a live proximity radar for excavation safety and repair guidance.

We respect your privacy. **Su Yolu does NOT collect, transmit, store on external servers, or sell any personal data, user identity, or location history.**

---

### 2. Permissions and Data Usage

#### A. Location Information (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`)
* **Purpose:**
  1. *Field Recording:* Periodically recording GPS coordinates at 10-meter intervals as the user walks along the pipeline.
  2. *Proximity Radar & Guidance:* Calculating real-time distance and compass bearing to the pipeline and nearest valve during maintenance or excavation ("ON THE PIPE" alert).
* **Storage & Transmission:** Location coordinates are processed exclusively on your device (in local memory / local preferences). No location data is ever transmitted to any remote cloud server.

#### B. Foreground Service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`)
* **Purpose:** Allows continuous GPS route logging while walking across fields, even when the screen is locked or turned off. A persistent notification indicates active recording status and allows immediate pausing.

#### C. Camera (`CAMERA`)
* **Purpose:** Used strictly to scan the physical QR code etched onto village fountain plaques using the embedded ZXing scanner.
* **Storage:** No images, photos, or video recordings are saved or uploaded. Image analysis is performed locally and instantaneously.

#### D. Storage / Media (`WRITE_EXTERNAL_STORAGE` on Android 9 and below / MediaStore)
* **Purpose:** Used solely when the user explicitly requests to export CNC files (`.dxf`), Laser files (`.svg`), Google Earth files (`.kml`), handheld GPS files (`.gpx`), or plaque images (`.png`) to public `Downloads/SuYolu` or `Pictures/SuYolu` directories.

#### E. Network Access (`INTERNET`, `ACCESS_NETWORK_STATE`)
* **Purpose:** Downloading OpenStreetMap (OSM) map tiles. No personal identifiers or location traces are attached to tile requests. The app functions 100% offline once maps/data are cached.

---

### 3. Third-Party Sharing and Analytics
* **No Advertisements:** The app contains zero ads and does not read advertising IDs.
* **No Analytics / Telemetry:** No third-party trackers (such as Google Analytics, Firebase, Facebook SDK) are integrated.
* **No Data Monetization:** We never sell, lease, or monetize user data.

---

### 4. Data Retention and Deletion
All pipeline records remain entirely on your physical device. You may permanently delete your data at any time by clearing the records within the app or clearing application storage in Android Settings.

---

### 5. Contact Information
If you have any questions or inquiries regarding this Privacy Policy, please contact:
* **Developer:** Turgay Yüzen
* **Email:** turgayyuzen@gmail.com
