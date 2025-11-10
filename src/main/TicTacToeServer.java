package main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TicTacToeServer {
    private static final int PORT = 5001;
    private static CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static Map<String, Room> rooms = new ConcurrentHashMap<>();

    private static void broadcastRoomList() {
        // Snapshot room names that are not full to avoid concurrent modifications during iteration
        List<String> availableRooms = new ArrayList<>();
        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            Room room = entry.getValue();
            if (!room.isFull()) {
                availableRooms.add(entry.getKey());
            }
        }

        StringBuilder roomList = new StringBuilder("DANH_SACH_PHONG");
        for (String roomName : availableRooms) {
            roomList.append("|").append(roomName);
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
        try {
            if (message == null || message.isEmpty()) {
                return;
            }

            if (message.startsWith("TAO_PHONG|")) {
                String[] parts = message.split("\\|", 2);
                if (parts.length < 2 || parts[1].trim().isEmpty()) {
                    client.sendMessage("LOI|Tên phòng không hợp lệ");
                    return;
                }
                String roomName = parts[1].trim();
                createRoom(client, roomName);
                return;
            }

            if (message.startsWith("VAO_PHONG|")) {
                String[] parts = message.split("\\|", 2);
                if (parts.length < 2 || parts[1].trim().isEmpty()) {
                    client.sendMessage("LOI|Tên phòng không hợp lệ");
                    return;
                }
                String roomName = parts[1].trim();
                joinRoom(client, roomName);
                return;
            }

            if (message.equals("LAY_DANH_SACH_PHONG")) {
                broadcastRoomList();
                return;
            }

            if (message.startsWith("DANH|")) {
                String[] parts = message.split("\\|", 2);
                if (parts.length < 2) {
                    client.sendMessage("LOI|Nước đi không hợp lệ");
                    return;
                }
                int position;
                try {
                    position = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ex) {
                    client.sendMessage("LOI|Nước đi không hợp lệ");
                    return;
                }
                Room room = client.getCurrentRoom();
                if (room != null && room.getGame() != null) {
                    room.getGame().makeMove(client, position);
                }
                return;
            }

            if (message.startsWith("CHAT|")) {
                String[] parts = message.split("\\|", 2);
                if (parts.length < 2 || parts[1].trim().isEmpty()) {
                    client.sendMessage("LOI|Nội dung chat không hợp lệ");
                    return;
                }
                Room room = client.getCurrentRoom();
                if (room != null) {
                    String sender;
                    Game g = room.getGame();
                    if (g != null) {
                        sender = (client == g.getPlayer1()) ? "X" : "O";
                    } else {
                        sender = client.getSocket().getInetAddress().getHostAddress();
                    }
                    String text = parts[1].trim();
                    room.broadcastToPlayers("CHAT|" + sender + "|" + text);
                }
                return;
            }

            if (message.equals("CHOI_LAI")) {
                Room room = client.getCurrentRoom();
                if (room != null) {
                    room.resetGame();
                }
                return;
            }

            if (message.equals("LAY_BANG_XEP_HANG")) {
                String payload = LeaderboardManager.serializeTopN(20);
                client.sendMessage(payload);
                return;
            }

            // Unknown message
            client.sendMessage("LOI|Thông điệp không được hỗ trợ");
        } catch (Exception ex) {
            client.sendMessage("LOI|Lỗi xử lý thông điệp");
        }
    }
}

class Game {
    private final ClientHandler player1; // Luôn là X
    private final ClientHandler player2; // Luôn là O
    private ClientHandler currentPlayer; // Người chơi đang đến lượt
    private final String[] board;
    private boolean gameEnded;
    private int[] lastWinLine = null; // 3 positions of winning line

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
                lastWinLine = new int[]{i, i + 1, i + 2};
                return true;
            }
        }

        // Kiểm tra hàng dọc
        for (int i = 0; i < 3; i++) {
            if (board[i].equals(symbol) && board[i + 3].equals(symbol) && board[i + 6].equals(symbol)) {
                lastWinLine = new int[]{i, i + 3, i + 6};
                return true;
            }
        }

        // Kiểm tra đường chéo
        if (board[0].equals(symbol) && board[4].equals(symbol) && board[8].equals(symbol)) {
            lastWinLine = new int[]{0, 4, 8};
            return true;
        }
        if (board[2].equals(symbol) && board[4].equals(symbol) && board[6].equals(symbol)) {
            lastWinLine = new int[]{2, 4, 6};
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
        // Inform clients about end and winning line if any
        String message = "KET_THUC|" + result;
        player1.sendMessage(message);
        player2.sendMessage(message);

        // Highlight line
        if (lastWinLine != null) {
            String highlight = "HIGHLIGHT|" + lastWinLine[0] + "," + lastWinLine[1] + "," + lastWinLine[2];
            player1.sendMessage(highlight);
            player2.sendMessage(highlight);
        }

        // Update leaderboard by client IPs
        String name1 = player1.getSocket().getInetAddress().getHostAddress();
        String name2 = player2.getSocket().getInetAddress().getHostAddress();
        if ("HOA".equals(result)) {
            LeaderboardManager.recordDraw(name1);
            LeaderboardManager.recordDraw(name2);
        } else if ("X".equals(result)) {
            // player of symbol X is player1
            LeaderboardManager.recordWin(name1);
            LeaderboardManager.recordLoss(name2);
        } else if ("O".equals(result)) {
            // player2
            LeaderboardManager.recordWin(name2);
            LeaderboardManager.recordLoss(name1);
        }
        LeaderboardManager.persist();
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

    public Socket getSocket() {
        return socket;
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

// Simple leaderboard manager: keeps win/loss/draw counts per display name and persists to a CSV file.
class LeaderboardManager {
    private static final Map<String, Stats> table = new ConcurrentHashMap<>();
    private static final File file = new File("leaderboard.csv");

    static {
        load();
    }

    public static void recordWin(String name) {
        table.computeIfAbsent(name, k -> new Stats()).wins++;
    }

    public static void recordLoss(String name) {
        table.computeIfAbsent(name, k -> new Stats()).losses++;
    }

    public static void recordDraw(String name) {
        table.computeIfAbsent(name, k -> new Stats()).draws++;
    }

    public static synchronized void persist() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, false))) {
            pw.println("name,wins,losses,draws");
            for (Map.Entry<String, Stats> e : table.entrySet()) {
                Stats s = e.getValue();
                // escape commas by replacing with spaces for simplicity
                String safeName = e.getKey().replace(",", " ");
                pw.println(safeName + "," + s.wins + "," + s.losses + "," + s.draws);
            }
        } catch (IOException ex) {
            System.err.println("Không thể lưu leaderboard: " + ex.getMessage());
        }
    }

    private static synchronized void load() {
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length < 4) continue;
                String name = parts[0];
                Stats s = new Stats();
                try {
                    s.wins = Integer.parseInt(parts[1]);
                    s.losses = Integer.parseInt(parts[2]);
                    s.draws = Integer.parseInt(parts[3]);
                } catch (NumberFormatException ignored) {}
                table.put(name, s);
            }
        } catch (IOException ex) {
            System.err.println("Không thể đọc leaderboard: " + ex.getMessage());
        }
    }

    public static String serializeTopN(int n) {
        // Returns LEADERBOARD|name|wins|losses|draws|name|...
        List<Map.Entry<String, Stats>> entries = new ArrayList<>(table.entrySet());
        entries.sort((a, b) -> Integer.compare(score(b.getValue()), score(a.getValue())));
        StringBuilder sb = new StringBuilder("LEADERBOARD");
        int count = 0;
        for (Map.Entry<String, Stats> e : entries) {
            if (count++ >= n) break;
            Stats s = e.getValue();
            sb.append("|").append(e.getKey()).append("|").append(s.wins).append("|").append(s.losses).append("|").append(s.draws);
        }
        return sb.toString();
    }

    private static int score(Stats s) {
        return s.wins * 3 + s.draws; // simple scoring
    }

    private static class Stats {
        int wins = 0;
        int losses = 0;
        int draws = 0;
    }
}
