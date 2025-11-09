package main;

import java.io.*;
import java.net.*;
import java.util.*;

public class TicTacToeServer {
    private static final int PORT = 5001;
    private static ArrayList<ClientHandler> clients = new ArrayList<>();
    private static Map<String, Room> rooms = new HashMap<>();

    private static void broadcastRoomList() {
        StringBuilder roomList = new StringBuilder("DANH_SACH_PHONG");
        for (String roomName : rooms.keySet()) {
            Room room = rooms.get(roomName);
            if (!room.isFull()) {
                roomList.append("|").append(roomName);
            }
        }
        String message = roomList.toString();
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    private static synchronized void createRoom(ClientHandler client, String roomName) {
        if (rooms.containsKey(roomName)) {
            client.sendMessage("LOI|Phòng đã tồn tại");
            return;
        }

        Room room = new Room(roomName, client);
        rooms.put(roomName, room);
        client.setCurrentRoom(room);
        client.sendMessage("CHO_DOI_THU");
        broadcastRoomList();
    }

    private static synchronized void joinRoom(ClientHandler client, String roomName) {
        Room room = rooms.get(roomName);
        if (room == null) {
            client.sendMessage("LOI|Phòng không tồn tại");
            return;
        }

        if (room.isFull()) {
            client.sendMessage("LOI|Phòng đã đầy");
            return;
        }

        room.addPlayer(client);
        client.setCurrentRoom(room);
        broadcastRoomList();
    }

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Máy chủ đang chạy trên cổng " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client mới kết nối: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                broadcastRoomList();
            }
        } catch (IOException e) {
            System.err.println("Lỗi khởi tạo server: " + e.getMessage());
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Lỗi khi đóng server socket: " + e.getMessage());
                }
            }
        }
    }

    // Remove client khỏi danh sách và xử lý khi disconnect
    public static synchronized void removeClient(ClientHandler client, Room room) {
        if (room != null) {
            room.removePlayer(client);
            if (room.isEmpty()) {
                rooms.remove(room.getName());
            }
            broadcastRoomList();
        }

        clients.remove(client);
        System.out.println("Client đã ngắt kết nối. Số client còn lại: " + clients.size());
    }

    public static synchronized void handleClientMessage(ClientHandler client, String message) {
        if (message.startsWith("TAO_PHONG|")) {
            String roomName = message.split("\\|")[1];
            createRoom(client, roomName);
        } else if (message.startsWith("VAO_PHONG|")) {
            String roomName = message.split("\\|")[1];
            joinRoom(client, roomName);
        } else if (message.equals("LAY_DANH_SACH_PHONG")) {
            broadcastRoomList();
        } else if (message.startsWith("DANH|")) {
            Room room = client.getCurrentRoom();
            if (room != null && room.getGame() != null) {
                int position = Integer.parseInt(message.split("\\|")[1]);
                room.getGame().makeMove(client, position);
            }
        } else if (message.equals("CHOI_LAI")) {
            Room room = client.getCurrentRoom();
            if (room != null) {
                room.resetGame();
            }
        }
    }
}

class Game {
    private final ClientHandler player1; // Luôn là X
    private final ClientHandler player2; // Luôn là O
    private ClientHandler currentPlayer; // Người chơi đang đến lượt
    private final String[] board;
    private boolean gameEnded;

    public Game(ClientHandler player1, ClientHandler player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.board = new String[9];
        Arrays.fill(board, "");
        this.gameEnded = false;
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
            // Thông báo cho người chơi còn lại
            ClientHandler otherPlayer = (player == player1) ? player2 : player1;
            if (otherPlayer != null) {
                otherPlayer.sendMessage("DOI_THU_THOAT");
            }
        }
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
    private Room currentRoom;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            startMessageHandler();
        } catch (IOException e) {
            System.err.println("Lỗi khởi tạo client handler: " + e.getMessage());
        }
    }

    private void startMessageHandler() {
        Thread messageThread = new Thread(this::handleMessages);
        messageThread.setDaemon(true);
        messageThread.start();
    }

    public void setCurrentRoom(Room room) {
        this.currentRoom = room;
    }

    public Room getCurrentRoom() {
        return currentRoom;
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
                TicTacToeServer.handleClientMessage(this, message);
            }
        } catch (IOException e) {
            System.out.println("Client ngắt kết nối: " + socket.getInetAddress());
            Room room = getCurrentRoom();
            if (room != null) {
                TicTacToeServer.removeClient(this, room);
            } else {
                TicTacToeServer.removeClient(this, null);
            }
        } finally {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Lỗi khi đóng socket: " + e.getMessage());
            }
        }
    }
}
