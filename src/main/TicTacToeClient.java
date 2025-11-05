package main;

import javax.swing.*;
import java.nio.file.Files;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.util.Base64;

public class TicTacToeClient extends JFrame {
    private JButton[] buttons;
    private JLabel statusLabel;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String playerSymbol;
    private boolean myTurn;
    private String serverAddress = "localhost";
    private int serverPort = 5001;
    private String nickname = null;

    private JPanel homePanel;
    private JPanel gamePanel;

    // Chat / file UI components
    private JTextArea chatArea;
    private JTextField chatInput;
    private JButton sendChatButton;
    private JButton sendFileButton;
    private static final long MAX_FILE_SIZE_BYTES = 2_000_000; // 2 MB limit

    public TicTacToeClient() {
        setTitle("Cờ Ca-rô");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setupHomeScreen();
        setSize(400, 450);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private JPanel roomListPanel;
    private DefaultListModel<String> roomListModel;
    private JList<String> roomList;

    private void setupHomeScreen() {
        homePanel = new JPanel();
        homePanel.setLayout(new BorderLayout());
        homePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Panel tiêu đề
        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel("Cờ Ca-rô");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 36));
        titlePanel.add(titleLabel);

        // Panel danh sách phòng
        roomListPanel = new JPanel(new BorderLayout());
        roomListPanel.setBorder(BorderFactory.createTitledBorder("Danh sách phòng"));
        // Top-right search by Room ID
        JPanel roomTopBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton findRoomButton = new JButton("Tìm phòng");
        roomTopBar.add(findRoomButton);
        roomListPanel.add(roomTopBar, BorderLayout.NORTH);

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setFont(new Font("Arial", Font.PLAIN, 16));
        JScrollPane scrollPane = new JScrollPane(roomList);
        scrollPane.setPreferredSize(new Dimension(300, 200));

        // Panel nút điều khiển
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());

        JButton createRoomButton = new JButton("Tạo phòng mới");
        createRoomButton.setFont(new Font("Arial", Font.BOLD, 16));

        JButton joinRoomButton = new JButton("Vào phòng");
        joinRoomButton.setFont(new Font("Arial", Font.BOLD, 16));

        JButton refreshButton = new JButton("Làm mới");
        refreshButton.setFont(new Font("Arial", Font.BOLD, 16));

        JButton exitButton = new JButton("Thoát");
        exitButton.setFont(new Font("Arial", Font.BOLD, 16));

        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(exitButton);

        roomListPanel.add(scrollPane, BorderLayout.CENTER);

        // Thêm các thành phần vào panel chính
        homePanel.add(titlePanel, BorderLayout.NORTH);
        homePanel.add(roomListPanel, BorderLayout.CENTER);
        homePanel.add(buttonPanel, BorderLayout.SOUTH);

        // Thêm sự kiện cho các nút
        createRoomButton.addActionListener(e -> createRoom());
        joinRoomButton.addActionListener(e -> joinSelectedRoom());
        findRoomButton.addActionListener(e -> findRoomById());
        refreshButton.addActionListener(e -> refreshRoomList());
        exitButton.addActionListener(e -> System.exit(0));

        // Hiển thị màn hình chính
        getContentPane().add(homePanel);

        // Kết nối đến server để lấy danh sách phòng
        connectToServerForRoomList();
    }

    private void startGame() {
        getContentPane().removeAll();
        setupGamePanel();
        getContentPane().add(gamePanel);
        revalidate();
        repaint();
    }

    private void setupGamePanel() {
        gamePanel = new JPanel(new BorderLayout());

        // Panel chứa bàn cờ
        JPanel boardPanel = new JPanel(new GridLayout(3, 3));
        buttons = new JButton[9];
        for (int i = 0; i < 9; i++) {
            buttons[i] = new JButton();
            buttons[i].setFont(new Font("Arial", Font.BOLD, 60));
            buttons[i].setFocusPainted(false);
            final int position = i;
            buttons[i].addActionListener(e -> makeMove(position));
            boardPanel.add(buttons[i]);
        }

        // Panel thông tin
        JPanel infoPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Đang chờ đối thủ...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        infoPanel.add(statusLabel, BorderLayout.NORTH);

        // Chat panel
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBorder(BorderFactory.createTitledBorder("Chat & File"));
        chatArea = new JTextArea(8, 30);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        JPanel chatInputPanel = new JPanel(new BorderLayout());
        chatInput = new JTextField();
        sendChatButton = new JButton("Gửi");
        sendFileButton = new JButton("Gửi file");
        JLabel fileLimitLabel = new JLabel("Giới hạn file: 2 MB");
        fileLimitLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        JPanel chatButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        chatButtons.add(sendFileButton);
        chatButtons.add(sendChatButton);

        chatInputPanel.add(fileLimitLabel, BorderLayout.WEST);
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        chatInputPanel.add(chatButtons, BorderLayout.EAST);

        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        infoPanel.add(chatPanel, BorderLayout.SOUTH);

        // Wire chat and file actions
        sendChatButton.addActionListener(e -> {
            String text = chatInput.getText().trim();
            if (!text.isEmpty() && out != null) {
                out.println("CHAT|" + text);
                chatArea.append("Bạn: " + text + "\n");
                chatInput.setText("");
            }
        });

        chatInput.addActionListener(e -> sendChatButton.doClick());

        sendFileButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int sel = chooser.showOpenDialog(this);
            if (sel == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                long size = f.length();
                if (size > MAX_FILE_SIZE_BYTES) {
                    JOptionPane.showMessageDialog(
                            this, "Không thể gửi file: kích thước vượt quá giới hạn "
                                    + (MAX_FILE_SIZE_BYTES / 1_000_000.0) + " MB.",
                            "File quá lớn", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Ask for confirmation with file size shown
                double kb = size / 1024.0;
                String sizeStr = kb >= 1024 ? String.format("%.2f MB", kb / 1024.0) : String.format("%.1f KB", kb);
                int confirm = JOptionPane.showConfirmDialog(this, "Gửi file '" + f.getName() + "' (" + sizeStr + ")?",
                        "Xác nhận gửi file", JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION)
                    return;

                try {
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    String b64 = Base64.getEncoder().encodeToString(bytes);
                    if (out != null) {
                        out.println("FILE|" + f.getName() + "|" + b64);
                        chatArea.append("Bạn đã gửi file: " + f.getName() + "\n");
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi gửi file: " + ex.getMessage());
                }
            }
        });

        // Thêm các panel vào game panel
        gamePanel.add(boardPanel, BorderLayout.CENTER);
        gamePanel.add(infoPanel, BorderLayout.SOUTH);
    }

    private void promptServerInfo() {
        // Ask for nickname first
        String nick = JOptionPane.showInputDialog(this, "Nhập nickname của bạn:", "", JOptionPane.PLAIN_MESSAGE);
        if (nick != null && !nick.trim().isEmpty()) {
            nickname = nick.trim();
        } else {
            nickname = "Guest" + (int) (Math.random() * 1000);
        }

        String addr = JOptionPane.showInputDialog(this, "Nhập địa chỉ máy chủ (IP hoặc hostname):", serverAddress);
        if (addr != null && !addr.trim().isEmpty()) {
            serverAddress = addr.trim();
        }

        String portStr = JOptionPane.showInputDialog(this, "Nhập cổng máy chủ:", Integer.toString(serverPort));
        if (portStr != null && !portStr.trim().isEmpty()) {
            try {
                serverPort = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
    }

    private void listenForServerMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                String finalMessage = message;
                SwingUtilities.invokeLater(() -> processServerMessage(finalMessage));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Mất kết nối đến máy chủ!\nLỗi: " + e.getMessage(),
                        "Lỗi Kết Nối",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            });
        }
    }

    private void processServerMessage(String message) {
        // Chat message format: CHAT|from|text
        if (message.startsWith("CHAT|")) {
            String[] parts = message.split("\\|", 3);
            String from = parts.length > 1 ? parts[1] : "Server";
            String text = parts.length > 2 ? parts[2] : "";
            SwingUtilities.invokeLater(() -> {
                chatArea.append(from + ": " + text + "\n");
            });
            return;
        }

        // File message format: FILE|from|filename|base64
        if (message.startsWith("FILE|")) {
            String[] parts = message.split("\\|", 4);
            String from = parts.length > 1 ? parts[1] : "Server";
            String filename = parts.length > 2 ? parts[2] : "file.received";
            String base64 = parts.length > 3 ? parts[3] : "";

            SwingUtilities.invokeLater(() -> {
                chatArea.append(from + " gửi 1 file: " + filename + "\n");
                int choice = JOptionPane.showConfirmDialog(this, "Bạn muốn lưu file '" + filename + "' không?",
                        "Nhận file từ " + from, JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setSelectedFile(new File(filename));
                    int sel = chooser.showSaveDialog(this);
                    if (sel == JFileChooser.APPROVE_OPTION) {
                        File saveTo = chooser.getSelectedFile();
                        try (FileOutputStream fos = new FileOutputStream(saveTo)) {
                            byte[] data = Base64.getDecoder().decode(base64);
                            fos.write(data);
                            JOptionPane.showMessageDialog(this, "Lưu file thành công: " + saveTo.getAbsolutePath());
                        } catch (IOException e) {
                            JOptionPane.showMessageDialog(this, "Lỗi khi lưu file: " + e.getMessage(), "Lỗi",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            });
            return;
        }

        if (message.startsWith("DANH_SACH_PHONG|")) {
            String[] rooms = message.substring("DANH_SACH_PHONG|".length()).split("\\|");
            SwingUtilities.invokeLater(() -> {
                roomListModel.clear();
                for (String item : rooms) {
                    if (!item.trim().isEmpty()) {
                        // item format: id,name
                        String[] parts = item.split(",", 2);
                        if (parts.length == 2) {
                            String id = parts[0];
                            String name = parts[1];
                            roomListModel.addElement(name + " (ID: " + id + ")");
                        } else {
                            roomListModel.addElement(item);
                        }
                    }
                }
            });
        } else if (message.startsWith("BAT_DAU|")) {
            playerSymbol = message.split("\\|")[1];
            statusLabel.setText("Trò chơi bắt đầu - Bạn là " + playerSymbol);
            myTurn = false;
            enableBoard(false);
        } else if (message.equals("CHO_DOI_THU")) {
            statusLabel.setText("Đang chờ người chơi khác vào phòng...");
            myTurn = false;
            enableBoard(false);
        } else if (message.equals("LUOT_CUA_BAN")) {
            statusLabel.setText("Lượt của bạn");
            myTurn = true;
            enableBoard(true);
        } else if (message.equals("LUOT_DOI_THU")) {
            statusLabel.setText("Lượt của đối thủ");
            myTurn = false;
            enableBoard(false);
        } else if (message.startsWith("DANH|")) {
            String[] parts = message.split("\\|");
            int position = Integer.parseInt(parts[1]);
            String symbol = parts[2];
            updateBoard(position, symbol);
        } else if (message.startsWith("KET_THUC|")) {
            String result = message.split("\\|")[1];
            handleGameEnd(result);
        } else if (message.equals("DOI_THU_THOAT")) {
            handleOpponentDisconnect();
        } else if (message.equals("VE_TRANG_CHU")) {
            returnToLobby();
        } else if (message.startsWith("LOI|")) {
            String errorMsg = message.split("\\|")[1];
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        errorMsg,
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void updateBoard(int position, String symbol) {
        buttons[position].setText(symbol);
        buttons[position].setEnabled(false);
    }

    private void makeMove(int position) {
        if (myTurn && buttons[position].getText().isEmpty()) {
            out.println("DANH|" + position);
            updateBoard(position, playerSymbol);
            myTurn = false;
        }
    }

    private void enableBoard(boolean enable) {
        for (JButton button : buttons) {
            button.setEnabled(enable && button.getText().isEmpty());
        }
    }

    private void handleGameEnd(String result) {
        String message;
        if (result.equals("HOA")) {
            message = "Kết thúc - Hòa!";
        } else {
            String winner = result.substring(0, 1);
            message = winner.equals(playerSymbol) ? "Chúc mừng - Bạn Thắng!" : "Rất tiếc - Bạn Thua!";
        }
        statusLabel.setText(message);
        enableBoard(false);
        showPlayAgainDialog();
    }

    private void handleOpponentDisconnect() {
        // Khi đối thủ disconnect, tự động về trạng thái chờ
        returnToLobby();
    }

    private void returnToLobby() {
        System.out.println("Bắt đầu returnToLobby - trở về trang chủ");
        // Reset bàn cờ
        for (JButton button : buttons) {
            button.setText("");
            button.setEnabled(false);
        }
        // Reset trạng thái
        playerSymbol = null;
        myTurn = false;

        // Đóng kết nối hiện tại
        closeConnection();

        // Quay về trang chủ
        getContentPane().removeAll();
        getContentPane().add(homePanel);
        revalidate();
        repaint();
        System.out.println("Đã hoàn thành returnToLobby - đã trở về trang chủ");
    }

    private void showPlayAgainDialog() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Bạn có muốn chơi lại không?",
                "Kết thúc game",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            resetGame();
        } else {
            closeConnection();
            System.exit(0);
        }
    }

    private void resetGame() {
        for (JButton button : buttons) {
            button.setText("");
            button.setEnabled(false);
        }
        statusLabel.setText("Đang chờ đối thủ...");
        myTurn = false;
        out.println("CHOI_LAI");
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Lỗi khi đóng kết nối: " + e.getMessage());
        }
    }

    private void connectToServerForRoomList() {
        try {
            // Ask user for server address/port before connecting
            promptServerInfo();
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Bắt đầu luồng lắng nghe tin nhắn từ server
            new Thread(this::listenForServerMessages).start();

            // Send nickname to server if available
            if (nickname != null && out != null) {
                out.println("NICK|" + nickname);
            }

            // Yêu cầu danh sách phòng
            out.println("LAY_DANH_SACH_PHONG");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Không thể kết nối đến máy chủ!\nLỗi: " + e.getMessage(),
                    "Lỗi Kết Nối",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void createRoom() {
        String roomName = JOptionPane.showInputDialog(this,
                "Nhập tên phòng:",
                "Tạo phòng mới",
                JOptionPane.PLAIN_MESSAGE);

        if (roomName != null && !roomName.trim().isEmpty()) {
            out.println("TAO_PHONG|" + roomName.trim());
            joinGame();
        }
    }

    private void joinSelectedRoom() {
        String selectedRoom = roomList.getSelectedValue();
        if (selectedRoom != null) {
            // selectedRoom may be in format: "name (ID: xxxxxx)"; extract name before "
            // (ID:"
            String roomName = selectedRoom;
            int idx = selectedRoom.indexOf(" (ID:");
            if (idx > 0) {
                roomName = selectedRoom.substring(0, idx);
            }
            out.println("VAO_PHONG|" + roomName);
            joinGame();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn một phòng để vào!",
                    "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void findRoomById() {
        String id = JOptionPane.showInputDialog(this,
                "Nhập ID phòng (6 chữ số):",
                "Tìm phòng",
                JOptionPane.PLAIN_MESSAGE);
        if (id == null)
            return;
        id = id.trim();
        if (!id.matches("\\d{6}")) {
            JOptionPane.showMessageDialog(this,
                    "ID không hợp lệ. Vui lòng nhập 6 chữ số.",
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        out.println("VAO_PHONG_ID|" + id);
        joinGame();
    }

    private void refreshRoomList() {
        out.println("LAY_DANH_SACH_PHONG");
    }

    private void joinGame() {
        getContentPane().removeAll();
        setupGamePanel();
        getContentPane().add(gamePanel);
        revalidate();
        repaint();
    }

    public static void main(String[] args) {
        try {
            // Set Look and Feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            System.err.println("Không thể thiết lập giao diện: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            TicTacToeClient client = new TicTacToeClient();
            client.setVisible(true);
        });
    }

    // Đảm bảo đóng kết nối khi đóng cửa sổ
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            closeConnection();
        }
        super.processWindowEvent(e);
    }
}