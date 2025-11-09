package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;

public class TicTacToeClient extends JFrame {
    private JButton[] buttons;
    private JLabel statusLabel;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String playerSymbol;
    private boolean myTurn;
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5001;

    private JPanel homePanel;
    private JPanel gamePanel;

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
        infoPanel.add(statusLabel, BorderLayout.CENTER);

        // Thêm các panel vào game panel
        gamePanel.add(boardPanel, BorderLayout.CENTER);
        gamePanel.add(infoPanel, BorderLayout.SOUTH);
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
        if (message.startsWith("DANH_SACH_PHONG|")) {
            String[] rooms = message.substring("DANH_SACH_PHONG|".length()).split("\\|");
            SwingUtilities.invokeLater(() -> {
                roomListModel.clear();
                for (String room : rooms) {
                    if (!room.trim().isEmpty()) {
                        roomListModel.addElement(room);
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
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Bắt đầu luồng lắng nghe tin nhắn từ server
            new Thread(this::listenForServerMessages).start();

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
            out.println("VAO_PHONG|" + selectedRoom);
            joinGame();
        } else {
            JOptionPane.showMessageDialog(this,
                "Vui lòng chọn một phòng để vào!",
                "Thông báo",
                JOptionPane.INFORMATION_MESSAGE);
        }
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
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
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