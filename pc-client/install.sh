#!/bin/bash
set -e

echo "=== Work Points Daemon Installer ==="

# Build the fat JAR
echo "Building..."
cd "$(dirname "$0")/.."
./gradlew :pc-client:shadowJar

# Install JAR
JAR_DIR="$HOME/.local/lib"
mkdir -p "$JAR_DIR"
cp pc-client/build/libs/workpointsd.jar "$JAR_DIR/workpointsd.jar"
echo "Installed JAR to $JAR_DIR/workpointsd.jar"

# Install config
CONFIG_DIR="$HOME/.config/workpointsd"
mkdir -p "$CONFIG_DIR"
if [ ! -f "$CONFIG_DIR/config.yaml" ]; then
    cp pc-client/config.example.yaml "$CONFIG_DIR/config.yaml"
    echo "Created default config at $CONFIG_DIR/config.yaml"
    echo "Edit this file to set your API URL and key."
else
    echo "Config already exists at $CONFIG_DIR/config.yaml (not overwriting)"
fi

# Create log directory
mkdir -p "$HOME/.local/share/workpointsd"

# Install systemd service
SYSTEMD_DIR="$HOME/.config/systemd/user"
mkdir -p "$SYSTEMD_DIR"
cp pc-client/workpointsd.service "$SYSTEMD_DIR/workpointsd.service"
echo "Installed systemd service"

# Reload and enable
systemctl --user daemon-reload
systemctl --user enable workpointsd.service
echo "Service enabled (will start on login)"

echo ""
echo "=== Installation Complete ==="
echo ""
echo "To start now:  systemctl --user start workpointsd"
echo "To check:      systemctl --user status workpointsd"
echo "Logs:          journalctl --user -u workpointsd -f"
echo "Config:        $CONFIG_DIR/config.yaml"
