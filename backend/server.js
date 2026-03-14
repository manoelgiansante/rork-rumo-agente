require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const fs = require('fs');
const path = require('path');
const Stripe = require('stripe');
const { createClient } = require('@supabase/supabase-js');
const Anthropic = require('@anthropic-ai/sdk');
const crypto = require('crypto');
const containerManager = require('./container-manager');
const desktopAgent = require('./desktop-agent');
const agentMemory = require('./agent-memory');

const app = express();
const stripe = process.env.STRIPE_SECRET_KEY ? Stripe(process.env.STRIPE_SECRET_KEY) : null;
const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY);
const anthropic = new Anthropic({ apiKey: process.env.CLAUDE_API_KEY });

app.use(helmet());
app.use(cors({
  origin: [
    'http://216.238.111.253',
    'https://216.238.111.253',
    'https://rork-rumo-agente.vercel.app',
    'https://agente.agrorumo.com',
    /\.agrorumo\.com$/,
    /\.rumoagente\.com\.br$/
  ],
  credentials: true
}));

// Rate limiting
const rateLimit = {};
function rateLimiter(windowMs, max) {
  return (req, res, next) => {
    const key = (req.user?.id || req.ip) + ':' + req.path;
    const now = Date.now();
    if (!rateLimit[key]) rateLimit[key] = { count: 0, start: now };
    if (now - rateLimit[key].start > windowMs) {
      rateLimit[key] = { count: 0, start: now };
    }
    rateLimit[key].count++;
    if (rateLimit[key].count > max) {
      return res.status(429).json({ error: 'Muitas requisições. Tente novamente em breve.' });
    }
    next();
  };
}

// Middleware para autenticar requests do app
async function authenticateUser(req, res, next) {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'Token required' });

  const { data: { user }, error } = await supabase.auth.getUser(token);
  if (error || !user) return res.status(401).json({ error: 'Invalid token' });

  req.user = user;
  next();
}

// ============================================
// Webhook do Stripe (precisa do body raw)
// ============================================
app.post('/webhook/stripe', express.raw({ type: 'application/json' }), async (req, res) => {
  const sig = req.headers['stripe-signature'];
  let event;
  try {
    event = stripe.webhooks.constructEvent(req.body, sig, process.env.STRIPE_WEBHOOK_SECRET);
  } catch (err) {
    console.error('Webhook signature verification failed:', err.message);
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }

  switch (event.type) {
    case 'checkout.session.completed': {
      const session = event.data.object;
      const userId = session.metadata.user_id;
      const plan = session.metadata.plan;
      const credits = session.metadata.credits;

      if (credits) {
        const amount = parseInt(credits);
        await supabase.from('profiles').update({
          credits: supabase.rpc('increment_credits', { user_id: userId, amount })
        }).eq('user_id', userId);
        await supabase.from('credit_transactions').insert({
          user_id: userId, amount, type: 'purchase',
          description: `Compra de ${amount} creditos`,
          stripe_payment_id: session.payment_intent
        });
      } else if (plan) {
        const planCredits = { starter: 100, pro: 500, enterprise: 2000 }[plan] || 10;
        await supabase.from('profiles').update({ plan, credits: planCredits }).eq('user_id', userId);
        await supabase.from('subscriptions').upsert({
          user_id: userId,
          stripe_customer_id: session.customer,
          stripe_subscription_id: session.subscription,
          plan, status: 'active'
        }, { onConflict: 'user_id' });
      }
      break;
    }
    case 'customer.subscription.deleted': {
      const sub = event.data.object;
      await supabase.from('subscriptions')
        .update({ status: 'canceled', plan: 'free' })
        .eq('stripe_subscription_id', sub.id);
      const { data } = await supabase.from('subscriptions')
        .select('user_id').eq('stripe_subscription_id', sub.id).single();
      if (data) {
        await supabase.from('profiles').update({ plan: 'free', credits: 10 }).eq('user_id', data.user_id);
      }
      break;
    }
  }
  res.json({ received: true });
});

app.use(express.json());

// ============================================
// Health / Status (public)
// ============================================
app.get('/status', (req, res) => {
  res.json({
    status: 'running',
    timestamp: new Date().toISOString(),
    activeDesktops: containerManager.activeContainers.size
  });
});

// ============================================
// Desktop - Status do usuario
// ============================================
app.get('/desktop/status', authenticateUser, (req, res) => {
  const info = containerManager.getDesktopInfo(req.user.id);
  res.json(info);
});

// ============================================
// Desktop - Iniciar container do usuario
// ============================================
app.post('/start-desktop', authenticateUser, async (req, res) => {
  try {
    const result = await containerManager.startDesktop(req.user.id);

    // Build noVNC URL for the user
    const host = req.headers.host || process.env.VPS_HOST || '216.238.111.253';
    const hostname = host.split(':')[0];

    res.json({
      success: true,
      status: result.status,
      desktop: true,
      noVncUrl: `http://${hostname}/novnc/${result.noVncPort}/vnc.html?autoconnect=true&password=${result.vncPassword || process.env.VNC_DEFAULT_PASSWORD || 'changeme'}&resize=scale`,
      message: 'Desktop iniciado com sucesso'
    });
  } catch (err) {
    console.error('Start desktop error:', err);
    res.status(503).json({ error: err.message || 'Erro ao iniciar desktop' });
  }
});

// ============================================
// Desktop - Parar container do usuario
// ============================================
app.post('/stop-desktop', authenticateUser, async (req, res) => {
  try {
    await containerManager.stopDesktop(req.user.id);
    res.json({ success: true, desktop: false, message: 'Desktop desligado' });
  } catch (err) {
    console.error('Stop desktop error:', err);
    res.status(500).json({ error: 'Erro ao parar desktop' });
  }
});

// ============================================
// Desktop - Screenshot (para apps mobile)
// ============================================
app.get('/screenshot', authenticateUser, async (req, res) => {
  try {
    const screenshotPath = await containerManager.takeScreenshot(req.user.id);
    if (!screenshotPath) {
      return res.status(404).json({ error: 'Desktop nao esta ativo' });
    }

    // Send the actual image file
    res.sendFile(screenshotPath, (err) => {
      // Clean up temp file after sending
      try { fs.unlinkSync(screenshotPath); } catch (e) {}
    });
  } catch (err) {
    console.error('Screenshot error:', err);
    res.status(500).json({ error: 'Erro ao capturar tela' });
  }
});

// ============================================
// Chat com Claude + Tool Use (agente inteligente)
// ============================================
app.post('/chat', authenticateUser, rateLimiter(60000, 20), async (req, res) => {
  try {
    const { messages, conversationId } = req.body;
    if (!Array.isArray(messages) || messages.length === 0) {
      return res.status(400).json({ error: 'messages array é obrigatório' });
    }
    const userId = req.user.id;

    const { data: profile } = await supabase
      .from('profiles').select('credits').eq('user_id', userId).single();

    if (!profile || profile.credits <= 0) {
      return res.status(403).json({ error: 'Sem créditos disponíveis' });
    }

    // Check if user has an active desktop
    const desktopInfo = containerManager.getDesktopInfo(userId);
    const hasDesktop = desktopInfo.desktop;

    // Build system prompt with memory + workflow context
    const systemPrompt = await desktopAgent.buildSystemPrompt(supabase, userId, hasDesktop);

    // Check if there's a matching workflow for this message
    const userMessage = messages[messages.length - 1]?.content || '';
    const matchedWorkflow = await agentMemory.findWorkflow(supabase, userId, userMessage);

    // Session ID for action logging
    const sessionId = crypto.randomUUID();

    // Agentic loop: send to Claude, execute tools, repeat until text response
    let claudeMessages = messages.map(m => ({ role: m.role, content: m.content }));

    // If a workflow was found, add it as context
    if (matchedWorkflow) {
      const lastMsg = claudeMessages[claudeMessages.length - 1];
      if (lastMsg && lastMsg.role === 'user') {
        lastMsg.content += `\n\n[SISTEMA: Workflow "${matchedWorkflow.name}" encontrado (${matchedWorkflow.success_count}x sucesso). Descricao: ${matchedWorkflow.description}. Software: ${matchedWorkflow.target_software || 'geral'}. Siga esse fluxo se aplicavel.]`;
      }
    }

    let finalText = '';
    let actionsExecuted = [];
    let iterations = 0;
    const MAX_ITERATIONS = 10;

    while (iterations < MAX_ITERATIONS) {
      iterations++;

      const requestParams = {
        model: 'claude-sonnet-4-20250514',
        max_tokens: 2048,
        system: systemPrompt,
        messages: claudeMessages,
        tools: desktopAgent.DESKTOP_TOOLS
      };

      const response = await anthropic.messages.create(requestParams);

      // Process response content blocks
      let hasToolUse = false;
      let toolResults = [];

      for (const block of response.content) {
        if (block.type === 'text') {
          finalText += block.text;
        } else if (block.type === 'tool_use') {
          hasToolUse = true;
          console.log(`[Agent] Tool: ${block.name}(${JSON.stringify(block.input)}) for user ${userId.substring(0, 8)}`);

          const result = await desktopAgent.executeTool(userId, block.name, block.input, supabase, sessionId);
          actionsExecuted.push({ tool: block.name, input: block.input, result });

          // Log action
          await agentMemory.logAction(supabase, userId, sessionId, block.name, block.input, result, true);

          toolResults.push({
            type: 'tool_result',
            tool_use_id: block.id,
            content: result
          });
        }
      }

      // If no tool use, we're done
      if (!hasToolUse || response.stop_reason === 'end_turn') {
        break;
      }

      // Add assistant response and tool results for next iteration
      claudeMessages.push({ role: 'assistant', content: response.content });
      claudeMessages.push({ role: 'user', content: toolResults });
    }

    // Auto-learn from actions executed
    if (actionsExecuted.length > 0) {
      const learnings = agentMemory.extractLearnings(actionsExecuted, userMessage);
      for (const l of learnings) {
        await agentMemory.saveMemory(supabase, userId, l.category, l.key, l.value);
      }
    }

    // Mark matched workflow as successful
    if (matchedWorkflow && actionsExecuted.length > 0) {
      await agentMemory.markWorkflowResult(supabase, matchedWorkflow.id, true);
    }

    // Take final screenshot if actions were executed
    let screenshotTaken = false;
    if (actionsExecuted.length > 0 && hasDesktop) {
      await containerManager.takeScreenshot(userId);
      screenshotTaken = true;
    }

    // Build response message
    const assistantMessage = finalText || 'Acoes executadas com sucesso. Veja o resultado na aba Tela.';

    // Save to database
    if (conversationId) {
      await supabase.from('chat_messages').insert([
        { conversation_id: conversationId, role: 'user', content: messages[messages.length - 1].content },
        { conversation_id: conversationId, role: 'assistant', content: assistantMessage }
      ]);
    }

    // Debit credit (optimistic locking to prevent race condition)
    const { data: updateResult } = await supabase.from('profiles')
      .update({ credits: profile.credits - 1 })
      .eq('user_id', userId)
      .eq('credits', profile.credits);
    if (!updateResult || updateResult.length === 0) {
      console.warn(`[RACE] Credit deduction race detected for user ${userId.substring(0, 8)}`);
    }

    await supabase.from('credit_transactions').insert({
      user_id: userId, amount: -1, type: 'usage',
      description: actionsExecuted.length > 0 ? 'Comando do agente' : 'Mensagem de chat'
    });

    res.json({
      message: assistantMessage,
      actions: actionsExecuted.length > 0 ? actionsExecuted.map(a => a.tool) : undefined,
      screenshotAvailable: screenshotTaken
    });
  } catch (err) {
    console.error('Chat error:', err);
    res.status(500).json({ error: 'Erro ao processar mensagem' });
  }
});

// ============================================
// Executar comando via agente no desktop do usuario
// ============================================
app.post('/execute', authenticateUser, rateLimiter(60000, 30), async (req, res) => {
  const { action, appContext, parameters } = req.body;
  const userId = req.user.id;

  const info = containerManager.getDesktopInfo(userId);
  if (!info.desktop) {
    return res.status(400).json({ error: 'Desktop não está ativo. Inicie o desktop primeiro.' });
  }

  const name = 'rumo-desktop-' + userId.replace(/-/g, '').substring(0, 12);

  // Strict whitelist for apps - NEVER interpolate user input into shell
  const SAFE_APPS = {
    'firefox': 'firefox',
    'browser': 'firefox',
    'terminal': 'xfce4-terminal',
    'editor': 'mousepad',
    'files': 'thunar',
    'arquivos': 'thunar',
    'calc': 'libreoffice --calc',
    'excel': 'libreoffice --calc',
    'writer': 'libreoffice --writer',
    'word': 'libreoffice --writer',
  };

  try {
    const { execFileSync } = require('child_process');
    let args;

    if (action === 'type') {
      const text = String(parameters?.text || '');
      if (text.length > 500) return res.status(400).json({ error: 'Texto muito longo (max 500 chars)' });
      args = ['exec', name, 'bash', '-c', `export DISPLAY=:1 && xdotool type --delay 50 -- '${text.replace(/'/g, "'\\''")}'`];
    } else if (action === 'click') {
      const x = parseInt(parameters?.x) || 640;
      const y = parseInt(parameters?.y) || 360;
      if (x < 0 || x > 1920 || y < 0 || y > 1080) return res.status(400).json({ error: 'Coordenadas inválidas' });
      args = ['exec', name, 'bash', '-c', `export DISPLAY=:1 && xdotool mousemove ${x} ${y} && xdotool click 1`];
    } else if (action === 'open') {
      const app = (appContext || parameters?.app || 'firefox').toLowerCase();
      const appCmd = SAFE_APPS[app];
      if (!appCmd) return res.status(400).json({ error: `App "${app}" não está na lista permitida. Apps: ${Object.keys(SAFE_APPS).join(', ')}` });
      args = ['exec', name, 'bash', '-c', `export DISPLAY=:1 && ${appCmd} &`];
    } else {
      return res.status(400).json({ error: 'Ação não reconhecida' });
    }

    execFileSync('docker', args, { timeout: 10000 });

    await new Promise(r => setTimeout(r, 500));
    const screenshotPath = await containerManager.takeScreenshot(userId);

    res.json({
      success: true,
      message: `Comando "${action}" executado com sucesso.`,
      screenshot_url: screenshotPath ? '/screenshot' : null,
      task_id: crypto.randomUUID()
    });
  } catch (err) {
    console.error('Execute error:', err);
    res.status(500).json({ error: 'Erro ao executar comando' });
  }
});

// ============================================
// Criar sessao de checkout Stripe (assinatura)
// ============================================
app.post('/create-checkout', authenticateUser, async (req, res) => {
  try {
    const { plan } = req.body;
    const userId = req.user.id;
    const email = req.user.email;

    const prices = {
      starter: 4990,
      pro: 14990,
      enterprise: 49990
    };

    if (!prices[plan]) return res.status(400).json({ error: 'Plano invalido' });

    const session = await stripe.checkout.sessions.create({
      payment_method_types: ['card'],
      mode: 'subscription',
      customer_email: email,
      metadata: { user_id: userId, plan },
      line_items: [{
        price_data: {
          currency: 'brl',
          product_data: { name: `Rumo Agente - Plano ${plan.charAt(0).toUpperCase() + plan.slice(1)}` },
          recurring: { interval: 'month' },
          unit_amount: prices[plan],
        },
        quantity: 1,
      }],
      success_url: 'https://vps.agrorumo.com/#success',
      cancel_url: 'https://vps.agrorumo.com/#cancel',
    });

    res.json({ url: session.url, sessionId: session.id });
  } catch (err) {
    console.error('Checkout error:', err);
    res.status(500).json({ error: 'Erro ao criar checkout. Tente novamente.' });
  }
});

// ============================================
// Comprar creditos extras
// ============================================
app.post('/buy-credits', authenticateUser, async (req, res) => {
  try {
    const { amount } = req.body;
    const userId = req.user.id;
    const email = req.user.email;

    const packs = { 50: 1990, 200: 5990, 500: 11990 };
    const price = packs[amount];
    if (!price) return res.status(400).json({ error: 'Pacote invalido. Opcoes: 50, 200 ou 500' });

    const session = await stripe.checkout.sessions.create({
      payment_method_types: ['card'],
      mode: 'payment',
      customer_email: email,
      metadata: { user_id: userId, credits: String(amount) },
      line_items: [{
        price_data: {
          currency: 'brl',
          product_data: { name: `${amount} Creditos Extras - Rumo Agente` },
          unit_amount: price,
        },
        quantity: 1,
      }],
      success_url: 'https://vps.agrorumo.com/#success',
      cancel_url: 'https://vps.agrorumo.com/#cancel',
    });

    res.json({ url: session.url });
  } catch (err) {
    console.error('Buy credits error:', err);
    res.status(500).json({ error: 'Erro ao processar compra. Tente novamente.' });
  }
});

// ============================================
// Perfil do usuario
// ============================================
app.get('/profile', authenticateUser, async (req, res) => {
  const { data, error } = await supabase
    .from('profiles').select('*').eq('user_id', req.user.id).single();
  if (error) return res.status(500).json({ error: error.message });
  res.json(data);
});

// ============================================
// Credenciais - Listar servicos salvos
// ============================================
app.get('/credentials', authenticateUser, async (req, res) => {
  try {
    const creds = await agentMemory.listCredentials(supabase, req.user.id);
    res.json(creds);
  } catch (err) {
    res.status(500).json({ error: 'Erro ao listar credenciais' });
  }
});

// ============================================
// Credenciais - Salvar nova credencial
// ============================================
app.post('/credentials', authenticateUser, async (req, res) => {
  try {
    const { service_name, service_url, username, password, extra_fields } = req.body;
    if (!service_name || !username || !password) {
      return res.status(400).json({ error: 'service_name, username e password sao obrigatorios' });
    }
    const saved = await agentMemory.saveCredential(supabase, req.user.id, service_name, service_url, username, password, extra_fields);
    res.json({ success: saved, message: saved ? 'Credencial salva com seguranca' : 'Erro ao salvar' });
  } catch (err) {
    res.status(500).json({ error: 'Erro ao salvar credencial' });
  }
});

// ============================================
// Credenciais - Deletar
// ============================================
app.delete('/credentials/:serviceName', authenticateUser, async (req, res) => {
  try {
    const deleted = await agentMemory.deleteCredential(supabase, req.user.id, req.params.serviceName);
    res.json({ success: deleted });
  } catch (err) {
    res.status(500).json({ error: 'Erro ao deletar credencial' });
  }
});

// ============================================
// Confirmacoes pendentes
// ============================================
app.get('/confirmations', authenticateUser, async (req, res) => {
  try {
    const pending = await agentMemory.getPendingConfirmations(supabase, req.user.id);
    res.json(pending);
  } catch (err) {
    res.status(500).json({ error: 'Erro ao buscar confirmacoes' });
  }
});

// ============================================
// Resolver confirmacao (aprovar/rejeitar)
// ============================================
app.post('/confirmations/:id/resolve', authenticateUser, async (req, res) => {
  try {
    const { approved } = req.body;
    const result = await agentMemory.resolveConfirmation(supabase, req.params.id, approved, req.user.id);
    if (!result) {
      return res.status(404).json({ error: 'Confirmação não encontrada ou já resolvida' });
    }
    res.json({ success: true, status: approved ? 'approved' : 'rejected' });
  } catch (err) {
    res.status(500).json({ error: 'Erro ao resolver confirmacao' });
  }
});

// ============================================
// Memoria do agente - ver aprendizados
// ============================================
app.get('/memory', authenticateUser, async (req, res) => {
  try {
    const { data } = await supabase
      .from('agent_memory')
      .select('category, key, value, usage_count, updated_at')
      .eq('user_id', req.user.id)
      .order('updated_at', { ascending: false })
      .limit(100);
    res.json(data || []);
  } catch (err) {
    res.status(500).json({ error: 'Erro ao buscar memoria' });
  }
});

// ============================================
// Memoria - deletar item
// ============================================
app.delete('/memory/:category/:key', authenticateUser, async (req, res) => {
  try {
    await supabase.from('agent_memory')
      .delete()
      .eq('user_id', req.user.id)
      .eq('category', req.params.category)
      .eq('key', req.params.key);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Erro ao deletar memoria' });
  }
});

// ============================================
// Workflows aprendidos
// ============================================
app.get('/workflows', authenticateUser, async (req, res) => {
  try {
    const { data } = await supabase
      .from('learned_workflows')
      .select('id, name, description, trigger_phrases, target_software, success_count, fail_count, is_active, updated_at')
      .eq('user_id', req.user.id)
      .order('success_count', { ascending: false });
    res.json(data || []);
  } catch (err) {
    res.status(500).json({ error: 'Erro ao buscar workflows' });
  }
});

// ============================================
// Workflow - deletar
// ============================================
app.delete('/workflows/:id', authenticateUser, async (req, res) => {
  try {
    await supabase.from('learned_workflows')
      .delete()
      .eq('id', req.params.id)
      .eq('user_id', req.user.id);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Erro ao deletar workflow' });
  }
});

// ============================================
// Historico de acoes do agente
// ============================================
app.get('/action-log', authenticateUser, async (req, res) => {
  try {
    const { data } = await supabase
      .from('agent_action_log')
      .select('tool_name, tool_input, tool_result, success, created_at')
      .eq('user_id', req.user.id)
      .order('created_at', { ascending: false })
      .limit(50);
    res.json(data || []);
  } catch (err) {
    res.status(500).json({ error: 'Erro ao buscar historico' });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, '127.0.0.1', async () => {
  console.log(`Rumo Agente API rodando na porta ${PORT}`);
  console.log(`Desktops ativos: ${containerManager.activeContainers.size}`);

  // Check if memory tables exist
  const tables = ['agent_memory', 'secure_credentials', 'learned_workflows', 'agent_action_log', 'pending_confirmations'];
  for (const t of tables) {
    const { error } = await supabase.from(t).select('*').limit(1);
    if (error) {
      console.warn(`[WARN] Table "${t}" missing - run setup-tables.sql in Supabase Dashboard`);
    }
  }
});
