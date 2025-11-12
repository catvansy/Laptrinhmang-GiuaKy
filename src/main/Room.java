package main;

public class Room {
    private final String name;
    private final int size;
    private Game game;
    private ClientHandler host;
    private ClientHandler guest;

    public Room(String name, ClientHandler host) {
        this(name, host, 3);
    }

    public Room(String name, ClientHandler host, int size) {
        this.name = name;
        this.host = host;
        this.size = size <= 0 ? 3 : size;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public boolean isFull() {
        return host != null && guest != null;
    }

    public boolean isEmpty() {
        return host == null && guest == null;
    }

    public void addPlayer(ClientHandler player) {
        if (host == null) {
            host = player;
        } else if (guest == null) {
            guest = player;
            startGame();
        }
    }

    public void removePlayer(ClientHandler player) {
        if (player == host) {
            host = null;
        } else if (player == guest) {
            guest = null;
        }

        if (game != null) {
            game.handlePlayerDisconnect(player);
            game = null;
        }
    }

    private void startGame() {
        if (host != null && guest != null) {
            game = new Game(host, guest, size);
            game.start();
        }
    }

    public Game getGame() {
        return game;
    }

    public void resetGame() {
        if (game != null) {
            game.resetGame();
        }
    }

    public boolean hasPlayer(ClientHandler player) {
        return player == host || player == guest;
    }

    public void broadcastToPlayers(String message) {
        if (host != null) {
            host.sendMessage(message);
        }
        if (guest != null) {
            guest.sendMessage(message);
        }
    }
}