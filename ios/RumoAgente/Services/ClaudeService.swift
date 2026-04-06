import Foundation

@Observable
@MainActor
class ClaudeService {
    var isProcessing = false

    private let backendURL = Config.EXPO_PUBLIC_AGENT_BACKEND_URL

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

        // Build messages array in the format the backend expects
        var messagesArray: [[String: String]] = []
        for msg in conversationHistory.suffix(20) {
            messagesArray.append([
                "role": msg.role == .user ? "user" : "assistant",
                "content": msg.content
            ])
        }
        // Add the current user message
        messagesArray.append([
            "role": "user",
            "content": message
        ])

        let body: [String: Any] = [
            "messages": messagesArray
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ServiceError.invalidResponse
        }

        if httpResponse.statusCode == 401 {
            throw ServiceError.authError("Sessão expirada. Faça login novamente.")
        }

        if httpResponse.statusCode == 403 {
            throw ServiceError.insufficientCredits
        }

        guard httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? ""
            throw ServiceError.authError("Erro no servidor: \(errorBody)")
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let text = json?["message"] as? String ?? json?["response"] as? String ?? "Desculpe, não consegui processar seu comando."
        let actions = json?["actions"] as? [String]
        let screenshotAvailable = json?["screenshotAvailable"] as? Bool ?? false

        let needsConfirmation = text.contains("?") && (
            text.lowercased().contains("confirmar") ||
            text.lowercased().contains("deseja") ||
            text.lowercased().contains("correto") ||
            text.lowercased().contains("qual") ||
            text.lowercased().contains("posso")
        )

        // If actions were executed and screenshot is available, include screenshot URL
        var screenshotURL: String?
        if screenshotAvailable {
            screenshotURL = "\(backendURL)/screenshot"
        }

        return ChatMessage(
            role: .assistant,
            content: text,
            screenshotURL: screenshotURL,
            isConfirmation: needsConfirmation,
            createdAt: Date()
        )
    }
}
