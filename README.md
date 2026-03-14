# ♟️ Chess Game with Time Limit

A multiplayer chess game built in **Java** with a **Swing GUI** and **client-server architecture** over TCP sockets. Two players connect to a central server and play in real time with countdown clocks, move validation, check detection, and game-over handling.

---

## 📋 Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Building the Project](#building-the-project)
- [Running the Game](#running-the-game)
- [How to Play](#how-to-play)
- [Architecture Overview](#architecture-overview)
- [Game Rules & Special Cases](#game-rules--special-cases)
- [Known Limitations](#known-limitations)
- [Future Improvements](#future-improvements)

---

## ✨ Features

- ♟️ Full chess rules via the [chesslib](https://github.com/bhlangonijr/chesslib) library
- 🌐 Client-server architecture over TCP (port 5555)
- 👥 Multiple simultaneous games — each pair of players gets their own session
- ⏱️ Countdown clocks for both players (default: 10 minutes each)
- 🔴 **Red king highlight** when a player is in check
- 🟡 **Yellow highlight** on the selected piece
- 🟢 **Green dots** showing all legal moves for the selected piece
- 🔵 **Blue highlight** on the last move played
- 👑 **Pawn promotion dialog** — choose Queen, Rook, Bishop, or Knight
- ⚡ **30-second first-move timeout** — White must move within 30 seconds or forfeits
- 🕐 **Real-time clock watchdog** — server detects flag fall even if no move is sent
- 📋 **Move history panel** showing all moves in UCI notation
- 🏷️ **Player names** displayed on each timer panel
- 🔁 **Play Again** or **Exit** after game over
- 🔌 **Disconnect handling** — opponent is notified if a player leaves

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17+ |
| Build Tool | Maven |
| Chess Logic | chesslib 1.3.4 (via JitPack) |
| Networking | Java Sockets (TCP) — `ObjectInputStream` / `ObjectOutputStream` |
| GUI | Java Swing (`JFrame`, `JPanel`, `javax.swing.Timer`) |
| Concurrency | `ScheduledExecutorService`, `synchronized` methods |

---

## 📁 Project Structure

```
chess-game/
├── src/main/java/chess/
│   ├── server/
│   │   ├── ChessServer.java       # Entry point — accepts 2 clients per game
│   │   ├── GameSession.java       # Manages one game: board, clocks, validation
│   │   └── ClientHandler.java     # Per-client thread — reads/writes messages
│   ├── client/
│   │   ├── ChessClient.java       # Entry point — connects to server, launches GUI
│   │   ├── NetworkThread.java     # Background message reader (daemon thread)
│   │   └── MessageListener.java   # Interface: onMessage(ChessMessage)
│   ├── gui/
│   │   ├── MainFrame.java         # JFrame — receives server messages, updates UI
│   │   ├── BoardPanel.java        # JPanel — draws board, pieces, highlights
│   │   ├── TimerPanel.java        # Countdown clock with player name
│   │   └── PieceRenderer.java     # Maps Piece enum → Unicode chess symbol
│   └── shared/
│       ├── ChessMessage.java      # Serializable DTO sent over the network
│       ├── MessageType.java       # Enum: MOVE, BOARD_UPDATE, GAME_OVER, etc.
│       └── GameState.java         # FEN + clock snapshot, formatTime() utility
├── src/main/resources/
│   └── pieces/                    # PNG piece images (optional — Unicode used by default)
└── pom.xml
```

---

## ✅ Prerequisites

- **Java 17+** — check with `java --version`
- **Maven 3.6+** — check with `mvn --version`
- Internet access for the first build (downloads chesslib from JitPack)

---

## 🔨 Building the Project

**Important:** Build a fat JAR so all dependencies (chesslib) are included.

First, make sure your `pom.xml` includes the `maven-shade-plugin`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-shade-plugin</artifactId>
      <version>3.5.0</version>
      <executions>
        <execution>
          <phase>package</phase>
          <goals><goal>shade</goal></goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Then build:

```bash
mvn clean package -DskipTests
```

The output JAR will be at `target/chess-game-1.0.jar`.

---

## ▶️ Running the Game

You need **3 terminals** — one for the server and one per player.

### Terminal 1 — Start the Server
```bash
java -cp target/chess-game-1.0.jar chess.server.ChessServer
```
Output:
```
Chess server started on port 5555
Waiting for player 1...
```

### Terminal 2 — Player 1 (White)
```bash
java -cp target/chess-game-1.0.jar chess.client.ChessClient
```
- Enter server address (default: `localhost`)
- Enter your name
- GUI opens showing "Waiting for opponent…"

### Terminal 3 — Player 2 (Black)
```bash
java -cp target/chess-game-1.0.jar chess.client.ChessClient
```
- Enter server address (default: `localhost`)
- Enter your name
- Both GUIs update — game starts!

> **Playing over a network?** Player 2 should enter Player 1's IP address instead of `localhost`.

---

## 🎮 How to Play

1. **Select a piece** — Click any of your pieces. It highlights yellow and green dots appear on all legal squares.
2. **Move the piece** — Click any green dot to send the move to the server.
3. **Re-select** — Click a different friendly piece to change your selection.
4. **Deselect** — Click the selected piece again to clear the selection.
5. **Pawn promotion** — A dialog appears when your pawn reaches the back rank. Choose your piece.

### UI Layout

```
┌─────────────────────────────────┐
│  Opponent Name        10:00     │  ← Opponent timer (top)
├──────────────────────┬──────────┤
│                      │  Moves   │
│     Chess Board      │  1. e2e4 │
│      (560×560)       │  2. e7e5 │
│                      │  ...     │
├──────────────────────┴──────────┤
│  ⏳ Waiting for first move...   │  ← Status label
│  Your Name            10:00     │  ← Your timer (bottom)
└─────────────────────────────────┘
```

---

## 🏗️ Architecture Overview

```
Client (White)                Server                Client (Black)
──────────────                ──────                ──────────────
ChessClient ──── TCP ────► ChessServer
                               │
                           GameSession
                          ┌────┴────┐
                    ClientHandler  ClientHandler
                         │              │
               ◄── ASSIGN_COLOR    ASSIGN_COLOR ──►
               ◄── GAME_START      GAME_START   ──►
                         │              │
    sendMove("e2e4") ───►│              │
                    validate move       │
                    deduct clock        │
                    check end cond.     │
               ◄── BOARD_UPDATE    BOARD_UPDATE ──►
```

### Message Flow

| Message | Direction | Meaning |
|---|---|---|
| `PLAYER_INFO` | Client → Server | Send player name |
| `ASSIGN_COLOR` | Server → Client | Your color + opponent's name |
| `GAME_START` | Server → Client | Both players connected, game begins |
| `MOVE` | Client → Server | UCI move string e.g. `"e2e4"` |
| `BOARD_UPDATE` | Server → Client | New FEN + updated clocks + last move |
| `INVALID_MOVE` | Server → Client | Move was illegal — flash red |
| `GAME_OVER` | Server → Client | Result string + final state |
| `OPPONENT_DISCONNECTED` | Server → Client | Other player left |

---

## ⚖️ Game Rules & Special Cases

| Situation | Behaviour |
|---|---|
| **Check** | King's square turns red on both boards |
| **Checkmate** | Game over immediately — winner declared |
| **Stalemate** | Draw — `1/2-1/2` |
| **Insufficient material** | Draw — `1/2-1/2` |
| **Threefold repetition** | Draw — `1/2-1/2` |
| **50-move rule** | Draw — `1/2-1/2` |
| **Flag fall (time out)** | Opponent wins — detected in real time by server watchdog |
| **No first move in 30s** | White forfeits — Black wins |
| **Disconnect** | Remaining player sees "Opponent disconnected" |
| **Castling** | Handled automatically by chesslib |
| **En passant** | Handled automatically by chesslib |
| **Pawn promotion** | Dialog shown — default Queen |

---

## ⚠️ Known Limitations

- Piece images are **Unicode symbols** — optional PNG support can be added in `PieceRenderer.java`.
- Only **2 players per game** — no AI opponent yet.
- No **reconnect** support — if a player disconnects, the game ends.

---

## 📄 License

MIT License — free to use, modify, and distribute.

---

*Built with Java 17, Maven, chesslib, and Java Swing.*