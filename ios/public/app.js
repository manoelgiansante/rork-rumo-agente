// Config
const SUPABASE_URL = "https://jxcnfyeemdltdfqtgbcl.supabase.co";
const SUPABASE_ANON_KEY =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imp4Y25meWVlbWRsdGRmcXRnYmNsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njg1MDQwNTksImV4cCI6MjA4NDA4MDA1OX0.MEqgaUHb0cDVoDrXY6rc1F6YJLxzbpNiks-SFRCg2go";
const AGENT_URL = "https://vps.agrorumo.com";
const API_URL = "https://vps.agrorumo.com";

// State
let authToken = localStorage.getItem("auth_token");
let refreshToken = localStorage.getItem("refresh_token");
let currentUser = null;
let isSignUp = false;
let selectedApp = null;
let screenInterval = null;
let splitScreenInterval = null;
let apps = [];
let selectedCategory = null;
let refreshInterval = null;

let onboardingStep = 0;

// PWA Install
let deferredInstallPrompt = null;

// Init
document.addEventListener("DOMContentLoaded", () => {
  setTimeout(() => {
    const splash = document.getElementById("splash-screen");
    if (splash) splash.classList.add("hidden");
  }, 1200);

  if (authToken) {
    checkSession();
  } else if (!localStorage.getItem("has_onboarded")) {
    document.getElementById("auth-screen").classList.remove("active");
    document.getElementById("onboarding-screen").classList.add("active");
  } else {
    document.getElementById("auth-screen").classList.add("active");
  }
  setDate();
  initHapticButtons();
  registerServiceWorker();
  initOfflineDetection();
  initPWAInstall();
  handleDeepLink();
});

function nextOnboarding() {
  onboardingStep++;
  if (onboardingStep >= 3) {
    skipOnboarding();
    return;
  }
  updateOnboarding();
}

function skipOnboarding() {
  localStorage.setItem("has_onboarded", "true");
  const splash = document.getElementById("splash-screen");
  if (splash) splash.classList.add("hidden");
  const onb = document.getElementById("onboarding-screen");
  onb.style.opacity = "0";
  onb.style.transition = "opacity 0.35s ease";
  setTimeout(() => {
    onb.classList.remove("active");
    onb.style.opacity = "";
    onb.style.transition = "";
    const auth = document.getElementById("auth-screen");
    auth.classList.add("active");
    auth.style.animation = "slideUpFade 0.5s cubic-bezier(0.16,1,0.3,1)";
  }, 300);
}

function updateOnboarding() {
  document
    .querySelectorAll(".onboarding-slide")
    .forEach((s) => s.classList.remove("active"));
  document
    .querySelectorAll(".onboarding-dots .dot")
    .forEach((d) => d.classList.remove("active"));
  const nextSlide = document.querySelector(
    '.onboarding-slide[data-slide="' + onboardingStep + '"]',
  );
  nextSlide.classList.add("active");
  nextSlide.style.animation = "slideUpFade 0.5s cubic-bezier(0.16,1,0.3,1)";
  document
    .querySelectorAll(".onboarding-dots .dot")
    [onboardingStep].classList.add("active");
  const btn = document.getElementById("onboarding-btn");
  btn.textContent = onboardingStep === 2 ? "Começar" : "Próximo";
  const skipBtn = document.getElementById("onboarding-skip");
  if (skipBtn) skipBtn.style.display = onboardingStep === 2 ? "none" : "block";
}

function setDate() {
  const d = new Date();
  const opts = { weekday: "long", day: "numeric", month: "long" };
  const el = document.getElementById("current-date");
  if (el) el.textContent = d.toLocaleDateString("pt-BR", opts);
}

// ============ AUTH ============
async function supabaseRequest(path, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    apikey: SUPABASE_ANON_KEY,
    ...options.headers,
  };
  if (authToken) headers["Authorization"] = "Bearer " + authToken;
  const res = await fetch(SUPABASE_URL + path, { ...options, headers });
  return res;
}

function toggleAuthMode(e) {
  e.preventDefault();
  isSignUp = !isSignUp;
  document.getElementById("signup-name-field").style.display = isSignUp
    ? "flex"
    : "none";
  document.getElementById("auth-subtitle").textContent = isSignUp
    ? "Crie sua conta"
    : "Entre na sua conta";
  document.getElementById("auth-submit-btn").textContent = isSignUp
    ? "Criar Conta"
    : "Entrar";
  document.getElementById("toggle-text").textContent = isSignUp
    ? "Já tem conta?"
    : "Não tem conta?";
  document.getElementById("toggle-link").textContent = isSignUp
    ? "Entrar"
    : "Criar conta";
  document.getElementById("forgot-btn").style.display = isSignUp
    ? "none"
    : "block";
  document.getElementById("consent-checkbox").style.display = isSignUp
    ? "block"
    : "none";
  document.getElementById("auth-error").style.display = "none";
}

function showAuthError(msg) {
  const el = document.getElementById("auth-error");
  el.textContent = msg;
  el.style.color = "#EF4444";
  el.style.display = "block";
}

function showAuthSuccess(msg) {
  const el = document.getElementById("auth-error");
  el.textContent = msg;
  el.style.color = "#34D399";
  el.style.display = "block";
}

// Auto-refresh token every 50 minutes (token expires in 60 min)
function startTokenRefresh() {
  if (refreshInterval) clearInterval(refreshInterval);
  refreshInterval = setInterval(
    async () => {
      if (!refreshToken) return;
      try {
        const res = await supabaseRequest(
          "/auth/v1/token?grant_type=refresh_token",
          {
            method: "POST",
            body: JSON.stringify({ refresh_token: refreshToken }),
          },
        );
        if (res.ok) {
          const data = await res.json();
          authToken = data.access_token;
          refreshToken = data.refresh_token;
          localStorage.setItem("auth_token", authToken);
          if (refreshToken) localStorage.setItem("refresh_token", refreshToken);
        }
      } catch (e) {
        console.warn("Token refresh failed:", e);
      }
    },
    50 * 60 * 1000,
  );
}

async function handleAuth() {
  const email = document.getElementById("auth-email").value.trim();
  const password = document.getElementById("auth-password").value;
  if (!email || !password) return showAuthError("Preencha todos os campos");

  if (isSignUp && !document.getElementById("consent-check").checked) {
    return showAuthError(
      "Você deve concordar com a Política de Privacidade e os Termos de Uso.",
    );
  }

  const btn = document.getElementById("auth-submit-btn");
  btn.disabled = true;
  btn.innerHTML = '<span class="auth-spinner"></span>';

  try {
    if (isSignUp) {
      const name = document.getElementById("auth-name").value.trim();
      const res = await supabaseRequest("/auth/v1/signup", {
        method: "POST",
        body: JSON.stringify({ email, password, data: { display_name: name } }),
      });
      const data = await res.json();
      if (!res.ok)
        throw new Error(
          data.error_description || data.msg || "Erro ao criar conta",
        );
      if (data.access_token) {
        authToken = data.access_token;
        refreshToken = data.refresh_token;
        localStorage.setItem("auth_token", authToken);
        if (refreshToken) localStorage.setItem("refresh_token", refreshToken);
        startTokenRefresh();
        await loadProfile();
        showApp();
      } else {
        showAuthError("Verifique seu email para confirmar o cadastro.");
      }
    } else {
      const res = await supabaseRequest("/auth/v1/token?grant_type=password", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      const data = await res.json();
      if (!res.ok)
        throw new Error(
          data.error_description || data.msg || "Email ou senha incorretos",
        );
      authToken = data.access_token;
      refreshToken = data.refresh_token;
      localStorage.setItem("auth_token", authToken);
      if (refreshToken) localStorage.setItem("refresh_token", refreshToken);
      startTokenRefresh();
      await loadProfile();
      showApp();
    }
  } catch (err) {
    showAuthError(err.message);
  }
  btn.disabled = false;
  btn.textContent = isSignUp ? "Criar Conta" : "Entrar";
}

async function signInWithGoogle() {
  const redirectURL = window.location.origin + "/";
  window.location.href =
    SUPABASE_URL +
    "/auth/v1/authorize?provider=google&redirect_to=" +
    encodeURIComponent(redirectURL);
}

async function signInWithApple() {
  const redirectURL = window.location.origin + "/";
  window.location.href =
    SUPABASE_URL +
    "/auth/v1/authorize?provider=apple&redirect_to=" +
    encodeURIComponent(redirectURL);
}

function togglePassword() {
  const input = document.getElementById("auth-password");
  input.type = input.type === "password" ? "text" : "password";
}

async function forgotPassword() {
  const email = document.getElementById("auth-email").value.trim();
  if (!email) return showAuthError("Digite seu email primeiro");
  try {
    await supabaseRequest("/auth/v1/recover", {
      method: "POST",
      body: JSON.stringify({ email }),
    });
    showAuthSuccess("Link de recuperação enviado! Verifique seu email.");
  } catch (e) {
    showAuthError("Erro ao enviar email");
  }
}

async function checkSession() {
  try {
    // Check URL for OAuth callback tokens
    const hash = window.location.hash;
    if (hash && hash.includes("access_token")) {
      const params = new URLSearchParams(hash.substring(1));
      const token = params.get("access_token");
      const rToken = params.get("refresh_token");
      if (token) {
        authToken = token;
        localStorage.setItem("auth_token", authToken);
        if (rToken) {
          refreshToken = rToken;
          localStorage.setItem("refresh_token", rToken);
        }
        window.location.hash = "";
      }
    }

    if (!authToken) return;

    const res = await supabaseRequest("/auth/v1/user");
    if (!res.ok) {
      logout(true);
      return;
    }
    startTokenRefresh();
    await loadProfile();
    showApp();
  } catch (e) {
    logout(true);
  }
}

async function loadProfile() {
  const res = await supabaseRequest("/auth/v1/user");
  if (!res.ok) return;
  const user = await res.json();

  // Fetch profile from DB
  const profileRes = await fetch(
    SUPABASE_URL + "/rest/v1/profiles?user_id=eq." + user.id + "&select=*",
    {
      headers: {
        apikey: SUPABASE_ANON_KEY,
        Authorization: "Bearer " + authToken,
      },
    },
  );
  const profiles = await profileRes.json();
  const profile = profiles[0] || {};

  currentUser = {
    id: user.id,
    email: user.email,
    displayName:
      user.user_metadata?.display_name ||
      profile.display_name ||
      user.email.split("@")[0],
    plan: profile.plan || "free",
    credits: profile.credits || 10,
  };

  updateUI();
}

function updateUI() {
  if (!currentUser) return;
  const initial = (currentUser.displayName || "U")[0].toUpperCase();
  const planLabel =
    "Plano " +
    currentUser.plan.charAt(0).toUpperCase() +
    currentUser.plan.slice(1);

  document.getElementById("greeting").textContent =
    "Olá, " + currentUser.displayName + "!";
  document.getElementById("avatar-initial").textContent = initial;
  document.getElementById("credits-count").textContent = currentUser.credits;
  document.getElementById("profile-name").textContent = currentUser.displayName;
  document.getElementById("profile-email").textContent = currentUser.email;
  document.getElementById("profile-avatar").textContent = initial;
  document.getElementById("plan-name").textContent = planLabel;
  document.getElementById("plan-credits").textContent =
    currentUser.credits + " créditos restantes";

  // Update desktop sidebar & topbar elements
  const sidebarAvatar = document.getElementById("sidebar-avatar");
  if (sidebarAvatar) sidebarAvatar.textContent = initial;
  const sidebarName = document.getElementById("sidebar-user-name");
  if (sidebarName) sidebarName.textContent = currentUser.displayName;
  const sidebarPlan = document.getElementById("sidebar-user-plan");
  if (sidebarPlan) sidebarPlan.textContent = planLabel;
  const topbarAvatar = document.getElementById("topbar-avatar");
  if (topbarAvatar) topbarAvatar.textContent = initial;
  const topbarCredits = document.getElementById("topbar-credits-count");
  if (topbarCredits) topbarCredits.textContent = currentUser.credits;

  // Update credits used today
  document.getElementById("credits-used").textContent = getCreditsUsedToday();

  // Update circular progress
  const maxCredits =
    { free: 10, starter: 100, pro: 500, enterprise: 2000 }[currentUser.plan] ||
    10;
  const ratio = Math.min(currentUser.credits / maxCredits, 1);
  const circumference = 2 * Math.PI * 20; // r=20
  const offset = circumference * (1 - ratio);
  const fg = document.querySelector(".circular-progress .fg");
  if (fg) fg.setAttribute("stroke-dashoffset", offset);

  loadApps();
  loadAppSelector();
  checkAgentStatus();
}

function showApp() {
  const splash = document.getElementById("splash-screen");
  if (splash) splash.classList.add("hidden");
  document.getElementById("onboarding-screen").classList.remove("active");
  const auth = document.getElementById("auth-screen");
  auth.style.opacity = "0";
  auth.style.transform = "scale(0.97)";
  auth.style.transition = "all 0.35s ease";
  setTimeout(() => {
    auth.classList.remove("active");
    auth.style.opacity = "";
    auth.style.transform = "";
    auth.style.transition = "";
    const app = document.getElementById("app-screen");
    app.classList.add("active");
    app.style.animation = "slideUpFade 0.5s cubic-bezier(0.16,1,0.3,1)";
  }, 300);
}

function logout(skipConfirm) {
  if (!skipConfirm && !confirm("Sair da conta?")) return;
  authToken = null;
  refreshToken = null;
  currentUser = null;
  localStorage.removeItem("auth_token");
  localStorage.removeItem("refresh_token");
  if (refreshInterval) {
    clearInterval(refreshInterval);
    refreshInterval = null;
  }
  if (splitScreenInterval) {
    clearInterval(splitScreenInterval);
    splitScreenInterval = null;
  }
  disconnectScreen();
  document.getElementById("app-screen").classList.remove("active");
  document.getElementById("auth-screen").classList.add("active");
}

// ============ NAVIGATION ============
const tabTitles = {
  dashboard: "Dashboard",
  screen: "Tela & Chat",
  chat: "Tela & Chat",
  apps: "Apps",
  profile: "Perfil",
};

function switchTab(name) {
  if (splitScreenInterval) {
    clearInterval(splitScreenInterval);
    splitScreenInterval = null;
  }

  // Redirect chat tab to screen (chat is now embedded in screen tab)
  if (name === "chat") name = "screen";

  const currentPanel = document.querySelector(".tab-panel.active");
  document
    .querySelectorAll(".tab-item")
    .forEach((t) => t.classList.remove("active"));
  document
    .querySelectorAll(".sidebar-item")
    .forEach((s) => s.classList.remove("active"));

  const nextPanel = document.getElementById("tab-" + name);
  if (currentPanel && currentPanel !== nextPanel) {
    currentPanel.classList.remove("active");
  }
  nextPanel.classList.add("active");

  const mobileTab = document.querySelector(
    '.tab-item[data-tab="' + name + '"]',
  );
  if (mobileTab) mobileTab.classList.add("active");

  const sidebarTab = document.querySelector(
    '.sidebar-item[data-tab="' + name + '"]',
  );
  if (sidebarTab) sidebarTab.classList.add("active");

  const topbarTitle = document.getElementById("topbar-title");
  if (topbarTitle) topbarTitle.textContent = tabTitles[name] || name;

  if (name === "screen") {
    const input = document.getElementById("chat-input");
    if (input) setTimeout(() => input.focus(), 150);
    scrollChat();
  }

  triggerHaptic();
}

async function fetchSplitScreenshot() {
  try {
    const res = await fetch(AGENT_URL + "/screenshot", {
      cache: "no-store",
      headers: { Authorization: "Bearer " + authToken },
    });
    if (!res.ok) return;
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const img = document.getElementById("split-screen-image");
    if (!img) return;
    const old = img.src;
    img.src = url;
    if (old && old.startsWith("blob:")) URL.revokeObjectURL(old);
  } catch (e) {}
}

// ============ SCREEN ============
let noVncUrl = null;

async function connectScreen() {
  const btn = document.getElementById("connect-btn");
  btn.textContent = "Conectando...";
  btn.disabled = true;

  try {
    // Check desktop status first
    const statusRes = await fetch(AGENT_URL + "/desktop/status", {
      headers: { Authorization: "Bearer " + authToken },
    });
    const statusData = await statusRes.json();

    if (statusData.desktop && statusData.noVncUrl) {
      // Desktop already running with known URL
      noVncUrl = statusData.noVncUrl;
    } else {
      // Start (or re-detect) user's desktop container
      const startRes = await fetch(AGENT_URL + "/start-desktop", {
        method: "POST",
        headers: { Authorization: "Bearer " + authToken },
      });
      const startData = await startRes.json();
      if (!startRes.ok)
        throw new Error(startData.error || "Erro ao iniciar desktop");
      noVncUrl = startData.noVncUrl;
      // Wait for desktop to fully boot
      await new Promise((r) => setTimeout(r, 4000));
    }

    document.getElementById("screen-placeholder").style.display = "none";
    document.getElementById("connection-status").innerHTML =
      '<span class="status-dot green"></span> Conectado';
    document.getElementById("disconnect-btn").style.display = "block";

    if (noVncUrl) {
      // Show interactive noVNC iframe
      const container = document.getElementById("screen-container");
      let iframe = document.getElementById("screen-iframe");
      if (!iframe) {
        iframe = document.createElement("iframe");
        iframe.id = "screen-iframe";
        iframe.style.cssText =
          "width:100%;height:100%;border:none;background:#000;position:absolute;inset:0;";
        container.appendChild(iframe);
      }
      // Use vnc_lite (no control bar/keyboard icon) and disable bell
      iframe.src = noVncUrl;
      iframe.style.display = "block";
      document.getElementById("screen-image").style.display = "none";
    } else {
      // Fallback to screenshots
      document.getElementById("screen-image").style.display = "block";
      fetchScreenshot();
      screenInterval = setInterval(fetchScreenshot, 1500);
    }
  } catch (e) {
    btn.textContent = "▶ Conectar";
    btn.disabled = false;
    alert("Erro ao conectar: " + e.message);
  }
}

async function disconnectScreen() {
  if (screenInterval) {
    clearInterval(screenInterval);
    screenInterval = null;
  }

  // Stop user's desktop container
  try {
    await fetch(AGENT_URL + "/stop-desktop", {
      method: "POST",
      headers: { Authorization: "Bearer " + authToken },
    });
  } catch (e) {}

  const iframe = document.getElementById("screen-iframe");
  if (iframe) {
    iframe.src = "";
    iframe.style.display = "none";
  }
  document.getElementById("screen-placeholder").style.display = "";
  document.getElementById("screen-image").style.display = "none";
  document.getElementById("connection-status").innerHTML =
    '<span class="status-dot red"></span> Desconectado';
  document.getElementById("disconnect-btn").style.display = "none";
  const btn = document.getElementById("connect-btn");
  btn.textContent = "▶ Conectar";
  btn.disabled = false;
  noVncUrl = null;
}

async function fetchScreenshot() {
  try {
    const res = await fetch(AGENT_URL + "/screenshot", {
      cache: "no-store",
      headers: { Authorization: "Bearer " + authToken },
    });
    if (!res.ok) return;
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const img = document.getElementById("screen-image");
    const old = img.src;
    img.src = url;
    if (old && old.startsWith("blob:")) URL.revokeObjectURL(old);
  } catch (e) {}
}

function toggleFullscreen() {
  document.body.classList.toggle("fullscreen");
}

async function checkAgentStatus() {
  try {
    const res = await fetch(AGENT_URL + "/status");
    const data = await res.json();
    const badge = document.getElementById("agent-status-badge");
    if (data.status === "running") {
      badge.innerHTML = '<span class="status-dot green"></span> Pronto';
    } else {
      badge.innerHTML = '<span class="status-dot orange"></span> Iniciando';
    }

    // Also check user's desktop status
    if (authToken) {
      const dRes = await fetch(AGENT_URL + "/desktop/status", {
        headers: { Authorization: "Bearer " + authToken },
      });
      const dData = await dRes.json();
      if (dData.desktop) {
        badge.innerHTML =
          '<span class="status-dot green"></span> Desktop ativo';
      }
    }
  } catch (e) {
    const badge = document.getElementById("agent-status-badge");
    badge.innerHTML = '<span class="status-dot red"></span> Offline';
  }
}

// ============ CHAT ============
// Configure marked for safe markdown rendering
if (typeof marked !== "undefined") {
  marked.setOptions({ breaks: true, gfm: true });
}

async function sendChat() {
  const input = document.getElementById("chat-input");
  const text = input.value.trim();
  if (!text) return;
  input.value = "";
  document.getElementById("send-btn").disabled = true;

  addMessage("user", text);

  // Create assistant message placeholder for streaming
  const container = document.getElementById("chat-messages");
  const time = new Date().toLocaleTimeString("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  });
  const div = document.createElement("div");
  div.className = "message assistant";
  div.innerHTML =
    '<div class="bubble streaming"><span class="stream-content"></span><span class="cursor-blink">|</span></div><span class="msg-time">' +
    time +
    "</span>";
  container.appendChild(div);
  scrollChat();

  const streamContent = div.querySelector(".stream-content");
  const cursor = div.querySelector(".cursor-blink");

  try {
    const res = await fetch(API_URL + "/chat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + authToken,
      },
      body: JSON.stringify({
        messages: [{ role: "user", content: text }],
        conversationId: null,
      }),
    });

    if (!res.ok) {
      // Handle error response
      try {
        const data = await res.json();
        streamContent.innerHTML =
          typeof marked !== "undefined"
            ? marked.parse(data.message || data.error || "Erro")
            : escapeHtml(data.message || data.error || "Erro");
      } catch (e) {
        streamContent.textContent =
          "Desculpe, ocorreu um erro. Tente novamente.";
      }
      cursor.remove();
      return;
    }

    // Check if response is streaming (SSE) or regular JSON
    const contentType = res.headers.get("content-type") || "";
    if (
      contentType.includes("text/event-stream") ||
      contentType.includes("text/plain")
    ) {
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let fullText = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        const lines = chunk.split("\n");

        for (const line of lines) {
          if (line.startsWith("data: ")) {
            const data = line.substring(6);
            if (data === "[DONE]") continue;
            try {
              const parsed = JSON.parse(data);
              if (parsed.text) {
                fullText += parsed.text;
                streamContent.innerHTML =
                  typeof marked !== "undefined"
                    ? marked.parse(fullText)
                    : escapeHtml(fullText);
                scrollChat();
              }
              if (parsed.credits !== undefined && currentUser) {
                currentUser.credits = parsed.credits;
                document.getElementById("credits-count").textContent =
                  parsed.credits;
                var _tc = document.getElementById("topbar-credits-count");
                if (_tc) _tc.textContent = parsed.credits;
              }
            } catch (e) {
              // Plain text chunk
              fullText += data;
              streamContent.innerHTML =
                typeof marked !== "undefined"
                  ? marked.parse(fullText)
                  : escapeHtml(fullText);
              scrollChat();
            }
          }
        }
      }
    } else {
      // Regular JSON response (fallback)
      const data = await res.json();
      streamContent.innerHTML =
        typeof marked !== "undefined"
          ? marked.parse(data.message || "")
          : escapeHtml(data.message || "");
    }

    cursor.remove();

    // Check if message needs confirmation (like iOS)
    const finalText =
      streamContent.textContent || streamContent.innerText || "";
    if (isConfirmationMessage(finalText)) {
      appendConfirmationButtons(div);
    }

    if (currentUser) {
      currentUser.credits = Math.max(0, currentUser.credits - 1);
      document.getElementById("credits-count").textContent =
        currentUser?.credits || 0;
      var _tc2 = document.getElementById("topbar-credits-count");
      if (_tc2) _tc2.textContent = currentUser?.credits || 0;
      incrementCreditsUsed();
    }
  } catch (e) {
    streamContent.textContent = "Erro de conexão. Verifique sua internet.";
    cursor.remove();
  }
}

function isConfirmationMessage(text) {
  const lower = text.toLowerCase();
  const confirmPatterns = [
    "deseja confirmar",
    "deseja continuar",
    "posso continuar",
    "posso prosseguir",
    "confirma?",
    "confirmar?",
    "quer que eu",
    "devo prosseguir",
    "devo continuar",
    "pode confirmar",
    "gostaria de confirmar",
    "está correto",
    "deseja prosseguir",
    "posso executar",
    "devo executar",
  ];
  return confirmPatterns.some((p) => lower.includes(p));
}

function appendConfirmationButtons(messageDiv) {
  const bubble = messageDiv.querySelector(".bubble");
  if (!bubble) return;
  const actions = document.createElement("div");
  actions.className = "confirmation-actions";
  actions.innerHTML =
    '<button class="confirm-btn" onclick="handleConfirmAction(this, true)"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg> Confirmar</button>' +
    '<button class="cancel-btn" onclick="handleConfirmAction(this, false)"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg> Cancelar</button>';
  bubble.after(actions);
}

function handleConfirmAction(btn, confirmed) {
  const actionsDiv = btn.closest(".confirmation-actions");
  if (actionsDiv) {
    actionsDiv.innerHTML =
      '<span style="font-size:0.8rem;color:var(--subtle);font-weight:500;">' +
      (confirmed ? "✅ Confirmado" : "❌ Cancelado") +
      "</span>";
  }
  // Send confirmation to chat
  const text = confirmed ? "Sim, pode continuar." : "Não, cancele a operação.";
  const input = document.getElementById("chat-input");
  input.value = text;
  sendChat();
}

// Disable send button when input is empty
document.addEventListener("DOMContentLoaded", () => {
  const chatInput = document.getElementById("chat-input");
  const sendBtn = document.getElementById("send-btn");
  if (chatInput && sendBtn) {
    sendBtn.disabled = true;
    chatInput.addEventListener("input", () => {
      sendBtn.disabled = !chatInput.value.trim();
    });
  }
});

function addMessage(role, content, opts) {
  opts = opts || {};
  const container = document.getElementById("chat-messages");
  const time = new Date().toLocaleTimeString("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  });
  const div = document.createElement("div");
  div.className = "message " + role;
  let rendered;
  if (role === "assistant" && typeof marked !== "undefined") {
    rendered = marked.parse(content);
  } else {
    rendered = escapeHtml(content);
  }
  div.innerHTML =
    '<div class="bubble">' +
    rendered +
    '</div><span class="msg-time">' +
    time +
    "</span>";
  container.appendChild(div);

  if (role === "assistant" && isConfirmationMessage(content)) {
    appendConfirmationButtons(div);
  }

  scrollChat();
}

function showTyping() {
  const container = document.getElementById("chat-messages");
  const div = document.createElement("div");
  div.id = "typing-indicator";
  div.className = "typing-indicator";
  div.innerHTML =
    '<div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div>';
  container.appendChild(div);
  scrollChat();
}

function hideTyping() {
  const el = document.getElementById("typing-indicator");
  if (el) el.remove();
}

function scrollChat() {
  const container = document.getElementById("chat-messages");
  setTimeout(() => (container.scrollTop = container.scrollHeight), 50);
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

// ============ APPS ============
async function loadApps() {
  try {
    const res = await fetch(
      SUPABASE_URL + "/rest/v1/cloud_apps?select=*&order=name",
      {
        headers: {
          apikey: SUPABASE_ANON_KEY,
          Authorization: "Bearer " + authToken,
        },
      },
    );
    apps = await res.json();
  } catch (e) {
    apps = [
      {
        name: "Ponta do S",
        icon_name: "leaf.fill",
        status: "installed",
        category: "Agronegocio",
      },
      {
        name: "Rumo Maquinas",
        icon_name: "gearshape.2.fill",
        status: "installed",
        category: "Agronegocio",
      },
      {
        name: "Aegro",
        icon_name: "chart.bar.fill",
        status: "installed",
        category: "Agronegocio",
      },
      {
        name: "Conta Azul",
        icon_name: "creditcard.fill",
        status: "installed",
        category: "Financeiro",
      },
      {
        name: "Excel Online",
        icon_name: "tablecells.fill",
        status: "installed",
        category: "Produtividade",
      },
      {
        name: "Google Sheets",
        icon_name: "doc.text.fill",
        status: "installed",
        category: "Produtividade",
      },
      {
        name: "WhatsApp Web",
        icon_name: "message.fill",
        status: "installed",
        category: "Comunicacao",
      },
      {
        name: "Slack",
        icon_name: "bubble.left",
        status: "not_installed",
        category: "Comunicacao",
      },
    ];
  }
  renderApps();
  renderCategoryFilter();
}

function filterApps() {
  renderApps();
}

function renderApps() {
  const grid = document.getElementById("apps-grid");
  const searchInput = document.getElementById("apps-search");
  const searchText = searchInput ? searchInput.value.trim().toLowerCase() : "";
  let filtered = selectedCategory
    ? apps.filter((a) => a.category === selectedCategory)
    : apps;
  if (searchText) {
    filtered = filtered.filter((a) =>
      a.name.toLowerCase().includes(searchText),
    );
  }

  const icons = {
    Agronegocio: "🌿",
    Agronegócio: "🌿",
    Financeiro: "💳",
    Produtividade: "📊",
    Comunicacao: "💬",
    Comunicação: "💬",
    Outros: "📦",
  };
  const colors = {
    Agronegocio: "52,211,153",
    Agronegócio: "52,211,153",
    Financeiro: "59,130,246",
    Produtividade: "249,115,22",
    Comunicacao: "168,85,247",
    Comunicação: "168,85,247",
    Outros: "156,163,175",
  };
  const statusNames = {
    installed: "Instalado",
    running: "Executando",
    installing: "Instalando...",
    not_installed: "Nao instalado",
  };
  const statusColors = {
    installed: "var(--subtle)",
    running: "#22C55E",
    installing: "#F59E0B",
    not_installed: "rgba(239,68,68,0.6)",
  };

  grid.innerHTML = filtered
    .map((app) => {
      const icon = icons[app.category] || "📦";
      const c = colors[app.category] || "156,163,175";
      return (
        '<div class="app-card' +
        (app.is_selected ? " selected" : "") +
        '" onclick="toggleApp(\'' +
        app.name +
        "')\">" +
        '<div class="app-card-icon" style="background:rgba(' +
        c +
        ',0.12)">' +
        icon +
        "</div>" +
        '<div class="app-card-name">' +
        app.name +
        "</div>" +
        '<div class="app-card-status" style="color:' +
        (statusColors[app.status] || "var(--subtle)") +
        '">' +
        (statusNames[app.status] || app.status) +
        "</div>" +
        "</div>"
      );
    })
    .join("");
}

function renderCategoryFilter() {
  const cats = [...new Set(apps.map((a) => a.category))];
  const container = document.getElementById("category-filter");
  container.innerHTML =
    '<button class="cat-chip' +
    (!selectedCategory ? " active" : "") +
    '" onclick="filterCategory(null)">Todos</button>' +
    cats
      .map(
        (c) =>
          '<button class="cat-chip' +
          (selectedCategory === c ? " active" : "") +
          '" onclick="filterCategory(\'' +
          c +
          "')\">" +
          c +
          "</button>",
      )
      .join("");
}

function filterCategory(cat) {
  selectedCategory = cat;
  renderApps();
  renderCategoryFilter();
}

function toggleApp(name) {
  apps.forEach(
    (a) => (a.is_selected = a.name === name ? !a.is_selected : false),
  );
  selectedApp = apps.find((a) => a.is_selected) || null;
  renderApps();
  loadAppSelector();
}

function loadAppSelector() {
  const container = document.getElementById("app-selector");
  const installed = apps.filter(
    (a) => a.status === "installed" || a.status === "running",
  );
  container.innerHTML =
    '<button class="app-chip' +
    (!selectedApp ? " active" : "") +
    '" onclick="selectChatApp(null)">✨ Geral</button>' +
    installed
      .map(
        (a) =>
          '<button class="app-chip' +
          (selectedApp?.name === a.name ? " active" : "") +
          '" onclick="selectChatApp(\'' +
          a.name +
          "')\">" +
          a.name +
          "</button>",
      )
      .join("");
}

function selectChatApp(name) {
  selectedApp = name ? apps.find((a) => a.name === name) : null;
  loadAppSelector();
}

// ============ SUBSCRIPTION ============
let selectedPlan = "pro";

function showSubscription() {
  const modal = document.getElementById("subscription-modal");
  modal.style.display = "flex";
  requestAnimationFrame(() => {
    modal.style.opacity = "1";
  });
  updateUsageOverview();
  updateFeaturesForPlan(selectedPlan);
}

function updateUsageOverview() {
  if (!currentUser) return;
  const maxCredits =
    { free: 10, starter: 100, pro: 500, enterprise: 2000 }[currentUser.plan] ||
    10;
  const el = document.getElementById("usage-current");
  const totalEl = document.getElementById("usage-total");
  const bar = document.getElementById("usage-bar");
  if (el) el.textContent = currentUser.credits;
  if (totalEl) totalEl.textContent = "/ " + maxCredits;
  if (bar)
    bar.style.width =
      Math.min((currentUser.credits / maxCredits) * 100, 100) + "%";
}

function updateFeaturesForPlan(plan) {
  const streaming = document.querySelector("#feature-streaming .feature-check");
  const support = document.querySelector("#feature-support .feature-check");
  const dedicated = document.querySelector("#feature-dedicated .feature-check");
  if (streaming) streaming.classList.toggle("included", plan !== "free");
  if (support)
    support.classList.toggle(
      "included",
      plan === "pro" || plan === "enterprise",
    );
  if (dedicated) dedicated.classList.toggle("included", plan === "enterprise");
  // First 3 always included
  document
    .querySelectorAll(".feature-row:nth-child(-n+3) .feature-check")
    .forEach((el) => el.classList.add("included"));
}
function closeSubscription() {
  const modal = document.getElementById("subscription-modal");
  const content = modal.querySelector(".modal-content");
  content.style.transform = "translateY(100%)";
  modal.style.opacity = "0";
  setTimeout(() => {
    modal.style.display = "none";
    content.style.transform = "";
    modal.style.opacity = "";
  }, 350);
}

function selectPlan(plan) {
  selectedPlan = plan;
  document.querySelectorAll(".plan-option").forEach((el) => {
    el.classList.toggle("selected", el.dataset.plan === plan);
  });
  const names = {
    free: "Gratuito",
    starter: "Starter",
    pro: "Pro",
    enterprise: "Enterprise",
  };
  document.getElementById("subscribe-btn").textContent =
    plan === "free" ? "Plano atual" : "Assinar " + names[plan];
  document.getElementById("subscribe-btn").disabled = plan === "free";
  updateFeaturesForPlan(plan);
}

async function subscribe() {
  if (selectedPlan === "free" || !currentUser) return;
  try {
    const res = await fetch(API_URL + "/create-checkout", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + authToken,
      },
      body: JSON.stringify({ plan: selectedPlan }),
    });
    const data = await res.json();
    if (data.url) window.open(data.url, "_blank");
  } catch (e) {
    alert("Erro ao criar checkout");
  }
}

async function buyCredits(amount) {
  if (!currentUser) return;
  try {
    const res = await fetch(API_URL + "/buy-credits", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + authToken,
      },
      body: JSON.stringify({ amount }),
    });
    const data = await res.json();
    if (data.url) window.open(data.url, "_blank");
  } catch (e) {
    alert("Erro ao comprar creditos");
  }
}

// ============ CREDITS USED TODAY ============
function getCreditsUsedTodayKey() {
  return "credits_used_" + new Date().toISOString().split("T")[0];
}

function getCreditsUsedToday() {
  return parseInt(localStorage.getItem(getCreditsUsedTodayKey()) || "0", 10);
}

function incrementCreditsUsed() {
  const key = getCreditsUsedTodayKey();
  const current = parseInt(localStorage.getItem(key) || "0", 10);
  localStorage.setItem(key, current + 1);
  document.getElementById("credits-used").textContent = current + 1;
}

// ============ DELETE ACCOUNT ============
async function deleteAccount() {
  if (
    !confirm(
      "Tem certeza? Esta ação é irreversível e todos os seus dados serão excluídos permanentemente.",
    )
  )
    return;
  try {
    const res = await fetch("https://agente.agrorumo.com/api/delete-account", {
      method: "DELETE",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + authToken,
      },
    });
    if (res.ok) {
      showSnackbar("Conta excluída com sucesso.", null, null);
      logout(true);
    } else {
      const data = await res.json().catch(() => ({}));
      alert("Erro ao excluir conta: " + (data.error || "Tente novamente."));
    }
  } catch (e) {
    alert("Erro ao excluir conta. Verifique sua conexão e tente novamente.");
  }
}

// ============ SETTINGS ============
function settingsIdioma() {
  showToast("Apenas Português disponível no momento");
}

function settingsNotificacoes(row) {
  const current = localStorage.getItem("rumo_notificacoes") || "Ativadas";
  const next = current === "Ativadas" ? "Desativadas" : "Ativadas";
  localStorage.setItem("rumo_notificacoes", next);
  const valueEl = document.getElementById("notif-value");
  if (valueEl) valueEl.textContent = next;

  if (next === "Ativadas" && "Notification" in window) {
    Notification.requestPermission();
  }
}

function settingsAparencia(row) {
  const cycle = ["Automático", "Escuro", "Claro"];
  const current = localStorage.getItem("rumo_aparencia") || "Automático";
  const idx = cycle.indexOf(current);
  const next = cycle[(idx + 1) % cycle.length];
  localStorage.setItem("rumo_aparencia", next);
  const valueEl = document.getElementById("aparencia-value");
  if (valueEl) valueEl.textContent = next;
}

// Load saved settings on init
document.addEventListener("DOMContentLoaded", () => {
  const savedNotif = localStorage.getItem("rumo_notificacoes");
  if (savedNotif) {
    const el = document.getElementById("notif-value");
    if (el) el.textContent = savedNotif;
  }
  const savedAparencia = localStorage.getItem("rumo_aparencia");
  if (savedAparencia) {
    const el = document.getElementById("aparencia-value");
    if (el) el.textContent = savedAparencia;
  }
});

// ============ TOAST NOTIFICATIONS ============
function showToast(message, duration) {
  duration = duration || 2500;
  const toast = document.getElementById("toast");
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => {
    toast.classList.remove("show");
  }, duration);
}

// ============ HAPTIC FEEDBACK (vibration API) ============
function triggerHaptic() {
  if (navigator.vibrate) navigator.vibrate(8);
}

// ============ BUTTON PRESS ANIMATIONS ============
function initHapticButtons() {
  document
    .querySelectorAll(
      ".quick-btn, .app-card, .settings-row, .plan-card, .credit-pack, .plan-option",
    )
    .forEach(function (el) {
      el.addEventListener(
        "touchstart",
        function () {
          this.style.transform = "scale(0.97)";
          this.style.transition = "transform 0.1s ease";
        },
        { passive: true },
      );
      el.addEventListener(
        "touchend",
        function () {
          this.style.transform = "";
          this.style.transition = "transform 0.3s cubic-bezier(0.16,1,0.3,1)";
        },
        { passive: true },
      );
    });
}

// ============ INTERSECTION OBSERVER FOR ANIMATIONS ============
document.addEventListener("DOMContentLoaded", () => {
  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("animate-in");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.1 },
    );

    document
      .querySelectorAll(
        ".card, .quick-actions, .section-title, .settings-row, .profile-header, .plan-card",
      )
      .forEach((el) => {
        observer.observe(el);
      });
  }
});

// ============ SWIPE GESTURES FOR ONBOARDING ============
document.addEventListener("DOMContentLoaded", () => {
  const slides = document.getElementById("onboarding-slides");
  if (!slides) return;
  let touchStartX = 0;
  let touchEndX = 0;
  slides.addEventListener(
    "touchstart",
    (e) => {
      touchStartX = e.changedTouches[0].screenX;
    },
    { passive: true },
  );
  slides.addEventListener(
    "touchend",
    (e) => {
      touchEndX = e.changedTouches[0].screenX;
      const diff = touchStartX - touchEndX;
      if (Math.abs(diff) > 60) {
        if (diff > 0 && onboardingStep < 2) {
          onboardingStep++;
          updateOnboarding();
        } else if (diff < 0 && onboardingStep > 0) {
          onboardingStep--;
          updateOnboarding();
        }
      }
    },
    { passive: true },
  );
});

// ============ SERVICE WORKER REGISTRATION ============
function registerServiceWorker() {
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker
      .register("/sw.js")
      .then((reg) => {
        reg.addEventListener("updatefound", () => {
          const newWorker = reg.installing;
          if (!newWorker) return;
          newWorker.addEventListener("statechange", () => {
            if (
              newWorker.state === "installed" &&
              navigator.serviceWorker.controller
            ) {
              showUpdateBanner();
            }
          });
        });
      })
      .catch(() => {});
  }
}

function showUpdateBanner() {
  let banner = document.getElementById("update-banner");
  if (!banner) {
    banner = document.createElement("div");
    banner.id = "update-banner";
    banner.className = "update-banner";
    banner.innerHTML =
      '<span>Nova versão disponível!</span><button onclick="applyUpdate()">Atualizar</button>';
    document.body.appendChild(banner);
  }
  requestAnimationFrame(() => banner.classList.add("show"));
}

function applyUpdate() {
  if (navigator.serviceWorker.controller) {
    navigator.serviceWorker.ready.then((reg) => {
      if (reg.waiting) reg.waiting.postMessage({ type: "SKIP_WAITING" });
    });
  }
  window.location.reload();
}

// ============ PWA INSTALL ============
function initPWAInstall() {
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    deferredInstallPrompt = e;

    if (!localStorage.getItem("install_banner_dismissed")) {
      setTimeout(() => showInstallBanner(), 3000);
    }

    const pwaBanner = document.getElementById("pwa-install-banner");
    if (pwaBanner) pwaBanner.style.display = "block";
  });

  window.addEventListener("appinstalled", () => {
    deferredInstallPrompt = null;
    hideInstallBanner();
    showSnackbar("App instalado com sucesso!", "Abrir", () => {});
    const pwaBanner = document.getElementById("pwa-install-banner");
    if (pwaBanner) pwaBanner.style.display = "none";
  });

  if (
    window.matchMedia("(display-mode: standalone)").matches ||
    window.navigator.standalone
  ) {
    const pwaBanner = document.getElementById("pwa-install-banner");
    if (pwaBanner) pwaBanner.style.display = "none";
  }
}

function showInstallBanner() {
  const banner = document.getElementById("android-install-banner");
  if (banner && deferredInstallPrompt) {
    banner.classList.add("show");
  }
}

function hideInstallBanner() {
  const banner = document.getElementById("android-install-banner");
  if (banner) banner.classList.remove("show");
}

function dismissInstallBanner() {
  hideInstallBanner();
  localStorage.setItem("install_banner_dismissed", "true");
}

function installPWA() {
  if (deferredInstallPrompt) {
    deferredInstallPrompt.prompt();
    deferredInstallPrompt.userChoice.then((choice) => {
      if (choice.outcome === "accepted") {
        hideInstallBanner();
      }
      deferredInstallPrompt = null;
    });
  } else {
    showToast('Abra no Chrome e toque em "Adicionar à tela inicial"');
  }
}

// ============ OFFLINE DETECTION ============
function initOfflineDetection() {
  const offlineBar = document.getElementById("offline-bar");
  if (!offlineBar) return;

  function updateOnlineStatus() {
    if (!navigator.onLine) {
      offlineBar.classList.add("show");
      document.body.style.paddingTop = "32px";
    } else {
      offlineBar.classList.remove("show");
      document.body.style.paddingTop = "";
    }
  }

  window.addEventListener("online", () => {
    updateOnlineStatus();
    showSnackbar("Conexão restaurada", null, null);
  });
  window.addEventListener("offline", updateOnlineStatus);
  updateOnlineStatus();
}

// ============ SNACKBAR (ANDROID-STYLE) ============
function showSnackbar(message, actionText, actionCallback) {
  const snackbar = document.getElementById("snackbar");
  if (!snackbar) return;

  let html = "<span>" + escapeHtml(message) + "</span>";
  if (actionText && actionCallback) {
    html +=
      '<button class="snackbar-action" id="snackbar-action-btn">' +
      escapeHtml(actionText) +
      "</button>";
  }
  snackbar.innerHTML = html;
  snackbar.classList.add("show");

  if (actionCallback) {
    const btn = document.getElementById("snackbar-action-btn");
    if (btn)
      btn.addEventListener("click", () => {
        actionCallback();
        snackbar.classList.remove("show");
      });
  }

  clearTimeout(snackbar._timer);
  snackbar._timer = setTimeout(() => snackbar.classList.remove("show"), 4000);
}

// ============ DEEP LINK HANDLING ============
function handleDeepLink() {
  const params = new URLSearchParams(window.location.search);
  const tab = params.get("tab");
  if (tab && ["dashboard", "screen", "chat", "apps", "profile"].includes(tab)) {
    const waitForApp = setInterval(() => {
      if (document.getElementById("app-screen").classList.contains("active")) {
        switchTab(tab);
        clearInterval(waitForApp);
      }
    }, 200);
    setTimeout(() => clearInterval(waitForApp), 10000);
  }
}

// ============ ANDROID BACK BUTTON ============
window.addEventListener("popstate", (e) => {
  const activePanel = document.querySelector(".tab-panel.active");
  if (activePanel && activePanel.id !== "tab-dashboard") {
    e.preventDefault();
    switchTab("dashboard");
    history.pushState(null, "", window.location.pathname);
  }
});

if (window.matchMedia("(display-mode: standalone)").matches) {
  history.pushState(null, "", window.location.pathname);
}
