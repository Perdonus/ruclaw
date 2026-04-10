#!/usr/bin/env bash
set -euo pipefail

out_root="${1:-}"
if [[ -z "${out_root}" ]]; then
  echo "usage: $0 <output-dir>" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runtime_dir="${out_root%/}/runtime"

version="${VERSION:-$(git -C "${repo_root}" describe --tags --match 'v*' --always --dirty 2>/dev/null || echo dev)}"
git_commit="${GIT_COMMIT:-$(git -C "${repo_root}" rev-parse --short=8 HEAD 2>/dev/null || echo dev)}"
build_time="${BUILD_TIME:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
go_version="${GOVERSION:-$(go version | awk '{print $3}')}"
config_pkg="github.com/Perdonus/ruclaw/pkg/config"
common_tags="goolm,stdjson"
ldflags="-X ${config_pkg}.Version=${version} -X ${config_pkg}.GitCommit=${git_commit} -X ${config_pkg}.BuildTime=${build_time} -X ${config_pkg}.GoVersion=${go_version} -s -w"

mkdir -p "${runtime_dir}"
rm -f "${runtime_dir}/ruclaw" "${runtime_dir}/ruclaw-launcher"

(
  cd "${repo_root}/web/frontend"
  pnpm install --frozen-lockfile
  pnpm build:backend
)

(
  cd "${repo_root}"
  go generate ./...

  GOOS=android GOARCH=arm64 CGO_ENABLED=0 \
    go build -tags "${common_tags}" -ldflags "${ldflags}" \
    -o "${runtime_dir}/ruclaw" ./cmd/picoclaw

  GOOS=android GOARCH=arm64 CGO_ENABLED=0 \
    go build -tags "${common_tags}" -ldflags "${ldflags}" \
    -o "${runtime_dir}/ruclaw-launcher" ./web/backend
)

cat > "${runtime_dir}/BUILD_INFO.txt" <<EOF
Version: ${version}
Commit: ${git_commit}
BuildTime: ${build_time}
GoVersion: ${go_version}
EOF

echo "Android runtime assets prepared in ${runtime_dir}"
