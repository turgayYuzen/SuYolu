# 📋 Google Play Console - Veri Güvenliği (Data Safety) Yanıt Rehberi

Google Play Console'a uygulamanızı yüklerken veya güncellerken **"Politika ve Programlar > Uygulama İçeriği > Veri Güvenliği (Data Safety)"** bölümünde aşağıdaki adımları seçmeniz onay sürecini hızlandıracaktır:

---

### 1. Genel Sorular (Overview)
* **Uygulamanız zorunlu kullanıcı veri türlerinden herhangi birini topluyor veya paylaşıyor mu?**  
  👉 **Evet** *(Konum izni kullandığımız için teknik olarak Evet seçilir)*
* **Uygulamanız tarafından toplanan tüm kullanıcı verileri aktarım sırasında şifreleniyor mu?**  
  👉 **Evet** *(Veya veriler sunucuya gönderilmediği için yerel güvenlik uygulanır)*
* **Kullanıcıların verilerinin silinmesini talep edebileceği bir yöntem sunuyor musunuz?**  
  👉 **Evet** *(Kullanıcı uygulama içinden "Kayıtları Sil" butonuyla veya telefon ayarlarından verileri temizleyebilir)*

---

### 2. Veri Türleri ve Beyanlar (Data Types)

#### A. Konum (Location)
* **Yaklaşık Konum (Approximate Location):** Seçin ✅
* **Hassas Konum (Precise Location):** Seçin ✅
* *Bu veriler nasıl işleniyor?*
  - **Toplandı mı, Paylaşıldı mı?** ➔ **Toplandı (Collected)**
  - **Bu veri geçici olarak mı işleniyor (Ephemeral)?** ➔ **Evet (Yes)** *(Yalnızca boru rotası çizimi esnasında ve radar mesafe hesabında cihazda anlık kullanılır, sunucuya gönderilmez)*
  - **Bu veri zorunlu mu?** ➔ **Evet, temel uygulama işlevselliği için zorunludur (App functionality)**
  - **Neden toplanıyor?** ➔ **Uygulama İşlevselliği (App Functionality)**

#### B. Fotoğraflar ve Videolar / Dosyalar (Photos & Videos / Storage)
* **Fotoğraflar / Dosyalar:**
  - *Sadece kullanıcı DXF/SVG dosyasını cihazına kaydettiğinde (Downloads) yerel depolama kullanılır.*
  - Eğer sorulursa: **Toplanmıyor (Not collected)** veya kullanıcı isteğiyle dosya dışa aktarımı için işaretlenebilir.

#### C. Kamera (Camera)
* *Kamera verisi sunucuya aktarılmadığı, yalnızca yerel ZXing QR okuyucu anlık görüntü işlediği için:*
  - Fotoğraf/Video toplanması: **Hayır (Not collected)**

---

### 3. Gizlilik Politikası URL'si (Privacy Policy URL)
Google Play Console'da **"Uygulama İçeriği > Gizlilik Politikası"** alanına yapıştırabileceğiniz seçenekler:

1. **GitHub Pages (Önerilen - Ücretsiz & Kalıcı):**
   - Projenizi GitHub'a yüklediğinizde, `Settings > Pages` sekmesinden `docs/` klasörünü seçtiğinizde anında linkiniz hazır olur:  
     `https://<kullanici_adiniz>.github.io/<depo_adiniz>/`
2. **Google Sites / Notist / Blog:**
   - Hazırladığımız [privacy_policy.html](file:///C:/Users/MONSTER/AndroidStudioProjects/SuYolu/privacy_policy.html) dosyasının metnini ücretsiz bir Google Sites veya GitHub Gist sayfasına yapıştırabilirsiniz.
3. **Doğrudan HTML Dosyası:**
   - Kendi sunucunuz veya alan adınız varsa `privacy_policy.html` dosyasını `https://siteadiniz.com/suyolu/privacy` adresine yükleyebilirsiniz.
