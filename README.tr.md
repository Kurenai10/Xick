# Xick

**Dil:** [English](README.md) · Türkçe

Xick, [Kick](https://kick.com) için Kotlin ile yazılmış, resmi olmayan ve hafif
bir Android istemcisidir. Düşük mobil veri kullanımına ve sade, hızlı bir
arayüze odaklanır.

> Xick, Kick ile bağlantılı değildir ve Kick tarafından onaylanmamıştır. "Kick", sahibinin ticari markasıdır.

## Özellikler

- Popüler canlı kanalları ve kategorileri sonsuz kaydırma ile gezme
- Kanal ve kategori arama, dile göre filtreleme
- Canlı yayın izleme: kalite seçimi, sığdırma modları, tam ekran ve mini
  oynatıcı (aşağı çekince küçülür, yana kaydırınca kapanır)
- Emote'lu canlı sohbet
- Her kanal için video arşivi (VOD) ve klipler
- Kick hesabıyla giriş (şifre Kick'in kendi sayfasında girilir)
- Takip edilen, şu an canlı olan kanallar; takip etme ve takipten çıkma

## Veri tasarrufu

- Küçük resimler mobil veride en küçük uygun boyutta indirilir.
- Mobil veride video varsayılan olarak 480p ile sınırlanır; kullanıcı değiştirebilir.
- HTTP yanıtları sunucunun `Cache-Control` başlıklarına göre önbelleğe alınır.
- Emote'ler yalnızca bir mesajda göründüğünde yüklenir.
- Sohbetin yan verileri, ilgili sekme açıldığında yüklenir.

## Mimari

- Repository'ler ve Paging 3 ile MVVM
- Oynatma için Media3 / ExoPlayer (HLS)
- Görseller için Coil, ağ için OkHttp, JSON için kotlinx.serialization
- Sohbet için Centrifugo WebSocket

## Derleme

Gereksinimler: JDK 17, Android SDK (API 35).

1. Depoyu klonla.
2. Proje kökünde `local.properties` dosyası oluştur (Git tarafından yok sayılır):

   ```properties
   sdk.dir=/Android/Sdk/yolu
   ```

3. Debug APK derle:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   Windows'ta `gradlew.bat :app:assembleDebug` kullan.

APK, `app/build/outputs/apk/debug/` klasörüne yazılır.

## Durum

Bu erken bir sürümdür. Oynatma, gezinme, sohbet ve giriş test cihazlarında
çalışıyor. Kick'in web sitesi ve API'leri herkese açık belgelenmediği için,
Kick değiştiğinde uygulamanın bazı kısımlarının güncellenmesi gerekebilir.

## Lisans

Lisans dosyası eklenene kadar tüm haklar yazara aittir. Eklendiğinde
[LICENSE](LICENSE) dosyasına bakın.
