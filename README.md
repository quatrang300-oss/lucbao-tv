# Lục Bảo TV

Ứng dụng xem YouTube **không có quảng cáo** dành riêng cho **Android TV / Google TV**, điều khiển hoàn toàn bằng remote. Đây là ứng dụng độc lập (tên gói `vn.lucbao.tv`), không liên quan tới bản Lục Bảo trên điện thoại.

## Tính năng

- **Trang chủ:**
  - Ảnh lớn nhìn rõ từ xa. Ô đang chọn phóng to và có viền xanh lá.
  - Có các mục *Dành cho bạn*, *Kênh bạn theo dõi* (video mới có nhãn **MỚI**), Âm nhạc, Trò chơi, Phim, Trực tiếp…
- **Trình phát toàn màn hình**, video chất lượng tới 8K. Chọn video là phát ngay, không có gì che hình. Chất lượng tự hạ/nâng theo mạng mà không dừng video:
  - Khi ẩn bảng điều khiển: **OK** để tạm dừng/phát, **◀ ▶** để tua 10 giây, **▲** để hiện bảng điều khiển, **▼** để xem *Nhiều video hơn*. Các nút media trên remote cũng dùng được.
  - Trong bảng điều khiển có video trước/sau, **Phụ đề / Thuyết minh**, **Tốc độ**, **Lặp lại**, Chất lượng (kèm bật/tắt thông số video), Yêu thích và Theo dõi kênh.
  - **Tốc độ** từ 0.25x tới 10x: bấm để lên mức kế tiếp, giữ OK khoảng 1.5 giây để hiện thanh chỉnh (◀ ▶ để chỉnh, tự tắt sau 3 giây). Trên 8x thì không có tiếng.
  - **Phụ đề tiếng Việt / thuyết minh:** phụ đề của video hoặc YouTube tự dịch, đọc thành tiếng bằng giọng tiếng Việt của TV.
- **Shorts:** video dọc ở giữa màn hình, chủ đề và ô tìm Shorts bên trái, mô tả bên phải. **▼** video kế, **▲** video trước (ở video đầu thì lên hàng chủ đề), **OK** tạm dừng, **▶** sang các nút Thích / Theo dõi / Mở video. Mỗi lần vào là danh sách mới, video đã xem không lặp lại; chọn lại chủ đề đang chọn để làm mới.
- **Nhạc:** tìm bài hát, Thịnh hành, Bài hát đã thích, Nghe gần đây, album/danh sách. Bảng phát nhạc bên phải có trộn bài, lặp, thích, bài tương tự, hẹn giờ tắt và *Xem video*. Nhạc vẫn phát khi chuyển sang mục khác.
- **Chế độ Trẻ em:** nút **KID** ở góc trên bên cạnh đồng hồ. Bé chỉ xem video của các kênh bố mẹ chọn (theo chủ đề), có hẹn giờ xem và màn hình *Hết giờ*. Thoát hoặc vào phần bố mẹ phải nhập **mã PIN** (dùng phím số trên remote được), nhập sai nhiều lần sẽ bị khoá tạm.
- **Tìm kiếm:** thanh tìm kiếm ở đầu Trang chủ (cạnh tên app), Đang theo dõi và Thư viện; Shorts và Nhạc có ô tìm riêng.
  - Tìm bằng giọng nói: nút micro trên màn hình, hoặc nút tìm kiếm trên remote nếu TV cho phép.
  - Bàn phím trên màn hình: hàng số ở trên cùng, chữ a–z, phím **Hoa** để gõ chữ IN HOA. Bấm OK vào chữ có chấm xanh (a, d, e, i, o, u, y) để mở bảng chữ có dấu, ví dụ A → Á À Ả Ã Ạ, Ă…, Â…
- **Cài đặt:** chất lượng mặc định (tới 8K), phụ đề tiếng Việt, bật/tắt các mục ở cột bên trái (Shorts, Nhạc, Đang theo dõi, Thư viện). Các bảng tuỳ chọn trong suốt để vẫn thấy video.
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
