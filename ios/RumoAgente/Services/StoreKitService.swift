import Foundation
import StoreKit

@Observable
@MainActor
class StoreKitService {
    // Product IDs matching App Store Connect
    static let subscriptionGroupID = "rumoagente_subscriptions"
    static let productIDs: Set<String> = [
        "app.rork.rumoagente.starter",
        "app.rork.rumoagente.pro",
        "app.rork.rumoagente.enterprise",
        "app.rork.rumoagente.credits.50",
        "app.rork.rumoagente.credits.200",
        "app.rork.rumoagente.credits.500"
    ]

    var products: [Product] = []
    var purchasedSubscriptions: [Product] = []
    var isLoading = false
    var errorMessage: String?
    var isPurchasing = false

    private var updateListenerTask: Task<Void, Never>?

    init() {
        updateListenerTask = listenForTransactions()
    }

    deinit {
        updateListenerTask?.cancel()
    }

    // MARK: - Load Products

    func loadProducts() async {
        isLoading = true
        defer { isLoading = false }

        do {
            let storeProducts = try await Product.products(for: StoreKitService.productIDs)
            products = storeProducts.sorted { $0.price < $1.price }
        } catch {
            errorMessage = "Erro ao carregar produtos: \(error.localizedDescription)"
        }
    }

    // MARK: - Purchase

    func purchase(_ product: Product) async throws -> StoreKit.Transaction? {
        isPurchasing = true
        defer { isPurchasing = false }

        let result = try await product.purchase()

        switch result {
        case .success(let verification):
            let transaction = try checkVerified(verification)
            await validateWithServer(transaction: transaction)
            await updatePurchasedProducts()
            await transaction.finish()
            return transaction

        case .userCancelled:
            return nil

        case .pending:
            errorMessage = "Compra pendente de aprovação."
            return nil

        @unknown default:
            return nil
        }
    }

    // MARK: - Restore Purchases (Apple requirement)

    func restorePurchases() async {
        isLoading = true
        defer { isLoading = false }

        do {
            try await AppStore.sync()
            await updatePurchasedProducts()
        } catch {
            errorMessage = "Erro ao restaurar compras: \(error.localizedDescription)"
        }
    }

    // MARK: - Check Entitlements

    func updatePurchasedProducts() async {
        var purchased: [Product] = []

        for await result in Transaction.currentEntitlements {
            do {
                let transaction = try checkVerified(result)
                if let product = products.first(where: { $0.id == transaction.productID }) {
                    purchased.append(product)
                }
            } catch {
                // Skip unverified transactions
            }
        }

        purchasedSubscriptions = purchased
    }

    func currentSubscriptionTier() async -> SubscriptionPlan {
        for await result in Transaction.currentEntitlements {
            if let transaction = try? checkVerified(result) {
                switch transaction.productID {
                case "app.rork.rumoagente.enterprise": return .enterprise
                case "app.rork.rumoagente.pro": return .pro
                case "app.rork.rumoagente.starter": return .starter
                default: continue
                }
            }
        }
        return .free
    }

    // MARK: - Get product by plan

    func product(for plan: SubscriptionPlan) -> Product? {
        switch plan {
        case .free: return nil
        case .starter: return products.first { $0.id == "app.rork.rumoagente.starter" }
        case .pro: return products.first { $0.id == "app.rork.rumoagente.pro" }
        case .enterprise: return products.first { $0.id == "app.rork.rumoagente.enterprise" }
        }
    }

    func creditProduct(amount: Int) -> Product? {
        switch amount {
        case 50: return products.first { $0.id == "app.rork.rumoagente.credits.50" }
        case 200: return products.first { $0.id == "app.rork.rumoagente.credits.200" }
        case 500: return products.first { $0.id == "app.rork.rumoagente.credits.500" }
        default: return nil
        }
    }

    // MARK: - Transaction Listener

    private func listenForTransactions() -> Task<Void, Never> {
        Task.detached { [weak self] in
            for await result in Transaction.updates {
                if let transaction = try? self?.checkVerified(result) {
                    await self?.validateWithServer(transaction: transaction)
                    await self?.updatePurchasedProducts()
                    await transaction.finish()
                }
            }
        }
    }

    // MARK: - Server-side Receipt Validation

    /// Sends the verified transaction to our backend to credit the user's account
    func validateWithServer(transaction: StoreKit.Transaction) async {
        guard let url = URL(string: "\(Config.EXPO_PUBLIC_AGENT_BACKEND_URL)/validate-apple-receipt") else { return }
        guard let token = KeychainService.load(key: "auth_token") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let body: [String: Any] = [
            "transactionId": String(transaction.id),
            "productId": transaction.productID,
            "originalTransactionId": String(transaction.originalID)
        ]

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            let (data, response) = try await URLSession.shared.data(for: request)

            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let success = json["success"] as? Bool, success {
                    print("[StoreKit] Server validation OK for \(transaction.productID)")
                }
            } else {
                print("[StoreKit] Server validation failed: HTTP \((response as? HTTPURLResponse)?.statusCode ?? 0)")
            }
        } catch {
            print("[StoreKit] Server validation error: \(error.localizedDescription)")
        }
    }

    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw StoreKitError.failedVerification
        case .verified(let safe):
            return safe
        }
    }
}

enum StoreKitError: LocalizedError {
    case failedVerification
    case purchaseFailed

    var errorDescription: String? {
        switch self {
        case .failedVerification: "A verificação da compra falhou."
        case .purchaseFailed: "Falha na compra. Tente novamente."
        }
    }
}
