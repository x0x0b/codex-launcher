# Codex Launcher - Project Context

## Project Overview
**Codex Launcher** is an unofficial IntelliJ IDEA plugin designed to integrate the [OpenAI Codex CLI](https://github.com/openai/codex) directly into the IDE. It allows users to launch the Codex CLI in a dedicated terminal tab, receive completion notifications, and automatically open files modified by Codex.

**Type:** IntelliJ Platform Plugin
**Language:** Kotlin
**Build System:** Gradle (Kotlin DSL)

## Key Features
*   **One-click Launch:** Action to open Codex CLI in the integrated terminal.
*   **Context Integration:** Send current selection or class to Codex.
*   **Notifications:** Alerts when Codex finishes processing.
*   **Auto-open Files:** Detects and opens files modified by Codex.
*   **Settings:** Configurable launch modes, models, and shell preferences (especially for Windows).

## Building and Running

The project uses the [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).

### Prerequisites
*   JDK 21 (defined in `build.gradle.kts`)
*   OpenAI Codex CLI installed on the system.

### Commands
*   **Build Plugin:**
    ```bash
    ./gradlew buildPlugin
    ```
    Generates the plugin distribution file in `build/distributions/`.

*   **Run IDE with Plugin:**
    ```bash
    ./gradlew runIde
    ```
    Starts a sandboxed instance of IntelliJ IDEA with the plugin installed for testing.

*   **Run Tests:**
    ```bash
    ./gradlew test
    ```

## Key Files and Structure

*   **`build.gradle.kts`**: Main build configuration file. Defines dependencies, plugin version, and IntelliJ Platform settings.
*   **`src/main/resources/META-INF/plugin.xml`**: The plugin manifest. Defines:
    *   **ID:** `com.github.x0x0b.codex-launcher`
    *   **Actions:** `LaunchCodexAction`, `SendRangeToCodexAction`
    *   **Extensions:** Settings configurable, Startup activity, Notification group.
*   **`src/main/kotlin/com/github/x0x0b/codexlauncher/`**: Source code root.
    *   **`actions/`**: Action implementations (`LaunchCodexAction.kt`).
    *   **`settings/`**: Configuration logic (`CodexLauncherSettings.kt`).
    *   **`terminal/`**: Terminal integration and script generation.
    *   **`startup/`**: Plugin startup logic.

## Development Conventions

*   **Language:** Kotlin is strictly used for source code.
*   **Testing:** JUnit 5 (`junit-jupiter`) is used for testing.
*   **Versioning:** Follows Semantic Versioning (currently `1.1.11`).
*   **IntelliJ SDK:** Targets IntelliJ IDEA 2024.2+ (Build `242+`).
