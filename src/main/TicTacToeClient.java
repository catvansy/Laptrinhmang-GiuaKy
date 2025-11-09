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
        setTitle("Tic Tac Toe");
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

        // Title panel
        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel("Tic Tac Toe");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 36));
        titlePanel.add(titleLabel);

        // Room list panel
        roomListPanel = new JPanel(new BorderLayout());
        roomListPanel.setBorder(BorderFactory.createTitledBorder("Room List"));
        
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setFont(new Font("Arial", Font.PLAIN, 16));
        JScrollPane scrollPane = new JScrollPane(roomList);
        scrollPane.setPreferredSize(new Dimension(300, 200));
        
        // Control buttons panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());

        JButton createRoomButton = new JButton("Create Room");
        createRoomButton.setFont(new Font("Arial", Font.BOLD, 16));
        
        JButton joinRoomButton = new JButton("Join Room");
        joinRoomButton.setFont(new Font("Arial", Font.BOLD, 16));
        
        JButton refreshButton = new JButton("Refresh");
        refreshButton.setFont(new Font("Arial", Font.BOLD, 16));
        
        JButton exitButton = new JButton("Exit");
        exitButton.setFont(new Font("Arial", Font.BOLD, 16));

        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(exitButton);

        roomListPanel.add(scrollPane, BorderLayout.CENTER);

        // Add components to main panel
        homePanel.add(titlePanel, BorderLayout.NORTH);
        homePanel.add(roomListPanel, BorderLayout.CENTER);
        homePanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add event listeners for buttons
        createRoomButton.addActionListener(e -> createRoom());
        joinRoomButton.addActionListener(e -> joinSelectedRoom());
        refreshButton.addActionListener(e -> refreshRoomList());
        exitButton.addActionListener(e -> System.exit(0));

        // Display home screen
        getContentPane().add(homePanel);
        
        // Connect to server to get room list
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

        // Board panel
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

        // Info panel
        JPanel infoPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Waiting for opponent...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        infoPanel.add(statusLabel, BorderLayout.CENTER);

        // Add panels to game panel
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
                        "Lost connection to server!\nError: " + e.getMessage(),
                        "Connection Error",
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
            statusLabel.setText("Game started - You are " + playerSymbol);
            myTurn = false;
            enableBoard(false);
        } else if (message.equals("CHO_DOI_THU")) {
            statusLabel.setText("Waiting for another player to join the room...");
            myTurn = false;
            enableBoard(false);
        } else if (message.equals("LUOT_CUA_BAN")) {
            statusLabel.setText("Your turn");
            myTurn = true;
            enableBoard(true);
        } else if (message.equals("LUOT_DOI_THU")) {
            statusLabel.setText("Opponent's turn");
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
                    "Error",
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
            message = "Game Over - Draw!";
        } else {
            String winner = result.substring(0, 1);
            message = winner.equals(playerSymbol) ? "Congratulations - You Win!" : "Sorry - You Lose!";
        }
        statusLabel.setText(message);
        enableBoard(false);
        showPlayAgainDialog();
    }

    private void handleOpponentDisconnect() {
        // When opponent disconnects, automatically return to waiting state
        returnToLobby();
    }

    private void returnToLobby() {
        System.out.println("Starting returnToLobby - returning to home screen");
        // Reset board
        for (JButton button : buttons) {
            button.setText("");
            button.setEnabled(false);
        }
        // Reset state
        playerSymbol = null;
        myTurn = false;
        
        // Close current connection
        closeConnection();
        
        // Return to home screen
        getContentPane().removeAll();
        getContentPane().add(homePanel);
        revalidate();
        repaint();
        System.out.println("Completed returnToLobby - returned to home screen");
    }

    private void showPlayAgainDialog() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Do you want to play again?",
                "Game Over",
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
        statusLabel.setText("Waiting for opponent...");
        myTurn = false;
        out.println("CHOI_LAI");
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    private void connectToServerForRoomList() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Start thread to listen for messages from server
            new Thread(this::listenForServerMessages).start();

            // Request room list
            out.println("LAY_DANH_SACH_PHONG");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Cannot connect to server!\nError: " + e.getMessage(),
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void createRoom() {
        String roomName = JOptionPane.showInputDialog(this, 
            "Enter room name:", 
            "Create New Room", 
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
                "Please select a room to join!",
                "Notification",
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
            System.err.println("Cannot set look and feel: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            TicTacToeClient client = new TicTacToeClient();
            client.setVisible(true);
        });
    }

    // Ensure connection is closed when closing window
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            closeConnection();
        }
        super.processWindowEvent(e);
    }
}