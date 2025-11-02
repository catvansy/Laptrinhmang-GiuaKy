package main;

import java.io.*;
import java.net.*;
import java.util.*;

public class TicTacToeServer {
    private static final int PORT = 5001;
    private static ArrayList<ClientHandler> clients = new ArrayList<>();
    private static ArrayList<Game> games = new ArrayList<>();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Máy chủ đang chạy trên cổng " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client mới kết nối: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);

                // Nếu có 2 người chơi, tạo game mới
                if (clients.size() % 2 == 0) {
                    createNewGame();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createNewGame() {
        int lastIndex = clients.size() - 1;
        ClientHandler player1 = clients.get(lastIndex - 1);
        ClientHandler player2 = clients.get(lastIndex);
        Game game = new Game(player1, player2);
        games.add(game);
        game.start();
    }

    // Remove client khỏi danh sách và xử lý khi disconnect
    public static synchronized void removeClient(ClientHandler client, Game game) {
        ClientHandler otherPlayer = null;

        if (game != null) {
            games.remove(game);
            System.out.println("Đã remove game khỏi danh sách. Số game còn lại: " + games.size());

            // Tìm player còn lại và đưa họ về trạng thái chờ
            if (game.getPlayer1() == client) {
                otherPlayer = game.getPlayer2();
            } else if (game.getPlayer2() == client) {
                otherPlayer = game.getPlayer1();
            }

            if (otherPlayer != null) {
                System.out.println("Tìm thấy player còn lại. Socket đóng: " + otherPlayer.isClosed());
                if (!otherPlayer.isClosed()) {
                    otherPlayer.setGame(null); // Remove game khỏi player
                    try {
                        otherPlayer.sendMessage("VE_TRANG_CHU"); // Gửi message về trang chủ
                        System.out.println("Đã gửi message VE_TRANG_CHU cho player còn lại");
                    } catch (Exception e) {
                        System.err.println("Lỗi khi gửi message VE_TRANG_CHU: " + e.getMessage());
                    }
                } else {
                    System.out.println("Socket của player còn lại đã đóng");
                }
            } else {
                System.out.println("Không tìm thấy player còn lại!");
            }
        }

        // Remove client khỏi danh sách
        boolean removed = clients.remove(client);
        System.out.println("Đã remove client. Removed: " + removed + ", Số client còn lại: " + clients.size());

        // Nếu có 2 người chờ, tạo game mới
        if (clients.size() % 2 == 0 && clients.size() >= 2) {
            System.out.println("Có đủ 2 người chờ, tạo game mới");
            createNewGame();
        }
    }
}

class Game {
    private ClientHandler player1; // Luôn là X
    private ClientHandler player2; // Luôn là O
    private ClientHandler currentPlayer; // Người chơi đang đến lượt
    private String[] board;
    private boolean gameEnded;

    public Game(ClientHandler player1, ClientHandler player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.board = new String[9];
        Arrays.fill(board, "");
        this.gameEnded = false;

        player1.setGame(this);
        player2.setGame(this);
    }

    public ClientHandler getPlayer1() {
        return player1;
    }

    public ClientHandler getPlayer2() {
        return player2;
    }

    public void start() {
        // Player1 luôn là X, Player2 luôn là O
        player1.sendMessage("BAT_DAU|X");
        player2.sendMessage("BAT_DAU|O");

        // Random chọn X hay O đi trước (dùng nanoTime để đảm bảo ngẫu nhiên)
        Random random = new Random(System.nanoTime());
        boolean xGoesFirst = random.nextBoolean();

        if (xGoesFirst) {
            // X đi trước (player1)
            currentPlayer = player1;
            System.out.println("Game bắt đầu: X (player1) đi trước");
            player1.sendMessage("LUOT_CUA_BAN");
            player2.sendMessage("LUOT_DOI_THU");
        } else {
            // O đi trước (player2)
            currentPlayer = player2;
            System.out.println("Game bắt đầu: O (player2) đi trước");
            player2.sendMessage("LUOT_CUA_BAN");
            player1.sendMessage("LUOT_DOI_THU");
        }
    }

    public synchronized void makeMove(ClientHandler player, int position) {
        if (gameEnded || position < 0 || position >= 9 || !board[position].isEmpty()) {
            return;
        }

        // Kiểm tra xem có phải lượt của người chơi này không
        if (player != currentPlayer) {
            return;
        }

        // Player1 luôn là X, Player2 luôn là O
        String symbol = (player == player1) ? "X" : "O";
        board[position] = symbol;

        // Thông báo nước đi cho cả hai người chơi
        broadcastMove(position, symbol);

        if (checkWin(symbol)) {
            endGame(symbol);
        } else if (isBoardFull()) {
            endGame("HOA");
        } else {
            // Đổi lượt
            if (currentPlayer == player1) {
                currentPlayer = player2;
                player1.sendMessage("LUOT_DOI_THU");
                player2.sendMessage("LUOT_CUA_BAN");
            } else {
                currentPlayer = player1;
                player2.sendMessage("LUOT_DOI_THU");
                player1.sendMessage("LUOT_CUA_BAN");
            }
        }
    }

    private void broadcastMove(int position, String symbol) {
        String message = "DANH|" + position + "|" + symbol;
        player1.sendMessage(message);
        player2.sendMessage(message);
    }

    private boolean checkWin(String symbol) {
        // Kiểm tra hàng ngang
        for (int i = 0; i < 9; i += 3) {
            if (board[i].equals(symbol) && board[i + 1].equals(symbol) && board[i + 2].equals(symbol)) {
                return true;
            }
        }

        // Kiểm tra hàng dọc
        for (int i = 0; i < 3; i++) {
            if (board[i].equals(symbol) && board[i + 3].equals(symbol) && board[i + 6].equals(symbol)) {
                return true;
            }
        }

        // Kiểm tra đường chéo
        if (board[0].equals(symbol) && board[4].equals(symbol) && board[8].equals(symbol)) {
            return true;
        }
        if (board[2].equals(symbol) && board[4].equals(symbol) && board[6].equals(symbol)) {
            return true;
        }

        return false;
    }

    private boolean isBoardFull() {
        for (String cell : board) {
            if (cell.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void endGame(String result) {
        gameEnded = true;
        String message = "KET_THUC|" + result;
        player1.sendMessage(message);
        player2.sendMessage(message);
    }

    public void handlePlayerDisconnect(ClientHandler player) {
        System.out.println("handlePlayerDisconnect được gọi cho player: " + player);
        if (!gameEnded) {
            gameEnded = true;
        }
        // Luôn gọi removeClient để cleanup, kể cả khi game đã kết thúc
        TicTacToeServer.removeClient(player, this);
    }

    public void resetGame() {
        Arrays.fill(board, "");
        gameEnded = false;
        start();
    }
}

class ClientHandler {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Game game;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Bắt đầu luồng đọc tin nhắn từ client
            new Thread(this::handleMessages).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public void sendMessage(String message) {
        try {
            if (out != null && !socket.isClosed() && !socket.isOutputShutdown()) {
                out.println(message);
                out.flush(); // Đảm bảo message được gửi ngay
            } else {
                System.err.println("Không thể gửi message - socket đã đóng hoặc output đã shutdown");
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi gửi message: " + e.getMessage());
        }
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    private void handleMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("DANH|")) {
                    int position = Integer.parseInt(message.split("\\|")[1]);
                    game.makeMove(this, position);
                } else if (message.equals("CHOI_LAI")) {
                    game.resetGame();
                }
            }
        } catch (IOException e) {
            System.out.println("Client ngắt kết nối: " + socket.getInetAddress());
            if (game != null) {
                game.handlePlayerDisconnect(this);
            } else {
                // Nếu chưa có game, chỉ remove khỏi clients
                TicTacToeServer.removeClient(this, null);
            }
        } finally {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
