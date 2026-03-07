import Foundation

nonisolated struct UserProfile: Codable, Identifiable, Sendable {
    let id: String
    var email: String
    var displayName: String
    var avatarURL: String?
    var plan: SubscriptionPlan
    var credits: Int
    var createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id, email
        case displayName = "display_name"
        case avatarURL = "avatar_url"
        case plan, credits
        case createdAt = "created_at"
    }
}

nonisolated enum SubscriptionPlan: String, Codable, Sendable, CaseIterable {
    case free = "free"
    case starter = "starter"
    case pro = "pro"
    case enterprise = "enterprise"

    var displayName: String {
        switch self {
        case .free: "Gratuito"
        case .starter: "Starter"
        case .pro: "Pro"
        case .enterprise: "Enterprise"
        }
    }

    var monthlyPrice: Double {
        switch self {
        case .free: 0
        case .starter: 49.90
        case .pro: 149.90
        case .enterprise: 499.90
        }
    }

    var includedCredits: Int {
        switch self {
        case .free: 10
        case .starter: 100
        case .pro: 500
        case .enterprise: 2000
        }
    }

    var description: String {
        switch self {
        case .free: "Teste o agente com 10 créditos"
        case .starter: "Ideal para pequenos produtores"
        case .pro: "Para fazendas de médio porte"
        case .enterprise: "Operações de grande escala"
        }
    }
}
