import Foundation

@Observable
@MainActor
class ChatViewModel {
    var messages: [ChatMessage] = []
    var inputText = ""
    var isTyping = false
    var selectedApp: CloudApp?
    var errorMessage: String?

    let claudeService: ClaudeService
    let supabase: SupabaseService

    init(claudeService: ClaudeService, supabase: SupabaseService) {
        self.claudeService = claudeService
        self.supabase = supabase
    }

    func loadInitialMessages() {
        guard messages.isEmpty else { return }
        messages = [
            ChatMessage(
                role: .assistant,
                content: "Olá! 👋 Sou o Rumo Agente, seu assistente inteligente. Como posso ajudar você hoje?\n\nVocê pode me pedir para:\n• Abrir e operar aplicativos\n• Lançar dados e relatórios\n• Executar tarefas nos seus softwares\n• Instalar novos programas\n\nÉ só digitar o comando!",
                createdAt: Date().addingTimeInterval(-60)
            )
        ]
    }

    private let maxRetries = 2

    func sendMessage() async {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        let historySnapshot = messages
        let userMessage = ChatMessage(role: .user, content: text, createdAt: Date())
        messages.append(userMessage)
        inputText = ""
        isTyping = true
        errorMessage = nil

        await sendWithRetry(text: text, history: historySnapshot, attempt: 0, didRefreshAuth: false)

        isTyping = false
    }

    private func sendWithRetry(text: String, history: [ChatMessage], attempt: Int, didRefreshAuth: Bool) async {
        do {
            let response = try await claudeService.sendCommand(
                message: text,
                appContext: selectedApp?.name,
                conversationHistory: history,
                authToken: supabase.authTokenValue
            )
            errorMessage = nil
            messages.append(response)
        } catch let error as ServiceError {
            switch error {
            case .networkError where attempt < maxRetries:
                let nextAttempt = attempt + 1
                errorMessage = "Tentando novamente... (\(nextAttempt)/\(maxRetries))"
                try? await Task.sleep(for: .seconds(Double(nextAttempt) * 2))
                await sendWithRetry(text: text, history: history, attempt: nextAttempt, didRefreshAuth: didRefreshAuth)
                return
            case .authError where !didRefreshAuth:
                // Try refreshing session once only
                if await supabase.refreshSession() {
                    await sendWithRetry(text: text, history: history, attempt: attempt, didRefreshAuth: true)
                    return
                }
                errorMessage = error.localizedDescription
                messages.append(ChatMessage(role: .assistant, content: "Sessão expirada. Faça login novamente.", createdAt: Date()))
            case .networkError:
                errorMessage = error.localizedDescription
                messages.append(ChatMessage(role: .assistant, content: "Sem conexão com a internet. Verifique sua rede e tente novamente.", createdAt: Date()))
            default:
                errorMessage = error.localizedDescription
                messages.append(ChatMessage(role: .assistant, content: "Desculpe, ocorreu um erro ao processar seu comando. Tente novamente.", createdAt: Date()))
            }
        } catch {
            if attempt < maxRetries {
                let nextAttempt = attempt + 1
                errorMessage = "Tentando novamente... (\(nextAttempt)/\(maxRetries))"
                try? await Task.sleep(for: .seconds(Double(nextAttempt) * 2))
                await sendWithRetry(text: text, history: history, attempt: nextAttempt, didRefreshAuth: didRefreshAuth)
                return
            }
            errorMessage = error.localizedDescription
            messages.append(ChatMessage(
                role: .assistant,
                content: "Desculpe, ocorreu um erro ao processar seu comando. Tente novamente.",
                createdAt: Date()
            ))
        }
    }

    func confirmAction() async {
        inputText = "Sim, pode prosseguir."
        await sendMessage()
    }

    func cancelAction() async {
        inputText = "Não, cancele essa ação."
        await sendMessage()
    }

    func clearConversation() {
        messages.removeAll()
        loadInitialMessages()
    }
}
