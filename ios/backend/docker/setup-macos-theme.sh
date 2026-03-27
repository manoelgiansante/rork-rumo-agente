#!/bin/bash
# Setup macOS-like theme for XFCE desktop
set -e

THEME_DIR="$HOME/.themes"
ICON_DIR="$HOME/.icons"
WALLPAPER_DIR="$HOME/.wallpapers"
XFCE_CONF="$HOME/.config/xfce4/xfconf/xfce-perchannel-xml"

mkdir -p "$THEME_DIR" "$ICON_DIR" "$WALLPAPER_DIR" "$XFCE_CONF"
mkdir -p "$HOME/.config/xfce4/panel"
mkdir -p "$HOME/.config/gtk-3.0"
mkdir -p "$HOME/Desktop" "$HOME/Documents" "$HOME/Downloads"

# ============================================
# 1. Download WhiteSur GTK Theme (macOS clone)
# ============================================
cd /tmp
if [ ! -d "$THEME_DIR/WhiteSur-Dark" ]; then
    echo "Installing WhiteSur GTK theme..."
    wget -q "https://github.com/vinceliuice/WhiteSur-gtk-theme/archive/refs/heads/master.zip" -O whitesur-gtk.zip
    unzip -q whitesur-gtk.zip
    cd WhiteSur-gtk-theme-master
    # Install just the dark theme
    mkdir -p "$THEME_DIR"
    ./install.sh -d "$THEME_DIR" -c Dark -t default 2>/dev/null || {
        # Fallback: copy directly
        cp -r src "$THEME_DIR/WhiteSur-Dark" 2>/dev/null || true
    }
    cd /tmp && rm -rf WhiteSur-gtk-theme-master whitesur-gtk.zip
fi

# ============================================
# 2. Download WhiteSur Icon Theme
# ============================================
cd /tmp
if [ ! -d "$ICON_DIR/WhiteSur" ]; then
    echo "Installing WhiteSur icon theme..."
    wget -q "https://github.com/vinceliuice/WhiteSur-icon-theme/archive/refs/heads/master.zip" -O whitesur-icons.zip
    unzip -q whitesur-icons.zip
    cd WhiteSur-icon-theme-master
    ./install.sh -d "$ICON_DIR" 2>/dev/null || {
        cp -r src "$ICON_DIR/WhiteSur" 2>/dev/null || true
    }
    cd /tmp && rm -rf WhiteSur-icon-theme-master whitesur-icons.zip
fi

# ============================================
# 3. Create dark macOS wallpaper (SVG)
# ============================================
cat > "$WALLPAPER_DIR/macos-dark.svg" << 'SVGEOF'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 720">
  <defs>
    <radialGradient id="g1" cx="50%" cy="40%" r="70%">
      <stop offset="0%" style="stop-color:#1a1a2e"/>
      <stop offset="40%" style="stop-color:#0f0f1a"/>
      <stop offset="100%" style="stop-color:#050510"/>
    </radialGradient>
    <radialGradient id="g2" cx="30%" cy="60%" r="40%">
      <stop offset="0%" style="stop-color:#16213e;stop-opacity:0.6"/>
      <stop offset="100%" style="stop-color:transparent"/>
    </radialGradient>
    <radialGradient id="g3" cx="75%" cy="30%" r="35%">
      <stop offset="0%" style="stop-color:#0a3d2e;stop-opacity:0.4"/>
      <stop offset="100%" style="stop-color:transparent"/>
    </radialGradient>
  </defs>
  <rect width="1280" height="720" fill="url(#g1)"/>
  <rect width="1280" height="720" fill="url(#g2)"/>
  <rect width="1280" height="720" fill="url(#g3)"/>
</svg>
SVGEOF

# Convert SVG to PNG for XFCE
if command -v rsvg-convert &>/dev/null; then
    rsvg-convert -w 1280 -h 720 "$WALLPAPER_DIR/macos-dark.svg" > "$WALLPAPER_DIR/wallpaper.png"
elif command -v convert &>/dev/null; then
    convert "$WALLPAPER_DIR/macos-dark.svg" "$WALLPAPER_DIR/wallpaper.png"
else
    # Create simple dark gradient with Python
    python3 -c "
from PIL import Image
import math
img = Image.new('RGB', (1280, 720))
for y in range(720):
    for x in range(1280):
        dx = (x - 640) / 640
        dy = (y - 288) / 720
        d = math.sqrt(dx*dx + dy*dy)
        r = max(0, min(255, int(26 - d * 20)))
        g = max(0, min(255, int(26 - d * 18)))
        b = max(0, min(255, int(46 - d * 30)))
        img.putpixel((x, y), (r, g, b))
img.save('$WALLPAPER_DIR/wallpaper.png')
" 2>/dev/null || {
        # Ultra fallback: solid dark color
        python3 -c "
import struct, zlib
def png(w,h,r,g,b):
    raw=b''
    for y in range(h):
        raw+=b'\x00'+bytes([r,g,b])*w
    def chunk(t,d):
        c=t+d; return struct.pack('>I',len(d))+c+struct.pack('>I',zlib.crc32(c)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
open('$WALLPAPER_DIR/wallpaper.png','wb').write(png(1280,720,10,10,20))
"
    }
fi

# ============================================
# 4. Configure XFCE - Desktop
# ============================================
cat > "$XFCE_CONF/xfce4-desktop.xml" << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-desktop" version="1.0">
  <property name="backdrop" type="empty">
    <property name="screen0" type="empty">
      <property name="monitorVNC-0" type="empty">
        <property name="workspace0" type="empty">
          <property name="color-style" type="int" value="0"/>
          <property name="image-style" type="int" value="5"/>
          <property name="last-image" type="string" value="WALLPAPER_PATH"/>
        </property>
      </property>
    </property>
  </property>
  <property name="desktop-icons" type="empty">
    <property name="style" type="int" value="0"/>
  </property>
</channel>
XMLEOF
sed -i "s|WALLPAPER_PATH|$WALLPAPER_DIR/wallpaper.png|g" "$XFCE_CONF/xfce4-desktop.xml"

# ============================================
# 5. Configure XFCE - Window Manager (buttons left)
# ============================================
cat > "$XFCE_CONF/xfwm4.xml" << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="button_layout" type="string" value="CHM|"/>
    <property name="theme" type="string" value="WhiteSur-Dark"/>
    <property name="title_font" type="string" value="Inter Semi-Bold 10"/>
    <property name="title_alignment" type="string" value="center"/>
    <property name="shadow_opacity" type="int" value="60"/>
    <property name="placement_ratio" type="int" value="50"/>
    <property name="raise_with_any_button" type="bool" value="true"/>
    <property name="snap_to_border" type="bool" value="true"/>
    <property name="snap_to_windows" type="bool" value="true"/>
    <property name="borderless_maximize" type="bool" value="true"/>
  </property>
</channel>
XMLEOF

# ============================================
# 6. Configure XFCE - Appearance (GTK theme)
# ============================================
cat > "$XFCE_CONF/xsettings.xml" << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xsettings" version="1.0">
  <property name="Net" type="empty">
    <property name="ThemeName" type="string" value="WhiteSur-Dark"/>
    <property name="IconThemeName" type="string" value="WhiteSur"/>
    <property name="CursorThemeName" type="string" value="Adwaita"/>
    <property name="CursorSize" type="int" value="24"/>
    <property name="EnableEventSounds" type="bool" value="false"/>
  </property>
  <property name="Gtk" type="empty">
    <property name="FontName" type="string" value="Inter 10"/>
    <property name="MonospaceFontName" type="string" value="Monospace 10"/>
    <property name="CursorThemeName" type="string" value="Adwaita"/>
  </property>
  <property name="Xft" type="empty">
    <property name="Antialias" type="int" value="1"/>
    <property name="HintStyle" type="string" value="hintslight"/>
    <property name="RGBA" type="string" value="rgb"/>
    <property name="DPI" type="int" value="96"/>
  </property>
</channel>
XMLEOF

# GTK3 dark theme config
cat > "$HOME/.config/gtk-3.0/settings.ini" << 'INIEOF'
[Settings]
gtk-theme-name=WhiteSur-Dark
gtk-icon-theme-name=WhiteSur
gtk-cursor-theme-name=Adwaita
gtk-font-name=Inter 10
gtk-application-prefer-dark-theme=true
gtk-decoration-layout=close,minimize,maximize:
INIEOF

# ============================================
# 7. Configure Panels (macOS style)
# ============================================
# Top panel = slim menu bar
# Bottom panel = dock with app launchers
cat > "$XFCE_CONF/xfce4-panel.xml" << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-panel" version="1.0">
  <property name="configver" type="int" value="2"/>
  <property name="panels" type="array">
    <value type="int" value="1"/>
    <value type="int" value="2"/>
    <property name="dark-mode" type="bool" value="true"/>
    <property name="panel-1" type="empty">
      <property name="position" type="string" value="p=6;x=0;y=0"/>
      <property name="position-locked" type="bool" value="true"/>
      <property name="size" type="uint" value="28"/>
      <property name="length" type="uint" value="100"/>
      <property name="length-adjust" type="bool" value="false"/>
      <property name="background-style" type="uint" value="1"/>
      <property name="background-rgba" type="array">
        <value type="double" value="0.12"/>
        <value type="double" value="0.12"/>
        <value type="double" value="0.15"/>
        <value type="double" value="0.92"/>
      </property>
      <property name="enter-opacity" type="uint" value="100"/>
      <property name="leave-opacity" type="uint" value="100"/>
      <property name="plugin-ids" type="array">
        <value type="int" value="1"/>
        <value type="int" value="2"/>
        <value type="int" value="3"/>
        <value type="int" value="4"/>
        <value type="int" value="5"/>
      </property>
    </property>
    <property name="panel-2" type="empty">
      <property name="position" type="string" value="p=10;x=0;y=0"/>
      <property name="position-locked" type="bool" value="true"/>
      <property name="size" type="uint" value="52"/>
      <property name="length" type="uint" value="1"/>
      <property name="length-adjust" type="bool" value="true"/>
      <property name="background-style" type="uint" value="1"/>
      <property name="background-rgba" type="array">
        <value type="double" value="0.12"/>
        <value type="double" value="0.12"/>
        <value type="double" value="0.15"/>
        <value type="double" value="0.85"/>
      </property>
      <property name="enter-opacity" type="uint" value="100"/>
      <property name="leave-opacity" type="uint" value="85"/>
      <property name="plugin-ids" type="array">
        <value type="int" value="10"/>
        <value type="int" value="11"/>
        <value type="int" value="12"/>
        <value type="int" value="13"/>
        <value type="int" value="14"/>
        <value type="int" value="15"/>
      </property>
    </property>
  </property>
  <property name="plugins" type="empty">
    <property name="plugin-1" type="string" value="applicationsmenu">
      <property name="button-icon" type="string" value="distributor-logo"/>
      <property name="button-title" type="string" value="Rumo"/>
      <property name="show-button-title" type="bool" value="true"/>
      <property name="small" type="bool" value="true"/>
    </property>
    <property name="plugin-2" type="string" value="separator">
      <property name="expand" type="bool" value="true"/>
      <property name="style" type="uint" value="0"/>
    </property>
    <property name="plugin-3" type="string" value="clock">
      <property name="digital-layout" type="uint" value="3"/>
      <property name="digital-date-format" type="string" value="%a %d %b"/>
      <property name="digital-time-format" type="string" value="%H:%M"/>
    </property>
    <property name="plugin-4" type="string" value="separator">
      <property name="style" type="uint" value="0"/>
    </property>
    <property name="plugin-5" type="string" value="systray">
      <property name="square-icons" type="bool" value="true"/>
    </property>
    <property name="plugin-10" type="string" value="launcher">
      <property name="items" type="array">
        <value type="string" value="thunar.desktop"/>
      </property>
    </property>
    <property name="plugin-11" type="string" value="launcher">
      <property name="items" type="array">
        <value type="string" value="firefox.desktop"/>
      </property>
    </property>
    <property name="plugin-12" type="string" value="launcher">
      <property name="items" type="array">
        <value type="string" value="xfce4-terminal.desktop"/>
      </property>
    </property>
    <property name="plugin-13" type="string" value="launcher">
      <property name="items" type="array">
        <value type="string" value="libreoffice-calc.desktop"/>
      </property>
    </property>
    <property name="plugin-14" type="string" value="launcher">
      <property name="items" type="array">
        <value type="string" value="libreoffice-writer.desktop"/>
      </property>
    </property>
    <property name="plugin-15" type="string" value="launcher">
      <property name="items" type="array">
        <value type="string" value="mousepad.desktop"/>
      </property>
    </property>
  </property>
</channel>
XMLEOF

echo "macOS theme setup complete!"
