FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV DISPLAY=:1
ENV VNC_PORT=5901
ENV NOVNC_PORT=6080
ENV VNC_RESOLUTION=1280x720
ENV VNC_PASSWORD=rumoagente
ENV USER=rumo
ENV HOME=/home/rumo

# Install base packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    xfce4 xfce4-terminal xfce4-goodies \
    tigervnc-standalone-server tigervnc-common \
    novnc websockify \
    firefox \
    libreoffice-calc libreoffice-writer \
    thunar \
    dbus-x11 \
    xdg-utils \
    fonts-noto-core fonts-noto-color-emoji \
    sudo curl wget ca-certificates \
    scrot xdotool \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -m -s /bin/bash $USER && \
    echo "$USER ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# Setup VNC
RUN mkdir -p $HOME/.vnc && \
    echo "$VNC_PASSWORD" | vncpasswd -f > $HOME/.vnc/passwd && \
    chmod 600 $HOME/.vnc/passwd && \
    chown -R $USER:$USER $HOME

# VNC startup config
RUN echo '#!/bin/bash\nstartxfce4 &' > $HOME/.vnc/xstartup && \
    chmod +x $HOME/.vnc/xstartup && \
    chown $USER:$USER $HOME/.vnc/xstartup

# XFCE config - dark theme, remove panels clutter
RUN mkdir -p $HOME/.config/xfce4/xfconf/xfce-perchannel-xml && \
    chown -R $USER:$USER $HOME/.config

# noVNC symlink
RUN ln -sf /usr/share/novnc/vnc.html /usr/share/novnc/index.html

COPY startup.sh /startup.sh
RUN chmod +x /startup.sh

USER $USER
WORKDIR $HOME

EXPOSE 5901 6080

CMD ["/startup.sh"]
