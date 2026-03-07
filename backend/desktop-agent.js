const { execSync } = require('child_process');
const containerManager = require('./container-manager');

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
  const fullCmd = `docker exec ${name} bash -c "export DISPLAY=:1 && ${cmd.replace(/"/g, '\\"')}"`;
  try {
    const result = execSync(fullCmd, { encoding: 'utf-8', timeout: 15000 });
    return { success: true, output: result.trim() };
  } catch (err) {
    return { success: false, error: err.message };
  }
}

// Execute a tool call from Claude
async function executeTool(userId, toolName, toolInput) {
  const name = containerName(userId);

  switch (toolName) {
    case 'open_app': {
      const appCmd = APP_COMMANDS[toolInput.app_name.toLowerCase()] || toolInput.app_name;
      execInContainer(userId, `${appCmd} &`);
      await sleep(2000);
      return `App "${toolInput.app_name}" aberto com sucesso.`;
    }

    case 'type_text': {
      const text = toolInput.text.replace(/'/g, "\\'");
      execInContainer(userId, `xdotool type --delay 30 '${text}'`);
      return `Texto digitado: "${toolInput.text}"`;
    }

    case 'press_key': {
      // Convert common key names to xdotool format
      let key = toolInput.key
        .replace('ctrl+', 'ctrl+')
        .replace('alt+', 'alt+')
        .replace('shift+', 'shift+');
      execInContainer(userId, `xdotool key ${key}`);
      return `Tecla "${toolInput.key}" pressionada.`;
    }

    case 'click': {
      const btn = { left: 1, right: 3, middle: 2 }[toolInput.button || 'left'] || 1;
      execInContainer(userId, `xdotool mousemove ${toolInput.x} ${toolInput.y} && xdotool click ${btn}`);
      return `Clique em (${toolInput.x}, ${toolInput.y}).`;
    }

    case 'double_click': {
      execInContainer(userId, `xdotool mousemove ${toolInput.x} ${toolInput.y} && xdotool click --repeat 2 1`);
      return `Duplo clique em (${toolInput.x}, ${toolInput.y}).`;
    }

    case 'run_command': {
      const result = execInContainer(userId, toolInput.command);
      if (result.success) {
        return `Comando executado.\nSaida: ${result.output || '(sem saida)'}`;
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

    default:
      return `Ferramenta desconhecida: ${toolName}`;
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

const SYSTEM_PROMPT = `Voce e o Rumo Agente, um assistente inteligente que controla um computador na nuvem para o usuario.

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

CONTEXTO AGROPECUARIO:
O usuario trabalha com gestao agropecuaria. Ajude com tarefas como:
- Acessar sistemas agro (Aegro, Conta Azul, etc)
- Criar planilhas de controle
- Pesquisar informacoes agricolas
- Gerar relatorios`;

module.exports = {
  DESKTOP_TOOLS,
  SYSTEM_PROMPT,
  executeTool,
  containerName
};
