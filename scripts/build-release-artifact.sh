#!/usr/bin/env bash
set -euo pipefail

platform="${1:-}"
if [[ -z "${platform}" ]]; then
  echo "usage: $0 <linux|windows|android>" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

version="${VERSION:-$(git describe --tags --match 'v*' --always --dirty 2>/dev/null || echo dev)}"
git_commit="${GIT_COMMIT:-$(git rev-parse --short=8 HEAD 2>/dev/null || echo dev)}"
build_time="${BUILD_TIME:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
go_version="${GOVERSION:-$(go version | awk '{print $3}')}"
config_pkg="github.com/Perdonus/ruclaw/pkg/config"
common_tags="goolm,stdjson"
ldflags="-X ${config_pkg}.Version=${version} -X ${config_pkg}.GitCommit=${git_commit} -X ${config_pkg}.BuildTime=${build_time} -X ${config_pkg}.GoVersion=${go_version} -s -w"
out_dir="${repo_root}/release-artifacts"
stage_dir="${out_dir}/stage/${platform}"

mkdir -p "${stage_dir}" "${out_dir}"
rm -rf "${stage_dir:?}"/*

prepare_go() {
  go mod tidy
  go generate ./...
}

prepare_frontend() {
  (
    cd web/frontend
    pnpm install --frozen-lockfile
    pnpm build:backend
  )
}

build_binary() {
  local goos="$1"
  local goarch="$2"
  local output="$3"
  local target="$4"

  GOOS="${goos}" GOARCH="${goarch}" CGO_ENABLED=0     go build -tags "${common_tags}" -ldflags "${ldflags}" -o "${output}" "${target}"
}

write_build_info() {
  cat > "${stage_dir}/BUILD_INFO.txt" <<EOF
Version: ${version}
Commit: ${git_commit}
BuildTime: ${build_time}
GoVersion: ${go_version}
EOF
}

package_tar() {
  local archive_name="$1"
  rm -f "${out_dir}/${archive_name}" "${out_dir}/${archive_name}.sha256"
  tar -C "${stage_dir}" -czf "${out_dir}/${archive_name}" .
  (
    cd "${out_dir}"
    sha256sum "${archive_name}" > "${archive_name}.sha256"
  )
}

package_zip() {
  local archive_name="$1"
  rm -f "${out_dir}/${archive_name}" "${out_dir}/${archive_name}.sha256"
  (
    cd "${stage_dir}"
    zip -q -9 "${out_dir}/${archive_name}" ./*
  )
  (
    cd "${out_dir}"
    sha256sum "${archive_name}" > "${archive_name}.sha256"
  )
}

case "${platform}" in
  linux)
    prepare_go
    prepare_frontend
    build_binary linux amd64 "${stage_dir}/ruclaw" ./cmd/picoclaw
    build_binary linux amd64 "${stage_dir}/ruclaw-launcher" ./web/backend
    build_binary linux amd64 "${stage_dir}/ruclaw-launcher-tui" ./cmd/picoclaw-launcher-tui
    write_build_info
    package_tar ruclaw_Linux_x86_64.tar.gz
    ;;
  windows)
    prepare_go
    prepare_frontend
    build_binary windows amd64 "${stage_dir}/ruclaw.exe" ./cmd/picoclaw
    build_binary windows amd64 "${stage_dir}/ruclaw-launcher.exe" ./web/backend
    build_binary windows amd64 "${stage_dir}/ruclaw-launcher-tui.exe" ./cmd/picoclaw-launcher-tui
    write_build_info
    package_zip ruclaw_Windows_x86_64.zip
    ;;
  android)
    prepare_go
    build_binary android arm64 "${stage_dir}/ruclaw" ./cmd/picoclaw
    cat > "${stage_dir}/ANDROID_NOTE.txt" <<EOF
This archive contains the Android CLI build only.
Launcher binaries are not included for Android.
EOF
    write_build_info
    package_tar ruclaw_Android_arm64.tar.gz
    ;;
  *)
    echo "unsupported platform: ${platform}" >&2
    exit 1
    ;;
esac
