# Laptrinhmang-GiuaKy
# Game Tic Tac Toe Multiplayer

Game Tic Tac Toe (Caro 3x3) sử dụng Java Socket với mô hình Multi Client-Server.

## Cách chạy chương trình

1. Biên dịch các file Java:
```bash
cd "d:\Baitap\làm game\TicTacToeGame\src"
```

2. Chạy Server:
```bash
java TicTacToeServer
```

3. Chạy Client (có thể chạy nhiều client):
```bash
java TicTacToeClient
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
- `GAME_START|X` hoặc `GAME_START|O`: Bắt đầu game, chỉ định ký hiệu cho người chơi
- `YOUR_TURN`: Đến lượt người chơi hiện tại
- `OPPONENT_TURN`: Đến lượt đối thủ
- `MOVE|position|symbol`: Nước đi mới (vị trí và ký hiệu)
- `GAME_END|result`: Kết thúc game (X_WIN, O_WIN, hoặc DRAW)
- `OPPONENT_DISCONNECTED`: Đối thủ đã ngắt kết nối

### Từ Client đến Server:
- `MOVE|position`: Gửi nước đi (vị trí 0-8)

## Lưu ý

- Server chạy trên port 5000
- Mặc định kết nối đến localhost
- Cần chạy Server trước khi chạy Client
- Cần ít nhất 2 Client để bắt đầu game
