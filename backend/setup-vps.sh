#!/bin/bash
# ============================================
# Script de setup do VPS - Rumo Agente
# Rodar como root no servidor 216.238.111.253
# ============================================

set -e

echo "==> Atualizando sistema..."
apt update && apt upgrade -y

echo "==> Instalando dependências..."
apt install -y curl wget git ufw nginx certbot python3-certbot-nginx

echo "==> Instalando Node.js 20 LTS..."
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt install -y nodejs

echo "==> Instalando Docker..."
curl -fsSL https://get.docker.com | sh
apt install -y docker-compose-plugin || true

echo "==> Instalando PM2..."
npm install -g pm2

echo "==> Configurando Firewall..."
ufw allow OpenSSH
ufw allow 80
ufw allow 443
ufw allow 3000
ufw --force enable

echo "==> Criando diretório da API..."
mkdir -p /opt/rumo-agente-api

echo "==> Verificações:"
echo "Node.js: $(node --version)"
echo "Docker: $(docker --version)"
echo "PM2: $(pm2 --version)"
echo "Nginx: $(nginx -v 2>&1)"

echo ""
echo "==> Setup base concluído!"
echo "==> Próximo passo: copie os arquivos backend para /opt/rumo-agente-api/"
echo "    scp backend/* root@216.238.111.253:/opt/rumo-agente-api/"
echo "    Depois: ssh root@216.238.111.253"
echo "    cd /opt/rumo-agente-api && npm install && pm2 start server.js --name rumo-api && pm2 save && pm2 startup"
