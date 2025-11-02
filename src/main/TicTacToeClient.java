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

    public TicTacToeClient() {
        setTitle("Cờ Ca-rô");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setupGUI();
        connectToServer();
    }

    private void setupGUI() {
        setLayout(new BorderLayout());

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

        // Thêm các panel vào frame
        add(boardPanel, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.SOUTH);

        // Thiết lập kích thước và vị trí
        setSize(400, 450);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Bắt đầu luồng lắng nghe tin nhắn từ server
            new Thread(this::listenForServerMessages).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Không thể kết nối đến máy chủ!\nLỗi: " + e.getMessage(),
                    "Lỗi Kết Nối",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
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
        if (message.startsWith("BAT_DAU|")) {
            playerSymbol = message.split("\\|")[1];
            statusLabel.setText("Trò chơi bắt đầu - Bạn là " + playerSymbol);
            // Không set myTurn và enableBoard ở đây vì server sẽ gửi
            // LUOT_CUA_BAN/LUOT_DOI_THU ngay sau đó
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
            // Đưa về trạng thái chờ đối thủ mới
            System.out.println("Client nhận được message VE_TRANG_CHU - đang về trạng thái chờ");
            returnToLobby();
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
        System.out.println("Bắt đầu returnToLobby - reset bàn cờ và trạng thái");
        // Reset bàn cờ
        for (JButton button : buttons) {
            button.setText("");
            button.setEnabled(false);
        }
        // Reset trạng thái
        playerSymbol = null;
        myTurn = false;
        statusLabel.setText("Đối thủ đã ngắt kết nối. Đang chờ đối thủ mới...");
        System.out.println("Đã hoàn thành returnToLobby - sẵn sàng chờ đối thủ mới");
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

    public static void main(String[] args) {
        try {
            // Set Look and Feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
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