#!/usr/bin/env bash
# Builds Lục Bảo TV and publishes GitHub releases in this repo:
#   engine-latest : engine.apk + engine.json    (downloaded silently by every installed TV)
#   tv-latest     : LucBaoTV.apk + tv.json      (app self-update + the link you share)
# Usage: build-and-publish.sh engine|all
set -euo pipefail
MODE="${1:-all}"
case "$MODE" in
  engine|all) ;;
  *) echo "::error::Unknown mode '$MODE' (engine|all)"; exit 1 ;;
esac

# SIGNING_PASSWORD comes from the repository secret KEYSTORE_PASSWORD.
if [ -z "${KEYSTORE_BASE64:-}" ] || [ -z "${SIGNING_PASSWORD:-}" ]; then
  echo "::error::Missing repository secrets KEYSTORE_BASE64 / KEYSTORE_PASSWORD (see README)."
  exit 1
fi
echo "$KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/lucbao.jks"
export SIGNING_KEYSTORE="$RUNNER_TEMP/lucbao.jks"
export SIGNING_ALIAS="${SIGNING_ALIAS:-lucbao}"
export UPDATE_REPO="$GITHUB_REPOSITORY"
export ENGINE_VERSION=$(( $(date +%s) / 60 ))
export TV_VERSION_CODE=$(( $(date +%s) / 60 ))
export TV_VERSION_NAME="1.$(date -u +%Y%m%d).$(date -u +%H%M)"

# JitPack builds NewPipeExtractor on first request, which can take minutes: warm it up.
EXTRACTOR="$(tr -d '[:space:]' < engine/extractor.version)"
curl -fsS -o /dev/null --retry 20 --retry-delay 30 --retry-all-errors --max-time 120 \
  "https://jitpack.io/com/github/TeamNewPipe/NewPipeExtractor/$EXTRACTOR/NewPipeExtractor-$EXTRACTOR.pom" \
  || echo "JitPack warm-up did not answer, trying the build anyway"

chmod +x gradlew
if [ "$MODE" = "engine" ]; then
  ./gradlew --no-daemon --stacktrace :engine:assembleRelease
else
  ./gradlew --no-daemon --stacktrace :engine:assembleRelease :tv:assembleRelease
fi

mkdir -p out
cp engine/build/outputs/apk/release/engine-release.apk out/engine.apk
cat > out/engine.json <<JSON
{"version":$ENGINE_VERSION,"api":1,"extractor":"$EXTRACTOR","file":"engine.apk","sha256":"$(sha256sum out/engine.apk | cut -d' ' -f1)"}
JSON

ensure_release() {
  gh release view "$1" >/dev/null 2>&1 || gh release create "$1" --title "$2" --notes "$3" --latest="$4"
}

ensure_release engine-latest "Bộ phát YouTube (tự cập nhật)" \
  "Tệp này do Lục Bảo TV tự tải về và dùng. Bạn không cần tải thủ công." false
# Upload the file first, then the json that points to it.
gh release upload engine-latest out/engine.apk --clobber
gh release upload engine-latest out/engine.json --clobber

if [ "$MODE" = "all" ]; then
  cp tv/build/outputs/apk/release/tv-release.apk out/LucBaoTV.apk
  cat > out/tv.json <<JSON
{"versionCode":$TV_VERSION_CODE,"versionName":"$TV_VERSION_NAME","file":"LucBaoTV.apk","sha256":"$(sha256sum out/LucBaoTV.apk | cut -d' ' -f1)"}
JSON
  ensure_release tv-latest "Lục Bảo TV – bản mới nhất" \
    "Bản dành cho Android TV / Google TV. Tải **LucBaoTV.apk** để cài lên TV. TV đã cài sẽ tự cập nhật." true
  gh release upload tv-latest out/LucBaoTV.apk --clobber
  gh release upload tv-latest out/tv.json --clobber
  gh release edit tv-latest --title "Lục Bảo TV $TV_VERSION_NAME" >/dev/null
fi

echo "Published engine $ENGINE_VERSION ($EXTRACTOR) mode=$MODE"
{
  echo "### Lục Bảo TV"
  echo "- Bộ phát YouTube: #$ENGINE_VERSION · NewPipeExtractor $EXTRACTOR"
  if [ "$MODE" = "all" ]; then
    echo "- Ứng dụng: $TV_VERSION_NAME ($TV_VERSION_CODE)"
    echo "- Link tải cho TV: https://github.com/$GITHUB_REPOSITORY/releases/download/tv-latest/LucBaoTV.apk"
  fi
} >> "$GITHUB_STEP_SUMMARY"
