---
name: tmux
description: Управление tmux-сессиями для интерактивных CLI через отправку клавиш и чтение вывода pane.
metadata: {"nanobot":{"emoji":"🧵","os":["darwin","linux"],"requires":{"bins":["tmux"]}}}
---

# Навык tmux

Используйте tmux только тогда, когда действительно нужен интерактивный TTY. Для долгих неинтерактивных задач сначала предпочитайте обычный exec.

## Быстрый старт

```bash
SOCKET_DIR="${NANOBOT_TMUX_SOCKET_DIR:-${TMPDIR:-/tmp}/nanobot-tmux-sockets}"
mkdir -p "$SOCKET_DIR"
SOCKET="$SOCKET_DIR/nanobot.sock"
SESSION=nanobot-python

tmux -S "$SOCKET" new -d -s "$SESSION" -n shell
tmux -S "$SOCKET" send-keys -t "$SESSION":0.0 -- 'PYTHON_BASIC_REPL=1 python3 -q' Enter
tmux -S "$SOCKET" capture-pane -p -J -t "$SESSION":0.0 -S -200
```

После запуска сессии всегда печатайте команды для мониторинга:

```
tmux -S "$SOCKET" attach -t "$SESSION"
tmux -S "$SOCKET" capture-pane -p -J -t "$SESSION":0.0 -S -200
```

## Соглашение по socket

- Используйте переменную `NANOBOT_TMUX_SOCKET_DIR`
- Путь по умолчанию: `$NANOBOT_TMUX_SOCKET_DIR/nanobot.sock`

## Таргеты pane

- Формат: `session:window.pane` (по умолчанию `:0.0`)
- Имена делайте короткими, без пробелов
- Для диагностики используйте:
  - `tmux -S "$SOCKET" list-sessions`
  - `tmux -S "$SOCKET" list-panes -a`

## Безопасная отправка ввода

- Для литеральных строк: `tmux -S "$SOCKET" send-keys -t target -l -- "$cmd"`
- Для control-клавиш: `tmux -S "$SOCKET" send-keys -t target C-c`

## Наблюдение за выводом

- История pane: `tmux -S "$SOCKET" capture-pane -p -J -t target -S -200`
- Ожидание текста: `{baseDir}/scripts/wait-for-text.sh -t session:0.0 -p 'pattern'`
- При необходимости можно attach; выход — `Ctrl+b d`

## Python REPL

Для Python REPL задавайте `PYTHON_BASIC_REPL=1`, иначе обычный REPL может ломать сценарии с `send-keys`.

## Windows / WSL

tmux поддерживается на macOS/Linux. На Windows используйте WSL и ставьте tmux внутри WSL.

## Оркестрация кодовых агентов

tmux хорошо подходит для параллельного запуска нескольких кодовых агентов:

```bash
SOCKET="${TMPDIR:-/tmp}/codex-army.sock"

for i in 1 2 3 4 5; do
  tmux -S "$SOCKET" new-session -d -s "agent-$i"
done

tmux -S "$SOCKET" send-keys -t agent-1 "cd /tmp/project1 && codex --yolo 'Fix bug X'" Enter
tmux -S "$SOCKET" send-keys -t agent-2 "cd /tmp/project2 && codex --yolo 'Fix bug Y'" Enter
```

Советы:
- Используйте отдельные git worktree для параллельных фиксов
- В свежих клонах сначала ставьте зависимости
- Для определения завершения проверяйте возврат приглашения shell
- Для неинтерактивных фиксов Codex обычно нужен `--yolo` или аналогичный режим

## Завершение

- Убить одну сессию: `tmux -S "$SOCKET" kill-session -t "$SESSION"`
- Убить все сессии на socket: `tmux -S "$SOCKET" list-sessions -F '#{session_name}' | xargs -r -n1 tmux -S "$SOCKET" kill-session -t`
- Полностью удалить приватный socket: `tmux -S "$SOCKET" kill-server`
