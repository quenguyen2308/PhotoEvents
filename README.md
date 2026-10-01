# Photo Events

Ứng dụng Android (Kotlin) ghi chú sự kiện kèm ảnh — chọn ảnh từ Photo Picker hệ thống,
tự upload thumbnail lên Google Drive riêng của người dùng, đồng bộ hai chiều giữa nhiều
thiết bị cùng tài khoản Google.

## Tính năng

- Tạo sự kiện với tiêu đề, ghi chú, **ngày diễn ra** (mặc định hôm nay, đổi lại được bất cứ lúc nào).
- Chọn **nhiều ảnh** cùng lúc cho 1 sự kiện (Photo Picker hệ thống, Android 13+ trở xuống đều dùng chung 1 API).
- Mỗi sự kiện có thể **chọn 1 ảnh làm ảnh đại diện** (icon ngôi sao), không nhất thiết là ảnh đầu tiên.
- **Kéo-thả** để đổi vị trí hiển thị ảnh trong 1 sự kiện.
- **Xoá từng ảnh riêng lẻ**, hoặc xoá cả sự kiện — hỗ trợ **chọn nhiều sự kiện** để xoá hàng loạt (giữ/long-press để vào chế độ chọn).
- Màn hình chính: **sắp xếp** theo ngày diễn ra / ngày tạo / cập nhật gần đây / tên / số lượng ảnh;
  mỗi sự kiện có thể **xổ ra xem nhanh** toàn bộ ảnh ngay trong danh sách (trạng thái mở/đóng chỉ cục bộ, không đồng bộ).
- **Kéo-để-làm-mới (pull-to-refresh)** khi cần chủ động đồng bộ; mọi thao tác lưu/xoá đều tự
  bắn đồng bộ ngầm, không có popup làm phiền.
- Chạy được **không cần đăng nhập Google** ở bản debug (xem phần Đồng bộ Google Drive bên dưới)
  — chỉ tính năng đồng bộ không hoạt động cho tới khi đăng nhập.

## Kiến trúc & kỹ thuật

- **Ngôn ngữ:** Kotlin, View hệ thống cũ (không Compose), ViewBinding tắt — dùng `findViewById`.
- **Lưu trữ cục bộ:** Room — 2 bảng `events` (1-nhiều) `event_images`, quan hệ qua `@Relation`
  (`EventWithImages`), khoá ngoại `ON DELETE CASCADE`.
- **Đồng bộ:** `SyncManager` — merge kiểu *last-write-wins* theo `updatedAt`, ghi toàn bộ
  metadata (sự kiện + ảnh, trừ đường dẫn cache cục bộ) vào 1 file `metadata.json` trong folder
  riêng `PhotoEventsApp` trên Google Drive (scope `drive.file` — app chỉ thấy file do chính nó tạo).
  Ảnh được resize thành thumbnail rồi upload riêng từng file.
- **Chạy ngầm, không chặn UI:** mọi lượt sync chạy trong `SyncScope` (coroutine scope sống theo
  cả app, không bị huỷ khi 1 Activity đóng ngay sau khi trigger) + `Mutex` (chống 2 lượt sync
  chồng nhau) + `NonCancellable` bên trong `SyncManager.sync()`.
- **Đăng nhập:** Google Sign-In (`com.google.android.gms:play-services-auth`) +
  `google-api-client-android` / `google-api-services-drive` gọi thẳng Drive REST API, không qua server riêng.

## Cấu trúc thư mục

```
app/src/main/java/com/example/photoevents/
├── MainActivity.kt           # Danh sách sự kiện, sắp xếp, chọn nhiều để xoá, pull-to-refresh
├── AddEventActivity.kt       # Tạo sự kiện mới + chọn ảnh + chọn ngày diễn ra
├── EventDetailActivity.kt    # Xem/xoá/kéo-thả ảnh, đổi ảnh đại diện, đổi ngày diễn ra
├── data/
│   ├── Event.kt               Event.kt, EventImage.kt    # Room entity
│   ├── EventDao.kt, EventImageDao.kt                     # Room DAO
│   ├── EventWithImages.kt                                # quan hệ 1 sự kiện - nhiều ảnh
│   └── AppDatabase.kt
├── drive/
│   ├── DriveServiceHelper.kt  # upload/download file, folder, metadata.json
│   ├── DriveSession.kt        # lấy DriveServiceHelper từ tài khoản Google đã đăng nhập
│   ├── SyncManager.kt         # logic merge + đồng bộ 2 chiều
│   └── SyncScope.kt           # coroutine scope sống theo app, dùng khi bắn sync ngầm
└── ui/
    ├── EventsAdapter.kt, ImagesAdapter.kt, ImageDragCallback.kt
    └── SortOption.kt
```

## Build

Yêu cầu: JDK 17, Android SDK (biến môi trường `ANDROID_HOME`). Không cần Android Studio —
project có sẵn Gradle Wrapper.

```bash
chmod +x gradlew build-apk.sh   # chỉ cần làm 1 lần
./build-apk.sh debug            # hoặc: release
```

APK bản release ra ở `PhotoEvents_apk/`, bản debug giữ nguyên ở vị trí mặc định `app/build/outputs/apk/debug/app-debug.apk`. `./gradlew assembleDebug` / `assembleRelease` dùng trực tiếp cũng được.

### Đăng nhập Google theo build type

- **Debug:** không bắt buộc đăng nhập lúc mở app — dùng thử mọi tính năng cục bộ (thêm/xem/xoá
  sự kiện, ảnh) kể cả trên máy ảo không có Google Play Services. Đồng bộ chỉ hoạt động sau khi
  đăng nhập thủ công (pull-to-refresh sẽ hỏi đăng nhập nếu chưa có tài khoản).
- **Release:** tự bật màn đăng nhập Google ngay khi mở app.

### Ký bản release

Không có `keystore.properties`, bản release tự ký tạm bằng debug keystore (vẫn cài lên máy thật
bình thường). Muốn dùng keystore riêng:

```bash
keytool -genkeypair -v -keystore release.jks -alias photoevents -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties   # rồi điền mật khẩu
```

`release.jks` đặt cạnh `keystore.properties` ở thư mục gốc. Cả 2 file đã có trong `.gitignore`.

## Cấu hình Google Cloud (bắt buộc để đồng bộ Drive hoạt động)

1. Tạo project trên [Google Cloud Console](https://console.cloud.google.com), bật **Google Drive API**.
2. Cấu hình **OAuth consent screen** (thêm tài khoản test nếu consent screen ở chế độ Testing).
3. Tạo **OAuth Client ID loại Android**:
   - Package name: `com.example.photoevents`
   - SHA-1: lấy bằng `./gradlew signingReport`, hoặc:
     ```bash
     APKSIGNER=$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)
     "$APKSIGNER" verify --print-certs app/build/outputs/apk/debug/app-debug.apk | grep "SHA-1"
     ```
4. Mỗi keystore (debug / release riêng) cần một Client ID riêng với đúng SHA-1 của keystore đó —
   lệch SHA-1 hoặc package name là nguyên nhân phổ biến nhất của lỗi đăng nhập mã `10` (`DEVELOPER_ERROR`).

Không cần tạo Web Client ID — app chỉ dùng scope `drive.file` qua `GoogleSignInAccount`, không lấy ID token.

## Giới hạn / lưu ý

- `AppDatabase` dùng `fallbackToDestructiveMigration()` — mỗi lần đổi schema (thêm field mới),
  dữ liệu cục bộ **chưa đồng bộ** trên máy đang chạy bản cũ sẽ mất khi cài bản mới. Nhớ đồng bộ
  trước khi cập nhật, hoặc tự viết `Migration` cụ thể nếu cần giữ dữ liệu khi lên production.
- Đổi tên folder Drive (`APP_FOLDER_NAME` trong `DriveServiceHelper.kt`) hoặc đổi package name /
  keystore sẽ khiến app mất kết nối với dữ liệu đã đồng bộ trước đó / cần tạo lại OAuth Client ID.
- Xoá mềm (soft-delete): sự kiện/ảnh bị xoá chỉ đánh dấu `deleted = true`, file thật trên Drive
  được dọn ở lượt sync kế tiếp — cần có mạng và đã đăng nhập thì việc xoá mới thực sự lan ra Drive
  và các thiết bị khác.
