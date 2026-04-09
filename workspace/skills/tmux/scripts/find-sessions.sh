#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Использование: find-sessions.sh [-L имя-socket|-S путь-к-socket|-A] [-q шаблон]

Показать tmux-сессии на socket (если не указан, используется socket tmux по умолчанию).

Параметры:
  -L, --socket       имя socket tmux (передаётся в tmux -L)
  -S, --socket-path  путь к socket tmux (передаётся в tmux -S)
  -A, --all          просканировать все socket в NANOBOT_TMUX_SOCKET_DIR
  -q, --query        подстрока без учёта регистра для фильтрации имён сессий
  -h, --help         показать эту справку
USAGE
}

socket_name=""
socket_path=""
query=""
scan_all=false
socket_dir="${NANOBOT_TMUX_SOCKET_DIR:-${TMPDIR:-/tmp}/nanobot-tmux-sockets}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -L|--socket)      socket_name="${2-}"; shift 2 ;;
    -S|--socket-path) socket_path="${2-}"; shift 2 ;;
    -A|--all)         scan_all=true; shift ;;
    -q|--query)       query="${2-}"; shift 2 ;;
    -h|--help)        usage; exit 0 ;;
    *) echo "Неизвестный параметр: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ "$scan_all" == true && ( -n "$socket_name" || -n "$socket_path" ) ]]; then
  echo "Нельзя использовать --all вместе с -L или -S" >&2
  exit 1
fi

if [[ -n "$socket_name" && -n "$socket_path" ]]; then
  echo "Используйте либо -L, либо -S, но не оба сразу" >&2
  exit 1
fi

if ! command -v tmux >/dev/null 2>&1; then
  echo "tmux не найден в PATH" >&2
  exit 1
fi

list_sessions() {
  local label="$1"; shift
  local tmux_cmd=(tmux "$@")

  if ! sessions="$("${tmux_cmd[@]}" list-sessions -F '#{session_name}\t#{session_attached}\t#{session_created_string}' 2>/dev/null)"; then
    echo "На $label tmux-сервер не найден" >&2
    return 1
  fi

  if [[ -n "$query" ]]; then
    sessions="$(printf '%s\n' "$sessions" | grep -i -- "$query" || true)"
  fi

  if [[ -z "$sessions" ]]; then
    echo "На $label сессии не найдены"
    return 0
  fi

  echo "Сессии на $label:"
  printf '%s\n' "$sessions" | while IFS=$'\t' read -r name attached created; do
    attached_label=$([[ "$attached" == "1" ]] && echo "подключена" || echo "отключена")
    printf '  - %s (%s, запущена %s)\n' "$name" "$attached_label" "$created"
  done
}

if [[ "$scan_all" == true ]]; then
  if [[ ! -d "$socket_dir" ]]; then
    echo "Каталог socket не найден: $socket_dir" >&2
    exit 1
  fi

  shopt -s nullglob
  sockets=("$socket_dir"/*)
  shopt -u nullglob

  if [[ "${#sockets[@]}" -eq 0 ]]; then
    echo "В каталоге $socket_dir socket не найдены" >&2
    exit 1
  fi

  exit_code=0
  for sock in "${sockets[@]}"; do
    if [[ ! -S "$sock" ]]; then
      continue
    fi
    list_sessions "пути socket '$sock'" -S "$sock" || exit_code=$?
  done
  exit "$exit_code"
fi

tmux_cmd=(tmux)
socket_label="socket по умолчанию"

if [[ -n "$socket_name" ]]; then
  tmux_cmd+=(-L "$socket_name")
  socket_label="имени socket '$socket_name'"
elif [[ -n "$socket_path" ]]; then
  tmux_cmd+=(-S "$socket_path")
  socket_label="пути socket '$socket_path'"
fi

list_sessions "$socket_label" "${tmux_cmd[@]:1}"
