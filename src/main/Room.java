package main;

public class Room {
    private final String id; // 6-digit numeric ID
    private final String name;
    private Game game;
    private ClientHandler host;
    private ClientHandler guest;

    public Room(String id, String name, ClientHandler host) {
        this.id = id;
        this.name = name;
        this.host = host;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
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
            game = new Game(host, guest);
            game.start();
        }
    }

    public Game getGame() {
        return game;
    }

    // Expose host/guest for server-side forwarding (chat/file)
    public ClientHandler getHost() {
        return host;
    }

    public ClientHandler getGuest() {
        return guest;
    }

    public void resetGame() {
        if (game != null) {
            game.resetGame();
        }
    }

    public boolean hasPlayer(ClientHandler player) {
        return player == host || player == guest;
    }
}