import Foundation

nonisolated struct Transaction: Codable, Identifiable, Sendable {
    let id: String
    let userId: String
    let amount: Int
    let type: TransactionType
    let transactionDescription: String?
    let stripePaymentId: String?
    let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case amount, type
        case transactionDescription = "description"
        case stripePaymentId = "stripe_payment_id"
        case createdAt = "created_at"
    }
}

nonisolated enum TransactionType: String, Codable, Sendable {
    case purchase
    case usage
    case bonus
    case refund

    var displayName: String {
        switch self {
        case .purchase: "Compra de créditos"
        case .usage: "Uso de créditos"
        case .bonus: "Bônus"
        case .refund: "Reembolso"
        }
    }

    var iconName: String {
        switch self {
        case .purchase: "plus.circle.fill"
        case .usage: "minus.circle.fill"
        case .bonus: "gift.fill"
        case .refund: "arrow.uturn.left.circle.fill"
        }
    }
}
