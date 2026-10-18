#!/usr/bin/env bash
# Fetches glslang + SPIRV-Cross into native/3rdparty (they were empty submodules in the zip you gave me).
# The vendored headers in native/include match glslang 15.4.0 and SPIRV-Cross C API 0.64.
set -euo pipefail
mkdir -p "$(dirname "$0")/3rdparty"; cd "$(dirname "$0")/3rdparty"

GLSLANG_TAG="${GLSLANG_TAG:-15.4.0}"
SPIRV_CROSS_REF="${SPIRV_CROSS_REF:-main}"   # override with a tag/commit whose C API is 0.64 if the check below warns

[ -d glslang ] && [ -f glslang/CMakeLists.txt ] || {
  rm -rf glslang
  git clone --depth 1 --branch "$GLSLANG_TAG" https://github.com/KhronosGroup/glslang.git glslang
}
[ -d SPIRV-Cross ] && [ -f SPIRV-Cross/CMakeLists.txt ] || {
  rm -rf SPIRV-Cross
  git clone https://github.com/KhronosGroup/SPIRV-Cross.git SPIRV-Cross
  (cd SPIRV-Cross && git checkout "$SPIRV_CROSS_REF")
}

# glslang's SPIRV-Tools dependency is not needed (ENABLE_OPT=OFF) but update_glslang_sources may be wanted by some tags.
hdr=SPIRV-Cross/spirv_cross_c.h
minor=$(grep -E '^#define SPVC_C_API_VERSION_MINOR' "$hdr" | awk '{print $3}')
if [ "$minor" != "64" ]; then
  echo "WARNING: SPIRV-Cross C API minor=$minor but vendored header is 64 -> mismatch can break linking/ABI." >&2
  echo "         Re-run with SPIRV_CROSS_REF=<tag-or-commit> or replace native/include/spirv_cross/* with this checkout's headers." >&2
fi
echo "deps ready."
