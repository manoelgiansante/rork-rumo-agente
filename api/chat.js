import Anthropic from '@anthropic-ai/sdk';
import { createClient } from '@supabase/supabase-js';

const anthropic = new Anthropic({ apiKey: process.env.CLAUDE_API_KEY });
const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY);

export default async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'Token required' });

  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) return res.status(401).json({ error: 'Invalid token' });

  try {
    const { messages, conversationId } = req.body;

    const { data: profile } = await supabase
      .from('profiles').select('credits').eq('id', user.id).single();

    if (!profile || profile.credits <= 0) {
      return res.status(403).json({ error: 'Sem créditos disponíveis' });
    }

    const response = await anthropic.messages.create({
      model: 'claude-sonnet-4-20250514',
      max_tokens: 2048,
      system: 'Você é o Rumo Agente, um assistente inteligente para gestão agropecuária. Responda em português do Brasil de forma clara e objetiva.',
      messages: messages.map(m => ({ role: m.role, content: m.content }))
    });

    const assistantMessage = response.content[0].text;

    if (conversationId) {
      await supabase.from('chat_messages').insert([
        { conversation_id: conversationId, role: 'user', content: messages[messages.length - 1].content },
        { conversation_id: conversationId, role: 'assistant', content: assistantMessage }
      ]);
    }

    await supabase.from('profiles')
      .update({ credits: profile.credits - 1 })
      .eq('id', user.id);

    await supabase.from('credit_transactions').insert({
      user_id: user.id, amount: -1, type: 'usage', description: 'Mensagem de chat'
    });

    res.json({ message: assistantMessage });
  } catch (err) {
    console.error('Chat error:', err);
    res.status(500).json({ error: 'Erro ao processar mensagem' });
  }
}
