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

    func sendMessage() async {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        let historySnapshot = messages
        let userMessage = ChatMessage(role: .user, content: text, createdAt: Date())
        messages.append(userMessage)
        inputText = ""
        isTyping = true
        errorMessage = nil

        do {
            let response = try await claudeService.sendCommand(
                message: text,
                appContext: selectedApp?.name,
                conversationHistory: historySnapshot,
                authToken: supabase.authTokenValue
            )
            messages.append(response)
        } catch {
            errorMessage = error.localizedDescription
            messages.append(ChatMessage(
                role: .assistant,
                content: "Desculpe, ocorreu um erro ao processar seu comando. Tente novamente.",
                createdAt: Date()
            ))
        }

        isTyping = false
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
