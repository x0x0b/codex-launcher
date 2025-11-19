# Gemini Launcher - GEMINI Context

## Project Overview
**Gemini Launcher** is an unofficial IntelliJ IDEA plugin designed to integrate the Google Gemini CLI directly into the IDE. It provides a one-click solution to launch a dedicated terminal tab running `gemini`, along with features like completion notifications and automatic file opening.

**Key Technologies:**
*   **Language:** Kotlin (JVM Target 21)
*   **Framework:** IntelliJ Platform SDK
*   **Build System:** Gradle (Kotlin DSL)
*   **Dependencies:** `org.jetbrains.plugins.terminal`, `com.google.code.gson`

## Building and Running
This project uses the IntelliJ Platform Gradle Plugin.

*   **Build Plugin:**
    ```bash
    ./gradlew buildPlugin
    ```
*   **Run IDE with Plugin:**
    ```bash
    ./gradlew runIde
    ```
    This starts a sandboxed instance of IntelliJ IDEA with the plugin installed.
*   **Run Tests:**
    ```bash
    ./gradlew test
    ```
*   **Clean:**
    ```bash
    ./gradlew clean
    ```

## Development Conventions

### Project Structure
*   **`src/main/kotlin/.../actions/`**: Contains `LaunchGeminiAction`, the entry point for the user interaction.
*   **`src/main/kotlin/.../cli/`**: Logic for building CLI arguments (`GeminiArgsBuilder`).
*   **`src/main/kotlin/.../settings/`**: Persistent state and configuration UI (`GeminiLauncherSettings`, `GeminiLauncherConfigurable`).
*   **`src/main/kotlin/.../terminal/`**: Manages the IDE's terminal tool window (`GeminiTerminalManager`).
*   **`src/main/resources/META-INF/plugin.xml`**: The plugin manifest file defining extensions, actions, and dependencies.

### Configuration
*   **Gradle:** `build.gradle.kts` configures the IntelliJ Platform version (currently targeting `2025.2`) and Kotlin version (`2.2.10`).
*   **Plugin ID:** `com.github.eisermann.gemini-launcher`

### Testing
*   Uses JUnit 4.
*   Tests are located in `src/test/kotlin`.
*   `testFramework(Platform)` is configured in Gradle for integration testing.

### Key Files to Know
*   **`plugin.xml`**: The heart of the plugin configuration. Define new actions or extensions here.
*   **`LaunchGeminiAction.kt`**: Handles the "Launch Gemini" button click.
*   **`GeminiArgsBuilder.kt`**: Constructs the command string to be executed in the terminal.