# TG WS Proxy — Android APK
 
Работает как **фоновый сервис** — Telegram продолжает работать через прокси даже когда приложение свёрнуто.

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

## Технологии

- **[Chaquopy](https://chaquo.com/chaquopy/)** — Python 3.11 встроен прямо в APK (ARM64 + x86_64)
- **`cryptography`** — собирается Chaquopy под Android автоматически
- **Android Foreground Service** — прокси живёт в фоне
- Ядро прокси — [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) без изменений

---

## Лицензия

MIT
