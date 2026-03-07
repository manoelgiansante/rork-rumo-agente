require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const fs = require('fs');
const path = require('path');
const Stripe = require('stripe');
const { createClient } = require('@supabase/supabase-js');
const Anthropic = require('@anthropic-ai/sdk');
const containerManager = require('./container-manager');

const app = express();
const stripe = Stripe(process.env.STRIPE_SECRET_KEY);
const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY);
const anthropic = new Anthropic({ apiKey: process.env.CLAUDE_API_KEY });

app.use(helmet());
app.use(cors());

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
      noVncUrl: `http://${hostname}:${result.noVncPort}/vnc.html?autoconnect=true&password=rumoagente&resize=scale`,
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
// Chat com Claude (proxy seguro)
// ============================================
app.post('/chat', authenticateUser, async (req, res) => {
  try {
    const { messages, conversationId } = req.body;
    const userId = req.user.id;

    const { data: profile } = await supabase
      .from('profiles').select('credits').eq('user_id', userId).single();

    if (!profile || profile.credits <= 0) {
      return res.status(403).json({ error: 'Sem creditos disponiveis' });
    }

    const response = await anthropic.messages.create({
      model: 'claude-sonnet-4-20250514',
      max_tokens: 2048,
      system: 'Voce e o Rumo Agente, um assistente inteligente para gestao agropecuaria. Responda em portugues do Brasil de forma clara e objetiva.',
      messages: messages.map(m => ({ role: m.role, content: m.content }))
    });

    const assistantMessage = response.content[0].text;

    if (conversationId) {
      await supabase.from('chat_messages').insert([
        { conversation_id: conversationId, role: 'user', content: messages[messages.length - 1].content },
        { conversation_id: conversationId, role: 'assistant', content: assistantMessage }
      ]);
    }

    await supabase.from('profiles')
      .update({ credits: profile.credits - 1 })
      .eq('user_id', userId);

    await supabase.from('credit_transactions').insert({
      user_id: userId, amount: -1, type: 'usage',
      description: 'Mensagem de chat'
    });

    res.json({ message: assistantMessage });
  } catch (err) {
    console.error('Chat error:', err);
    res.status(500).json({ error: 'Erro ao processar mensagem' });
  }
});

// ============================================
// Executar comando via agente no desktop do usuario
// ============================================
app.post('/execute', authenticateUser, async (req, res) => {
  const { action, appContext, parameters } = req.body;
  const userId = req.user.id;

  const info = containerManager.getDesktopInfo(userId);
  if (!info.desktop) {
    return res.status(400).json({ error: 'Desktop nao esta ativo. Inicie o desktop primeiro.' });
  }

  const name = 'rumo-desktop-' + userId.replace(/-/g, '').substring(0, 12);

  try {
    // Use xdotool to execute actions inside the user's container
    const { execSync } = require('child_process');
    let cmd;

    if (action === 'type') {
      cmd = `docker exec ${name} bash -c "export DISPLAY=:1 && xdotool type --delay 50 '${(parameters?.text || '').replace(/'/g, "\\'")}'"`;
    } else if (action === 'click') {
      const x = parameters?.x || 640;
      const y = parameters?.y || 360;
      cmd = `docker exec ${name} bash -c "export DISPLAY=:1 && xdotool mousemove ${x} ${y} && xdotool click 1"`;
    } else if (action === 'open') {
      const app = (appContext || parameters?.app || 'firefox').toLowerCase();
      const appCmds = {
        'firefox': 'firefox &',
        'browser': 'firefox &',
        'terminal': 'xfce4-terminal &',
        'editor': 'mousepad &',
        'files': 'thunar &',
        'arquivos': 'thunar &',
        'calc': 'libreoffice --calc &',
        'excel': 'libreoffice --calc &',
        'writer': 'libreoffice --writer &',
        'word': 'libreoffice --writer &',
      };
      const appCmd = appCmds[app] || `${app} &`;
      cmd = `docker exec ${name} bash -c "export DISPLAY=:1 && ${appCmd}"`;
    } else {
      return res.status(400).json({ error: 'Acao nao reconhecida' });
    }

    execSync(cmd, { timeout: 10000 });

    // Take screenshot after action
    await new Promise(r => setTimeout(r, 500));
    const screenshotPath = await containerManager.takeScreenshot(userId);

    res.json({
      success: true,
      message: `Comando "${action}" executado com sucesso.`,
      screenshot_url: screenshotPath ? '/screenshot' : null,
      task_id: require('crypto').randomUUID()
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
      success_url: 'https://rork-rumo-agente.vercel.app/#success',
      cancel_url: 'https://rork-rumo-agente.vercel.app/#cancel',
    });

    res.json({ url: session.url, sessionId: session.id });
  } catch (err) {
    console.error('Checkout error:', err);
    res.status(500).json({ error: err.message });
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
      success_url: 'https://rork-rumo-agente.vercel.app/#success',
      cancel_url: 'https://rork-rumo-agente.vercel.app/#cancel',
    });

    res.json({ url: session.url });
  } catch (err) {
    console.error('Buy credits error:', err);
    res.status(500).json({ error: err.message });
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

const PORT = process.env.PORT || 3000;
app.listen(PORT, '127.0.0.1', () => {
  console.log(`Rumo Agente API rodando na porta ${PORT}`);
  console.log(`Desktops ativos: ${containerManager.activeContainers.size}`);
});
