-- ============================================
-- Tabelas para Memoria, Credenciais e Workflows
-- Executar no Supabase SQL Editor
-- ============================================

-- 1. MEMORIA DO AGENTE - preferencias e aprendizado por usuario
CREATE TABLE IF NOT EXISTS agent_memory (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  category text NOT NULL, -- 'preference', 'app_usage', 'learned_fact', 'software_knowledge'
  key text NOT NULL,
  value jsonb NOT NULL,
  confidence real DEFAULT 1.0, -- 0-1, quanto o sistema confia nessa memoria
  usage_count integer DEFAULT 1,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now(),
  UNIQUE(user_id, category, key)
);

CREATE INDEX idx_agent_memory_user ON agent_memory(user_id);
CREATE INDEX idx_agent_memory_category ON agent_memory(user_id, category);

-- 2. CREDENCIAIS SEGURAS - cofre de logins para sistemas terceiros
CREATE TABLE IF NOT EXISTS secure_credentials (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  service_name text NOT NULL, -- 'aegro', 'conta_azul', 'siagri', etc
  service_url text,
  username text, -- pode ser email, CPF, etc
  encrypted_password text NOT NULL, -- criptografado com AES-256
  extra_fields jsonb, -- campos extras (fazenda, CNPJ, etc)
  last_used_at timestamptz,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now(),
  UNIQUE(user_id, service_name)
);

CREATE INDEX idx_secure_credentials_user ON secure_credentials(user_id);

-- RLS: usuario so ve suas proprias credenciais
ALTER TABLE secure_credentials ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage own credentials" ON secure_credentials
  FOR ALL USING (auth.uid() = user_id);

-- 3. WORKFLOWS APRENDIDOS - sequencias de acoes que funcionaram
CREATE TABLE IF NOT EXISTS learned_workflows (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL, -- 'login_aegro', 'criar_planilha_gado', etc
  description text,
  trigger_phrases text[], -- frases que ativam esse workflow
  steps jsonb NOT NULL, -- array de { tool, input, expected_result }
  target_software text, -- 'aegro', 'conta_azul', 'firefox', etc
  success_count integer DEFAULT 1,
  fail_count integer DEFAULT 0,
  is_active boolean DEFAULT true,
  version integer DEFAULT 1,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

CREATE INDEX idx_workflows_user ON learned_workflows(user_id);
CREATE INDEX idx_workflows_software ON learned_workflows(user_id, target_software);

-- 4. LOG DE ACOES - historico completo de tudo que o agente fez
CREATE TABLE IF NOT EXISTS agent_action_log (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  session_id text, -- agrupa acoes de uma mesma interacao
  tool_name text NOT NULL,
  tool_input jsonb,
  tool_result text,
  success boolean DEFAULT true,
  requires_confirmation boolean DEFAULT false,
  user_confirmed boolean,
  created_at timestamptz DEFAULT now()
);

CREATE INDEX idx_action_log_user ON agent_action_log(user_id);
CREATE INDEX idx_action_log_session ON agent_action_log(session_id);

-- 5. PENDING CONFIRMATIONS - acoes aguardando confirmacao do usuario
CREATE TABLE IF NOT EXISTS pending_confirmations (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  action_description text NOT NULL,
  tool_name text NOT NULL,
  tool_input jsonb NOT NULL,
  risk_level text DEFAULT 'medium', -- 'low', 'medium', 'high', 'critical'
  status text DEFAULT 'pending', -- 'pending', 'approved', 'rejected', 'expired'
  expires_at timestamptz DEFAULT (now() + interval '5 minutes'),
  created_at timestamptz DEFAULT now(),
  resolved_at timestamptz
);

CREATE INDEX idx_pending_user ON pending_confirmations(user_id, status);

-- RLS para todas as tabelas
ALTER TABLE agent_memory ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users own memory" ON agent_memory FOR ALL USING (auth.uid() = user_id);

ALTER TABLE learned_workflows ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users own workflows" ON learned_workflows FOR ALL USING (auth.uid() = user_id);

ALTER TABLE agent_action_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users own logs" ON agent_action_log FOR ALL USING (auth.uid() = user_id);

ALTER TABLE pending_confirmations ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users own confirmations" ON pending_confirmations FOR ALL USING (auth.uid() = user_id);
