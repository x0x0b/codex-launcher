# Codex Launcher - IntelliJ Plugin

[![Version](https://img.shields.io/badge/version-1.1.3-blue.svg)](https://github.com/x0x0b/codex-launcher/releases)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.2+-orange.svg)](https://www.jetbrains.com/idea/)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/x0x0b/codex-launcher)

<img width="800" alt="The screenshot of Codex Launcher." src="https://github.com/user-attachments/assets/4ee3fbd8-e384-4672-94c6-e4e9041a8e0d" />

Codex Launcher is an **unofficial** IntelliJ IDEA plugin that keeps the OpenAI Codex CLI one click away inside the IDE.

> **Important:** Install the [OpenAI Codex CLI](https://github.com/openai/codex) separately before using this plugin.

> **For Windows users:** Please select your terminal shell in the plugin settings to ensure proper functionality. Go to _Settings (→ Other Settings) → Codex Launcher_.

## ✨ Features

- **One-click launch** from the toolbar or Tools menu
- **Integrated terminal** that opens a dedicated "Codex" tab in the project root
- **Completion notifications** after Codex CLI finishes processing the current run
- **Automatic file opening** for files updated by Codex
- **Built-in MCP server pairing** with guided setup for IntelliJ's MCP server (2025.2+)
- **Flexible configuration** for launch modes, models, and notifications

## 🛠️ Installation

### Prerequisites
- IntelliJ IDEA 2024.2 or later (or other compatible JetBrains IDEs)
- OpenAI Codex CLI installed and available in your system PATH

### Installation
[![Install Plugin](https://img.shields.io/badge/Install%20Plugin-JetBrains-orange?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/28264-codex-launcher)

## 🚀 Usage

### Quick Start
1. Click the **Launch Codex** button in the main toolbar.
2. Or choose **Tools** → **Launch Codex**.
3. The integrated terminal opens a new "Codex" tab and runs `codex` automatically.

### Configuration
Open **Settings (→ Other Settings) → Codex Launcher** to pick the launch mode, model, notification behavior, and auto-open options.

## 📝 Development

### Building from Source
```bash
git clone https://github.com/x0x0b/codex-launcher.git
cd codex-launcher
./gradlew buildPlugin
```

## 📄 License

This project is licensed under the terms specified in the [LICENSE](LICENSE) file.
