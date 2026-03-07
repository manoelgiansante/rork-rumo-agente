import Foundation

@Observable
@MainActor
class ClaudeService {
    var isProcessing = false

    private var backendURL: String {
        Config.AGENT_BACKEND_URL
    }

    func sendCommand(message: String, appContext: String?, conversationHistory: [ChatMessage], authToken: String?) async throws -> ChatMessage {
        isProcessing = true
        defer { isProcessing = false }

        let url = URL(string: "\(backendURL)/chat")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 60

        if let token = authToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        var history: [[String: String]] = []
        for msg in conversationHistory.suffix(20) {
            history.append([
                "role": msg.role == .user ? "user" : "assistant",
                "content": msg.content
            ])
        }

        let body: [String: Any] = [
            "message": message,
            "appContext": appContext ?? "",
            "history": history
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? ""
            throw ServiceError.authError("Erro no servidor: \(errorBody)")
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let text = json?["response"] as? String ?? json?["message"] as? String ?? "Desculpe, não consegui processar seu comando."

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
