package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
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

    private EffectOverlay effectOverlay;

    public TicTacToeClient() {
        setTitle("Cờ Ca-rô");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setupHomeScreen();
        setSize(520, 640);
        setLocationRelativeTo(null);
        setResizable(false);
        setIconImage(generateAppIcon());
        effectOverlay = new EffectOverlay();
        setGlassPane(effectOverlay);
        effectOverlay.setVisible(false);
        effectOverlay.setBounds(0, 0, getWidth(), getHeight());
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                effectOverlay.setBounds(0, 0, getWidth(), getHeight());
            }
        });
    }

    private JPanel roomListPanel;
    private DefaultListModel<String> roomListModel;
    private JList<String> roomList;
    private JTextArea chatArea;
    private JTextField chatInput;

    // Theming - Light modern palette (no dark mode)
    private final Color lightBg = new Color(244, 247, 255);
    private final Color lightPanel = new Color(255, 255, 255);
    private final Color lightText = new Color(32, 42, 64);
    private final Color lightAccent = new Color(108, 99, 255); // modern indigo

    private Font titleFont = new Font("Segoe UI", Font.BOLD, 36);
    private Font bodyFont = new Font("Segoe UI", Font.PLAIN, 16);
    private Font buttonFont = new Font("Segoe UI", Font.BOLD, 16);
    private Font monoFont = new Font("Consolas", Font.PLAIN, 12);

    private void setupHomeScreen() {
        homePanel = new GradientPanel();
        homePanel.setLayout(new BorderLayout());
        homePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Panel tiêu đề
        JPanel titlePanel = transparentPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Cờ Ca-rô");
        titleLabel.setFont(titleFont);
        titleLabel.setForeground(getPrimaryText());
        titlePanel.add(titleLabel, BorderLayout.WEST);

        // Right side space (no theme toggle)
        JPanel rightBox = transparentPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        titlePanel.add(rightBox, BorderLayout.EAST);

        // Panel danh sách phòng
        roomListPanel = roundedPanel(new BorderLayout());
        roomListPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(80, 86, 96), 2), "Danh sách phòng"));
        
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setFont(bodyFont);
        JScrollPane scrollPane = new JScrollPane(roomList);
        scrollPane.setPreferredSize(new Dimension(300, 200));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 86, 96), 2));
        
        // Panel nút điều khiển
        JPanel buttonPanel = transparentPanel(new FlowLayout());
        buttonPanel.setLayout(new FlowLayout());

        JButton createRoomButton = themedButton("Tạo phòng mới");
        
        JButton joinRoomButton = themedButton("Vào phòng");
        
        JButton refreshButton = themedButton("Làm mới");
        
        JButton exitButton = themedButton("Thoát");

        JButton leaderboardButton = new JButton("Bảng xếp hạng");
        styleSecondaryButton(leaderboardButton);

        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(leaderboardButton);
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
        leaderboardButton.addActionListener(e -> requestLeaderboard());
        exitButton.addActionListener(e -> System.exit(0));

        // Hiển thị màn hình chính
        getContentPane().add(homePanel);
        
        // Kết nối đến server để lấy danh sách phòng
        connectToServerForRoomList();

        // Apply theme
        applyThemeRecursively(getContentPane());
    }

    private void switchToPanel(JPanel targetPanel) {
        Container content = getContentPane();
        BufferedImage before = content.getComponentCount() > 0 ? captureComponent(content) : null;

        content.removeAll();
        applyThemeRecursively(targetPanel);
        content.add(targetPanel);
        content.revalidate();
        content.doLayout();
        content.repaint();

        BufferedImage after = captureComponent(content);
        if (effectOverlay != null && before != null && after != null) {
            effectOverlay.startTransition(before, after);
        }
    }

    private BufferedImage captureComponent(Component component) {
        int width = component.getWidth();
        int height = component.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        component.paint(g2);
        g2.dispose();
        return image;
    }

    private void startGame() {
        setupGamePanel();
        switchToPanel(gamePanel);
    }

    private void setupGamePanel() {
        gamePanel = new GradientPanel();
        gamePanel.setLayout(new BorderLayout());

        // Panel chứa bàn cờ
        JPanel boardPanel = roundedPanel(new GridLayout(3, 3));
        buttons = new JButton[9];
        for (int i = 0; i < 9; i++) {
            buttons[i] = createBoardButton();
            final int position = i;
            buttons[i].addActionListener(e -> makeMove(position));
            boardPanel.add(buttons[i]);
        }

        // Panel thông tin
        JPanel infoPanel = roundedPanel(new BorderLayout());
        statusLabel = new JLabel("Đang chờ đối thủ...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 16));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        statusLabel.setForeground(getPrimaryText());
        infoPanel.add(statusLabel, BorderLayout.CENTER);

        // Khu vực chính: chứa board + info (giữ lớn, trung tâm)
        JPanel mainArea = new JPanel(new BorderLayout());
        mainArea.setOpaque(false);
        mainArea.add(boardPanel, BorderLayout.CENTER);
        mainArea.add(infoPanel, BorderLayout.SOUTH);

        // Chat panel dạng nhỏ luôn cố định bên phải, không đè lên bàn cờ
        JPanel chatPanel = roundedPanel(new BorderLayout());
        chatPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(80, 86, 96), 2), "Chat"));
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chatArea.setBackground(getPanelBg());
        chatArea.setForeground(getPrimaryText());
        JScrollPane chatScroll = new JScrollPane(chatArea);
        // Chiều rộng nhỏ để không chiếm chỗ, luôn hiển thị rõ ràng
        chatScroll.setPreferredSize(new Dimension(180, 0));
        chatScroll.setBorder(BorderFactory.createLineBorder(new Color(80, 86, 96), 2));
        chatInput = new JTextField();
        chatInput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JButton sendBtn = themedButton("Gửi");
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendBtn.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());
        JPanel chatInputPanel = new JPanel(new BorderLayout());
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        chatInputPanel.add(sendBtn, BorderLayout.EAST);
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        // Thêm vào game panel: mainArea ở CENTER, chatPanel ở EAST (nhỏ, cố định)
        gamePanel.add(mainArea, BorderLayout.CENTER);
        gamePanel.add(chatPanel, BorderLayout.EAST);

        applyThemeRecursively(gamePanel);
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
        } else if (message.startsWith("HIGHLIGHT|")) {
            String data = message.substring("HIGHLIGHT|".length());
            String[] idx = data.split(",");
            if (idx.length == 3) {
                try {
                    int a = Integer.parseInt(idx[0]);
                    int b = Integer.parseInt(idx[1]);
                    int c = Integer.parseInt(idx[2]);
                    highlightWinningLine(a, b, c);
                } catch (NumberFormatException ignored) {}
            }
        } else if (message.equals("DOI_THU_THOAT")) {
            handleOpponentDisconnect();
        } else if (message.equals("VE_TRANG_CHU")) {
            returnToLobby();
        } else if (message.startsWith("CHAT|")) {
            String[] parts = message.split("\\|", 3);
            if (parts.length >= 3) {
                appendChat(parts[1], parts[2]);
            }
        } else if (message.startsWith("LEADERBOARD")) {
            showLeaderboardDialog(message);
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
        if ("X".equals(symbol)) {
            buttons[position].setForeground(new Color(220, 20, 60));
        } else {
            buttons[position].setForeground(new Color(30, 144, 255));
        }
        Sound.playClick();
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

    private void highlightWinningLine(int a, int b, int c) {
        Color winColor = new Color(255, 230, 140);
        buttons[a].setBackground(winColor);
        buttons[b].setBackground(winColor);
        buttons[c].setBackground(winColor);
    }

    private void handleGameEnd(String result) {
        String message;
        if (result.equals("HOA")) {
            message = "Kết thúc - Hòa!";
            Sound.playDraw();
            showConfetti();
        } else {
            String winner = result.substring(0, 1);
            message = winner.equals(playerSymbol) ? "Chúc mừng - Bạn Thắng!" : "Rất tiếc - Bạn Thua!";
            if (winner.equals(playerSymbol)) {
                Sound.playWin();
            } else {
                Sound.playLose();
            }
            showConfetti();
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
            button.setBackground(getBoardCellBg());
        }
        // Reset trạng thái
        playerSymbol = null;
        myTurn = false;
        
        // Đóng kết nối hiện tại
        closeConnection();
        
        // Quay về trang chủ
        switchToPanel(homePanel);
        System.out.println("Đã hoàn thành returnToLobby - đã trở về trang chủ");

        // Kết nối lại lobby
        connectToServerForRoomList();
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
            button.setBackground(getBoardCellBg());
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

    private void requestLeaderboard() {
        if (out != null) {
            out.println("LAY_BANG_XEP_HANG");
        }
    }

    private void showLeaderboardDialog(String message) {
        // format: LEADERBOARD|name|wins|losses|draws|name|...
        String[] parts = message.split("\\|");
        if (parts.length <= 1) {
            JOptionPane.showMessageDialog(this, "Chưa có dữ liệu bảng xếp hạng.", "Bảng xếp hạng", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %6s %6s %6s%n", "Tên", "Thắng", "Thua", "Hòa"));
        for (int i = 1; i + 3 < parts.length; i += 4) {
            String name = parts[i];
            String wins = parts[i + 1];
            String losses = parts[i + 2];
            String draws = parts[i + 3];
            sb.append(String.format("%-20s %6s %6s %6s%n", name, wins, losses, draws));
        }
        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Bảng xếp hạng", JOptionPane.INFORMATION_MESSAGE);
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty() || out == null) return;
        out.println("CHAT|" + text);
        chatInput.setText("");
    }

    private void appendChat(String sender, String text) {
        if (chatArea != null) {
            chatArea.append(sender + ": " + text + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }

    private void joinGame() {
        setupGamePanel();
        switchToPanel(gamePanel);
    }

    // ========== Theming helpers ==========
    private Color getBackgroundGradientTop() { return new Color(238, 242, 255); }
    private Color getBackgroundGradientBottom() { return new Color(252, 253, 255); }
    private Color getPrimaryText() { return lightText; }
    private Color getPanelBg() { return lightPanel; }
    private Color getAccent() { return lightAccent; }
    private Color getBoardCellBg() { return lightBg; }

    private JPanel roundedPanel(LayoutManager layout) {
        JPanel p = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getPanelBg());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return p;
    }

    private JPanel transparentPanel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    private JButton themedButton(String text) {
        JButton b = new JButton(text);
        b.setFont(buttonFont);
        b.setBackground(getAccent());
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 66, 76), 2),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                b.setBackground(getAccent().darker());
            }
            @Override
            public void mouseExited(MouseEvent e) {
                b.setBackground(getAccent());
            }
            @Override
            public void mousePressed(MouseEvent e) {
                b.setBackground(getAccent().darker().darker());
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                b.setBackground(getAccent());
            }
        });
        return b;
    }

    private void styleSecondaryButton(JButton b) {
        b.setFont(buttonFont);
        b.setBackground(new Color(0, 0, 0, 0));
        b.setForeground(getAccent());
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(getAccent()),
                BorderFactory.createEmptyBorder(7, 15, 7, 15)
        ));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                b.setBackground(new Color(getAccent().getRed(), getAccent().getGreen(), getAccent().getBlue(), 40));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                b.setBackground(new Color(0, 0, 0, 0));
            }
        });
    }

    private JButton createBoardButton() {
        JButton btn = new JButton();
        btn.setFont(new Font("Segoe UI", Font.BOLD, 64));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBackground(getBoardCellBg());
        btn.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, new Color(80, 86, 96)));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled() && btn.getText().isEmpty()) {
                    btn.setBackground(getBoardCellBg().brighter());
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.getText().isEmpty()) {
                    btn.setBackground(getBoardCellBg());
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (btn.isEnabled() && btn.getText().isEmpty()) {
                    btn.setBackground(getBoardCellBg().darker());
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (btn.getText().isEmpty()) {
                    btn.setBackground(getBoardCellBg());
                }
            }
        });
        return btn;
    }

    private void applyThemeRecursively(Component comp) {
        if (comp instanceof JComponent) {
            comp.setForeground(getPrimaryText());
            if (!(comp instanceof JScrollPane)) {
                comp.setBackground(getPanelBg());
            }
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                applyThemeRecursively(child);
            }
        }
        if (comp == homePanel || comp == gamePanel) {
            comp.repaint();
        }
    }

    private class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            GradientPaint gp = new GradientPaint(0, 0, getBackgroundGradientTop(),
                    0, getHeight(), getBackgroundGradientBottom());
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public boolean isOpaque() {
            return false;
        }
    }

    // ===== Sound effects (synthesized) =====
    private static class Sound {
        private static void playTone(double freq, int ms, double volume) {
            try {
                float sampleRate = 44100;
                int numSamples = (int)((ms / 1000.0) * sampleRate);
                byte[] data = new byte[numSamples];
                for (int i = 0; i < numSamples; i++) {
                    double angle = 2.0 * Math.PI * i * freq / sampleRate;
                    double val = Math.sin(angle);
                    int amp = (int)(val * 127 * volume);
                    data[i] = (byte) amp;
                }
                AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                    line.open(format, numSamples);
                    line.start();
                    line.write(data, 0, data.length);
                    line.drain();
                }
            } catch (Exception ignored) {}
        }
        public static void playClick() {
            playTone(700, 50, 0.35);
        }
        public static void playWin() {
            playTone(660, 100, 0.6);
            playTone(880, 120, 0.6);
            playTone(1046.5, 160, 0.6);
        }
        public static void playLose() {
            playTone(523.3, 140, 0.6);
            playTone(392.0, 160, 0.6);
            playTone(329.6, 180, 0.6);
        }
        public static void playDraw() {
            playTone(600, 120, 0.5);
            playTone(600, 120, 0.5);
        }
    }

    // ===== Effects overlay (confetti + transitions) =====
    private void showConfetti() {
        if (effectOverlay != null) {
            effectOverlay.startConfetti(getWidth(), getHeight());
        }
    }

    private static class ConfettiParticle {
        float x, y, vx, vy, size, life;
        Color color;
    }

    private class EffectOverlay extends JComponent {
        private final java.util.List<ConfettiParticle> particles = new java.util.ArrayList<>();
        private javax.swing.Timer confettiTimer;
        private javax.swing.Timer transitionTimer;
        private BufferedImage fromImage;
        private BufferedImage toImage;
        private float transitionProgress = 1f;

        EffectOverlay() {
            setOpaque(false);
        }

        void startTransition(BufferedImage from, BufferedImage to) {
            if (from == null || to == null) {
                clearTransition();
                return;
            }
            fromImage = from;
            toImage = to;
            transitionProgress = 0f;
            if (transitionTimer != null && transitionTimer.isRunning()) {
                transitionTimer.stop();
            }
            setBounds(0, 0, TicTacToeClient.this.getWidth(), TicTacToeClient.this.getHeight());
            setVisible(true);
            updateVisibility();
            transitionTimer = new javax.swing.Timer(16, e -> {
                transitionProgress += 0.06f;
                if (transitionProgress >= 1f) {
                    transitionProgress = 1f;
                    transitionTimer.stop();
                    transitionTimer = null;
                    fromImage = null;
                    toImage = null;
                    updateVisibility();
                }
                repaint();
            });
            transitionTimer.start();
        }

        void startConfetti(int width, int height) {
            int w = Math.max(width, TicTacToeClient.this.getWidth());
            int h = Math.max(height, TicTacToeClient.this.getHeight());
            particles.clear();
            java.util.Random rnd = new java.util.Random();
            int count = 120;
            for (int i = 0; i < count; i++) {
                ConfettiParticle p = new ConfettiParticle();
                p.x = rnd.nextInt(Math.max(1, w));
                p.y = -rnd.nextInt(100);
                p.vx = -1.5f + rnd.nextFloat() * 3.0f;
                p.vy = 2.5f + rnd.nextFloat() * 3.5f;
                p.size = 4 + rnd.nextFloat() * 6;
                p.color = new Color(100 + rnd.nextInt(155), 100 + rnd.nextInt(155), 100 + rnd.nextInt(155));
                p.life = 2.0f + rnd.nextFloat() * 1.5f;
                particles.add(p);
            }
            if (confettiTimer != null && confettiTimer.isRunning()) {
                confettiTimer.stop();
            }
            setBounds(0, 0, TicTacToeClient.this.getWidth(), TicTacToeClient.this.getHeight());
            setVisible(true);
            updateVisibility();
            confettiTimer = new javax.swing.Timer(16, e -> {
                float dt = 0.016f;
                for (ConfettiParticle p : particles) {
                    p.x += p.vx;
                    p.y += p.vy;
                    p.vy += 0.05f;
                    p.life -= dt;
                }
                particles.removeIf(p -> p.life <= 0 || p.y > getHeight() + 20);
                repaint();
                if (particles.isEmpty()) {
                    confettiTimer.stop();
                    confettiTimer = null;
                    updateVisibility();
                }
            });
            confettiTimer.start();
        }

        private void clearTransition() {
            if (transitionTimer != null) {
                transitionTimer.stop();
                transitionTimer = null;
            }
            fromImage = null;
            toImage = null;
            transitionProgress = 1f;
            updateVisibility();
            repaint();
        }

        private void updateVisibility() {
            boolean transitionActive = (transitionTimer != null && transitionTimer.isRunning()) || (fromImage != null && toImage != null);
            boolean confettiActive = (confettiTimer != null && confettiTimer.isRunning()) || !particles.isEmpty();
            setVisible(transitionActive || confettiActive);
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (fromImage != null && toImage != null) {
                float alpha = Math.min(1f, Math.max(0f, transitionProgress));
                Composite original = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - alpha));
                g2.drawImage(fromImage, 0, 0, null);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.drawImage(toImage, 0, 0, null);
                g2.setComposite(original);
            }

            for (ConfettiParticle p : particles) {
                g2.setColor(p.color);
                g2.fillOval(Math.round(p.x), Math.round(p.y), Math.round(p.size), Math.round(p.size));
            }
            g2.dispose();
        }
    }

    // ===== App icon generator =====
    private Image generateAppIcon() {
        int w = 64, h = 64;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(99, 132, 255));
        g.fillRoundRect(0, 0, w, h, 14, 14);
        g.setStroke(new BasicStroke(4f));
        g.setColor(Color.WHITE);
        int m = 12;
        g.drawLine(m, h / 3, w - m, h / 3);
        g.drawLine(m, 2 * h / 3, w - m, 2 * h / 3);
        g.drawLine(w / 3, m, w / 3, h - m);
        g.drawLine(2 * w / 3, m, 2 * w / 3, h - m);
        g.setColor(new Color(255, 80, 120));
        g.drawLine(m + 4, m + 4, w / 3 - 4, h / 3 - 4);
        g.drawLine(w / 3 - 4, m + 4, m + 4, h / 3 - 4);
        g.setColor(new Color(230, 250, 255));
        g.drawOval(2 * w / 3 + 4 - 10, 2 * h / 3 + 4 - 10, 20, 20);
        g.dispose();
        return img;
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