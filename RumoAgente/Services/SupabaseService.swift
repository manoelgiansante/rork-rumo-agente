import Foundation

@Observable
@MainActor
class SupabaseService {
    var currentUser: UserProfile?
    var isAuthenticated = false

    private let baseURL: String
    private let anonKey: String

    init() {
        self.baseURL = Config.EXPO_PUBLIC_SUPABASE_URL
        self.anonKey = Config.EXPO_PUBLIC_SUPABASE_ANON_KEY
    }

    var authTokenValue: String? {
        UserDefaults.standard.string(forKey: "auth_token")
    }

    private var authToken: String? {
        get { UserDefaults.standard.string(forKey: "auth_token") }
        set { UserDefaults.standard.set(newValue, forKey: "auth_token") }
    }

    func signUp(email: String, password: String, displayName: String) async throws {
        let url = URL(string: "\(baseURL)/auth/v1/signup")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")

        let body: [String: Any] = [
            "email": email,
            "password": password,
            "data": ["display_name": displayName]
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw ServiceError.authError(errorBody)
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let accessToken = json?["access_token"] as? String {
            authToken = accessToken
            await loadUserProfile()
        }
    }

    func signIn(email: String, password: String) async throws {
        let url = URL(string: "\(baseURL)/auth/v1/token?grant_type=password")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")

        let body: [String: String] = ["email": email, "password": password]
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw ServiceError.authError(errorBody)
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let accessToken = json?["access_token"] as? String {
            authToken = accessToken
            await loadUserProfile()
        }
    }

    func signOut() {
        authToken = nil
        currentUser = nil
        isAuthenticated = false
    }

    func checkSession() async {
        guard authToken != nil else { return }
        await loadUserProfile()
    }

    private func loadUserProfile() async {
        guard let token = authToken else { return }
        let url = URL(string: "\(baseURL)/auth/v1/user")!
        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                signOut()
                return
            }

            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            guard let userId = json?["id"] as? String,
                  let email = json?["email"] as? String else { return }

            let metadata = json?["user_metadata"] as? [String: Any]
            let displayName = metadata?["display_name"] as? String ?? email.components(separatedBy: "@").first ?? ""

            currentUser = UserProfile(
                id: userId,
                email: email,
                displayName: displayName,
                avatarURL: metadata?["avatar_url"] as? String,
                plan: .free,
                credits: 10,
                createdAt: Date()
            )
            isAuthenticated = true
            await fetchProfile()
        } catch {
            signOut()
        }
    }

    func signInWithAppleToken(idToken: String, nonce: String, fullName: String?) async throws {
        let url = URL(string: "\(baseURL)/auth/v1/token?grant_type=id_token")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")

        let body: [String: Any] = [
            "provider": "apple",
            "id_token": idToken,
            "nonce": nonce
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Erro desconhecido"
            throw ServiceError.authError(errorBody)
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let accessToken = json?["access_token"] as? String {
            authToken = accessToken
            if let name = fullName {
                try? await updateUserMetadata(displayName: name)
            }
            await loadUserProfile()
        }
    }

    func signInWithGoogleOAuth() async throws -> URL {
        let redirectURL = "app.rork.rumoagente://login-callback"
        let urlString = "\(baseURL)/auth/v1/authorize?provider=google&redirect_to=\(redirectURL)"
        guard let url = URL(string: urlString) else {
            throw ServiceError.networkError
        }
        return url
    }

    func handleOAuthCallback(url: URL) async throws {
        guard let fragment = url.fragment else {
            let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
            if let code = components?.queryItems?.first(where: { $0.name == "code" })?.value {
                try await exchangeCodeForSession(code: code)
                return
            }
            throw ServiceError.authError("Resposta inválida do OAuth")
        }

        let params = fragment.components(separatedBy: "&").reduce(into: [String: String]()) { result, pair in
            let parts = pair.components(separatedBy: "=")
            if parts.count == 2 {
                result[parts[0]] = parts[1]
            }
        }

        if let accessToken = params["access_token"] {
            authToken = accessToken
            await loadUserProfile()
        } else {
            throw ServiceError.authError("Token não encontrado na resposta")
        }
    }

    private func exchangeCodeForSession(code: String) async throws {
        let url = URL(string: "\(baseURL)/auth/v1/token?grant_type=pkce")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")

        let body: [String: String] = ["auth_code": code]
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Erro desconhecido"
            throw ServiceError.authError(errorBody)
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let accessToken = json?["access_token"] as? String {
            authToken = accessToken
            await loadUserProfile()
        }
    }

    private func updateUserMetadata(displayName: String) async throws {
        guard let token = authToken else { return }
        let url = URL(string: "\(baseURL)/auth/v1/user")!
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let body: [String: Any] = ["data": ["display_name": displayName]]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (_, _) = try await URLSession.shared.data(for: request)
    }

    func resetPassword(email: String) async throws {
        let url = URL(string: "\(baseURL)/auth/v1/recover")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")

        let body = ["email": email]
        request.httpBody = try JSONEncoder().encode(body)

        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw ServiceError.networkError
        }
    }

    func fetchProfile() async {
        guard let token = authToken, let userId = currentUser?.id else { return }
        let urlString = "\(baseURL)/rest/v1/profiles?id=eq.\(userId)&select=*&limit=1"
        guard let url = URL(string: urlString) else { return }

        var request = URLRequest(url: url)
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return }

            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let profiles = try decoder.decode([UserProfile].self, from: data)
            if let profile = profiles.first {
                currentUser = profile
            }
        } catch {}
    }

    func fetchTasks() async -> [AgentTask] {
        guard let token = authToken, let userId = currentUser?.id else { return [] }
        let urlString = "\(baseURL)/rest/v1/agent_tasks?user_id=eq.\(userId)&select=*&order=created_at.desc&limit=20"
        guard let url = URL(string: urlString) else { return [] }

        var request = URLRequest(url: url)
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return [] }

            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode([AgentTask].self, from: data)
        } catch {
            return []
        }
    }

    func fetchApps() async -> [CloudApp] {
        guard let token = authToken else { return [] }
        let urlString = "\(baseURL)/rest/v1/cloud_apps?select=*&order=name.asc"
        guard let url = URL(string: urlString) else { return [] }

        var request = URLRequest(url: url)
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return [] }

            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode([CloudApp].self, from: data)
        } catch {
            return []
        }
    }
}

nonisolated enum ServiceError: LocalizedError, Sendable {
    case authError(String)
    case networkError
    case invalidResponse
    case insufficientCredits
    case agentOffline

    nonisolated var errorDescription: String? {
        switch self {
        case .authError(let msg): "Erro de autenticação: \(msg)"
        case .networkError: "Erro de conexão. Verifique sua internet."
        case .invalidResponse: "Resposta inválida do servidor."
        case .insufficientCredits: "Créditos insuficientes."
        case .agentOffline: "O agente está offline no momento."
        }
    }
}
