require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const Stripe = require('stripe');
const { createClient } = require('@supabase/supabase-js');
const Anthropic = require('@anthropic-ai/sdk');

const app = express();
const stripe = Stripe(process.env.STRIPE_SECRET_KEY);
const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY);
const anthropic = new Anthropic({ apiKey: process.env.CLAUDE_API_KEY });

app.use(helmet());
app.use(cors());

// Middleware para autenticar requests do app iOS
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
        // Compra de créditos extras
        const amount = parseInt(credits);
        await supabase.from('profiles').update({
          credits: supabase.rpc('increment_credits', { user_id: userId, amount })
        }).eq('id', userId);
        await supabase.from('credit_transactions').insert({
          user_id: userId, amount, type: 'purchase',
          description: `Compra de ${amount} créditos`,
          stripe_payment_id: session.payment_intent
        });
      } else if (plan) {
        // Assinatura de plano
        const planCredits = { starter: 100, pro: 500, enterprise: 2000 }[plan] || 10;
        await supabase.from('profiles').update({ plan, credits: planCredits }).eq('id', userId);
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
      // Reset user to free plan
      const { data } = await supabase.from('subscriptions')
        .select('user_id').eq('stripe_subscription_id', sub.id).single();
      if (data) {
        await supabase.from('profiles').update({ plan: 'free', credits: 10 }).eq('id', data.user_id);
      }
      break;
    }
  }
  res.json({ received: true });
});

app.use(express.json());

// ============================================
// Health check
// ============================================
app.get('/status', (req, res) => {
  res.json({ status: 'online', timestamp: new Date().toISOString() });
});

// ============================================
// Chat com Claude (proxy seguro - key não fica no app)
// ============================================
app.post('/chat', authenticateUser, async (req, res) => {
  try {
    const { messages, conversationId } = req.body;
    const userId = req.user.id;

    // Verificar créditos
    const { data: profile } = await supabase
      .from('profiles').select('credits').eq('id', userId).single();

    if (!profile || profile.credits <= 0) {
      return res.status(403).json({ error: 'Sem créditos disponíveis' });
    }

    // Chamar Claude
    const response = await anthropic.messages.create({
      model: 'claude-sonnet-4-20250514',
      max_tokens: 2048,
      system: 'Você é o Rumo Agente, um assistente inteligente para gestão agropecuária. Responda em português do Brasil de forma clara e objetiva.',
      messages: messages.map(m => ({ role: m.role, content: m.content }))
    });

    const assistantMessage = response.content[0].text;

    // Salvar mensagem no banco
    if (conversationId) {
      await supabase.from('chat_messages').insert([
        { conversation_id: conversationId, role: 'user', content: messages[messages.length - 1].content },
        { conversation_id: conversationId, role: 'assistant', content: assistantMessage }
      ]);
    }

    // Debitar 1 crédito
    await supabase.from('profiles')
      .update({ credits: profile.credits - 1 })
      .eq('id', userId);

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
// Criar sessão de checkout Stripe (assinatura)
// ============================================
app.post('/create-checkout', authenticateUser, async (req, res) => {
  try {
    const { plan } = req.body;
    const userId = req.user.id;
    const email = req.user.email;

    const prices = {
      starter: 4990,   // R$ 49,90
      pro: 14990,       // R$ 149,90
      enterprise: 49990 // R$ 499,90
    };

    if (!prices[plan]) return res.status(400).json({ error: 'Plano inválido' });

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
      success_url: 'https://rumoagente.com.br/success?session_id={CHECKOUT_SESSION_ID}',
      cancel_url: 'https://rumoagente.com.br/cancel',
    });

    res.json({ url: session.url, sessionId: session.id });
  } catch (err) {
    console.error('Checkout error:', err);
    res.status(500).json({ error: err.message });
  }
});

// ============================================
// Comprar créditos extras
// ============================================
app.post('/buy-credits', authenticateUser, async (req, res) => {
  try {
    const { amount } = req.body;
    const userId = req.user.id;
    const email = req.user.email;

    const packs = { 50: 1990, 200: 5990, 500: 11990 };
    const price = packs[amount];
    if (!price) return res.status(400).json({ error: 'Pacote inválido. Opções: 50, 200 ou 500' });

    const session = await stripe.checkout.sessions.create({
      payment_method_types: ['card'],
      mode: 'payment',
      customer_email: email,
      metadata: { user_id: userId, credits: String(amount) },
      line_items: [{
        price_data: {
          currency: 'brl',
          product_data: { name: `${amount} Créditos Extras - Rumo Agente` },
          unit_amount: price,
        },
        quantity: 1,
      }],
      success_url: 'https://rumoagente.com.br/success?session_id={CHECKOUT_SESSION_ID}',
      cancel_url: 'https://rumoagente.com.br/cancel',
    });

    res.json({ url: session.url });
  } catch (err) {
    console.error('Buy credits error:', err);
    res.status(500).json({ error: err.message });
  }
});

// ============================================
// Executar comando do agente (placeholder)
// ============================================
app.post('/execute', authenticateUser, async (req, res) => {
  const { action, appContext, parameters } = req.body;
  // TODO: Integrar com daemon real que controla a VM
  res.json({
    success: true,
    message: `Comando "${action}" executado com sucesso no ${appContext || 'sistema'}.`,
    screenshot_url: null,
    task_id: require('crypto').randomUUID()
  });
});

// ============================================
// Screenshot (placeholder)
// ============================================
app.get('/screenshot', authenticateUser, (req, res) => {
  // TODO: Capturar screenshot real da VM
  res.json({ screenshot_url: null });
});

// ============================================
// Perfil do usuário
// ============================================
app.get('/profile', authenticateUser, async (req, res) => {
  const { data, error } = await supabase
    .from('profiles').select('*').eq('id', req.user.id).single();
  if (error) return res.status(500).json({ error: error.message });
  res.json(data);
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Rumo Agente API rodando na porta ${PORT}`);
});
