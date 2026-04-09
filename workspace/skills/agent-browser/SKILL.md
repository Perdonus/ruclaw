---
name: agent-browser
description: "Автоматизация браузера через CLI agent-browser. Используйте, когда нужно открыть сайт, заполнить форму, нажать кнопки, снять скриншот, извлечь данные или проверить веб-приложение."
metadata: {"nanobot":{"emoji":"🌐","requires":{"bins":["agent-browser"]},"install":[{"id":"npm","kind":"npm","package":"agent-browser","global":true,"bins":["agent-browser"],"label":"Установить agent-browser (npm)"}]}}
---

# Навык Agent Browser

CLI-автоматизация Chrome/Chromium через CDP. Установка: `npm i -g agent-browser && agent-browser install`.

**Перед использованием навыка** проверьте наличие инструмента командой `which agent-browser`. Если команда не найдена, скажите пользователю, что для автоматизации браузера нужны `agent-browser` и Chromium, доступные только в heavy-сборке. Не пытайтесь ставить их во время выполнения.

## Базовый процесс

1. `agent-browser open <url>` — открыть страницу
2. `agent-browser snapshot -i` — получить интерактивные элементы с ref-идентификаторами (`@e1`, `@e2`, ...)
3. Выполнить действие по ref — `click @e1`, `fill @e2 "text"`
4. После перехода или изменения DOM заново сделайте snapshot — старые ref становятся невалидными

```bash
agent-browser open https://example.com/form
agent-browser snapshot -i
# @e1 [input] "Email", @e2 [input] "Password", @e3 [button] "Submit"
agent-browser fill @e1 "user@example.com"
agent-browser fill @e2 "secret"
agent-browser click @e3
agent-browser wait --load networkidle
agent-browser snapshot -i
```

Если промежуточный вывод не нужен, команды можно объединять через `&&`.

## Команды

```bash
# Навигация
agent-browser open <url>
agent-browser close

# Снимок DOM
agent-browser snapshot -i
agent-browser snapshot -s "#selector"

# Взаимодействие
agent-browser click @e1
agent-browser fill @e2 "text"
agent-browser type @e2 "text"
agent-browser select @e1 "option"
agent-browser check @e1
agent-browser press Enter
agent-browser scroll down 500

# Получение данных
agent-browser get text @e1
agent-browser get url
agent-browser get title

# Ожидание
agent-browser wait @e1
agent-browser wait --load networkidle
agent-browser wait --url "**/dashboard"
agent-browser wait --text "Welcome"
agent-browser wait 2000

# Захват
agent-browser screenshot
agent-browser screenshot --full
agent-browser screenshot --annotate
agent-browser pdf output.pdf

# Семантический поиск
agent-browser find text "Sign In" click
agent-browser find label "Email" fill "user@test.com"
agent-browser find role button click --name "Submit"
```

## Авторизация

```bash
# Импорт состояния из запущенного Chrome
agent-browser --auto-connect state save ./auth.json
agent-browser --state ./auth.json open https://app.example.com

# Постоянный профиль
agent-browser --profile ~/.myapp open https://app.example.com/login

# Именованная сессия
agent-browser --session-name myapp open https://app.example.com/login

# Файл состояния
agent-browser state save auth.json
agent-browser state load auth.json
```

## Работа с iframe

Содержимое iframe встраивается в snapshot. Работайте с ref напрямую, без ручного переключения frame.

## Выполнение JavaScript

```bash
agent-browser eval 'document.title'

agent-browser eval --stdin <<'EVALEOF'
JSON.stringify(Array.from(document.querySelectorAll("a")).map(a => a.href))
EVALEOF
```

## Завершение

Всегда закрывайте сессии после работы:

```bash
agent-browser close
agent-browser --session s1 close
```
