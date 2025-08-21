Codex Launcher IntelliJ Plugin

What it does
- Adds a toolbar button and Tools menu action to open a new integrated Terminal tab named "Codex" and run `codex` in the current project directory.

Build and run (from this project root)
- Run IDE with the plugin: `./gradlew runIde`
- Build the plugin: `./gradlew build` (artifact under `build/distributions`)

Requirements
- IntelliJ IDEA 2025.1 (or compatible IDE) with the bundled Terminal plugin.
- JDK 21 for building/running via Gradle.
- `codex` must be available on your PATH in the IDE's environment.

Notes
- Toolbar placement targets both the new UI (MainToolbarLeft/Right) and a fallback group (NavBarToolBar), plus a Tools menu entry.
- To change the launched command, edit `LaunchCodexAction.kt` and replace `widget.executeCommand("codex")`.
