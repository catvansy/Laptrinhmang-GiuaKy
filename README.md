# Tic Tac Toe (Caro 3x3) – Multiplayer qua Socket

Ứng dụng Java Swing chơi Caro 3x3 qua mô hình Client–Server (TCP). Hỗ trợ sảnh phòng, chat và gửi file giữa 2 người chơi trong cùng phòng.

## Yêu cầu
- JDK 8+ (có `java` và `javac` trong PATH)

## Chạy nhanh (Windows)
Tại thư mục gốc dự án:

1) Build:
```bat
build.bat
```

2) Chạy server (một cửa sổ riêng):
```bat
run_server.bat
```

3) Chạy client (mỗi client một cửa sổ):
```bat
run_client.bat
```

4) Làm sạch (xóa thư mục `out` và mọi `.class` rời):
```bat
clean.bat
```

Ghi chú: Các script ưu tiên chạy từ `out` nếu đã build; nếu chưa build, client/server có thể chạy trực tiếp từ `src`.

### Chạy bằng double‑click (File Explorer)
- Có thể nhấp đúp các file: `build.bat`, `run_server.bat`, `run_client.bat`.
- Script sẽ tự vào đúng thư mục, kiểm tra `java` trong PATH, chọn classpath (`out`/`src`) và dừng màn hình nếu có lỗi (pause) để bạn xem thông báo.

## Cách để chạy game
1. Khởi động server trước với `run_server.bat`.
2. Mở 2 client bằng `run_client.bat` (mỗi client một cửa sổ).
3. Khi client khởi chạy, nhập:
   - Nickname: tùy ý
   - Địa chỉ server: `localhost` (cùng máy) hoặc IP LAN của máy chạy server
   - Cổng: `5001`
4. Ở client 1: chọn “Tạo phòng mới” và đặt tên phòng.
5. Ở client 2: chọn phòng trong danh sách và “Vào phòng”. Trò chơi sẽ bắt đầu.
6. Bấm vào ô trên bàn cờ để đánh. Kết thúc ván có thể chọn “Chơi lại”.

## Tính năng
- Giao diện Swing 3x3 với trạng thái lượt đi rõ ràng.
- Sảnh phòng: tạo phòng, liệt kê và tham gia phòng còn trống.
- Chat thời gian thực giữa 2 người chơi trong phòng.
- Gửi/nhận file (giới hạn 2 MB, xác nhận lưu file khi nhận).
- Xử lý ngắt kết nối: tự động đưa người còn lại về sảnh.
- Chơi lại sau khi kết thúc ván.

## Giao thức Client–Server (tóm tắt)
Từ Server → Client:
- `DANH_SACH_PHONG|room1|room2|...`
- `BAT_DAU|X|O`: thông báo biểu tượng của người chơi (client tự nhận `X` hoặc `O` từ payload)
- `LUOT_CUA_BAN`, `LUOT_DOI_THU`
- `DANH|position|symbol`
- `KET_THUC|X` | `KET_THUC|O` | `KET_THUC|HOA`
- `DOI_THU_THOAT`
- `CHAT|from|text`
- `FILE|from|filename|base64`

Từ Client → Server:
- `NICK|nickname`: gửi nickname sau khi kết nối
- `LAY_DANH_SACH_PHONG`
- `TAO_PHONG|roomName`, `VAO_PHONG|roomName`
- `DANH|position`
- `CHOI_LAI`
- `CHAT|text`
- `FILE|filename|base64`

## Cấu hình mạng
- Server mặc định chạy cổng `5001`.
- Chơi LAN: nhập IP máy chạy server; cần cho phép Java qua tường lửa cổng 5001.

## Khắc phục sự cố
- Không kết nối được: kiểm tra server đã chạy, đúng IP/cổng, tường lửa không chặn.
- Build lỗi với `*.java`: dùng `build.bat` (đã xử lý wildcard đúng cho Windows).
- Không thấy cửa sổ mới: mở thủ công qua Cmd bằng các script trên hoặc chạy trực tiếp lệnh `java -cp out main.TicTacToeClient` sau khi build.

