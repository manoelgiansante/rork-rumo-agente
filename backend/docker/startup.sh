#!/bin/bash
set -e

# Clean stale VNC locks
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1

# Start VNC server
vncserver :1 \
  -geometry ${VNC_RESOLUTION} \
  -depth 24 \
  -SecurityTypes VncAuth \
  -rfbport ${VNC_PORT} \
  -localhost no

# Start noVNC (websocket proxy to VNC)
websockify --web /usr/share/novnc ${NOVNC_PORT} localhost:${VNC_PORT} &

echo "Desktop ready - VNC on :${VNC_PORT}, noVNC on :${NOVNC_PORT}"

# Keep container alive
tail -f /dev/null
