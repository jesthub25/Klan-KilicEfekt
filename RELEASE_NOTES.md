## ClanSwordPlugin 2.1.0

**Uyumluluk:** Spigot / Paper / Purpur **1.16.5 – 1.21.x** (tek JAR, Java 8+ bytecode).
Vault, PlaceholderAPI ve WorldGuard artık **isteğe bağlı** (softdepend).

### Kılıç efektleri
- Efekt hiçbir hedefe işlemediğinde (hedef yok / hepsi klan-dost / alan dolu) **bekleme süresi başlamaz**.
- Ejderha nefesi bulutu kullanana, klanına ve dost klanlara **hiç işlemez**.
- Ghast, Wither ve Creeper patlamaları kendine, klana ve dostlara hasar vermez; blok kırmaz.
- **Spider:** menzildeki tüm düşmanlara ağ kurar. Normalde ayak hizasına, elytra ile süzülürken hitbox merkezine;
  oyuncu bloğun ortasında değilse hitbox'ın en çok kapladığı bloğa. O blokta su, lav, buton, başka ağ vb. varsa
  o oyuncuya kurulmaz. Bekleme süresi yalnızca en az bir ağ kurulursa başlar.
- **Shulker:** menzildeki tüm düşmanlara uygulanır.
- **Freeze:** düşmanlar gerçekten donar (hareket ve zıplama kilitlenir, ender incisiyle kaçılamaz).
- **Phantom:** düşmanlardan gizlenme + görünmezlik (eski SPECTATOR yöntemi oyun modunu bozuyordu).
- **Enderman:** duvarların içinden geçmez, yarım bloklarda doğru seviyeye ışınlar.
- **Pillager / Slime:** sahibine, klanına ve dostlara saldırmaz, eşya düşürmez; slime bölünmez.
- Oyuncuya nişan alıp sağ tıklayınca da efekt çalışır; çift el yüzünden çift tetiklenme giderildi.

### Menü ve PvP bölgesi
- `/kilicefektgui` baştan yazıldı: sol tık aç/kapat (yeniden başlatmada korunur, `durum.yml`), sağ tık kopya al.
- WorldGuard'da `pvp allow` olan bölgede "efekt kullanamazsın" hatası giderildi. WorldGuard yoksa efektler her yerde çalışır.
  `sword.require-pvp-region: true` ile sadece PvP bölgelerinde çalışma modu açılabilir.

### Klan / dost sistemi
- Dost daveti veritabanına hiç yazılmıyordu (SQL parametre hatası) — düzeltildi.
- Aynı dost grubundaki tüm klanlar birbirinin dostu sayılır (sadece sahip-üye değil).
- Dost klan üyelerinin oklarına, TNT'sine, kristaline karşı da koruma.
- Mesajlardaki fazladan `%` karakteri giderildi; `&#RRGGBB` hex renk desteği.
- Veritabanı yazmaları ana thread'i hiç bloklamaz (tek arka plan kuyruğu).
- Ekonomi eklentisi bizden sonra yüklense bile Vault bağlantısı bulunur.
