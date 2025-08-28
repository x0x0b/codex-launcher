# Codex Launcher - IntelliJ Plugin

[![Version](https://img.shields.io/badge/version-1.0.2-blue.svg)](https://github.com/x0x0b/codex-launcher/releases)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.2+-orange.svg)](https://www.jetbrains.com/idea/)

<img width="800" alt="The screenshot of Codex Launcher." src="https://github.com/user-attachments/assets/4ee3fbd8-e384-4672-94c6-e4e9041a8e0d" />

An **unofficial** IntelliJ IDEA plugin that seamlessly integrates OpenAI Codex CLI into your development workflow by launching it directly from the IDE.

> **This plugin requires the OpenAI Codex CLI to be installed separately.** Visit the [OpenAI Codex GitHub repository](https://github.com/openai/codex) for installation instructions.

## ✨ Features

- 🚀 **One-click launch**: Toolbar button and Tools menu action for instant access
- 🖥️ **Integrated Terminal**: Opens a dedicated "Codex" terminal tab in your IDE
- 📁 **Project-aware**: Automatically runs `codex` in the current project root directory
- ⚙️ **Configurable**: Customizable launch modes, model selection, and auto file opening

## 🛠️ Installation

### Prerequisites
- IntelliJ IDEA 2024.2 or later
- OpenAI Codex CLI installed and available in your system PATH

### Installation
[![Install Plugin](https://img.shields.io/badge/Install%20Plugin-JetBrains-orange?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/28264-codex-launcher)

## 🚀 Usage

### Quick Start
1. Click the **Launch Codex** button in the main toolbar
2. Or navigate to **Tools** → **Launch Codex**
3. The integrated Terminal opens with a new "Codex" tab and runs `codex` automatically

### Configuration
Access plugin settings via **Preferences** → **Codex Launcher**

#### Available Options
- **Launch Mode**: 
  - `default` - Standard Codex behavior
  - `--full-auto` - Fully automated mode
- **Model Selection**: 
  - Default model
  - Preset models
  - Custom model configuration
- **Auto File Open**: Automatically open files modified by Codex in the editor

## 🔧 Requirements

- **OpenAI Codex CLI**: Must be installed separately and available in your system PATH
- **IntelliJ IDEA**: Version 2024.2 or later
- **Java**: JDK 21 or later (for plugin development)

## 📝 Development

### Building from Source
```bash
git clone https://github.com/x0x0b/codex-launcher.git
cd codex-launcher
./gradlew buildPlugin
```

## 📄 License

This project is licensed under the terms specified in the [LICENSE](LICENSE) file.
