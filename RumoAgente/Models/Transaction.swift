import Foundation

nonisolated struct Transaction: Codable, Identifiable, Sendable {
    let id: String
    let userId: String
    let type: TransactionType
    let amount: Double
    let credits: Int
    let description: String
    let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case type, amount, credits, description
        case createdAt = "created_at"
    }
}

nonisolated enum TransactionType: String, Codable, Sendable {
    case subscription
    case creditPurchase = "credit_purchase"
    case creditUsage = "credit_usage"
    case refund

    var displayName: String {
        switch self {
        case .subscription: "Assinatura"
        case .creditPurchase: "Compra de créditos"
        case .creditUsage: "Uso de créditos"
        case .refund: "Reembolso"
        }
    }

    var iconName: String {
        switch self {
        case .subscription: "crown.fill"
        case .creditPurchase: "plus.circle.fill"
        case .creditUsage: "minus.circle.fill"
        case .refund: "arrow.uturn.left.circle.fill"
        }
    }
}
