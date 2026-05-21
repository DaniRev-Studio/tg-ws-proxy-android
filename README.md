# TG WS Proxy — Android APK

Telegram MTProto WebSocket прокси в виде Android-приложения.  
Работает как **фоновый сервис** — Telegram продолжает работать через прокси даже когда приложение свёрнуто.

---

## Как получить APK (через GitHub Actions — бесплатно)

### 1. Создай репозиторий на GitHub

```
github.com → New repository → tg-ws-proxy-android → Create
```

### 2. Загрузи этот проект

```bash
cd tg-proxy-apk
git init
git remote add origin https://github.com/ТВО_ИМЯ/tg-ws-proxy-android.git
git add .
git commit -m "initial"
git push -u origin main
```

### 3. GitHub Actions автоматически начнёт сборку

Перейди на `github.com/ТВО_ИМЯ/tg-ws-proxy-android` → вкладка **Actions**  
Дождись завершения (5–15 мин) → зелёная галочка ✅

### 4. Скачай APK

Открой завершённый run → раздел **Artifacts** → скачай **TgWsProxy-APK**

### 5. Установи на телефон

- Включи «Установку из неизвестных источников» (один раз)  
- Установи скачанный APK

---

## Как пользоваться

1. Открой **TG WS Proxy** на телефоне
2. Нажми **Запустить**
3. В шторке уведомлений появится:
   - Ссылка `tg://proxy?...`
   - Кнопка **Открыть в TG** — одно нажатие подключает Telegram
   - Кнопка **Стоп** — остановка прокси
4. Telegram → подключён ✓

---

## Для выпуска релиза с APK в разделе Releases

```bash
git tag v1.0.0
git push origin v1.0.0
```

APK автоматически появится в `github.com/.../releases` — можно делиться ссылкой.

---

## Технологии

- **[Chaquopy](https://chaquo.com/chaquopy/)** — Python 3.11 встроен прямо в APK (ARM64 + x86_64)
- **`cryptography`** — собирается Chaquopy под Android автоматически
- **Android Foreground Service** — прокси живёт в фоне
- Ядро прокси — [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) без изменений

---

## Лицензия

MIT
