---
name: github
description: "Работа с GitHub через CLI gh. Используйте gh issue, gh pr, gh run и gh api для задач, PR, CI и продвинутых запросов."
metadata: {"nanobot":{"emoji":"🐙","requires":{"bins":["gh"]},"install":[{"id":"brew","kind":"brew","formula":"gh","bins":["gh"],"label":"Установить GitHub CLI (brew)"},{"id":"apt","kind":"apt","package":"gh","bins":["gh"],"label":"Установить GitHub CLI (apt)"}]}}
---

# Навык GitHub

Используйте CLI `gh` для работы с GitHub. Если вы не находитесь внутри git-репозитория, всегда указывайте `--repo owner/repo` или передавайте прямой URL.

## Pull Request и CI

Проверить статусы CI у PR:

```bash
gh pr checks 55 --repo owner/repo
```

Показать последние запуски GitHub Actions:

```bash
gh run list --repo owner/repo --limit 10
```

Посмотреть конкретный run и упавшие шаги:

```bash
gh run view <run-id> --repo owner/repo
gh run view <run-id> --repo owner/repo --log-failed
```

## Продвинутые запросы через API

```bash
gh api repos/owner/repo/pulls/55 --jq '.title, .state, .user.login'
```

## JSON-вывод

Большинство команд поддерживает `--json` и `--jq`:

```bash
gh issue list --repo owner/repo --json number,title --jq '.[] | "\(.number): \(.title)"'
```
