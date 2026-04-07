import Stripe from 'stripe';
import { createClient } from '@supabase/supabase-js';

const { STRIPE_SECRET_KEY, SUPABASE_URL, SUPABASE_SERVICE_KEY } = process.env;
if (!STRIPE_SECRET_KEY || !SUPABASE_URL || !SUPABASE_SERVICE_KEY) {
  throw new Error('Missing required env vars: STRIPE_SECRET_KEY, SUPABASE_URL, SUPABASE_SERVICE_KEY');
}

const stripe = new Stripe(STRIPE_SECRET_KEY);
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

export default async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'Token required' });

  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) return res.status(401).json({ error: 'Invalid token' });

  try {
    const { plan } = req.body;
    const prices = { starter: 4990, pro: 14990, enterprise: 49990 };

    if (!prices[plan]) return res.status(400).json({ error: 'Plano inválido' });

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
      success_url: 'https://agente.agrorumo.com/success?session_id={CHECKOUT_SESSION_ID}',
      cancel_url: 'https://agente.agrorumo.com/cancel',
    });

    res.json({ url: session.url, sessionId: session.id });
  } catch (err) {
    console.error('Checkout error:', err);
    res.status(500).json({ error: err.message });
  }
}
