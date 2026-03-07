import Foundation

nonisolated struct ChatMessage: Codable, Identifiable, Sendable {
    let id: String
    let conversationId: String
    let role: MessageRole
    let content: String
    var screenshotURL: String?
    var isConfirmation: Bool
    let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case conversationId = "conversation_id"
        case role, content
        case screenshotURL = "screenshot_url"
        case isConfirmation = "is_confirmation"
        case createdAt = "created_at"
    }

    init(id: String = UUID().uuidString, conversationId: String = "", role: MessageRole, content: String, screenshotURL: String? = nil, isConfirmation: Bool = false, createdAt: Date = Date()) {
        self.id = id
        self.conversationId = conversationId
        self.role = role
        self.content = content
        self.screenshotURL = screenshotURL
        self.isConfirmation = isConfirmation
        self.createdAt = createdAt
    }
}

nonisolated enum MessageRole: String, Codable, Sendable {
    case user
    case assistant
    case system
}
