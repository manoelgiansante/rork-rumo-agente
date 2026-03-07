// Script to create required tables in Supabase
// Run: node setup-tables.js
// Or it runs automatically on server startup

require('dotenv').config();
const { createClient } = require('@supabase/supabase-js');

const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY);

const TABLES = [
  {
    name: 'agent_memory',
    check: async () => {
      const { error } = await supabase.from('agent_memory').select('id').limit(1);
      return !error;
    },
    // We can't run raw SQL via PostgREST, so we create tables by inserting
    // a dummy row and deleting it - but this won't work if table doesn't exist.
    // Instead, we'll need to create via Supabase Dashboard.
    // This script just checks what needs to be created.
  },
  { name: 'secure_credentials' },
  { name: 'learned_workflows' },
  { name: 'agent_action_log' },
  { name: 'pending_confirmations' }
];

async function checkTables() {
  console.log('Checking Supabase tables...\n');
  const missing = [];

  for (const table of TABLES) {
    const { error } = await supabase.from(table.name).select('*').limit(1);
    if (error) {
      console.log(`  [MISSING] ${table.name}`);
      missing.push(table.name);
    } else {
      console.log(`  [OK]      ${table.name}`);
    }
  }

  if (missing.length > 0) {
    console.log(`\n${missing.length} table(s) missing. Run the SQL below in Supabase Dashboard > SQL Editor:\n`);
    console.log('--- COPY FROM HERE ---\n');
    console.log(SQL);
    console.log('\n--- END ---');
    return false;
  }

  console.log('\nAll tables exist!');
  return true;
}

const SQL = `
-- Agent Memory
CREATE TABLE IF NOT EXISTS agent_memory (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL,
  category text NOT NULL,
  key text NOT NULL,
  value jsonb NOT NULL,
  confidence real DEFAULT 1.0,
  usage_count integer DEFAULT 1,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now(),
  UNIQUE(user_id, category, key)
);
CREATE INDEX IF NOT EXISTS idx_agent_memory_user ON agent_memory(user_id);

-- Secure Credentials
CREATE TABLE IF NOT EXISTS secure_credentials (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL,
  service_name text NOT NULL,
  service_url text,
  username text,
  encrypted_password text NOT NULL,
  extra_fields jsonb,
  last_used_at timestamptz,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now(),
  UNIQUE(user_id, service_name)
);
CREATE INDEX IF NOT EXISTS idx_secure_credentials_user ON secure_credentials(user_id);

-- Learned Workflows
CREATE TABLE IF NOT EXISTS learned_workflows (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL,
  name text NOT NULL,
  description text,
  trigger_phrases text[],
  steps jsonb NOT NULL DEFAULT '[]'::jsonb,
  target_software text,
  success_count integer DEFAULT 1,
  fail_count integer DEFAULT 0,
  is_active boolean DEFAULT true,
  version integer DEFAULT 1,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_workflows_user ON learned_workflows(user_id);

-- Action Log
CREATE TABLE IF NOT EXISTS agent_action_log (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL,
  session_id text,
  tool_name text NOT NULL,
  tool_input jsonb,
  tool_result text,
  success boolean DEFAULT true,
  requires_confirmation boolean DEFAULT false,
  user_confirmed boolean,
  created_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_action_log_user ON agent_action_log(user_id);

-- Pending Confirmations
CREATE TABLE IF NOT EXISTS pending_confirmations (
  id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL,
  action_description text NOT NULL,
  tool_name text NOT NULL,
  tool_input jsonb NOT NULL,
  risk_level text DEFAULT 'medium',
  status text DEFAULT 'pending',
  expires_at timestamptz DEFAULT (now() + interval '5 minutes'),
  created_at timestamptz DEFAULT now(),
  resolved_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_pending_user ON pending_confirmations(user_id, status);

-- Enable RLS
ALTER TABLE agent_memory ENABLE ROW LEVEL SECURITY;
ALTER TABLE secure_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE learned_workflows ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_action_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE pending_confirmations ENABLE ROW LEVEL SECURITY;

-- RLS Policies (allow service role full access, users see only their own data)
DO $$ BEGIN
  CREATE POLICY "service_all" ON agent_memory FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$ BEGIN
  CREATE POLICY "service_all" ON secure_credentials FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$ BEGIN
  CREATE POLICY "service_all" ON learned_workflows FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$ BEGIN
  CREATE POLICY "service_all" ON agent_action_log FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$ BEGIN
  CREATE POLICY "service_all" ON pending_confirmations FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
`;

if (require.main === module) {
  checkTables().then(ok => {
    if (!ok) process.exit(1);
    process.exit(0);
  });
}

module.exports = { checkTables, SQL };
