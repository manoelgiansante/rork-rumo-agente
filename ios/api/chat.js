import Anthropic from "@anthropic-ai/sdk";
import { createClient } from "@supabase/supabase-js";

const { CLAUDE_API_KEY, SUPABASE_URL, SUPABASE_SERVICE_KEY } = process.env;
if (!CLAUDE_API_KEY || !SUPABASE_URL || !SUPABASE_SERVICE_KEY) {
  throw new Error("Missing required env vars: CLAUDE_API_KEY, SUPABASE_URL, SUPABASE_SERVICE_KEY");
}

const anthropic = new Anthropic({ apiKey: CLAUDE_API_KEY });
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

export default async function handler(req, res) {
  if (req.method !== "POST")
    return res.status(405).json({ error: "Method not allowed" });

  const token = req.headers.authorization?.replace("Bearer ", "");
  if (!token) return res.status(401).json({ error: "Token required" });

  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser(token);
  if (authError || !user)
    return res.status(401).json({ error: "Invalid token" });

  try {
    const { messages, conversationId } = req.body;

    // Validate messages is an array
    if (!Array.isArray(messages) || messages.length === 0) {
      return res
        .status(400)
        .json({ error: "messages deve ser um array nao vazio" });
    }

    // Limit messages array to max 50 items
    if (messages.length > 50) {
      return res
        .status(400)
        .json({ error: "Maximo de 50 mensagens por requisicao" });
    }

    // Validate each message
    const allowedRoles = ["user", "assistant"];
    for (let i = 0; i < messages.length; i++) {
      const msg = messages[i];

      if (!msg || typeof msg !== "object") {
        return res.status(400).json({ error: `Mensagem ${i} invalida` });
      }

      if (!allowedRoles.includes(msg.role)) {
        return res
          .status(400)
          .json({
            error: `Mensagem ${i}: role deve ser "user" ou "assistant"`,
          });
      }

      if (typeof msg.content !== "string" || msg.content.trim().length === 0) {
        return res
          .status(400)
          .json({
            error: `Mensagem ${i}: content deve ser uma string nao vazia`,
          });
      }

      if (msg.content.length > 10000) {
        return res
          .status(400)
          .json({
            error: `Mensagem ${i}: content excede o limite de 10000 caracteres`,
          });
      }
    }

    const { data: profile } = await supabase
      .from("profiles")
      .select("credits")
      .eq("user_id", user.id)
      .single();

    if (!profile || profile.credits <= 0) {
      return res.status(403).json({ error: "Sem créditos disponíveis" });
    }

    // Debit credit BEFORE the LLM call to prevent free usage on debit failure
    const { error: rpcError } = await supabase.rpc("decrement_credits", {
      p_user_id: user.id,
      p_amount: 1,
    });
    if (rpcError) {
      // Fallback to optimistic update
      await supabase
        .from("profiles")
        .update({ credits: Math.max(0, profile.credits - 1) })
        .eq("user_id", user.id);
    }

    await supabase.from("credit_transactions").insert({
      user_id: user.id,
      amount: -1,
      type: "usage",
      description: "Mensagem de chat",
    });

    const response = await anthropic.messages.create({
      model: "claude-sonnet-4-20250514",
      max_tokens: 2048,
      system:
        "Você é o Rumo Agente, um assistente inteligente para gestão agropecuária. Responda em português do Brasil de forma clara e objetiva.",
      messages: messages.map((m) => ({ role: m.role, content: m.content })),
    });

    const assistantMessage = response.content[0].text;

    if (conversationId) {
      await supabase.from("chat_messages").insert([
        {
          conversation_id: conversationId,
          role: "user",
          content: messages[messages.length - 1].content,
        },
        {
          conversation_id: conversationId,
          role: "assistant",
          content: assistantMessage,
        },
      ]);
    }

    res.json({ message: assistantMessage });
  } catch (err) {
    console.error("Chat error:", err);
    res.status(500).json({ error: "Erro ao processar mensagem" });
  }
}
