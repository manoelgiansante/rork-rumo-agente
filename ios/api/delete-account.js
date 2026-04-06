import { createClient } from "@supabase/supabase-js";

const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_SERVICE_KEY,
);

export default async function handler(req, res) {
  if (req.method !== "DELETE")
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
    // Delete user data from all related tables (LGPD compliance)
    const tablesToDelete = [
      "pending_confirmations",
      "agent_action_log",
      "learned_workflows",
      "secure_credentials",
      "agent_memory",
      "chat_messages",
      "conversations",
      "agent_tasks",
      "credit_transactions",
      "subscriptions",
      "profiles",
    ];

    for (const table of tablesToDelete) {
      const { error } = await supabase
        .from(table)
        .delete()
        .eq("user_id", user.id);

      if (error) {
        console.error(`Error deleting from ${table}:`, error);
      }
    }

    // Delete the auth user
    const { error: deleteError } = await supabase.auth.admin.deleteUser(
      user.id,
    );

    if (deleteError) {
      console.error("Error deleting auth user:", deleteError);
      return res
        .status(500)
        .json({ error: "Erro ao excluir conta de autenticacao" });
    }

    res.json({ success: true, message: "Conta excluida com sucesso" });
  } catch (err) {
    console.error("Delete account error:", err);
    res.status(500).json({ error: "Erro ao excluir conta" });
  }
}
