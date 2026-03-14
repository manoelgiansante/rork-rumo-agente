import Stripe from 'stripe';
import { createClient } from '@supabase/supabase-js';

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY);
const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY);

export default async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'Token required' });

  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) return res.status(401).json({ error: 'Invalid token' });

  try {
    const { plan } = req.body;
    if (typeof plan !== 'string') return res.status(400).json({ error: 'Plano invalido' });
    const prices = { starter: 4990, pro: 14990, enterprise: 49990 };

    if (!prices[plan]) return res.status(400).json({ error: 'Plano invalido' });

    const session = await stripe.checkout.sessions.create({
      payment_method_types: ['card'],
      mode: 'subscription',
      customer_email: user.email,
      metadata: { user_id: user.id, plan },
      line_items: [{
        price_data: {
          currency: 'brl',
          product_data: { name: `Rumo Agente - Plano ${plan.charAt(0).toUpperCase() + plan.slice(1)}` },
          recurring: { interval: 'month' },
          unit_amount: prices[plan],
        },
        quantity: 1,
      }],
      success_url: 'https://rork-rumo-agente.vercel.app/success?session_id={CHECKOUT_SESSION_ID}',
      cancel_url: 'https://rork-rumo-agente.vercel.app/cancel',
    });

    res.json({ url: session.url, sessionId: session.id });
  } catch (err) {
    console.error('Checkout error:', err);
    console.error('Stripe error details:', err.message);
    res.status(500).json({ error: 'Erro ao criar sessao de checkout. Tente novamente.' });
  }
}
