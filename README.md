# Lục Bảo TV

Ứng dụng xem YouTube **không có quảng cáo** dành riêng cho **Android TV / Google TV**, điều khiển hoàn toàn bằng remote. Đây là ứng dụng độc lập (tên gói `vn.lucbao.tv`), không liên quan tới bản Lục Bảo trên điện thoại.

## Tính năng

- **Trang chủ:**
  - Ảnh lớn nhìn rõ từ xa. Ô đang chọn phóng to và có viền xanh lá.
  - Có các mục *Dành cho bạn*, *Kênh bạn theo dõi* (video mới có nhãn **MỚI**), Âm nhạc, Trò chơi, Phim, Trực tiếp…
- **Trình phát toàn màn hình**, video chất lượng tới 4K:
  - Khi ẩn bảng điều khiển: **OK** để tạm dừng/phát, **◀ ▶** để tua 10 giây, **▲** để hiện bảng điều khiển, **▼** để xem *Nhiều video hơn*. Các nút media trên remote cũng dùng được.
  - Trong bảng điều khiển có chọn chất lượng, tốc độ, Yêu thích, Theo dõi kênh và video trước/sau.
- **Tìm kiếm:**
  - Tìm bằng giọng nói: nút micro trên màn hình, hoặc nút tìm kiếm trên remote nếu TV cho phép.
  - Bàn phím trên màn hình có đủ chữ có dấu. Gõ chữ rồi bấm **dấu** để đổi lần lượt sắc → huyền → hỏi → ngã → nặng.
- **Theo dõi kênh không cần tài khoản.** Có Xem tiếp (nhớ chỗ đang xem), Yêu thích và chọn phông chữ. Mọi thứ chỉ lưu trên TV.
- **Tự cập nhật.** Cài một lần là xong.

## Tự cập nhật hoạt động thế nào

| Phần | Cập nhật |
|---|---|
| **Bộ phát YouTube** (`engine/`), là phần hay hỏng khi YouTube thay đổi | **Tự động hoàn toàn.** TV tải gói mới, kiểm tra chữ ký rồi nạp ngay, không cần cài lại. |
| **Ứng dụng** (`tv/`) | Khi bạn sửa code. TV tự tải bản mới và cài khi đang mở Lục Bảo TV mà không phát video. Android TV 12 trở lên thường cài âm thầm. Bản cũ hơn hiện hộp xác nhận, chỉ cần bấm **Cài đặt**. |

Phía GitHub (không ai phải làm gì):

- `.github/workflows/engine-update.yml` chạy **6 tiếng một lần**. Nếu NewPipeExtractor có bản sửa lỗi mới, nó tự build bộ phát và đăng lên release `engine-latest`.
- `.github/workflows/build.yml` chạy mỗi khi code được đẩy lên `main`. Nó build ứng dụng rồi đăng lên `tv-latest`.
- Mọi gói tải về đều phải được ký bằng **cùng khoá** với ứng dụng đã cài.

**Link tải (không bao giờ đổi):**
`https://github.com/<tài-khoản>/<repo>/releases/download/tv-latest/LucBaoTV.apk`

## Thiết lập một lần

1. Tạo repo **công khai** rồi đưa toàn bộ thư mục này lên nhánh `main`.
2. Vào **Settings → Secrets and variables → Actions** và thêm 2 secret:
   - `KEYSTORE_BASE64`
   - `KEYSTORE_PASSWORD` (alias của khoá: `lucbao`)
3. Vào **Actions → Build & publish Lục Bảo TV → Run workflow**. Khoảng 10–15 phút sau sẽ có `LucBaoTV.apk` trong mục Releases.

> ⚠️ Giữ kỹ file khoá `lucbao.jks` và mật khẩu. Nếu mất khoá, các TV đã cài sẽ không nhận được bản cập nhật nữa.

## Cài lên TV (chỉ một lần)

1. **Bật cài từ nguồn không xác định:** vào *Cài đặt → Bảo mật / Quyền riêng tư → Nguồn không xác định* (hoặc *Ứng dụng → Truy cập ứng dụng đặc biệt → Cài ứng dụng không rõ nguồn*). Bật cho ứng dụng bạn dùng để mở file APK.
2. Chọn **một** trong các cách sau:
   - **Downloader** (dễ nhất): cài *Downloader* (của AFTVnews) từ CH Play trên TV, gõ link tải ở trên rồi bấm Cài đặt.
   - **USB:** chép `LucBaoTV.apk` vào USB, cắm vào TV rồi mở file bằng trình quản lý tệp.
   - **Send Files to TV:** cài ứng dụng này trên cả điện thoại lẫn TV rồi gửi file sang.
   - **ADB:** chạy `adb connect <IP-TV>` rồi `adb install LucBaoTV.apk`.
3. Mở **Lục Bảo TV** rồi bấm **Cho phép tự cập nhật**.

## Lưu ý

- Ứng dụng lấy video trực tiếp từ YouTube mà không qua trình phát chính thức, nên vi phạm điều khoản của YouTube. Chỉ dùng riêng và chia sẻ cho người quen, **không đưa lên CH Play**.
- Video giới hạn độ tuổi chưa xem được. Lục Bảo TV không thuộc YouTube hay Google.
- Mã nguồn theo giấy phép **GPLv3** (xem `LICENSE`). Các phông chữ dùng giấy phép SIL OFL (xem `docs/licenses`).
