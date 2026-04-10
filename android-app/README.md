# RuClaw Android

Нативный Android-клиент для `RuClaw launcher`.

Что уже есть:

- onboarding под `launcher URL` и `launcher access token`
- список сессий из `/api/sessions`
- история текущей сессии из `/api/sessions/{id}`
- live chat через Pico WebSocket
- markdown/code rendering
- GitHub Actions workflow для сборки APK-артефакта

Транспорт:

- HTTP auth: `Authorization: Bearer <launcher token>`
- WebSocket auth: `Sec-WebSocket-Protocol: token.<pico token>`
- default LAN URL: `http://192.168.1.109:18800`

Сборка:

- локально по умолчанию не требуется
- основной путь: workflow `.github/workflows/android-apk.yml`
