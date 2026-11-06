# My English Teacher: loyiha rejasi

Android ilova: ingliz tilini A2/B1 dan B2 gacha, asosan gapirish orqali ko'tarish. Grammatika, so'z boyligi va listening ham real suhbatga xizmat qiladi.

## Holat (2026-11-05)

1–5-bosqichlar yozilgan, joriy versiya 1.1.0. Foydalanuvchi 1.0.0 ni telefonda ishlatib ko'rdi. Reliz: https://github.com/Nodirbek-Abdulaxadov/my-english-teacher/releases/tag/v1.1.0

Tekshirilgan (x86_64 emulator, Android 14, R8 bilan, Claude o'rniga mahalliy soxta server):
- Toza o'rnatish: kalit, modellarni ilova ichida yuklash, ruxsatlar.
- Whisper va Supertonic haqiqiy modellar bilan, 67 soniyalik audioni bo'lib tanish.
- Barcha ekranlar: suhbat, tahlil, grammatika, so'zlar, tinglash, o'z audiosi, 4-3-2, test, natijalar, sozlama.
- Noto'g'ri kalitga haqiqiy Claude API'ning 401 javobi.

Hali tekshirilmagan:
- Yangi darslar haqiqiy Claude javoblari bilan: tayyorgarlik mazmuni va o'qituvchining darajaga moslashishi soxta serverda sinalmaydi.
- Keshlash va `fallbacks`: kompyuterda API kalit yo'q, telefondagi Diagnostika hisoboti hali olinmagan.
- Poco X7 Pro'dagi kechikish raqamlari.
- Sizning talaffuzingiz bilan Whisper testi: Sozlama, Diagnostika bo'limida bor.

Telefonda birinchi sinovdan keyin model va Whisper turini Diagnostika raqamlariga qarab tanlaymiz.

## Qarorlar

| Mavzu | Qaror |
|---|---|
| Platforma | Faqat Android, Kotlin va Jetpack Compose |
| Sinov telefoni | Poco X7 Pro |
| Byudjet | Faqat Claude API krediti ($100/oy), boshqa pullik xizmat yo'q |
| Ovoz | To'liq telefonda ishlaydi, bulut yo'q |
| Tarqatish | Open source, har kim o'z API kaliti bilan ishlatadi |
| Backend | Yo'q |
| Litsenziya | MIT |

## Funksiyalar

**Dars tuzilishi (1.1.0 dan)**
- Har bo'lim tartibi: so'zlar, grammatika, tinglash, suhbat, ravonlik. Birinchi bo'lim eng sodda mavzular: salomlashish, oila, kundalik ishlar.
- Yo'ldagi suhbat oldidan tayyorgarlik: iboralar, namunaviy dialog, qaytarish, qisqa og'zaki mashq, keyin rol o'yini.
- Daraja testi yo'lning boshlanish joyini belgilaydi, oldingi bo'limlar ochiq qoladi. Darajani Sozlamada qo'lda o'zgartirsa bo'ladi.
- O'qituvchi darajaga moslashadi: A2 da qisqa gaplar va sekinroq ovoz. Daraja faqat test yoki qo'lda tanlash bilan o'zgaradi, erkin suhbatda esa yo'ldagi joyingizga qarab ko'tariladi.
- Muhim xatoda o'qituvchi to'g'ri variantni qaytarishni so'raydi, ko'pi bilan har to'rt javobda bir marta.
- "Suhbat" bo'limidagi erkin suhbat tayyorgarliksiz qoladi.

**Speaking (asosiy)**
- Erkin suhbat va rol o'yinlari: intervyu, shifokor, aeroport, small talk.
- Tugmani bosib gapirish: o'ylab pauza qilganda gap kesilmaydi.
- Tutor xato uchun suhbatni to'xtatmaydi, to'g'ri variantni javobida tabiiy qo'llaydi. Xatolar sessiya oxirida tahlil qilinadi.
- "Yaxshiroq ayt" tugmasi: oxirgi gapning B2 varianti (alohida qisqa so'rov, faqat bosilganda).
- Ravonlik mashqlari: qayta hikoya qilish, bitta hikoyani uch marta, har safar qisqaroq vaqtda aytish (A2 da 1:30, 1:00, 0:45; B2 da 4, 3, 2 daqiqa).
- "Nima deyishni bilmayman" tugmasi: 3 ta tayyor javob varianti.

**Grammatika**
- Darslik emas, takroriy xatolardan tuziladi.
- Tushuntirish o'zbekcha, mashq og'zaki.
- Tutor o'zbek tilida so'zlashuvchilarga xos xatolarni biladi: artikllar, zamonlar, predloglar, so'z tartibi, he/she.

**So'z boyligi**
- Suhbatda chiqqan so'zlar, kollokatsiyalar va phrasal verb'lar.
- Oraliqli takrorlash (FSRS), javob og'zaki.

**Listening**
- Darajaga mos dialog va hikoyalar, sozlanadigan tezlik.
- Og'zaki savollar, diktant, shadowing.
- Ixtiyoriy: o'zingiz yuklagan audio (masalan, podcast) telefonda matnga aylanadi va Claude undan savol tuzadi. Bu bepul real nutq manbai.

**Xotira**
- Har sessiyadan keyin profil yangilanadi: xatolar, qiziqishlar, o'rganilgan so'zlar.

**Kunlik tartib (~1 soat):** 5 daqiqa so'z takrori, 30 daqiqa suhbat, 5 daqiqa xatolar tahlili, 15 daqiqa listening va shadowing.

**Odat mexanikasi (Duolingo'dan ilhomlangan)**
- Olinadi: ketma-ket kunlar (streak), kunlik maqsad va XP, bosqichma-bosqich ochiladigan yo'l, qisqa darslar, darhol fikr-mulohaza, zaif joylarni takrorlash.
- XP asosan gapirishga beriladi, shunda ilova gapirishni rag'batlantiradi.
- Olinmaydi: "yurak" jazosi, chunki xato qilish gapirishdan qo'rqitmasligi kerak; liga va reyting, chunki backend yo'q.
- Nom, maskot, rasm, ovoz va dars mazmuni nusxalanmaydi, ko'rinish o'zimizniki bo'ladi.

## Texnik yechim

| Qism | Tanlov | Izoh |
|---|---|---|
| UI | Jetpack Compose | |
| Ma'lumot | Room (SQLite) | Sessiyalar, xatolar, so'zlar, profil telefonda saqlanadi |
| Ovoz yozish | `AudioRecord`, tugmani bosib gapirish | |
| Nutqdan matnga | Whisper (sherpa-onnx) | `base.en` va `small.en` 0-bosqichda solishtiriladi |
| Matndan nutqqa | Supertonic yoki Kokoro (sherpa-onnx) | Zaxira: Android TTS. Piper chiqarildi: sherpa-onnx 2.0 da espeak-ng olib tashlangan |
| Claude | `com.anthropic:anthropic-java` | R8 bilan Android'da ishlashi emulatorda tasdiqlandi |

Bitta navbat oqimi:
1. Foydalanuvchi tugmani bosib gapiradi.
2. Whisper matnga aylantiradi, foydalanuvchi matnni ko'radi va kerak bo'lsa tahrirlaydi.
3. Claude javobi stream bo'lib keladi.
4. Javob gapma-gap ovozga uzatiladi.

## Claude API qoidalari

- **Modellar:** `claude-opus-5-5` (standart), `claude-sonnet-5-5`, `claude-haiku-5-5`. Sozlamada tanlanadi, chunki har foydalanuvchining byudjeti har xil.
- **Effort:** `output_config.effort` doim aniq beriladi: suhbatda `low`, tahlilda `high`.
- **Thinking:** Opus 5.5'da o'chmaydi. Sonnet 5.5'da faqat `{type: "between_tools"}` bilan o'chadi.
- **Keshlash:** tartib `system`, profil, tarix. Profil faqat sessiyalar orasida o'zgaradi. Natija `usage.cache_read_input_tokens` bilan tekshiriladi.
- **Tarix faqat qo'shib boriladi:** `response.content` to'liq qaytariladi (thinking bloklari bilan). Eski navbatlar tahrirlanmaydi, foydalanuvchi faqat yuborilmagan oxirgi gapini tahrirlay oladi.
- **Sessiya tahlili:** `output_config.format` bilan tuzilgan JSON.
- **Yuborilmaydi:** `temperature`, `top_p`, `top_k`, assistant prefill (400 qaytaradi).
- **Refusal:** `stop_reason` har javobda tekshiriladi. Opus va Sonnet'da `fallbacks: "default"` yoqiladi (beta `server-side-fallback-2026-07-01`).
- **Xarajat:** har so'rovning `usage` qiymati saqlanadi va ilovada hisoblagich ko'rsatiladi.

## Xarajat (taxmin, hali o'lchanmagan)

Shartlar:
- Kuniga 1 soat, oyiga 3600 navbat.
- Har navbatda ~8500 token keshdan o'qiladi, ~250 token keshga yoziladi, ~250 token chiqadi (thinking bilan).
- Kuniga 2 sessiya tahlili va 1 ta dars.

| Model | Narx (kirish / chiqish / kesh o'qish, $ per MTok) | Suhbat | Tahlil va darslar | Jami/oy |
|---|---|---|---|---|
| Opus 5.5 | 4 / 20 / 0.20 | ~$29 | ~$11 | ~$40 |
| Sonnet 5.5 | 2 / 10 / 0.20 | ~$17 | ~$5 | ~$23 |
| Haiku 5.5 | 0.10 / 0.50 / ~0.01 | ~$1 | <$1 | ~$1 |

Chiqish tokenlari ikki baravar ko'p bo'lsa, Opus ~$57 bo'ladi. Narxlar 2026-10-06 holatiga ko'ra, kod yozishdan oldin qayta tekshiriladi.

## Open source

- **API kaliti:** birinchi ishga tushirishda ilova ichida kiritiladi va Android Keystore bilan shifrlanadi. Kod, repo va APK ichida bo'lmaydi.
- **Tarqatish:** manbadan build qilish yoki GitHub Releases'dagi tayyor APK. Google Play yo'q.
- **Modellar:** Whisper va ovoz fayllari repo'ga qo'shilmaydi, birinchi ishga tushirishda yuklanadi va checksum bilan tekshiriladi.
- **Litsenziyalar:** har bir model, ovoz va so'z ro'yxati qo'shishdan oldin tekshiriladi. Oxford 3000/5000 kabi himoyalangan ro'yxatlar ko'chirilmaydi.
- **Maxfiylik:** audio telefondan chiqmaydi. Faqat matn foydalanuvchi kaliti bilan Anthropic'ga boradi. Telemetriya yo'q.
- **README:** build, kalit olish, xarajat taxmini, Console'da spend limit qo'yish tavsiyasi.

## Cheklovlar

1. **Talaffuz qo'pol baholanadi.** Claude faqat matnni ko'radi. Berilgan matnni o'qitib, Whisper eshitgani bilan solishtiramiz. Tovush darajasidagi baho bu rejada yo'q, chunki u pullik xizmat talab qiladi.
2. **Whisper xatoni tuzatib yuborishi mumkin.** Bunda Claude xatoni ko'rmaydi. 0-bosqichda o'lchanadi.
3. **Sintetik ovoz real nutq emas.** Telefondagi ovozlar real suhbatdagi qisqarishlarni to'liq bermaydi, shuning uchun o'zingiz yuklagan audio funksiyasi qo'shilgan.
4. **Muddat.** Umumiy hisob bo'yicha B1 dan B2 gacha 200+ soat kerak, ya'ni kuniga 1 soat bilan 7-10 oy. A2 ga yaqin bo'lsa, ko'proq.
5. **Baho taxminiy.** Ilova bergan daraja rasmiy emas, rasmiy natija uchun IELTS kabi imtihon kerak.

## Bosqichlar

### 0. Texnik sinov (Poco X7 Pro'da)
Mikrofon, Whisper, Claude va ovoz bitta zanjirga ulanadi. Asosiy ilova emas, sinov uchun.

Tayyor bo'lish sharti:
- Gap tugagandan birinchi ovozgacha 3 soniyadan kam (Opus 5.5 va Sonnet 5.5 solishtiriladi, model tanlanadi).
- 20 ta ataylab xato gapdan Whisper kamida 15 tasidagi xatoni saqlaydi.
- `base.en` yoki `small.en`, Supertonic yoki Kokoro tanlanadi.
- Java SDK Android'da ishlashi tekshiriladi.

### 1. Gaplashadigan MVP
Kalit kiritish, model yuklash, erkin suhbat, mavzular, suhbat tarixi, xarajat hisoblagichi.

0-bosqichdan qolgan vazifalar:
- Kokoro asosiy ovoz bo'lsa: lug'atda yo'q so'zlar (ismlar) tashlab ketilmasligi uchun qo'shimcha lug'at.

Tayyor bo'lish sharti: 30 daqiqalik suhbat uzilishsiz o'tadi va kesh ishlaydi.

### 2. O'rganish halqasi
Sessiya tahlili, xatolar ro'yxati, "Yaxshiroq ayt", profil xotirasi, rol o'yinlari.

Tayyor bo'lish sharti: keyingi sessiyada tutor oldingi xatolar va qiziqishlarni hisobga oladi.

### 3. Grammatika va so'zlar
Xatolardan tuzilgan mini-darslar, FSRS takrorlash, og'zaki mashqlar.

Tayyor bo'lish sharti: suhbatdagi xato va yangi so'z avtomatik mashqqa aylanadi.

### 4. Listening va talaffuz
Dialoglar, tezlik sozlash, diktant, shadowing, matn solishtirish, o'zingiz yuklagan audio.

Tayyor bo'lish sharti: kunlik 15 daqiqalik listening internetsiz yaratilgan ovoz bilan ishlaydi.

### 5. Progress va ochiq reliz
Boshlang'ich daraja testi, oylik og'zaki test, grafiklar (daqiqasiga so'zlar, pauzalar, 100 so'zga xatolar, B2 so'zlar ulushi), README, MIT litsenziya, birinchi GitHub reliz.

Tayyor bo'lish sharti: boshqa odam README bo'yicha o'z kaliti bilan ilovani ishga tushira oladi.

Har bosqichdan keyin ilova amalda ishlatib ko'riladi, keyingi bosqich shu tajribaga qarab o'zgarishi mumkin.

## Rejaga kirmaydi

- Backend, akkaunt, sinxronizatsiya
- iOS, Google Play
- Bulutli STT/TTS, tovush darajasidagi talaffuz bahosi
- O'zbekchadan boshqa tushuntirish tillari (birinchi versiyada)
