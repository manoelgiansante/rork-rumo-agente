import Foundation

nonisolated struct AgentTask: Codable, Identifiable, Sendable {
    let id: String
    let userId: String
    var title: String
    var status: TaskStatus
    var appName: String?
    var creditsUsed: Int
    let createdAt: Date
    var completedAt: Date?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case title, status
        case appName = "app_name"
        case creditsUsed = "credits_used"
        case createdAt = "created_at"
        case completedAt = "completed_at"
    }
}

nonisolated enum TaskStatus: String, Codable, Sendable {
    case pending
    case running
    case completed
    case failed
    case waitingConfirmation = "waiting_confirmation"

    var displayName: String {
        switch self {
        case .pending: "Pendente"
        case .running: "Executando"
        case .completed: "Concluída"
        case .failed: "Falhou"
        case .waitingConfirmation: "Aguardando"
        }
    }

    var iconName: String {
        switch self {
        case .pending: "clock"
        case .running: "play.circle.fill"
        case .completed: "checkmark.circle.fill"
        case .failed: "xmark.circle.fill"
        case .waitingConfirmation: "questionmark.circle.fill"
        }
    }
}
