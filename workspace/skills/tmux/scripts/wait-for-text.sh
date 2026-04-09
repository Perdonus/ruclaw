#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Использование: wait-for-text.sh -t target -p pattern [параметры]

Периодически проверять tmux pane на наличие текста и завершиться, когда он найден.

Параметры:
  -t, --target    цель tmux (session:window.pane), обязательно
  -p, --pattern   regex-шаблон для поиска, обязательно
  -F, --fixed     считать шаблон обычной строкой (grep -F)
  -T, --timeout   сколько ждать в секундах (целое число, по умолчанию: 15)
  -i, --interval  интервал проверки в секундах (по умолчанию: 0.5)
  -l, --lines     сколько строк истории проверять (целое число, по умолчанию: 1000)
  -h, --help      показать эту справку
USAGE
}

target=""
pattern=""
grep_flag="-E"
timeout=15
interval=0.5
lines=1000

while [[ $# -gt 0 ]]; do
  case "$1" in
    -t|--target)   target="${2-}"; shift 2 ;;
    -p|--pattern)  pattern="${2-}"; shift 2 ;;
    -F|--fixed)    grep_flag="-F"; shift ;;
    -T|--timeout)  timeout="${2-}"; shift 2 ;;
    -i|--interval) interval="${2-}"; shift 2 ;;
    -l|--lines)    lines="${2-}"; shift 2 ;;
    -h|--help)     usage; exit 0 ;;
    *) echo "Неизвестный параметр: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "$target" || -z "$pattern" ]]; then
  echo "Нужно указать target и pattern" >&2
  usage
  exit 1
fi

if ! [[ "$timeout" =~ ^[0-9]+$ ]]; then
  echo "timeout должен быть целым числом секунд" >&2
  exit 1
fi

if ! [[ "$lines" =~ ^[0-9]+$ ]]; then
  echo "lines должен быть целым числом" >&2
  exit 1
fi

if ! command -v tmux >/dev/null 2>&1; then
  echo "tmux не найден в PATH" >&2
  exit 1
fi

# End time in epoch seconds (integer, good enough for polling)
start_epoch=$(date +%s)
deadline=$((start_epoch + timeout))

while true; do
  # -J joins wrapped lines, -S uses negative index to read last N lines
  pane_text="$(tmux capture-pane -p -J -t "$target" -S "-${lines}" 2>/dev/null || true)"

  if printf '%s\n' "$pane_text" | grep $grep_flag -- "$pattern" >/dev/null 2>&1; then
    exit 0
  fi

  now=$(date +%s)
  if (( now >= deadline )); then
    echo "Истекло время ожидания: ${timeout}с, шаблон не найден: $pattern" >&2
    echo "Последние ${lines} строк из $target:" >&2
    printf '%s\n' "$pane_text" >&2
    exit 1
  fi

  sleep "$interval"
done
