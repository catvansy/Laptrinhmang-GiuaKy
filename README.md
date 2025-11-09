# Tic Tac Toe Multiplayer Game

Tic Tac Toe (3x3 Caro) game using Java Socket with Multi Client-Server model.

## How to Run the Program

1. Navigate to the src directory:
```bash
cd src
```

2. Compile the Java files:
```bash
javac main/TicTacToeServer.java main/TicTacToeClient.java
```

3. Run the Server:
```bash
java main.TicTacToeServer
```

4. Run the Client (you can run multiple clients):
```bash
java main.TicTacToeClient
```

## How to Play

1. Start the Server first
2. Open 2 Client windows to start the game
3. Server will automatically pair players
4. Player X will go first
5. Click on an empty cell to make a move
6. Win by getting 3 identical symbols in a row (horizontal, vertical, or diagonal)

## Features

- Graphical interface for Client (Swing)
- Support for multiple player pairs simultaneously
- Automatic player pairing
- Disconnection handling
- Game status display
- Option to play again after game ends

## Client-Server Communication Protocol

### From Server to Client:
- `BAT_DAU|X` or `BAT_DAU|O`: Start game, assign symbol to player
- `LUOT_CUA_BAN`: It's the current player's turn
- `LUOT_DOI_THU`: It's the opponent's turn
- `DANH|position|symbol`: New move (position and symbol)
- `KET_THUC|result`: Game over (X_THANG, O_THANG, or HOA)
- `DOI_THU_THOAT`: Opponent has disconnected

### From Client to Server:
- `DANH|position`: Send move (position 0-8)
- `CHOI_LAI`: Request a new game

## Notes

- Server runs on port 5001
- Default connection to localhost
- Server must be running before starting Client
- At least 2 Clients are needed to start a game
