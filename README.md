# Klan & Kılıç Efekt (ClanSwordPlugin)

Spigot / Paper / Purpur **1.16.5 – 1.21.x** için klan, dost klan ve efektli kılıç eklentisi.

## Kurulum
1. [Releases](../../releases/latest) sayfasından `ClanSwordPlugin-x.y.z.jar` dosyasını indir.
2. `plugins/` klasörüne at ve sunucuyu başlat.
3. İsteğe bağlı: Vault + bir ekonomi eklentisi (limit artırma), PlaceholderAPI, WorldGuard.

## Komutlar
| Komut | Açıklama |
|---|---|
| `/klan` | Klan komutları (oluştur, davet, dost, sohbet, sıralama ...) |
| `/kilicefekt ekle <efekt>` / `kaldır` / `liste` | Elindeki kılıca efekt ekle/kaldır |
| `/kilicefektgui` | Efekt yönetim menüsü (sol tık aç/kapat, sağ tık kopya al) |
| `/klan reload` | Tüm ayarları yeniden yükle |

## Dosyalar
- `config.yml` – genel ayarlar, veritabanı (SQLite/MySQL), PvP bölgesi modu
- `efekts.yml` – her efektin menzil / bekleme / süre / seviye ve kılıç adı-açıklaması
- `messages.yml` – mesajlar
- `durum.yml` – menüden kapatılan efektler (otomatik)

## Derleme
```
mvn clean package
```
Çıktı: `target/ClanSwordPlugin-<sürüm>.jar`. Her `main` push'unda GitHub Actions derleyip Release'e yükler.
