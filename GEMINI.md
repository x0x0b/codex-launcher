# Codex Launcher

**Codex Launcher** is an unofficial IntelliJ IDEA plugin that integrates the OpenAI Codex CLI directly into the IDE. It allows developers to launch the Codex CLI in a dedicated terminal tab, send file contexts, and receive notifications upon task completion.

## Project Structure

This project is an IntelliJ Platform Plugin written in Kotlin.

### Key Directories

*   **`src/main/kotlin/com/github/x0x0b/codexlauncher/`**: Contains the plugin's source code.
    *   **`actions/`**: Defines the actions available in the IDE menus and toolbars (e.g., `LaunchCodexAction`).
    *   **`cli/`**: Handles the construction of command-line arguments for the Codex CLI.
    *   **`files/`**: Logic for monitoring file changes and automatically opening them (`FileOpenService`).
    *   **`http/`**: Implements a local HTTP server (`HttpTriggerService`) to receive callbacks from the Codex CLI.
    *   **`notifications/`**: Manages IDE notifications.
    *   **`settings/`**: Handles plugin configuration and persistence (`CodexLauncherSettings`, `CodexLauncherConfigurable`).
    *   **`startup/`**: Activities run on project startup (`CodexStartupActivity`).
    *   **`terminal/`**: Manages the integrated terminal instances (`CodexTerminalManager`).
*   **`src/main/resources/`**: Contains resources like icons and the plugin configuration file.
    *   **`META-INF/plugin.xml`**: The core plugin configuration file (plugin ID, actions, extensions).
*   **`build.gradle.kts`**: The Gradle build script using the Kotlin DSL.

## Building and Running

The project uses Gradle with the IntelliJ Platform Gradle Plugin.

### Prerequisites

*   JDK 21
*   IntelliJ IDEA 2024.2+ (Targeting 2025.3 in build config)
*   OpenAI Codex CLI installed and in your system PATH.

### Commands

*   **Build Plugin:**
    ```bash
    ./gradlew buildPlugin
    ```
    This task assembles the plugin and prepares a distribution ZIP in `build/distributions/`.

*   **Run IDE with Plugin:**
    ```bash
    ./gradlew runIde
    ```
    This starts a sandboxed instance of IntelliJ IDEA with the plugin installed for testing and development.

*   **Run Tests:**
    ```bash
    ./gradlew test
    ```

## Key Features & implementation details

1.  **Launch Codex**:
    *   **Action**: `LaunchCodexAction` triggers the process.
    *   **Logic**: It checks for an existing Codex terminal using `CodexTerminalManager`. If active, it inserts the current file path. If not, it launches a new terminal session running the `codex` command.
    *   **Configuration**: Command arguments (model, mode, etc.) are built via `CodexArgsBuilder` based on `CodexLauncherSettings`.

2.  **Integrated Terminal**:
    *   **Manager**: `CodexTerminalManager` handles the creation and reuse of `TerminalWidget` instances. It ensures the terminal is labeled "Codex" and tracks its running state.

3.  **Completion Notifications & Auto-Open**:
    *   **Mechanism**: The plugin starts a local HTTP server (`HttpTriggerService`) on a random port.
    *   **Flow**: The plugin passes this port to the Codex CLI. When the CLI finishes a task, it sends a POST request to `http://localhost:<port>/refresh`.
    *   **Reaction**: The plugin refreshes the file system, shows a notification (if enabled), and opens changed files (if enabled).

4.  **Settings**:
    *   **UI**: Configurable via `Settings > Other Settings > Codex Launcher`.
    *   **Options**: Supports selecting Models (Default, GPT-5, Custom), Modes (Default, Full Auto), Notification preferences, and MCP Server configuration.

## Development Conventions

*   **Kotlin**: The project is written entirely in Kotlin.
*   **IntelliJ SDK**: Follows standard IntelliJ Platform SDK practices (Services, Actions, Extensions).
*   **UI**: Uses the Kotlin UI DSL for settings pages.
