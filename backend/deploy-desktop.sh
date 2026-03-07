#!/bin/bash
# ============================================
# Deploy do sistema de desktops isolados
# Rodar como root no VPS 216.238.111.253
# ============================================

set -e

echo "==> Verificando Docker..."
if ! command -v docker &> /dev/null; then
    echo "Docker nao encontrado. Instalando..."
    curl -fsSL https://get.docker.com | sh
fi

echo "==> Verificando se Docker esta rodando..."
systemctl start docker
systemctl enable docker

echo "==> Abrindo portas do firewall para noVNC (6100-6114)..."
ufw allow 6100:6114/tcp

echo "==> Construindo imagem Docker do desktop..."
cd /opt/rumo-agente-api/docker
docker build -t rumo-desktop .

echo "==> Testando imagem..."
docker run -d --name rumo-desktop-test -p 6199:6080 --memory=1g --cpus=0.5 rumo-desktop
echo "Aguardando desktop iniciar..."
sleep 5

# Check if container is running
if docker ps | grep -q rumo-desktop-test; then
    echo "Container de teste OK!"
    docker stop rumo-desktop-test
    docker rm rumo-desktop-test
else
    echo "ERRO: Container de teste falhou!"
    docker logs rumo-desktop-test
    docker rm rumo-desktop-test
    exit 1
fi

echo "==> Reiniciando API..."
cd /opt/rumo-agente-api
npm install
pm2 restart rumo-api || pm2 start server.js --name rumo-api
pm2 save

echo ""
echo "============================================"
echo "Deploy concluido com sucesso!"
echo ""
echo "Cada usuario agora recebe seu proprio desktop isolado."
echo "Limites por container: 1GB RAM, 0.5 CPU"
echo "Max containers simultaneos: 15"
echo "Auto-desliga apos 30min de inatividade"
echo ""
echo "Para monitorar:"
echo "  docker ps                    # containers ativos"
echo "  docker stats                 # uso de recursos"
echo "  pm2 logs rumo-api           # logs da API"
echo "============================================"
