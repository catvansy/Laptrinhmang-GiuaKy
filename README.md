# Game Tic Tac Toe Multiplayer

Game Tic Tac Toe (Caro 3x3) sử dụng Java Socket với mô hình Multi Client-Server.

## Cách chạy chương trình

1. Di chuyển vào thư mục src:
```bash
cd TicTacToeGame/src
```

2. Biên dịch các file Java:
```bash
javac main/TicTacToeServer.java main/TicTacToeClient.java
```

3. Chạy Server:
```bash
java main.TicTacToeServer
```

4. Chạy Client (có thể chạy nhiều client):
```bash
java main.TicTacToeClient
```

## Cách chơi

1. Khởi động Server trước
2. Mở 2 cửa sổ Client để bắt đầu game
3. Server sẽ tự động ghép cặp người chơi
4. Người chơi X sẽ đi trước
5. Click vào ô trống để đánh
6. Thắng khi có 3 ký hiệu giống nhau thẳng hàng (ngang, dọc, chéo)

## Tính năng

- Giao diện đồ họa cho Client (Swing)
- Hỗ trợ nhiều cặp người chơi cùng lúc
- Tự động ghép cặp người chơi
- Xử lý ngắt kết nối
- Hiển thị trạng thái game
- Tùy chọn chơi lại sau khi game kết thúc

## Protocol giao tiếp Client-Server

### Từ Server đến Client:
- `BAT_DAU|X` hoặc `BAT_DAU|O`: Bắt đầu game, chỉ định ký hiệu cho người chơi
- `LUOT_CUA_BAN`: Đến lượt người chơi hiện tại
- `LUOT_DOI_THU`: Đến lượt đối thủ
- `DANH|position|symbol`: Nước đi mới (vị trí và ký hiệu)
- `KET_THUC|result`: Kết thúc game (X_THANG, O_THANG, hoặc HOA)
- `DOI_THU_THOAT`: Đối thủ đã ngắt kết nối

### Từ Client đến Server:
- `DANH|position`: Gửi nước đi (vị trí 0-8)
- `CHOI_LAI`: Yêu cầu chơi game mới

## Lưu ý

- Server chạy trên port 5001
- Mặc định kết nối đến localhost
- Cần chạy Server trước khi chạy Client
- Cần ít nhất 2 Client để bắt đầu game
