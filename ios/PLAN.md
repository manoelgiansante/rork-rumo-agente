# Rumo Agente — Assistente IA Inteligente


## Visão Geral
Um app iOS elegante e moderno (estilo Apple) onde o usuário dá comandos por chat e o agente IA executa tarefas automaticamente nos seus aplicativos — ideal para o agronegócio brasileiro e qualquer profissional que quer automatizar operações.

---

## **Funcionalidades**

### Autenticação
- Criar conta e fazer login com email/senha
- Login com Google
- Login com Apple (Sign in with Apple)
- Recuperação de senha por email
- Perfil do usuário com foto e dados

### Dashboard Principal
- Status do agente (pronto/iniciando)
- Saldo de créditos disponíveis
- Histórico de tarefas recentes executadas pelo agente
- Acesso rápido ao chat e à tela

### Visualização da Tela
- Tela em tempo real do que o agente está fazendo
- Modo tela cheia com zoom por gesto de pinça
- Indicador de status da conexão

### Chat com Agente IA (Claude)
- Interface de chat moderna para enviar comandos ao agente
- O agente interpreta o comando e executa a tarefa automaticamente
- Respostas com screenshots do que o agente está fazendo
- Perguntas de confirmação quando o comando não está claro
- Histórico completo de conversas

### Gerenciamento de Apps
- Lista de aplicativos disponíveis
- Solicitar instalação de novos apps via chat
- Selecionar um app antes de dar o comando (para o agente saber em qual app atuar)
- Status de cada app (instalado, em uso, etc.)

### Assinatura e Créditos
- Planos mensais com limite de uso incluso
- Opção de comprar créditos adicionais
- Painel de consumo mostrando créditos gastos e restantes
- Histórico de transações
- Integração com Stripe para pagamentos

### Configurações
- Idioma do app (Português, English, Español)
- Notificações (quando uma tarefa terminar, créditos baixos)
- Gerenciar conta e assinatura
- Tema claro/escuro automático

---

## **Design**

- **Estilo:** Limpo e moderno inspirado nos apps da Apple — fundo escuro elegante com acentos em verde/azul
- **Tipografia:** SF Pro com hierarquia clara (títulos bold, corpo regular)
- **Navegação:** Barra de abas na parte inferior com 4 seções: Dashboard, Tela, Chat, Perfil
- **Cards:** Cards com cantos arredondados e sombras sutis para informações
- **Chat:** Bolhas de mensagem estilo iMessage com indicador de digitação animado
- **Tela:** Visualização imersiva com bordas mínimas
- **Animações:** Transições suaves com spring animations, haptics ao completar tarefas
- **Cores:** Fundo escuro (#0D1117), acentos em verde (#34D399) para agro e azul (#3B82F6) para ações

---

## **Telas do App**

1. **Onboarding** — 3 slides explicando o app com ilustrações, botão "Começar"
2. **Login/Cadastro** — Campos de email/senha + botões Google e Apple
3. **Dashboard** — Status do agente, créditos, tarefas recentes, atalhos
4. **Tela** — Visualização em tempo real do que o agente está executando
5. **Chat do Agente** — Conversa com a IA, seletor de app no topo, envio de comandos
6. **Apps** — Grid de apps com opção de selecionar/instalar
7. **Planos e Créditos** — Planos de assinatura, compra de créditos extras
8. **Perfil e Configurações** — Dados pessoais, idioma, notificações, assinatura

---

## **Ícone do App**
- Fundo com gradiente escuro (preto para verde escuro)
- Símbolo de um monitor/tela com um ícone de cérebro/IA dentro
- Estilo minimalista e profissional

---

## **Arquitetura do Agente**
- O app iOS se conecta ao Supabase para autenticação e dados
- O chat envia comandos para a API Claude que interpreta a intenção do usuário
- O Claude gera comandos estruturados que são enviados ao AgentService
- O AgentService se comunica com o daemon do agente que executa as tarefas
- O Stripe gerencia assinaturas e créditos
- Screenshots são capturados para mostrar o progresso ao usuário
