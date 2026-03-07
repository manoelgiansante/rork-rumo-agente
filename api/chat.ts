import type { VercelRequest, VercelResponse } from '@vercel/node';
import Anthropic from '@anthropic-ai/sdk';

const anthropic = new Anthropic({
    apiKey: process.env.CLAUDE_API_KEY,
});

const SYSTEM_PROMPT = `Voce e o Rumo Agente, um assistente IA inteligente especializado em automacao de tarefas.
Voce ajuda usuarios do agronegocio brasileiro e profissionais que querem automatizar operacoes.
Responda sempre em portugues do Brasil.
Quando o usuario pedir para executar uma tarefa em um aplicativo, gere um comando estruturado no formato JSON:
{ "action": "execute", "app": "nome_do_app", "command": "descricao_do_comando", "params": {} }
Para perguntas gerais, responda normalmente de forma util e amigavel.`;

export default async function handler(req: VercelRequest, res: VercelResponse) {
    if (req.method !== 'POST') {
          return res.status(405).json({ error: 'Method not allowed' });
    }

  try {
        const { message, conversationHistory = [] } = req.body;

      if (!message) {
              return res.status(400).json({ error: 'Message is required' });
      }

      const messages = [
              ...conversationHistory.map((msg: { role: string; content: string }) => ({
                        role: msg.role as 'user' | 'assistant',
                        content: msg.content,
              })),
        { role: 'user' as const, content: message },
            ];

      const response = await anthropic.messages.create({
              model: 'claude-sonnet-4-20250514',
              max_tokens: 4096,
              system: SYSTEM_PROMPT,
              messages,
      });

      const assistantMessage = response.content[0].type === 'text' 
        ? response.content[0].text 
              : '';

      return res.status(200).json({
              response: assistantMessage,
              usage: {
                        input_tokens: response.usage.input_tokens,
                        output_tokens: response.usage.output_tokens,
              },
      });
  } catch (error: any) {
        console.error('Chat API error:', error);
        return res.status(500).json({ 
                                          error: 'Failed to process chat message',
                details: error.message,
        });
  }
}
