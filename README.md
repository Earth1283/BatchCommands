# BatchCommands

**BatchCommands** is a powerful and safe Minecraft server plugin (Paper/Spigot) that allows administrators to execute sequences of console commands defined in external text files. It is designed for automation, timed events, and complex server management tasks without the risk of lagging the main server thread during file operations.

## ✨ Features

*   **Batch Execution:** Run hundreds of commands from a single file.
*   **Performance First:** Reads files **asynchronously** to prevent server lag, then executes commands synchronously on the main thread.
*   **Timed Execution:** Use `!sleep <seconds>` to insert delays between commands (supports both Tick-based and Realtime modes).
*   **Smart Linter:** Analyzes your batch files *before* execution to catch errors:
    *   Typos (suggests similar commands using Levenshtein distance).
    *   Syntax errors in meta-commands.
    *   Security risks (blacklisted commands).
    *   Configurable linting rules and performance profiling.
*   **Safety:** Built-in blacklist to prevent accidental execution of dangerous commands (e.g., `/stop`, `/op`).
*   **Python-Style Comments:** Use `#` to add comments to your batch files.

## 📦 Installation

1.  Download the latest `BatchCommands-2.0.0.jar`.
2.  Place the JAR file into your server's `plugins` folder.
3.  Restart your server.
4.  The plugin will generate a `batches` folder inside `plugins/BatchCommands/`.

## 🚀 Usage

### 1. Creating a Batch File
Create a new text file in `plugins/BatchCommands/batches/`. You can use the `.batch` extension (default) or configure your own.

**Example:** `plugins/BatchCommands/batches/welcome.batch`
```properties
# This is a comment. It will be ignored.
say Welcome to the server event!
title @a title {"text":"Event Starting!", "color":"gold"}

# Wait for 3 seconds
!sleep 3

say The gates are opening...
execute run setblock 100 64 100 air
!sleep 1
say GO!
```

### 2. Running the Batch
Run the command from the console or in-game (if you have permission):
```
/filebatch welcome
```
*(Note: You don't need to type the `.batch` extension)*

## ⚙️ Configuration

### `config.yml`
Controls general settings and messages.

*   **`batch-folder`**: Directory for your batch files.
*   **`timer-mode`**:
    *   `ticks`: Syncs with server TPS (20 ticks = 1s).
    *   `realtime`: Syncs with real-world time (system clock).
*   **`security.command-blacklist`**: List of commands that will be skipped/blocked.

### `linter.yml`
Controls the pre-execution analysis.

*   **`rules`**: Toggle specific checks (e.g., `check-command-existence`, `warn-on-blacklisted`).
*   **`execution.max-lint-time-ms`**: Max time allowed for linting to prevent lag.
*   **`execution.debug-mode`**: If `true`, prints a performance report of the linter.

## 🔒 Permissions

*   **`batchcommands.execute`**: Allows access to the `/filebatch` command.
    *   *Default:* OP only.

## 🛠 Building form Source

Requirements: Java 21+ and Gradle.

```bash
git clone https://github.com/Earth1283/BatchCommands.git
cd BatchCommands
./gradlew build
```
The compiled JAR will be in `build/libs/`.
