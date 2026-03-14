import Stripe from 'stripe';
import { createClient } from '@supabase/supabase-js';

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY);
const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY);

export const config = {
  api: { bodyParser: false }
};

async function buffer(readable) {
  const chunks = [];
  for await (const chunk of readable) {
    chunks.push(typeof chunk === 'string' ? Buffer.from(chunk) : chunk);
  }
  return Buffer.concat(chunks);
}

export default async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  const buf = await buffer(req);
  const sig = req.headers['stripe-signature'];

  let event;
  try {
    event = stripe.webhooks.constructEvent(buf, sig, process.env.STRIPE_WEBHOOK_SECRET);
  } catch (err) {
    console.error('Webhook signature failed:', err.message);
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }

  switch (event.type) {
    case 'checkout.session.completed': {
      const session = event.data.object;
      const userId = session.metadata?.user_id;
      const plan = session.metadata?.plan;
      const credits = session.metadata?.credits;

      if (!userId) {
        console.error('Webhook: checkout.session.completed missing user_id in metadata');
        break;
      }

      if (credits) {
        const amount = parseInt(credits);
        if (isNaN(amount) || amount <= 0 || amount > 10000) {
          console.error('Webhook: invalid credits amount:', credits);
          break;
        }
        const { data: profile } = await supabase
          .from('profiles').select('credits').eq('user_id', userId).single();
        await supabase.from('profiles')
          .update({ credits: (profile?.credits || 0) + amount })
          .eq('user_id', userId);
        await supabase.from('credit_transactions').insert({
          user_id: userId, amount, type: 'purchase',
          description: `Compra de ${amount} créditos`,
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
      const { data } = await supabase.from('subscriptions')
        .select('user_id').eq('stripe_subscription_id', sub.id).single();
      if (data) {
        await supabase.from('profiles').update({ plan: 'free', credits: 10 }).eq('user_id', data.user_id);
        await supabase.from('subscriptions')
          .update({ status: 'canceled', plan: 'free' })
          .eq('stripe_subscription_id', sub.id);
      }
      break;
    }
  }

  res.json({ received: true });
}
