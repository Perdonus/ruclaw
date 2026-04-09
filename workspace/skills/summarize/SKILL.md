---
name: summarize
description: Кратко пересказывать или извлекать текст/транскрипты из URL, подкастов и локальных файлов. Хороший запасной вариант для запросов вроде “расшифруй это видео”.
homepage: https://summarize.sh
metadata: {"nanobot":{"emoji":"🧾","requires":{"bins":["summarize"]},"install":[{"id":"brew","kind":"brew","formula":"steipete/tap/summarize","bins":["summarize"],"label":"Установить summarize (brew)"}]}}
---

# Навык Summarize

Быстрый CLI для пересказа URL, локальных файлов и ссылок YouTube.

## Когда использовать

Включайте этот навык сразу, если пользователь просит:
- “используй summarize.sh”
- “о чём эта ссылка / это видео?”
- “сделай краткий пересказ по URL / статье”
- “расшифруй это YouTube / видео”

## Быстрый старт

```bash
summarize "https://example.com" --model google/gemini-3-flash-preview
summarize "/path/to/file.pdf" --model google/gemini-3-flash-preview
summarize "https://youtu.be/dQw4w9WgXcQ" --youtube auto
```

## YouTube: краткий пересказ или транскрипт

Только извлечение транскрипта:

```bash
summarize "https://youtu.be/dQw4w9WgXcQ" --youtube auto --extract-only
```

Если пользователь просит полный транскрипт, но он огромный, сначала дайте краткий пересказ, потом уточните нужный раздел или таймкод.

## Модель и ключи

Задайте API-ключ для нужного провайдера:
- OpenAI: `OPENAI_API_KEY`
- Anthropic: `ANTHROPIC_API_KEY`
- xAI: `XAI_API_KEY`
- Google: `GEMINI_API_KEY` (алиасы: `GOOGLE_GENERATIVE_AI_API_KEY`, `GOOGLE_API_KEY`)

Если модель не указана, по умолчанию используется `google/gemini-3-flash-preview`.

## Полезные флаги

- `--length short|medium|long|xl|xxl|<chars>`
- `--max-output-tokens <count>`
- `--extract-only`
- `--json`
- `--firecrawl auto|off|always`
- `--youtube auto`

## Конфиг

Необязательный файл: `~/.summarize/config.json`

```json
{ "model": "openai/gpt-5.4" }
```

Дополнительные сервисы:
- `FIRECRAWL_API_KEY` для трудных сайтов
- `APIFY_API_TOKEN` для запасного варианта по YouTube
