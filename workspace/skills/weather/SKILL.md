---
name: weather
description: Получать текущую погоду и прогноз с проверкой соответствия локации без API-ключа.
homepage: https://wttr.in/:help
metadata: {"nanobot":{"emoji":"🌤️","requires":{"bins":["curl"]}}}
---

# Навык погоды

Сначала выбирайте самое надёжное совпадение по локации. Для китайских городов и других нелатинских запросов предпочтительнее `wttr.in` с исходным запросом, потому что сервис умеет резолвить нативные названия. Open-Meteo используйте после того, как город точно подтверждён.

## Правила точности

- В финальном ответе всегда повторяйте подтверждённую локацию, регион/страну и время наблюдения
- Не доверяйте первому результату геокодирования вслепую: сверяйте `country`, `admin1`, `admin2`, `population`
- Для запросов на китайском сначала пробуйте `wttr.in` с оригинальным названием
- Если остаётся несколько правдоподобных совпадений, задайте уточняющий вопрос или явно сформулируйте допущение
- Для Open-Meteo используйте `timezone=auto`

## wttr.in

```bash
curl -s "https://wttr.in/London?format=%l:+%c+%t+%h+%w"
curl -s "https://wttr.in/%E6%88%90%E9%83%BD?format=%l:+%c+%t+%h+%w"
curl -s "https://wttr.in/Chengdu?format=j1"
```

Подсказки:
- Пробелы кодируйте как `+`
- Нелатинский текст URL-кодируйте
- `?m` — метрические единицы, `?u` — американские

## Open-Meteo

1. Сначала геокодирование и проверка метаданных:

```bash
curl -s "https://geocoding-api.open-meteo.com/v1/search?name=Chengdu&count=3&language=en&format=json"
```

2. Затем текущая погода и прогноз:

```bash
curl -s "https://api.open-meteo.com/v1/forecast?latitude=30.66667&longitude=104.06667&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
```

Если геокодирование выглядит сомнительно, лучше откатиться к `wttr.in`, чем показать неверную погоду.

Документация: https://open-meteo.com/en/docs
