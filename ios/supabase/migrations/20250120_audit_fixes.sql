-- ============================================
-- RUMO AGENTE - Migration SQL
-- Execute no Supabase Dashboard > SQL Editor
-- Este script corrige TODOS os problemas
-- identificados pela auditoria
-- ============================================

-- 1. Criar tabela profiles (se não existir)
CREATE TABLE IF NOT EXISTS profiles (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email TEXT NOT NULL,
  display_name TEXT,
  avatar_url TEXT,
  plan TEXT NOT NULL DEFAULT 'free' CHECK (plan IN ('free', 'starter', 'pro', 'enterprise')),
  credits INTEGER NOT NULL DEFAULT 10,
  stripe_customer_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Adicionar coluna user_id como alias de id (compatibilidade com backend)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'profiles' AND column_name = 'user_id'
  ) THEN
    ALTER TABLE profiles ADD COLUMN user_id UUID GENERATED ALWAYS AS (id) STORED;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_profiles_user_id ON profiles(id);
CREATE INDEX IF NOT EXISTS idx_profiles_email ON profiles(email);

-- 2. RLS para profiles
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "Users can view own profile" ON profiles FOR SELECT USING (auth.uid() = id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Users can update own profile" ON profiles FOR UPDATE USING (auth.uid() = id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "System can insert profiles" ON profiles FOR INSERT WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 3. Função RPC para decremento atômico de créditos (previne race condition)
CREATE OR REPLACE FUNCTION decrement_credits(p_user_id UUID, p_amount INTEGER)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  new_credits INTEGER;
BEGIN
  UPDATE profiles
  SET credits = GREATEST(credits - p_amount, 0),
      updated_at = now()
  WHERE id = p_user_id AND credits >= p_amount
  RETURNING credits INTO new_credits;

  IF NOT FOUND THEN
    RETURN -1; -- Créditos insuficientes
  END IF;

  -- Registrar transação
  INSERT INTO credit_transactions (user_id, amount, type, description)
  VALUES (p_user_id, -p_amount, 'usage', 'Uso de crédito via agente');

  RETURN new_credits;
END;
$$;

-- 4. Corrigir RLS das tabelas do agente (remover USING(true) e usar auth.uid())
-- Primeiro, dropar as policies inseguras antigas
DO $$ BEGIN DROP POLICY IF EXISTS "service_all" ON agent_memory; EXCEPTION WHEN undefined_table THEN NULL; END $$;
DO $$ BEGIN DROP POLICY IF EXISTS "service_all" ON secure_credentials; EXCEPTION WHEN undefined_table THEN NULL; END $$;
DO $$ BEGIN DROP POLICY IF EXISTS "service_all" ON learned_workflows; EXCEPTION WHEN undefined_table THEN NULL; END $$;
DO $$ BEGIN DROP POLICY IF EXISTS "service_all" ON agent_action_log; EXCEPTION WHEN undefined_table THEN NULL; END $$;
DO $$ BEGIN DROP POLICY IF EXISTS "service_all" ON pending_confirmations; EXCEPTION WHEN undefined_table THEN NULL; END $$;

-- Recriar com policies seguras
DO $$ BEGIN
  CREATE POLICY "users_own_data" ON agent_memory FOR ALL
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "users_own_data" ON secure_credentials FOR ALL
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "users_own_data" ON learned_workflows FOR ALL
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "users_own_data" ON agent_action_log FOR ALL
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "users_own_data" ON pending_confirmations FOR ALL
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; WHEN undefined_table THEN NULL;
END $$;

-- 5. Corrigir trigger de novos usuários
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (id, email, display_name, avatar_url, plan, credits)
  VALUES (
    NEW.id,
    NEW.email,
    COALESCE(NEW.raw_user_meta_data->>'display_name', split_part(NEW.email, '@', 1)),
    NEW.raw_user_meta_data->>'avatar_url',
    'free',
    10
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 6. Função para incrementar créditos (usada pelo webhook Stripe)
CREATE OR REPLACE FUNCTION increment_credits(p_user_id UUID, p_amount INTEGER)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  new_credits INTEGER;
BEGIN
  UPDATE profiles
  SET credits = credits + p_amount,
      updated_at = now()
  WHERE id = p_user_id
  RETURNING credits INTO new_credits;

  IF NOT FOUND THEN
    RETURN -1;
  END IF;

  INSERT INTO credit_transactions (user_id, amount, type, description)
  VALUES (p_user_id, p_amount, 'purchase', 'Compra de créditos');

  RETURN new_credits;
END;
$$;

-- ============================================
-- DONE! Verifique no SQL Editor que não houve erros.
-- ============================================
