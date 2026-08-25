# Catatan implementasi — Unlimited Combination Enchantment (MC 26.2 / Fabric)

## Apa yang ditambahkan di update ini

Kode ada di `src/main/java/com/example/addenchanted/mixin/`:

### 1. `EnchantmentMixin.java`
- `isCompatibleWith` di-force selalu `true` → enchantment yang biasanya
  saling eksklusif (Sharpness/Smite/Bane of Arthropods, keluarga Protection,
  dll) sekarang bisa digabung bersamaan di anvil.
- `isSupportedItem` di-force selalu `true` → enchanted book apa pun bisa
  dipasang ke item apa pun (mis. Frost Walker ke pedang), bukan cuma ke
  item yang "cocok" secara vanilla.

### 2. `AnvilMenuMixin.java`
- Cap "Too Expensive!" (biasanya muncul saat cost anvil mencapai 40 level)
  dinaikkan jadi tak terbatas → anvil tidak akan pernah menolak karena
  kemahalan, boleh digabung berkali-kali sesuka hati.
- Fitur baru **ekstrak enchantment ke book**: taruh item yang sudah
  ber-enchant di slot **kiri** anvil, dan Book biasa di slot **kanan**.
  Hasilnya satu Enchanted Book berisi *semua* enchantment yang ada di item
  tadi (kalau enchantment-nya lebih dari satu, buku hasilnya otomatis
  berisi semuanya juga). Item sumber di slot kiri habis terpakai, sama
  seperti perilaku anvil normal (konsumsi input kiri).

## ⚠️ Penting — batasan sandbox saat kode ini dibuat

Kode ini dibuat/diedit di sandbox **tanpa akses internet** ke Maven Fabric
maupun server distribusi Minecraft (`maven.fabricmc.net`, dll), jadi saya
**tidak bisa menjalankan `./gradlew build` di sini** untuk memverifikasi
100% bebas error terhadap source Minecraft 26.2 yang sebenarnya (yang
mappingnya baru saja pindah total dari Yarn ke Mojang mappings dan belum
ada di data training saya). Yang sudah saya lakukan untuk menekan risiko:

- Semua kode Java sudah dicek valid secara **sintaks** (parse berhasil,
  tidak ada typo kurung/titik koma dsb).
- Nama kelas/metode target mixin (`AnvilMenu`, `Enchantment`,
  `isCompatibleWith`, `createResult`, `isSupportedItem`, field `slots`,
  dll) memakai nama resmi Mojang mappings yang secara historis stabil di
  banyak versi Minecraft — tapi belum saya cocokkan langsung ke source
  26.2 karena tidak bisa mengunduhnya di sini.
- Injector yang risikonya lebih tinggi (`isSupportedItem`, cap `40`) sudah
  diberi `require = 0`: kalau nama/konstanta itu ternyata berubah di 26.2,
  mixin itu akan gagal apply dengan warning di log saja — **bukan
  meng-crash seluruh mod/game**. Fitur inti (kompatibilitas enchant +
  ekstraksi ke book) tetap wajib match karena method-nya jauh lebih stabil.

**Singkatnya: silakan build di komputermu sendiri (yang punya akses
internet ke Maven Fabric/Mojang) — kalau ada error, kemungkinan besar
hanya soal penyesuaian nama method/field, bukan kesalahan logika.**

### Kalau build gagal / ada mixin yang tidak apply

1. Jalankan `./gradlew build` di komputermu.
2. Kalau error "Unable to find method/field ...", buka
   [mcsrc.dev](https://mcsrc.dev) — tool decompiler resmi yang disebut
   Fabric untuk 26.x — cari kelas `AnvilMenu` atau `Enchantment`, cek nama
   method/field aslinya di 26.2, lalu sesuaikan string `method = "..."`
   di file mixin terkait.
3. Kalau constant `40` di `AnvilMenuMixin` sudah tidak match (misal sudah
   diganti jadi field bernama semacam `MAXIMUM_COST` alih-alih literal
   inline), ganti `@ModifyConstant` dengan `@Inject` yang menimpa field
   cost setelah `createResult` selesai dihitung.

## Build

```
./gradlew build
```

Hasil jar ada di `build/libs/unlimited-combination-enchantment-<versi>.jar`.
