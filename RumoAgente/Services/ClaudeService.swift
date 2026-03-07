import Foundation

@Observable
@MainActor
class ClaudeService {
    var isProcessing = false

    private let apiKey: String

    init() {
        self.apiKey = Config.EXPO_PUBLIC_CLAUDE_API_KEY
    }

    func sendCommand(message: String, appContext: String?, conversationHistory: [ChatMessage]) async throws -> ChatMessage {
        isProcessing = true
        defer { isProcessing = false }

        let url = URL(string: "https://api.anthropic.com/v1/messages")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")

        var systemPrompt = """
        Você é o Rumo Agente, um assistente inteligente que executa tarefas para o usuário. \
        Você opera aplicativos, lança dados, gera relatórios e automatiza processos. \
        Responda sempre em português do Brasil. Seja claro e objetivo. \
        Se o comando não estiver claro, faça perguntas de confirmação antes de executar. \
        Nunca mencione detalhes técnicos de infraestrutura ao usuário.
        """

        if let appContext {
            systemPrompt += "\n\nO usuário está trabalhando no aplicativo: \(appContext). Foque os comandos neste contexto."
        }

        var messages: [[String: String]] = []
        for msg in conversationHistory.suffix(20) {
            messages.append([
                "role": msg.role == .user ? "user" : "assistant",
                "content": msg.content
            ])
        }
        messages.append(["role": "user", "content": message])

        let body: [String: Any] = [
            "model": "claude-sonnet-4-20250514",
            "max_tokens": 2048,
            "system": systemPrompt,
            "messages": messages
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? ""
            throw ServiceError.authError("Claude API error: \(errorBody)")
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let content = json?["content"] as? [[String: Any]]
        let text = content?.first?["text"] as? String ?? "Desculpe, não consegui processar seu comando."

        let needsConfirmation = text.contains("?") && (
            text.lowercased().contains("confirmar") ||
            text.lowercased().contains("deseja") ||
            text.lowercased().contains("correto") ||
            text.lowercased().contains("qual")
        )

        return ChatMessage(
            role: .assistant,
            content: text,
            isConfirmation: needsConfirmation,
            createdAt: Date()
        )
    }
}
