#!/usr/bin/env bash
set -e

# ---- Kiểm tra môi trường trên macOS ----

if [[ ! -f "./gradlew" ]]; then
    echo "Không tìm thấy ./gradlew — hãy chạy script này từ thư mục gốc của project."
    exit 1
fi
if [[ ! -x "./gradlew" ]]; then
    echo "gradlew chưa có quyền thực thi, đang tự động chmod +x..."
    chmod +x ./gradlew
fi

if ! java -version >/dev/null 2>&1; then
    echo "Không tìm thấy Java runtime hoạt động được."
    echo "Cài bằng Homebrew: brew install openjdk@17"
    echo "Sau đó thêm vào ~/.zshrc:"
    echo "  export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
    echo "  export PATH=\"\$JAVA_HOME/bin:\$PATH\""
    exit 1
fi

BUILD_TYPE="${1:-debug}"

if [[ "$BUILD_TYPE" != "debug" && "$BUILD_TYPE" != "release" ]]; then
    echo "Usage: ./build-apk.sh [debug|release]"
    exit 1
fi

TASK_SUFFIX="$(tr '[:lower:]' '[:upper:]' <<< "${BUILD_TYPE:0:1}")${BUILD_TYPE:1}"

echo "Building APK ($BUILD_TYPE)..."
./gradlew "assemble${TASK_SUFFIX}"

timestamp=$(date +%Y%m%d_%H%M%S)

# Tên APK mặc định của AGP khi chưa custom archivesName: app-debug.apk / app-release.apk
FOUND_APK=$(find "app/build/outputs/apk/${BUILD_TYPE}" -name "*.apk" -type f -print 2>/dev/null | head -n 1)

if [[ -z "$FOUND_APK" ]]; then
    echo "Không tìm thấy file APK trong app/build/outputs/apk/${BUILD_TYPE}/"
    exit 1
fi

if [[ "$FOUND_APK" == *unsigned* ]]; then
    echo "CẢNH BÁO: APK chưa được ký nên KHÔNG cài được lên máy thật. Kiểm tra cấu hình signing."
fi

mkdir -p PhotoEvents_apk
DEST_APK="PhotoEvents_apk/PhotoEvents_${BUILD_TYPE}_${timestamp}.apk"
cp "$FOUND_APK" "$DEST_APK"

echo "Done: $DEST_APK"
