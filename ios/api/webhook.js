import Stripe from "stripe";
import { createClient } from "@supabase/supabase-js";

const {
  STRIPE_SECRET_KEY,
  STRIPE_WEBHOOK_SECRET,
  SUPABASE_URL,
  SUPABASE_SERVICE_KEY,
} = process.env;
if (
  !STRIPE_SECRET_KEY ||
  !STRIPE_WEBHOOK_SECRET ||
  !SUPABASE_URL ||
  !SUPABASE_SERVICE_KEY
) {
  throw new Error(
    "Missing required env vars: STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, SUPABASE_URL, SUPABASE_SERVICE_KEY",
  );
}

const stripe = new Stripe(STRIPE_SECRET_KEY);
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

export const config = {
  api: { bodyParser: false },
};

async function buffer(readable) {
  const chunks = [];
  for await (const chunk of readable) {
    chunks.push(typeof chunk === "string" ? Buffer.from(chunk) : chunk);
  }
  return Buffer.concat(chunks);
}

// Simple in-memory idempotency (Vercel serverless may restart, but prevents within-instance dupes)
const processedEvents = new Set();

export default async function handler(req, res) {
  if (req.method !== "POST")
    return res.status(405).json({ error: "Method not allowed" });

  const buf = await buffer(req);
  const sig = req.headers["stripe-signature"];

  let event;
  try {
    event = stripe.webhooks.constructEvent(buf, sig, STRIPE_WEBHOOK_SECRET);
  } catch (err) {
    console.error("Webhook signature failed:", err.message);
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }

  // Idempotency check
  if (processedEvents.has(event.id)) {
    return res.json({ received: true, duplicate: true });
  }
  processedEvents.add(event.id);
  // Limit set size to prevent memory leak in long-running instances
  if (processedEvents.size > 1000) {
    const first = processedEvents.values().next().value;
    processedEvents.delete(first);
  }

  switch (event.type) {
    case "checkout.session.completed": {
      const session = event.data.object;
      const userId = session.metadata.user_id;
      const plan = session.metadata.plan;
      const credits = session.metadata.credits;

      if (credits) {
        const amount = parseInt(credits);
        if (isNaN(amount) || amount <= 0) {
          console.error("Invalid credits amount:", credits);
          break;
        }
        // Atomic credit increment via RPC
        const { error: rpcError } = await supabase.rpc("increment_credits", {
          p_user_id: userId,
          p_amount: amount,
        });
        if (rpcError) {
          // Fallback: read-then-update (less safe but better than failing silently)
          console.error(
            "RPC increment_credits failed, using fallback:",
            rpcError.message,
          );
          const { data: profile } = await supabase
            .from("profiles")
            .select("credits")
            .eq("user_id", userId)
            .single();
          await supabase
            .from("profiles")
            .update({ credits: (profile?.credits || 0) + amount })
            .eq("user_id", userId);
        }
        await supabase.from("credit_transactions").insert({
          user_id: userId,
          amount,
          type: "purchase",
          description: `Compra de ${amount} créditos`,
          stripe_payment_id: session.payment_intent,
        });
      } else if (plan) {
        const planCredits =
          { starter: 100, pro: 500, enterprise: 2000 }[plan] || 10;
        await supabase
          .from("profiles")
          .update({ plan, credits: planCredits })
          .eq("user_id", userId);
        await supabase.from("subscriptions").upsert(
          {
            user_id: userId,
            stripe_customer_id: session.customer,
            stripe_subscription_id: session.subscription,
            plan,
            status: "active",
          },
          { onConflict: "user_id" },
        );
      }
      break;
    }
    case "customer.subscription.deleted": {
      const sub = event.data.object;
      const { data } = await supabase
        .from("subscriptions")
        .select("user_id")
        .eq("stripe_subscription_id", sub.id)
        .single();
      if (data) {
        await supabase
          .from("profiles")
          .update({ plan: "free", credits: 10 })
          .eq("user_id", data.user_id);
        await supabase
          .from("subscriptions")
          .update({ status: "canceled", plan: "free" })
          .eq("stripe_subscription_id", sub.id);
      }
      break;
    }
  }

  res.json({ received: true });
}
