Codex Launcher IntelliJ Plugin

What it does
- Adds a toolbar button and Tools menu action to open a new integrated Terminal tab named "Codex" and run `codex` in the current project directory.
- Provides a simple option to include `--full-auto` in the launch command. Toggle it from the toolbar or set it in Settings.

Build and run (from this project root)
- Run IDE with the plugin: `./gradlew runIde`
- Build the plugin: `./gradlew build` (artifact under `build/distributions`)

Notes
- Toolbar placement targets both the new UI (MainToolbarLeft/Right) and a fallback group (NavBarToolBar), plus a Tools menu entry.
- Settings: Preferences | Tools | Codex Launcher — choose Mode (Default or Full Auto) and optional Model. When a model is selected (or a custom model id is entered), the launcher adds `--model <id>`.
- Toolbar: use the "Full Auto" toggle to quickly enable/disable the flag.
