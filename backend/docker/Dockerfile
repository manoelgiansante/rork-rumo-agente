FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV DISPLAY=:1
ENV VNC_PORT=5901
ENV NOVNC_PORT=6080
ENV VNC_RESOLUTION=1280x720
ENV USER=rumo
ENV HOME=/home/rumo

# Install base packages + theme dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    xfce4 xfce4-terminal \
    tigervnc-standalone-server tigervnc-common tigervnc-tools \
    novnc websockify python3 \
    firefox \
    thunar mousepad \
    dbus-x11 \
    xdg-utils \
    fonts-noto-core fonts-inter \
    sudo curl wget ca-certificates \
    scrot xdotool \
    unzip sassc librsvg2-bin \
    gtk2-engines-murrine gtk2-engines-pixbuf \
    adwaita-icon-theme-full \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -m -s /bin/bash $USER && \
    echo "$USER ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# Setup VNC password
RUN mkdir -p $HOME/.vnc && \
    bash -c 'echo "rumoagente" | tigervncpasswd -f > $HOME/.vnc/passwd' && \
    chmod 600 $HOME/.vnc/passwd && \
    chown -R $USER:$USER $HOME

# VNC startup config
RUN printf '#!/bin/bash\nunset SESSION_MANAGER\nunset DBUS_SESSION_BUS_ADDRESS\nexport XDG_SESSION_TYPE=x11\neval $(dbus-launch --sh-syntax)\nexec startxfce4\n' > $HOME/.vnc/xstartup && \
    chmod +x $HOME/.vnc/xstartup && \
    chown $USER:$USER $HOME/.vnc/xstartup

# Setup macOS theme as user
COPY setup-macos-theme.sh /tmp/setup-macos-theme.sh
RUN chmod +x /tmp/setup-macos-theme.sh && \
    su - $USER -c "/tmp/setup-macos-theme.sh" && \
    rm /tmp/setup-macos-theme.sh

# noVNC symlink
RUN ln -sf /usr/share/novnc/vnc.html /usr/share/novnc/index.html 2>/dev/null || true

COPY startup.sh /startup.sh
RUN chmod +x /startup.sh

# Set ownership
RUN chown -R $USER:$USER $HOME

USER $USER
WORKDIR $HOME

EXPOSE 5901 6080

CMD ["/startup.sh"]
