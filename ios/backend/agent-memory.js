const crypto = require('crypto');

// Encryption key from env (32 bytes for AES-256)
const ENCRYPTION_KEY = process.env.CREDENTIAL_ENCRYPTION_KEY || 'rumo-agente-default-key-32bytes!';
if (!process.env.CREDENTIAL_ENCRYPTION_KEY) {
  console.warn('[SECURITY] CREDENTIAL_ENCRYPTION_KEY não configurada! Usando chave padrão. Defina no .env para produção.');
}
const IV_LENGTH = 16;

function encrypt(text) {
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv('aes-256-cbc', Buffer.from(ENCRYPTION_KEY, 'utf-8').subarray(0, 32), iv);
  let encrypted = cipher.update(text, 'utf8', 'hex');
  encrypted += cipher.final('hex');
  return iv.toString('hex') + ':' + encrypted;
}

function decrypt(text) {
  const parts = text.split(':');
  const iv = Buffer.from(parts.shift(), 'hex');
  const encrypted = parts.join(':');
  const decipher = crypto.createDecipheriv('aes-256-cbc', Buffer.from(ENCRYPTION_KEY, 'utf-8').subarray(0, 32), iv);
  let decrypted = decipher.update(encrypted, 'hex', 'utf8');
  decrypted += decipher.final('utf8');
  return decrypted;
}

// ============================================
// 1. MEMORIA DO AGENTE
// ============================================

async function getMemoryContext(supabase, userId) {
  // Fetch all memory for this user to build context
  const { data: memories } = await supabase
    .from('agent_memory')
    .select('category, key, value, usage_count')
    .eq('user_id', userId)
    .order('usage_count', { ascending: false })
    .limit(50);

  if (!memories || memories.length === 0) return '';

  const sections = {
    preference: [],
    app_usage: [],
    learned_fact: [],
    software_knowledge: []
  };

  for (const m of memories) {
    const cat = m.category;
    if (sections[cat]) {
      sections[cat].push(m);
    }
  }

  let context = '\n\n--- MEMORIA DO USUARIO ---\n';

  if (sections.preference.length > 0) {
    context += '\nPreferencias:\n';
    for (const p of sections.preference) {
      context += `- ${p.key}: ${JSON.stringify(p.value)}\n`;
    }
  }

  if (sections.app_usage.length > 0) {
    context += '\nApps mais usados:\n';
    for (const a of sections.app_usage) {
      context += `- ${a.key} (usado ${a.usage_count}x)\n`;
    }
  }

  if (sections.learned_fact.length > 0) {
    context += '\nFatos aprendidos:\n';
    for (const f of sections.learned_fact) {
      context += `- ${f.key}: ${JSON.stringify(f.value)}\n`;
    }
  }

  if (sections.software_knowledge.length > 0) {
    context += '\nConhecimento sobre softwares:\n';
    for (const s of sections.software_knowledge) {
      context += `- ${s.key}: ${JSON.stringify(s.value)}\n`;
    }
  }

  return context;
}

async function saveMemory(supabase, userId, category, key, value) {
  const { data: existing } = await supabase
    .from('agent_memory')
    .select('id, usage_count')
    .eq('user_id', userId)
    .eq('category', category)
    .eq('key', key)
    .single();

  if (existing) {
    await supabase.from('agent_memory').update({
      value,
      usage_count: existing.usage_count + 1,
      updated_at: new Date().toISOString()
    }).eq('id', existing.id);
  } else {
    await supabase.from('agent_memory').insert({
      user_id: userId, category, key, value
    });
  }
}

async function trackAppUsage(supabase, userId, appName) {
  await saveMemory(supabase, userId, 'app_usage', appName, { last_used: new Date().toISOString() });
}

// ============================================
// 2. CREDENCIAIS SEGURAS
// ============================================

async function saveCredential(supabase, userId, serviceName, serviceUrl, username, password, extraFields) {
  const encryptedPassword = encrypt(password);

  const { error } = await supabase.from('secure_credentials').upsert({
    user_id: userId,
    service_name: serviceName,
    service_url: serviceUrl || null,
    username,
    encrypted_password: encryptedPassword,
    extra_fields: extraFields || null,
    updated_at: new Date().toISOString()
  }, { onConflict: 'user_id,service_name' });

  return !error;
}

async function getCredential(supabase, userId, serviceName) {
  const { data } = await supabase.from('secure_credentials')
    .select('*')
    .eq('user_id', userId)
    .eq('service_name', serviceName)
    .single();

  if (!data) return null;

  // Update last_used
  await supabase.from('secure_credentials')
    .update({ last_used_at: new Date().toISOString() })
    .eq('id', data.id);

  return {
    service_name: data.service_name,
    service_url: data.service_url,
    username: data.username,
    password: decrypt(data.encrypted_password),
    extra_fields: data.extra_fields
  };
}

async function listCredentials(supabase, userId) {
  const { data } = await supabase.from('secure_credentials')
    .select('service_name, service_url, username, last_used_at')
    .eq('user_id', userId)
    .order('last_used_at', { ascending: false });

  return data || [];
}

async function deleteCredential(supabase, userId, serviceName) {
  const { error } = await supabase.from('secure_credentials')
    .delete()
    .eq('user_id', userId)
    .eq('service_name', serviceName);

  return !error;
}

// ============================================
// 3. CONFIRMACAO DE ACOES CRITICAS
// ============================================

// Actions that require confirmation
const CRITICAL_ACTIONS = {
  // High risk - always confirm
  'run_command': {
    patterns: [/rm\s+-rf/, /sudo\s+/, /apt\s+remove/, /pip\s+uninstall/, /npm\s+uninstall/, /shutdown/, /reboot/,
               /drop\s+table/i, /delete\s+from/i, /format/i],
    risk: 'high'
  },
  // Medium risk - confirm on certain patterns
  'type_text': {
    patterns: [/senha/i, /password/i, /cartao/i, /card/i, /pix/i, /transferir/i, /pagar/i, /comprar/i],
    risk: 'medium'
  },
  'click': {
    // Clicks near common "delete", "send", "pay" buttons
    risk: 'low'
  }
};

function shouldConfirm(toolName, toolInput) {
  const config = CRITICAL_ACTIONS[toolName];
  if (!config) return null;

  if (toolName === 'run_command' && config.patterns) {
    for (const pattern of config.patterns) {
      if (pattern.test(toolInput.command)) {
        return {
          risk: 'high',
          reason: `Comando potencialmente perigoso: "${toolInput.command}"`
        };
      }
    }
  }

  if (toolName === 'type_text' && config.patterns) {
    for (const pattern of config.patterns) {
      if (pattern.test(toolInput.text)) {
        return {
          risk: 'medium',
          reason: `Digitando informacao sensivel`
        };
      }
    }
  }

  return null;
}

async function createConfirmation(supabase, userId, toolName, toolInput, description, riskLevel) {
  const { data, error } = await supabase.from('pending_confirmations').insert({
    user_id: userId,
    action_description: description,
    tool_name: toolName,
    tool_input: toolInput,
    risk_level: riskLevel,
    expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString()
  }).select('id').single();

  return data?.id;
}

async function resolveConfirmation(supabase, confirmationId, approved, userId) {
  const query = supabase.from('pending_confirmations')
    .update({
      status: approved ? 'approved' : 'rejected',
      resolved_at: new Date().toISOString()
    })
    .eq('id', confirmationId)
    .eq('status', 'pending');

  if (userId) query.eq('user_id', userId);

  const { data, error } = await query.select('*').single();
  return data;
}

async function getPendingConfirmations(supabase, userId) {
  const { data } = await supabase.from('pending_confirmations')
    .select('*')
    .eq('user_id', userId)
    .eq('status', 'pending')
    .gt('expires_at', new Date().toISOString())
    .order('created_at', { ascending: false });

  return data || [];
}

// ============================================
// 4. WORKFLOW LEARNING
// ============================================

async function saveWorkflow(supabase, userId, name, description, triggerPhrases, steps, targetSoftware) {
  // Check if similar workflow exists
  const { data: existing } = await supabase.from('learned_workflows')
    .select('id, success_count, version')
    .eq('user_id', userId)
    .eq('name', name)
    .single();

  if (existing) {
    // Update existing workflow with new version
    await supabase.from('learned_workflows').update({
      steps,
      trigger_phrases: triggerPhrases,
      description,
      success_count: existing.success_count + 1,
      version: existing.version + 1,
      updated_at: new Date().toISOString()
    }).eq('id', existing.id);
    return existing.id;
  } else {
    const { data } = await supabase.from('learned_workflows').insert({
      user_id: userId,
      name,
      description,
      trigger_phrases: triggerPhrases,
      steps,
      target_software: targetSoftware
    }).select('id').single();
    return data?.id;
  }
}

async function findWorkflow(supabase, userId, userMessage) {
  // Search workflows by trigger phrases
  const { data: workflows } = await supabase.from('learned_workflows')
    .select('*')
    .eq('user_id', userId)
    .eq('is_active', true)
    .order('success_count', { ascending: false });

  if (!workflows || workflows.length === 0) return null;

  const msgLower = userMessage.toLowerCase();

  // Score each workflow by how well it matches the user message
  let bestMatch = null;
  let bestScore = 0;

  for (const wf of workflows) {
    let score = 0;
    if (wf.trigger_phrases) {
      for (const phrase of wf.trigger_phrases) {
        if (msgLower.includes(phrase.toLowerCase())) {
          score += 2;
        }
      }
    }
    // Also check name and description
    if (wf.name && msgLower.includes(wf.name.toLowerCase())) score += 3;
    if (wf.description) {
      const words = wf.description.toLowerCase().split(/\s+/);
      for (const word of words) {
        if (word.length > 3 && msgLower.includes(word)) score += 0.5;
      }
    }

    if (score > bestScore) {
      bestScore = score;
      bestMatch = wf;
    }
  }

  // Require minimum score to avoid false matches
  return bestScore >= 2 ? bestMatch : null;
}

async function markWorkflowResult(supabase, workflowId, success) {
  const field = success ? 'success_count' : 'fail_count';
  const { data } = await supabase.from('learned_workflows')
    .select(field)
    .eq('id', workflowId)
    .single();

  if (data) {
    await supabase.from('learned_workflows').update({
      [field]: (data[field] || 0) + 1,
      // Deactivate if too many failures
      is_active: success ? true : ((data.fail_count || 0) + 1 < 5),
      updated_at: new Date().toISOString()
    }).eq('id', workflowId);
  }
}

async function getWorkflowContext(supabase, userId) {
  const { data: workflows } = await supabase.from('learned_workflows')
    .select('name, description, trigger_phrases, target_software, success_count')
    .eq('user_id', userId)
    .eq('is_active', true)
    .order('success_count', { ascending: false })
    .limit(10);

  if (!workflows || workflows.length === 0) return '';

  let context = '\n\n--- WORKFLOWS APRENDIDOS ---\n';
  context += 'Estes sao fluxos que o usuario ja executou com sucesso:\n';
  for (const wf of workflows) {
    context += `- "${wf.name}" (${wf.target_software || 'geral'}, ${wf.success_count}x sucesso): ${wf.description || ''}\n`;
    if (wf.trigger_phrases?.length > 0) {
      context += `  Frases: ${wf.trigger_phrases.join(', ')}\n`;
    }
  }

  return context;
}

// ============================================
// 5. ACTION LOGGING
// ============================================

async function logAction(supabase, userId, sessionId, toolName, toolInput, result, success, requiresConfirmation, userConfirmed) {
  await supabase.from('agent_action_log').insert({
    user_id: userId,
    session_id: sessionId,
    tool_name: toolName,
    tool_input: toolInput,
    tool_result: typeof result === 'string' ? result : JSON.stringify(result),
    success,
    requires_confirmation: requiresConfirmation || false,
    user_confirmed: userConfirmed
  });
}

// ============================================
// 6. AUTO-LEARNING from actions
// ============================================

function extractLearnings(actions, userMessage) {
  const learnings = [];

  for (const action of actions) {
    // Track app usage
    if (action.tool === 'open_app') {
      learnings.push({
        category: 'app_usage',
        key: action.input.app_name,
        value: { last_used: new Date().toISOString(), context: userMessage.substring(0, 100) }
      });
    }

    // Track URLs visited
    if (action.tool === 'type_text' && action.input.text?.match(/^https?:\/\//)) {
      learnings.push({
        category: 'learned_fact',
        key: `url_frequente_${action.input.text.split('/')[2]}`,
        value: { url: action.input.text, context: userMessage.substring(0, 100) }
      });
    }

    // Track software used
    if (action.tool === 'run_command' && action.input.command) {
      const cmd = action.input.command.split(' ')[0];
      learnings.push({
        category: 'software_knowledge',
        key: `comando_${cmd}`,
        value: { full_command: action.input.command, context: userMessage.substring(0, 100) }
      });
    }
  }

  return learnings;
}

module.exports = {
  // Memory
  getMemoryContext,
  saveMemory,
  trackAppUsage,
  extractLearnings,
  // Credentials
  encrypt,
  decrypt,
  saveCredential,
  getCredential,
  listCredentials,
  deleteCredential,
  // Confirmation
  shouldConfirm,
  createConfirmation,
  resolveConfirmation,
  getPendingConfirmations,
  // Workflows
  saveWorkflow,
  findWorkflow,
  markWorkflowResult,
  getWorkflowContext,
  // Logging
  logAction
};
