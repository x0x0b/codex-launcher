# Codex Launcher - IntelliJ Plugin

[![Version](https://img.shields.io/badge/version-1.0.10-blue.svg)](https://github.com/x0x0b/codex-launcher/releases)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.2+-orange.svg)](https://www.jetbrains.com/idea/)

<img width="800" alt="The screenshot of Codex Launcher." src="https://github.com/user-attachments/assets/4ee3fbd8-e384-4672-94c6-e4e9041a8e0d" />

An **unofficial** IntelliJ IDEA plugin that seamlessly integrates OpenAI Codex CLI into your development workflow by launching it directly from the IDE.

> **This plugin requires the OpenAI Codex CLI to be installed separately.** Visit the [OpenAI Codex GitHub repository](https://github.com/openai/codex) for installation instructions.

> **For Windows users: Please select your terminal shell in the plugin settings to ensure proper functionality via _Settings (→ Other Settings) → Codex Launcher_.**

## ✨ Features

- 🚀 **One-click launch**: Toolbar button and Tools menu action for instant access
- 🔔 **IDE Notifications**: Optional notifications when Codex processing is completed
- 📄 **Auto file opening**: Automatically opens files modified by Codex in the editor
- 🔌 **Integration with the built-in MCP server**: Semi-automatic connection setup for IntelliJ's built-in MCP server (2025.2+)
- ⚙️ **Configurable**: Customizable launch modes, model selection, and more

## 🛠️ Installation

### Prerequisites
- IntelliJ IDEA 2024.2 or later (or other compatible JetBrains IDEs)
- OpenAI Codex CLI installed and available in your system PATH

### Installation
[![Install Plugin](https://img.shields.io/badge/Install%20Plugin-JetBrains-orange?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/28264-codex-launcher)

## 🚀 Usage

### Quick Start
1. Click the **Launch Codex** button in the main toolbar
2. Or navigate to **Tools** → **Launch Codex**
3. The integrated Terminal opens with a new "Codex" tab and runs `codex` automatically

### Configuration
Access plugin settings via **Settings** (→ **Other Settings**) → **Codex Launcher**

## 📝 Development

### Building from Source
```bash
git clone https://github.com/x0x0b/codex-launcher.git
cd codex-launcher
./gradlew buildPlugin
```

## 📄 License

This project is licensed under the terms specified in the [LICENSE](LICENSE) file.
