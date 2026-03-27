#!/bin/bash
set -e

# Clean stale VNC locks
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1

# Ensure VNC password exists
if [ ! -f "$HOME/.vnc/passwd" ]; then
    mkdir -p "$HOME/.vnc"
    echo "rumoagente" | tigervncpasswd -f > "$HOME/.vnc/passwd"
    chmod 600 "$HOME/.vnc/passwd"
fi

# Create proper xstartup that keeps running
cat > "$HOME/.vnc/xstartup" << 'XEOF'
#!/bin/bash
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
export XDG_SESSION_TYPE=x11

# Start dbus
eval $(dbus-launch --sh-syntax)

# Start XFCE desktop - exec keeps it in foreground
exec startxfce4
XEOF
chmod +x "$HOME/.vnc/xstartup"

# Start VNC server (foreground mode to avoid early exit detection)
tigervncserver :1 \
  -geometry ${VNC_RESOLUTION:-1280x720} \
  -depth 24 \
  -rfbport ${VNC_PORT:-5901} \
  -localhost no \
  -fg &

VNC_PID=$!
sleep 3

echo "VNC server started on port ${VNC_PORT:-5901} (PID: $VNC_PID)"

# Start noVNC (websocket proxy to VNC)
NOVNC_DIR="/usr/share/novnc"
if [ -d "$NOVNC_DIR" ]; then
    websockify --web "$NOVNC_DIR" ${NOVNC_PORT:-6080} localhost:${VNC_PORT:-5901} &
    echo "noVNC started on port ${NOVNC_PORT:-6080}"
fi

echo "Desktop ready!"

# Wait for VNC process
wait $VNC_PID
