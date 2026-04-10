#!/usr/bin/env bash
set -euo pipefail

out_root="${1:-}"
if [[ -z "${out_root}" ]]; then
  echo "usage: $0 <output-dir>" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runtime_dir="${out_root%/}/runtime"
runtime_lib_dir="${runtime_dir}/lib"
llama_cpp_repo="${LLAMA_CPP_REPO:-https://github.com/ggml-org/llama.cpp.git}"
llama_cpp_ref="${LLAMA_CPP_REF:-b8749}"
llama_android_platform="${LLAMA_CPP_ANDROID_PLATFORM:-android-26}"
android_ndk="${ANDROID_NDK:-${ANDROID_NDK_ROOT:-${ANDROID_NDK_LATEST_HOME:-}}}"

version="${VERSION:-$(git -C "${repo_root}" describe --tags --match 'v*' --always --dirty 2>/dev/null || echo dev)}"
git_commit="${GIT_COMMIT:-$(git -C "${repo_root}" rev-parse --short=8 HEAD 2>/dev/null || echo dev)}"
build_time="${BUILD_TIME:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
go_version="${GOVERSION:-$(go version | awk '{print $3}')}"
config_pkg="github.com/Perdonus/ruclaw/pkg/config"
common_tags="goolm,stdjson"
ldflags="-X ${config_pkg}.Version=${version} -X ${config_pkg}.GitCommit=${git_commit} -X ${config_pkg}.BuildTime=${build_time} -X ${config_pkg}.GoVersion=${go_version} -s -w"

mkdir -p "${runtime_dir}"
rm -f "${runtime_dir}/ruclaw" "${runtime_dir}/ruclaw-launcher" "${runtime_dir}/llama-server"
rm -rf "${runtime_lib_dir}"

if [[ -z "${android_ndk}" && -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}/ndk" ]]; then
  android_ndk="$(find "${ANDROID_SDK_ROOT}/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
fi
if [[ -z "${android_ndk}" || ! -f "${android_ndk}/build/cmake/android.toolchain.cmake" ]]; then
  echo "Android NDK not found. Set ANDROID_NDK / ANDROID_NDK_ROOT / ANDROID_NDK_LATEST_HOME." >&2
  exit 1
fi

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

llama_work_dir="$(mktemp -d)"
trap 'rm -rf "${llama_work_dir}"' EXIT

git clone --depth 1 --branch "${llama_cpp_ref}" "${llama_cpp_repo}" "${llama_work_dir}/llama.cpp"

cmake -S "${llama_work_dir}/llama.cpp" -B "${llama_work_dir}/build-android" \
  -DCMAKE_TOOLCHAIN_FILE="${android_ndk}/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM="${llama_android_platform}" \
  -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DLLAMA_BUILD_SERVER=ON \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_WEBUI=OFF \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=OFF

cmake --build "${llama_work_dir}/build-android" --config Release --target llama-server -j"$(nproc)"

cp "${llama_work_dir}/build-android/bin/llama-server" "${runtime_dir}/llama-server"
chmod +x "${runtime_dir}/llama-server"

while IFS= read -r -d '' shared_lib; do
  mkdir -p "${runtime_lib_dir}"
  cp "${shared_lib}" "${runtime_lib_dir}/"
done < <(find "${llama_work_dir}/build-android" -type f \( -name '*.so' -o -name '*.so.*' \) -print0)

cat > "${runtime_dir}/BUILD_INFO.txt" <<EOF
Version: ${version}
Commit: ${git_commit}
BuildTime: ${build_time}
GoVersion: ${go_version}
LlamaCppRef: ${llama_cpp_ref}
EOF

echo "Android runtime assets prepared in ${runtime_dir}"
