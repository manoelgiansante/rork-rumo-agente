require("dotenv").config();
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const fs = require("fs");
const path = require("path");
const Stripe = require("stripe");
const { createClient } = require("@supabase/supabase-js");
const crypto = require("crypto");
const containerManager = require("./container-manager");
const desktopAgent = require("./desktop-agent");
const agentMemory = require("./agent-memory");

const app = express();

// Validate required environment variables at startup
const REQUIRED_ENV = ["SUPABASE_URL", "SUPABASE_SERVICE_KEY"];
const missingEnv = REQUIRED_ENV.filter((key) => !process.env[key]);
if (missingEnv.length > 0) {
  console.error(`FATAL: Missing required env vars: ${missingEnv.join(", ")}`);
  process.exit(1);
}

const stripe = process.env.STRIPE_SECRET_KEY
  ? Stripe(process.env.STRIPE_SECRET_KEY)
  : null;
const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_SERVICE_KEY,
);

// Gemini API (free tier)
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
if (!GEMINI_API_KEY) {
  console.warn("WARNING: GEMINI_API_KEY not set — /chat endpoint will fail");
}
const GEMINI_MODEL = "gemini-2.5-flash";
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`;

app.use(
  helmet({
    contentSecurityPolicy: false, // Disabled for noVNC compatibility
    crossOriginEmbedderPolicy: false,
  }),
);

const ALLOWED_ORIGINS = [
  "https://vps.agrorumo.com",
  "https://rork-rumo-agente.vercel.app",
  "https://agente.agrorumo.com",
  "https://rumoagente.com.br",
  "https://www.rumoagente.com.br",
];

app.use(
  cors({
    origin: function (origin, callback) {
      // Allow requests with no origin (mobile apps, server-to-server)
      if (!origin) return callback(null, true);
      if (ALLOWED_ORIGINS.includes(origin) || /\.agrorumo\.com$/.test(origin)) {
        return callback(null, true);
      }
      callback(new Error("Bloqueado por CORS"));
    },
    credentials: true,
  }),
);

// Rate limiting
const rateLimit = {};
function rateLimiter(windowMs, max) {
  return (req, res, next) => {
    const key = (req.user?.id || req.ip) + ":" + req.path;
    const now = Date.now();
    if (!rateLimit[key]) rateLimit[key] = { count: 0, start: now };
    if (now - rateLimit[key].start > windowMs) {
      rateLimit[key] = { count: 0, start: now };
    }
    rateLimit[key].count++;
    if (rateLimit[key].count > max) {
      return res
        .status(429)
        .json({ error: "Muitas requisições. Tente novamente em breve." });
    }
    next();
  };
}

// Middleware para autenticar requests do app
async function authenticateUser(req, res, next) {
  const token = req.headers.authorization?.replace("Bearer ", "");
  if (!token) return res.status(401).json({ error: "Token required" });

  const {
    data: { user },
    error,
  } = await supabase.auth.getUser(token);
  if (error || !user) return res.status(401).json({ error: "Invalid token" });

  req.user = user;
  next();
}

// ============================================
// Webhook do Stripe (precisa do body raw)
// ============================================
// Idempotency: track processed event IDs to prevent double-processing
const processedWebhookEvents = new Map(); // eventId -> timestamp
setInterval(() => {
  const oneHourAgo = Date.now() - 3600000;
  for (const [id, ts] of processedWebhookEvents.entries()) {
    if (ts < oneHourAgo) processedWebhookEvents.delete(id);
  }
}, 300000); // Clean every 5 min

app.post(
  "/webhook/stripe",
  express.raw({ type: "application/json" }),
  async (req, res) => {
    const sig = req.headers["stripe-signature"];
    let event;
    try {
      event = stripe.webhooks.constructEvent(
        req.body,
        sig,
        process.env.STRIPE_WEBHOOK_SECRET,
      );
    } catch (err) {
      console.error("Webhook signature verification failed:", err.message);
      return res.status(400).send(`Webhook Error: ${err.message}`);
    }

    // Idempotency check
    if (processedWebhookEvents.has(event.id)) {
      console.log(`[Webhook] Duplicate event ${event.id}, skipping`);
      return res.json({ received: true, duplicate: true });
    }
    processedWebhookEvents.set(event.id, Date.now());

    switch (event.type) {
      case "checkout.session.completed": {
        const session = event.data.object;
        const userId = session.metadata.user_id;
        const plan = session.metadata.plan;
        const credits = session.metadata.credits;

        if (credits) {
          const amount = parseInt(credits);
          await supabase
            .from("profiles")
            .update({
              credits: supabase.rpc("increment_credits", {
                user_id: userId,
                amount,
              }),
            })
            .eq("user_id", userId);
          await supabase.from("credit_transactions").insert({
            user_id: userId,
            amount,
            type: "purchase",
            description: `Compra de ${amount} creditos`,
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
        await supabase
          .from("subscriptions")
          .update({ status: "canceled", plan: "free" })
          .eq("stripe_subscription_id", sub.id);
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
        }
        break;
      }
    }
    res.json({ received: true });
  },
);

app.use(express.json());

// ============================================
// Health / Status (public)
// ============================================
app.get("/status", (req, res) => {
  res.json({
    status: "running",
    timestamp: new Date().toISOString(),
    activeDesktops: containerManager.activeContainers.size,
  });
});

// ============================================
// Desktop - Status do usuario
// ============================================
app.get("/desktop/status", authenticateUser, (req, res) => {
  const info = containerManager.getDesktopInfo(req.user.id);
  res.json(info);
});

// ============================================
// Desktop - Iniciar container do usuario
// ============================================
app.post("/start-desktop", authenticateUser, async (req, res) => {
  try {
    const result = await containerManager.startDesktop(req.user.id);

    // Build noVNC URL for the user
    const host = req.headers.host || process.env.VPS_HOST || "216.238.111.253";
    const hostname = host.split(":")[0];

    // Get VNC password from container info
    const containerInfo = containerManager.activeContainers.get(req.user.id);
    const vncPass = containerInfo?.vncPassword || "rumoagente";

    res.json({
      success: true,
      status: result.status,
      desktop: true,
      noVncUrl: `https://${hostname}/novnc/${result.noVncPort}/vnc.html?autoconnect=true&password=${encodeURIComponent(vncPass)}&resize=scale`,
      message: "Desktop iniciado com sucesso",
    });
  } catch (err) {
    console.error("Start desktop error:", err);
    res.status(503).json({ error: err.message || "Erro ao iniciar desktop" });
  }
});

// ============================================
// Desktop - Parar container do usuario
// ============================================
app.post("/stop-desktop", authenticateUser, async (req, res) => {
  try {
    await containerManager.stopDesktop(req.user.id);
    res.json({ success: true, desktop: false, message: "Desktop desligado" });
  } catch (err) {
    console.error("Stop desktop error:", err);
    res.status(500).json({ error: "Erro ao parar desktop" });
  }
});

// ============================================
// Desktop - Screenshot (para apps mobile)
// ============================================
app.get("/screenshot", authenticateUser, async (req, res) => {
  try {
    const screenshotPath = await containerManager.takeScreenshot(req.user.id);
    if (!screenshotPath) {
      return res.status(404).json({ error: "Desktop nao esta ativo" });
    }

    // Send the actual image file
    res.sendFile(screenshotPath, (err) => {
      // Clean up temp file after sending
      try {
        fs.unlinkSync(screenshotPath);
      } catch (e) {}
    });
  } catch (err) {
    console.error("Screenshot error:", err);
    res.status(500).json({ error: "Erro ao capturar tela" });
  }
});

// ============================================
// Chat com Claude + Tool Use (agente inteligente)
// ============================================
app.post(
  "/chat",
  authenticateUser,
  rateLimiter(60000, 20),
  async (req, res) => {
    try {
      const { messages, conversationId } = req.body;
      if (!Array.isArray(messages) || messages.length === 0) {
        return res.status(400).json({ error: "messages array é obrigatório" });
      }

      // Input validation
      if (messages.length > 50) {
        return res
          .status(400)
          .json({ error: "Máximo de 50 mensagens por requisição" });
      }
      for (const msg of messages) {
        if (!msg.role || !msg.content) {
          return res
            .status(400)
            .json({ error: "Cada mensagem deve ter role e content" });
        }
        if (typeof msg.content !== "string") {
          return res.status(400).json({ error: "content deve ser string" });
        }
        if (msg.content.length > 10000) {
          return res
            .status(400)
            .json({ error: "Mensagem muito longa (max 10000 chars)" });
        }
        if (!["user", "assistant", "system"].includes(msg.role)) {
          return res
            .status(400)
            .json({ error: "role deve ser user, assistant ou system" });
        }
      }
      if (conversationId && typeof conversationId !== "string") {
        return res
          .status(400)
          .json({ error: "conversationId deve ser string" });
      }

      const userId = req.user.id;

      const { data: profile } = await supabase
        .from("profiles")
        .select("credits")
        .eq("user_id", userId)
        .single();

      if (!profile || profile.credits <= 0) {
        return res.status(403).json({ error: "Sem créditos disponíveis" });
      }

      // Check if user has an active desktop
      const desktopInfo = containerManager.getDesktopInfo(userId);
      const hasDesktop = desktopInfo.desktop;

      // Build system prompt with memory + workflow context
      const systemPrompt = await desktopAgent.buildSystemPrompt(
        supabase,
        userId,
        hasDesktop,
      );

      // Check if there's a matching workflow for this message
      const userMessage = messages[messages.length - 1]?.content || "";
      const matchedWorkflow = await agentMemory.findWorkflow(
        supabase,
        userId,
        userMessage,
      );

      // Session ID for action logging
      const sessionId = crypto.randomUUID();

      // Convert tools to Gemini function declarations format
      const geminiTools = [
        {
          functionDeclarations: desktopAgent.DESKTOP_TOOLS.map((t) => ({
            name: t.name,
            description: t.description,
            parameters: {
              type: "OBJECT",
              properties: Object.fromEntries(
                Object.entries(t.input_schema.properties || {}).map(
                  ([k, v]) => [
                    k,
                    {
                      type:
                        v.type === "integer"
                          ? "INTEGER"
                          : v.type === "array"
                            ? "ARRAY"
                            : "STRING",
                      description: v.description || "",
                      ...(v.items ? { items: { type: "STRING" } } : {}),
                    },
                  ],
                ),
              ),
              required: t.input_schema.required || [],
            },
          })),
        },
      ];

      // Build Gemini messages (contents array)
      let geminiContents = [];

      // Add conversation history
      for (const m of messages) {
        geminiContents.push({
          role: m.role === "assistant" ? "model" : "user",
          parts: [{ text: m.content }],
        });
      }

      // If a workflow was found, append to last user message
      if (matchedWorkflow) {
        const lastContent = geminiContents[geminiContents.length - 1];
        if (lastContent && lastContent.role === "user") {
          lastContent.parts[0].text += `\n\n[SISTEMA: Workflow "${matchedWorkflow.name}" encontrado (${matchedWorkflow.success_count}x sucesso). Descricao: ${matchedWorkflow.description}. Software: ${matchedWorkflow.target_software || "geral"}. Siga esse fluxo se aplicavel.]`;
        }
      }

      let finalText = "";
      let actionsExecuted = [];
      let iterations = 0;
      const MAX_ITERATIONS = 10;

      while (iterations < MAX_ITERATIONS) {
        iterations++;

        const requestBody = {
          contents: geminiContents,
          systemInstruction: { parts: [{ text: systemPrompt }] },
          tools: geminiTools,
          generationConfig: { maxOutputTokens: 2048 },
        };

        const geminiResp = await fetch(GEMINI_URL, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(requestBody),
        });

        if (!geminiResp.ok) {
          const errBody = await geminiResp.text();
          console.error("[Gemini] API error:", geminiResp.status, errBody);
          throw new Error(`Gemini API error: ${geminiResp.status}`);
        }

        const geminiData = await geminiResp.json();
        const candidate = geminiData.candidates?.[0];
        if (!candidate) {
          console.error(
            "[Gemini] No candidates in response:",
            JSON.stringify(geminiData).substring(0, 500),
          );
          throw new Error("Gemini returned no candidates");
        }

        const parts = candidate.content?.parts || [];
        let hasToolUse = false;
        let toolResultParts = [];

        // Build assistant parts for history
        let assistantParts = [];

        for (const part of parts) {
          if (part.text) {
            finalText += part.text;
            assistantParts.push({ text: part.text });
          } else if (part.functionCall) {
            hasToolUse = true;
            const toolName = part.functionCall.name;
            const toolInput = part.functionCall.args || {};
            console.log(
              `[Agent] Tool: ${toolName}(${JSON.stringify(toolInput)}) for user ${userId.substring(0, 8)}`,
            );

            const result = await desktopAgent.executeTool(
              userId,
              toolName,
              toolInput,
              supabase,
              sessionId,
            );
            actionsExecuted.push({ tool: toolName, input: toolInput, result });

            // Log action
            await agentMemory.logAction(
              supabase,
              userId,
              sessionId,
              toolName,
              toolInput,
              result,
              true,
            );

            assistantParts.push({
              functionCall: { name: toolName, args: toolInput },
            });
            toolResultParts.push({
              functionResponse: {
                name: toolName,
                response: {
                  result:
                    typeof result === "string"
                      ? result
                      : JSON.stringify(result),
                },
              },
            });
          }
        }

        // If no tool use or finish reason is STOP, we're done
        if (!hasToolUse || candidate.finishReason === "STOP") {
          break;
        }

        // Add assistant response and tool results for next iteration
        geminiContents.push({ role: "model", parts: assistantParts });
        geminiContents.push({ role: "user", parts: toolResultParts });
      }

      // Auto-learn from actions executed
      if (actionsExecuted.length > 0) {
        const learnings = agentMemory.extractLearnings(
          actionsExecuted,
          userMessage,
        );
        for (const l of learnings) {
          await agentMemory.saveMemory(
            supabase,
            userId,
            l.category,
            l.key,
            l.value,
          );
        }
      }

      // Mark matched workflow as successful
      if (matchedWorkflow && actionsExecuted.length > 0) {
        await agentMemory.markWorkflowResult(
          supabase,
          matchedWorkflow.id,
          true,
        );
      }

      // Take final screenshot if actions were executed
      let screenshotTaken = false;
      if (actionsExecuted.length > 0 && hasDesktop) {
        await containerManager.takeScreenshot(userId);
        screenshotTaken = true;
      }

      // Build response message
      const assistantMessage =
        finalText ||
        "Acoes executadas com sucesso. Veja o resultado na aba Tela.";

      // Save to database
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

      // Debit credit atomically using RPC (prevents race conditions)
      const { data: rpcResult, error: rpcError } = await supabase.rpc(
        "decrement_credits",
        {
          p_user_id: userId,
          p_amount: 1,
        },
      );
      if (rpcError) {
        // Fallback to optimistic update if RPC not available
        const { data: updateResult } = await supabase
          .from("profiles")
          .update({ credits: Math.max(0, profile.credits - 1) })
          .eq("user_id", userId);
        if (!updateResult || updateResult.length === 0) {
          console.warn(
            `[CREDITS] Failed to debit credit for user ${userId.substring(0, 8)}`,
          );
        }
      }

      await supabase.from("credit_transactions").insert({
        user_id: userId,
        amount: -1,
        type: "usage",
        description:
          actionsExecuted.length > 0 ? "Comando do agente" : "Mensagem de chat",
      });

      res.json({
        message: assistantMessage,
        actions:
          actionsExecuted.length > 0
            ? actionsExecuted.map((a) => a.tool)
            : undefined,
        screenshotAvailable: screenshotTaken,
      });
    } catch (err) {
      console.error("Chat error:", err);
      res.status(500).json({ error: "Erro ao processar mensagem" });
    }
  },
);

// ============================================
// Executar comando via agente no desktop do usuario
// ============================================
app.post(
  "/execute",
  authenticateUser,
  rateLimiter(60000, 30),
  async (req, res) => {
    const { action, appContext, parameters } = req.body;
    const userId = req.user.id;

    const info = containerManager.getDesktopInfo(userId);
    if (!info.desktop) {
      return res
        .status(400)
        .json({ error: "Desktop não está ativo. Inicie o desktop primeiro." });
    }

    const name = "rumo-desktop-" + userId.replace(/-/g, "").substring(0, 12);

    // Strict whitelist for apps - NEVER interpolate user input into shell
    const SAFE_APPS = {
      firefox: "firefox",
      browser: "firefox",
      terminal: "xfce4-terminal",
      editor: "mousepad",
      files: "thunar",
      arquivos: "thunar",
      calc: "libreoffice --calc",
      excel: "libreoffice --calc",
      writer: "libreoffice --writer",
      word: "libreoffice --writer",
    };

    try {
      const { execFileSync } = require("child_process");
      let args;

      if (action === "type") {
        const text = String(parameters?.text || "");
        if (text.length > 500)
          return res
            .status(400)
            .json({ error: "Texto muito longo (max 500 chars)" });
        args = [
          "exec",
          name,
          "bash",
          "-c",
          `export DISPLAY=:1 && xdotool type --delay 50 -- '${text.replace(/'/g, "'\\''")}'`,
        ];
      } else if (action === "click") {
        const x = parseInt(parameters?.x) || 640;
        const y = parseInt(parameters?.y) || 360;
        if (x < 0 || x > 1920 || y < 0 || y > 1080)
          return res.status(400).json({ error: "Coordenadas inválidas" });
        args = [
          "exec",
          name,
          "bash",
          "-c",
          `export DISPLAY=:1 && xdotool mousemove ${x} ${y} && xdotool click 1`,
        ];
      } else if (action === "open") {
        const app = (appContext || parameters?.app || "firefox").toLowerCase();
        const appCmd = SAFE_APPS[app];
        if (!appCmd)
          return res.status(400).json({
            error: `App "${app}" não está na lista permitida. Apps: ${Object.keys(SAFE_APPS).join(", ")}`,
          });
        args = ["exec", name, "bash", "-c", `export DISPLAY=:1 && ${appCmd} &`];
      } else {
        return res.status(400).json({ error: "Ação não reconhecida" });
      }

      execFileSync("docker", args, { timeout: 10000 });

      await new Promise((r) => setTimeout(r, 500));
      const screenshotPath = await containerManager.takeScreenshot(userId);

      res.json({
        success: true,
        message: `Comando "${action}" executado com sucesso.`,
        screenshot_url: screenshotPath ? "/screenshot" : null,
        task_id: crypto.randomUUID(),
      });
    } catch (err) {
      console.error("Execute error:", err);
      res.status(500).json({ error: "Erro ao executar comando" });
    }
  },
);

// ============================================
// Criar sessao de checkout Stripe (assinatura)
// ============================================
app.post("/create-checkout", authenticateUser, async (req, res) => {
  try {
    const { plan } = req.body;
    const userId = req.user.id;
    const email = req.user.email;

    const prices = {
      starter: 4990,
      pro: 14990,
      enterprise: 49990,
    };

    if (!prices[plan]) return res.status(400).json({ error: "Plano invalido" });

    const session = await stripe.checkout.sessions.create({
      payment_method_types: ["card"],
      mode: "subscription",
      customer_email: email,
      metadata: { user_id: userId, plan },
      line_items: [
        {
          price_data: {
            currency: "brl",
            product_data: {
              name: `Rumo Agente - Plano ${plan.charAt(0).toUpperCase() + plan.slice(1)}`,
            },
            recurring: { interval: "month" },
            unit_amount: prices[plan],
          },
          quantity: 1,
        },
      ],
      success_url: "https://vps.agrorumo.com/#success",
      cancel_url: "https://vps.agrorumo.com/#cancel",
    });

    res.json({ url: session.url, sessionId: session.id });
  } catch (err) {
    console.error("Checkout error:", err);
    res.status(500).json({ error: "Erro ao criar checkout. Tente novamente." });
  }
});

// ============================================
// Comprar creditos extras
// ============================================
app.post("/buy-credits", authenticateUser, async (req, res) => {
  try {
    const { amount } = req.body;
    const userId = req.user.id;
    const email = req.user.email;

    const packs = { 50: 1990, 200: 5990, 500: 11990 };
    const price = packs[amount];
    if (!price)
      return res
        .status(400)
        .json({ error: "Pacote invalido. Opcoes: 50, 200 ou 500" });

    const session = await stripe.checkout.sessions.create({
      payment_method_types: ["card"],
      mode: "payment",
      customer_email: email,
      metadata: { user_id: userId, credits: String(amount) },
      line_items: [
        {
          price_data: {
            currency: "brl",
            product_data: { name: `${amount} Creditos Extras - Rumo Agente` },
            unit_amount: price,
          },
          quantity: 1,
        },
      ],
      success_url: "https://vps.agrorumo.com/#success",
      cancel_url: "https://vps.agrorumo.com/#cancel",
    });

    res.json({ url: session.url });
  } catch (err) {
    console.error("Buy credits error:", err);
    res
      .status(500)
      .json({ error: "Erro ao processar compra. Tente novamente." });
  }
});

// ============================================
// Perfil do usuario
// ============================================
app.get("/profile", authenticateUser, async (req, res) => {
  const { data, error } = await supabase
    .from("profiles")
    .select("*")
    .eq("user_id", req.user.id)
    .single();
  if (error) return res.status(500).json({ error: error.message });
  res.json(data);
});

// ============================================
// Credenciais - Listar servicos salvos
// ============================================
app.get("/credentials", authenticateUser, async (req, res) => {
  try {
    const creds = await agentMemory.listCredentials(supabase, req.user.id);
    res.json(creds);
  } catch (err) {
    res.status(500).json({ error: "Erro ao listar credenciais" });
  }
});

// ============================================
// Credenciais - Salvar nova credencial
// ============================================
app.post("/credentials", authenticateUser, async (req, res) => {
  try {
    const { service_name, service_url, username, password, extra_fields } =
      req.body;
    if (!service_name || !username || !password) {
      return res
        .status(400)
        .json({ error: "service_name, username e password sao obrigatorios" });
    }
    const saved = await agentMemory.saveCredential(
      supabase,
      req.user.id,
      service_name,
      service_url,
      username,
      password,
      extra_fields,
    );
    res.json({
      success: saved,
      message: saved ? "Credencial salva com seguranca" : "Erro ao salvar",
    });
  } catch (err) {
    res.status(500).json({ error: "Erro ao salvar credencial" });
  }
});

// ============================================
// Credenciais - Deletar
// ============================================
app.delete("/credentials/:serviceName", authenticateUser, async (req, res) => {
  try {
    const deleted = await agentMemory.deleteCredential(
      supabase,
      req.user.id,
      req.params.serviceName,
    );
    res.json({ success: deleted });
  } catch (err) {
    res.status(500).json({ error: "Erro ao deletar credencial" });
  }
});

// ============================================
// Confirmacoes pendentes
// ============================================
app.get("/confirmations", authenticateUser, async (req, res) => {
  try {
    const pending = await agentMemory.getPendingConfirmations(
      supabase,
      req.user.id,
    );
    res.json(pending);
  } catch (err) {
    res.status(500).json({ error: "Erro ao buscar confirmacoes" });
  }
});

// ============================================
// Resolver confirmacao (aprovar/rejeitar)
// ============================================
app.post("/confirmations/:id/resolve", authenticateUser, async (req, res) => {
  try {
    const { approved } = req.body;
    const result = await agentMemory.resolveConfirmation(
      supabase,
      req.params.id,
      approved,
      req.user.id,
    );
    if (!result) {
      return res
        .status(404)
        .json({ error: "Confirmação não encontrada ou já resolvida" });
    }
    res.json({ success: true, status: approved ? "approved" : "rejected" });
  } catch (err) {
    res.status(500).json({ error: "Erro ao resolver confirmacao" });
  }
});

// ============================================
// Memoria do agente - ver aprendizados
// ============================================
app.get("/memory", authenticateUser, async (req, res) => {
  try {
    const { data } = await supabase
      .from("agent_memory")
      .select("category, key, value, usage_count, updated_at")
      .eq("user_id", req.user.id)
      .order("updated_at", { ascending: false })
      .limit(100);
    res.json(data || []);
  } catch (err) {
    res.status(500).json({ error: "Erro ao buscar memoria" });
  }
});

// ============================================
// Memoria - deletar item
// ============================================
app.delete("/memory/:category/:key", authenticateUser, async (req, res) => {
  try {
    await supabase
      .from("agent_memory")
      .delete()
      .eq("user_id", req.user.id)
      .eq("category", req.params.category)
      .eq("key", req.params.key);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: "Erro ao deletar memoria" });
  }
});

// ============================================
// Workflows aprendidos
// ============================================
app.get("/workflows", authenticateUser, async (req, res) => {
  try {
    const { data } = await supabase
      .from("learned_workflows")
      .select(
        "id, name, description, trigger_phrases, target_software, success_count, fail_count, is_active, updated_at",
      )
      .eq("user_id", req.user.id)
      .order("success_count", { ascending: false });
    res.json(data || []);
  } catch (err) {
    res.status(500).json({ error: "Erro ao buscar workflows" });
  }
});

// ============================================
// Workflow - deletar
// ============================================
app.delete("/workflows/:id", authenticateUser, async (req, res) => {
  try {
    await supabase
      .from("learned_workflows")
      .delete()
      .eq("id", req.params.id)
      .eq("user_id", req.user.id);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: "Erro ao deletar workflow" });
  }
});

// ============================================
// Historico de acoes do agente
// ============================================
app.get("/action-log", authenticateUser, async (req, res) => {
  try {
    const { data } = await supabase
      .from("agent_action_log")
      .select("tool_name, tool_input, tool_result, success, created_at")
      .eq("user_id", req.user.id)
      .order("created_at", { ascending: false })
      .limit(50);
    res.json(data || []);
  } catch (err) {
    res.status(500).json({ error: "Erro ao buscar historico" });
  }
});

// ============================================
// Apple StoreKit - Validar recibo e creditar
// ============================================
app.post("/validate-apple-receipt", authenticateUser, async (req, res) => {
  try {
    const { transactionId, productId, originalTransactionId } = req.body;
    const userId = req.user.id;

    if (!transactionId || !productId) {
      return res
        .status(400)
        .json({ error: "transactionId e productId são obrigatórios" });
    }

    // Check if this transaction was already processed (idempotency)
    const { data: existing } = await supabase
      .from("credit_transactions")
      .select("id")
      .eq("stripe_payment_id", `apple_${transactionId}`)
      .single();

    if (existing) {
      return res.json({
        success: true,
        message: "Transação já processada",
        duplicate: true,
      });
    }

    // Determine what was purchased
    const creditPacks = {
      "app.rork.rumoagente.credits.50": 50,
      "app.rork.rumoagente.credits.200": 200,
      "app.rork.rumoagente.credits.500": 500,
    };

    const planMap = {
      "app.rork.rumoagente.starter": { plan: "starter", credits: 100 },
      "app.rork.rumoagente.pro": { plan: "pro", credits: 500 },
      "app.rork.rumoagente.enterprise": { plan: "enterprise", credits: 2000 },
    };

    if (creditPacks[productId]) {
      // Credit pack purchase
      const amount = creditPacks[productId];
      const { error: rpcError } = await supabase.rpc("increment_credits", {
        p_user_id: userId,
        p_amount: amount,
      });

      if (rpcError) {
        // Fallback
        const { data: profile } = await supabase
          .from("profiles")
          .select("credits")
          .eq("user_id", userId)
          .single();
        if (profile) {
          await supabase
            .from("profiles")
            .update({ credits: profile.credits + amount })
            .eq("user_id", userId);
        }
      }

      await supabase.from("credit_transactions").insert({
        user_id: userId,
        amount,
        type: "purchase",
        description: `Compra Apple IAP: ${amount} créditos`,
        stripe_payment_id: `apple_${transactionId}`,
      });

      console.log(
        `[Apple IAP] +${amount} créditos para user ${userId.substring(0, 8)}`,
      );
      res.json({ success: true, creditsAdded: amount });
    } else if (planMap[productId]) {
      // Subscription purchase
      const { plan, credits } = planMap[productId];
      await supabase
        .from("profiles")
        .update({ plan, credits })
        .eq("user_id", userId);

      await supabase.from("subscriptions").upsert(
        {
          user_id: userId,
          stripe_subscription_id: `apple_${originalTransactionId || transactionId}`,
          plan,
          status: "active",
        },
        { onConflict: "user_id" },
      );

      await supabase.from("credit_transactions").insert({
        user_id: userId,
        amount: credits,
        type: "purchase",
        description: `Assinatura Apple IAP: Plano ${plan}`,
        stripe_payment_id: `apple_${transactionId}`,
      });

      console.log(
        `[Apple IAP] Plano ${plan} ativado para user ${userId.substring(0, 8)}`,
      );
      res.json({ success: true, plan, credits });
    } else {
      res.status(400).json({ error: `Produto não reconhecido: ${productId}` });
    }
  } catch (err) {
    console.error("[Apple IAP] Error:", err);
    res.status(500).json({ error: "Erro ao validar compra Apple" });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, "127.0.0.1", async () => {
  console.log(`Rumo Agente API rodando na porta ${PORT}`);
  console.log(`Desktops ativos: ${containerManager.activeContainers.size}`);

  // Check if memory tables exist
  const tables = [
    "agent_memory",
    "secure_credentials",
    "learned_workflows",
    "agent_action_log",
    "pending_confirmations",
  ];
  for (const t of tables) {
    const { error } = await supabase.from(t).select("*").limit(1);
    if (error) {
      console.warn(
        `[WARN] Table "${t}" missing - run setup-tables.sql in Supabase Dashboard`,
      );
    }
  }
});
