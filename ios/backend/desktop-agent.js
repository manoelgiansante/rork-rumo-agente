const { execSync } = require('child_process');
const containerManager = require('./container-manager');
const agentMemory = require('./agent-memory');

// Tools that Claude can use to control the user's desktop
const DESKTOP_TOOLS = [
  {
    name: 'open_app',
    description: 'Abre um aplicativo no desktop do usuario. Apps disponiveis: firefox, terminal, files (thunar), editor (mousepad), calc (LibreOffice Calc), writer (LibreOffice Writer).',
    input_schema: {
      type: 'object',
      properties: {
        app_name: {
          type: 'string',
          description: 'Nome do app: firefox, terminal, files, editor, calc, writer'
        }
      },
      required: ['app_name']
    }
  },
  {
    name: 'type_text',
    description: 'Digita texto no campo ativo do desktop. Use para preencher formularios, URLs, documentos, etc.',
    input_schema: {
      type: 'object',
      properties: {
        text: {
          type: 'string',
          description: 'Texto a ser digitado'
        }
      },
      required: ['text']
    }
  },
  {
    name: 'press_key',
    description: 'Pressiona uma tecla ou combinacao de teclas. Exemplos: Return, Tab, Escape, BackSpace, ctrl+a, ctrl+c, ctrl+v, alt+F4, ctrl+t (nova aba), ctrl+l (barra de endereco).',
    input_schema: {
      type: 'object',
      properties: {
        key: {
          type: 'string',
          description: 'Tecla ou combinacao: Return, Tab, ctrl+a, ctrl+c, alt+F4, etc.'
        }
      },
      required: ['key']
    }
  },
  {
    name: 'click',
    description: 'Clica em uma posicao x,y na tela (resolucao 1280x720). Use apos ver o screenshot para saber onde clicar.',
    input_schema: {
      type: 'object',
      properties: {
        x: { type: 'integer', description: 'Posicao X (0-1280)' },
        y: { type: 'integer', description: 'Posicao Y (0-720)' },
        button: { type: 'string', description: 'Botao: left (padrao), right, middle', default: 'left' }
      },
      required: ['x', 'y']
    }
  },
  {
    name: 'double_click',
    description: 'Clica duas vezes em uma posicao x,y na tela.',
    input_schema: {
      type: 'object',
      properties: {
        x: { type: 'integer', description: 'Posicao X (0-1280)' },
        y: { type: 'integer', description: 'Posicao Y (0-720)' }
      },
      required: ['x', 'y']
    }
  },
  {
    name: 'run_command',
    description: 'Executa um comando no terminal do desktop do usuario. Use para instalar programas, executar scripts, etc.',
    input_schema: {
      type: 'object',
      properties: {
        command: {
          type: 'string',
          description: 'Comando bash a ser executado'
        }
      },
      required: ['command']
    }
  },
  {
    name: 'take_screenshot',
    description: 'Tira um screenshot da tela atual do desktop. Use para ver o estado atual antes de decidir a proxima acao.',
    input_schema: {
      type: 'object',
      properties: {},
      required: []
    }
  },
  {
    name: 'wait',
    description: 'Aguarda um tempo antes da proxima acao. Use apos abrir apps ou carregar paginas.',
    input_schema: {
      type: 'object',
      properties: {
        seconds: {
          type: 'integer',
          description: 'Segundos para aguardar (1-10)',
          minimum: 1,
          maximum: 10
        }
      },
      required: ['seconds']
    }
  },
  // Novas tools para memoria e credenciais
  {
    name: 'save_user_preference',
    description: 'Salva uma preferencia ou informacao aprendida sobre o usuario. Use quando o usuario mencionar algo que deve ser lembrado (nome da fazenda, sistema preferido, forma de trabalhar, etc).',
    input_schema: {
      type: 'object',
      properties: {
        key: { type: 'string', description: 'Identificador da preferencia (ex: fazenda_nome, sistema_favorito, unidade_peso)' },
        value: { type: 'string', description: 'Valor da preferencia' },
        category: { type: 'string', description: 'Categoria: preference, learned_fact, software_knowledge', default: 'preference' }
      },
      required: ['key', 'value']
    }
  },
  {
    name: 'get_credential',
    description: 'Recupera as credenciais salvas de um sistema/servico do usuario. Use quando o usuario pedir para acessar um sistema que ele ja cadastrou login.',
    input_schema: {
      type: 'object',
      properties: {
        service_name: { type: 'string', description: 'Nome do servico (ex: aegro, conta_azul, siagri)' }
      },
      required: ['service_name']
    }
  },
  {
    name: 'save_credential',
    description: 'Salva credenciais de um sistema/servico do usuario de forma segura (criptografada). Use quando o usuario fornecer login/senha de um sistema para salvar.',
    input_schema: {
      type: 'object',
      properties: {
        service_name: { type: 'string', description: 'Nome do servico (ex: aegro, conta_azul)' },
        service_url: { type: 'string', description: 'URL do servico (ex: https://app.aegro.com.br)' },
        username: { type: 'string', description: 'Login do usuario (email, CPF, etc)' },
        password: { type: 'string', description: 'Senha do usuario' }
      },
      required: ['service_name', 'username', 'password']
    }
  },
  {
    name: 'list_credentials',
    description: 'Lista todos os servicos que o usuario tem credenciais salvas (sem mostrar senhas).',
    input_schema: {
      type: 'object',
      properties: {},
      required: []
    }
  },
  {
    name: 'save_workflow',
    description: 'Salva uma sequencia de acoes que funcionou como um workflow reutilizavel. Use apos completar com sucesso uma tarefa que o usuario pode querer repetir.',
    input_schema: {
      type: 'object',
      properties: {
        name: { type: 'string', description: 'Nome curto do workflow (ex: login_aegro, criar_planilha_gado)' },
        description: { type: 'string', description: 'Descricao do que o workflow faz' },
        trigger_phrases: {
          type: 'array',
          items: { type: 'string' },
          description: 'Frases que ativam esse workflow (ex: ["acessar aegro", "entrar no aegro"])'
        },
        target_software: { type: 'string', description: 'Software alvo (ex: aegro, firefox, libreoffice)' }
      },
      required: ['name', 'description', 'trigger_phrases']
    }
  },
  {
    name: 'request_confirmation',
    description: 'Pede confirmacao ao usuario antes de executar uma acao critica (deletar, pagar, enviar, instalar). SEMPRE use antes de acoes que possam causar dano ou custo.',
    input_schema: {
      type: 'object',
      properties: {
        action_description: { type: 'string', description: 'Descricao clara da acao que sera executada' },
        risk_level: { type: 'string', description: 'Nivel de risco: low, medium, high, critical' }
      },
      required: ['action_description', 'risk_level']
    }
  }
];

const APP_COMMANDS = {
  'firefox': 'firefox',
  'browser': 'firefox',
  'navegador': 'firefox',
  'terminal': 'xfce4-terminal',
  'files': 'thunar',
  'arquivos': 'thunar',
  'editor': 'mousepad',
  'notepad': 'mousepad',
  'calc': 'libreoffice --calc',
  'excel': 'libreoffice --calc',
  'planilha': 'libreoffice --calc',
  'writer': 'libreoffice --writer',
  'word': 'libreoffice --writer',
  'documento': 'libreoffice --writer',
};

function containerName(userId) {
  return 'rumo-desktop-' + userId.replace(/-/g, '').substring(0, 12);
}

function execInContainer(userId, cmd) {
  const name = containerName(userId);
  const { execFileSync } = require('child_process');
  try {
    const result = execFileSync('docker', ['exec', name, 'bash', '-c', `export DISPLAY=:1 && ${cmd}`], {
      encoding: 'utf-8',
      timeout: 15000
    });
    return { success: true, output: result.trim() };
  } catch (err) {
    return { success: false, error: err.message };
  }
}

// Execute a tool call from Claude
async function executeTool(userId, toolName, toolInput, supabase, sessionId) {

  switch (toolName) {
    case 'open_app': {
      const appCmd = APP_COMMANDS[toolInput.app_name.toLowerCase()];
      if (!appCmd) return `App "${toolInput.app_name}" não encontrado. Apps disponíveis: ${Object.keys(APP_COMMANDS).join(', ')}`;
      execInContainer(userId, `${appCmd} &`);
      await sleep(2000);
      // Track app usage in memory
      if (supabase) {
        await agentMemory.trackAppUsage(supabase, userId, toolInput.app_name.toLowerCase());
      }
      return `App "${toolInput.app_name}" aberto com sucesso.`;
    }

    case 'type_text': {
      const confirmCheck = agentMemory.shouldConfirm(toolName, toolInput);
      if (confirmCheck && supabase) {
        return `CONFIRMACAO_NECESSARIA: ${confirmCheck.reason}. O texto contém informação sensível. Peça confirmação ao usuário antes de continuar.`;
      }
      const text = String(toolInput.text || '');
      if (text.length > 1000) return 'Texto muito longo (máximo 1000 caracteres).';
      // Use xdotool with -- to prevent flag injection, escape single quotes
      const safeText = text.replace(/'/g, "'\\''");
      execInContainer(userId, `xdotool type --delay 30 -- '${safeText}'`);
      return `Texto digitado: "${toolInput.text}"`;
    }

    case 'press_key': {
      const key = String(toolInput.key || '');
      // Whitelist valid xdotool key names (alphanumeric, modifiers, special keys)
      if (!/^[a-zA-Z0-9+_]+$/.test(key)) return `Tecla inválida: "${key}"`;
      if (key.length > 30) return 'Combinação de teclas muito longa.';
      execInContainer(userId, `xdotool key -- ${key}`);
      return `Tecla "${key}" pressionada.`;
    }

    case 'click': {
      const x = parseInt(toolInput.x) || 0;
      const y = parseInt(toolInput.y) || 0;
      if (x < 0 || x > 1920 || y < 0 || y > 1080) return 'Coordenadas fora da tela.';
      const btn = { left: 1, right: 3, middle: 2 }[toolInput.button || 'left'] || 1;
      execInContainer(userId, `xdotool mousemove ${x} ${y} && xdotool click ${btn}`);
      return `Clique em (${x}, ${y}).`;
    }

    case 'double_click': {
      const x = parseInt(toolInput.x) || 0;
      const y = parseInt(toolInput.y) || 0;
      if (x < 0 || x > 1920 || y < 0 || y > 1080) return 'Coordenadas fora da tela.';
      execInContainer(userId, `xdotool mousemove ${x} ${y} && xdotool click --repeat 2 1`);
      return `Duplo clique em (${x}, ${y}).`;
    }

    case 'run_command': {
      const cmd = String(toolInput.command || '');
      if (cmd.length > 500) return 'Comando muito longo (máximo 500 caracteres).';
      // Block dangerous patterns
      const blocked = [
        /rm\s+(-[a-z]*)?r/i, /sudo\s+/, /apt\s+(remove|purge)/i, /pip\s+uninstall/i,
        /shutdown/i, /reboot/i, /mkfs/i, /dd\s+if=/i, />\s*\/dev\//i,
        /chmod\s+777/i, /chown\s+root/i, /curl.*\|\s*(bash|sh)/i,
        /wget.*\|\s*(bash|sh)/i, /python.*-c.*os\.(system|exec|popen)/i,
        /eval\s*\(/i, /base64\s+-d.*\|/i, /nc\s+-[a-z]*l/i,
        /\/etc\/(passwd|shadow|sudoers)/i
      ];
      for (const pattern of blocked) {
        if (pattern.test(cmd)) {
          return `BLOQUEADO: Comando potencialmente perigoso detectado. Peça confirmação ao usuário.`;
        }
      }
      const confirmCheck = agentMemory.shouldConfirm(toolName, toolInput);
      if (confirmCheck) {
        return `BLOQUEADO: ${confirmCheck.reason}. Risco: ${confirmCheck.risk}. Peça confirmação ao usuário.`;
      }
      const result = execInContainer(userId, cmd);
      if (result.success) {
        const output = (result.output || '(sem saída)').substring(0, 2000);
        return `Comando executado.\nSaída: ${output}`;
      }
      return `Erro ao executar comando: ${result.error}`;
    }

    case 'take_screenshot': {
      const path = await containerManager.takeScreenshot(userId);
      if (path) {
        return `Screenshot capturado com sucesso.`;
      }
      return 'Falha ao capturar screenshot.';
    }

    case 'wait': {
      const seconds = Math.min(Math.max(toolInput.seconds || 2, 1), 10);
      await sleep(seconds * 1000);
      return `Aguardou ${seconds} segundos.`;
    }

    // ===== NEW TOOLS =====

    case 'save_user_preference': {
      if (!supabase) return 'Erro: banco de dados nao disponivel.';
      const category = toolInput.category || 'preference';
      await agentMemory.saveMemory(supabase, userId, category, toolInput.key, toolInput.value);
      return `Preferencia salva: ${toolInput.key} = ${toolInput.value}`;
    }

    case 'get_credential': {
      if (!supabase) return 'Erro: banco de dados nao disponivel.';
      const cred = await agentMemory.getCredential(supabase, userId, toolInput.service_name);
      if (!cred) {
        return `Nenhuma credencial encontrada para "${toolInput.service_name}". Peca ao usuario o login e senha.`;
      }
      return `Credenciais recuperadas para ${cred.service_name}: usuario="${cred.username}", senha recuperada do cofre seguro. URL: ${cred.service_url || 'nao cadastrada'}`;
    }

    case 'save_credential': {
      if (!supabase) return 'Erro: banco de dados nao disponivel.';
      const saved = await agentMemory.saveCredential(
        supabase, userId,
        toolInput.service_name,
        toolInput.service_url,
        toolInput.username,
        toolInput.password
      );
      if (saved) {
        return `Credenciais para "${toolInput.service_name}" salvas com seguranca (criptografadas). Na proxima vez, basta pedir para acessar ${toolInput.service_name}.`;
      }
      return 'Erro ao salvar credenciais.';
    }

    case 'list_credentials': {
      if (!supabase) return 'Erro: banco de dados nao disponivel.';
      const creds = await agentMemory.listCredentials(supabase, userId);
      if (creds.length === 0) {
        return 'Nenhuma credencial salva. O usuario pode fornecer login/senha de sistemas que deseja acessar.';
      }
      const list = creds.map(c => `- ${c.service_name}: ${c.username} (${c.service_url || 'sem URL'})`).join('\n');
      return `Credenciais salvas:\n${list}`;
    }

    case 'save_workflow': {
      if (!supabase) return 'Erro: banco de dados nao disponivel.';
      // Get last actions from the current session to save as steps
      const wfId = await agentMemory.saveWorkflow(
        supabase, userId,
        toolInput.name,
        toolInput.description,
        toolInput.trigger_phrases,
        [], // Steps will be filled from action log
        toolInput.target_software
      );
      return `Workflow "${toolInput.name}" salvo com sucesso. Da proxima vez que o usuario pedir algo similar, vou seguir esse fluxo automaticamente.`;
    }

    case 'request_confirmation': {
      if (!supabase) return 'Erro: banco de dados nao disponivel.';
      const confId = await agentMemory.createConfirmation(
        supabase, userId,
        'pending_action',
        toolInput,
        toolInput.action_description,
        toolInput.risk_level
      );
      return `AGUARDANDO_CONFIRMACAO: "${toolInput.action_description}" (risco: ${toolInput.risk_level}). Informe ao usuario e peca para confirmar antes de continuar.`;
    }

    default:
      return `Ferramenta desconhecida: ${toolName}`;
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Build system prompt with user's memory and workflow context
async function buildSystemPrompt(supabase, userId, hasDesktop) {
  let prompt = SYSTEM_PROMPT_BASE;

  if (!hasDesktop) {
    prompt += '\n\nATENCAO: O desktop do usuario NAO esta ativo. Se ele pedir para executar acoes no computador, diga para ele conectar primeiro na aba "Tela".';
  }

  if (supabase) {
    try {
      const memoryContext = await agentMemory.getMemoryContext(supabase, userId);
      const workflowContext = await agentMemory.getWorkflowContext(supabase, userId);
      prompt += memoryContext;
      prompt += workflowContext;
    } catch (err) {
      console.error('Error loading memory context:', err.message);
    }
  }

  return prompt;
}

const SYSTEM_PROMPT_BASE = `Voce e o Rumo Agente, um assistente inteligente que controla um computador na nuvem para o usuario.

Voce tem acesso a um desktop Linux (XFCE) com os seguintes aplicativos:
- Firefox (navegador)
- LibreOffice Calc (planilhas)
- LibreOffice Writer (documentos)
- Thunar (gerenciador de arquivos)
- Mousepad (editor de texto)
- Terminal (linha de comando)

REGRAS IMPORTANTES:
1. Sempre responda em portugues do Brasil.
2. Quando o usuario pedir para fazer algo no computador, use as ferramentas disponiveis.
3. Apos executar acoes, tire um screenshot para verificar o resultado.
4. Se o desktop nao estiver ativo, peca ao usuario para conectar primeiro.
5. Explique brevemente o que esta fazendo antes de cada acao.
6. A resolucao da tela e 1280x720.
7. Para navegar na web: abra o firefox, use ctrl+l para ir na barra de endereco, digite a URL, e pressione Return.
8. Limite-se a no maximo 8 acoes por mensagem do usuario.
9. Se for uma pergunta simples que nao precisa do computador, responda normalmente sem usar ferramentas.

MEMORIA E APRENDIZADO:
10. Quando o usuario mencionar preferencias (nome da fazenda, sistema favorito, unidade de medida), salve usando save_user_preference.
11. Quando o usuario fornecer login/senha de um sistema, salve usando save_credential e informe que esta criptografado.
12. Apos completar uma tarefa com sucesso que pode ser repetida, salve como workflow usando save_workflow.
13. Use a memoria do usuario (listada abaixo) para nao fazer perguntas repetidas.
14. Quando precisar acessar um sistema, primeiro verifique se tem credenciais salvas com get_credential.

SEGURANCA E CONFIRMACAO:
15. SEMPRE peca confirmacao antes de: deletar arquivos, fazer pagamentos, enviar emails, instalar programas, executar comandos com sudo.
16. Use request_confirmation para acoes criticas.
17. NUNCA mostre senhas em texto no chat. Diga apenas "credenciais recuperadas do cofre seguro".
18. Se um comando for bloqueado por seguranca, explique ao usuario e peca autorizacao.

CONTEXTO AGROPECUARIO:
O usuario trabalha com gestao agropecuaria. Ajude com tarefas como:
- Acessar sistemas agro (Aegro, Conta Azul, Siagri, etc)
- Criar planilhas de controle (gado, custos, estoque)
- Pesquisar informacoes agricolas (precos, clima, regulamentacao)
- Gerar relatorios
- Automatizar lancamentos repetitivos`;

// Keep SYSTEM_PROMPT for backwards compat
const SYSTEM_PROMPT = SYSTEM_PROMPT_BASE;

module.exports = {
  DESKTOP_TOOLS,
  SYSTEM_PROMPT,
  SYSTEM_PROMPT_BASE,
  executeTool,
  containerName,
  buildSystemPrompt
};
