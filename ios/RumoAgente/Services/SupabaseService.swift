import Foundation

@Observable
@MainActor
class SupabaseService {
    var currentUser: UserProfile?
    var isAuthenticated = false

    private let baseURL: String
    private let anonKey: String
    private let iso8601Decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()

    init() {
        self.baseURL = Config.EXPO_PUBLIC_SUPABASE_URL
        self.anonKey = Config.EXPO_PUBLIC_SUPABASE_ANON_KEY
    }

    /// Safe URL builder — throws if the URL string is malformed
    private func makeURL(_ path: String) throws -> URL {
        guard let url = URL(string: "\(baseURL)\(path)") else {
            throw ServiceError.networkError
        }
        return url
    }

    /// Build an authenticated request for the Supabase REST API
    private func makeRESTRequest(urlString: String) -> URLRequest? {
        guard let token = authToken, let url = URL(string: urlString) else { return nil }
        var request = URLRequest(url: url)
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        return request
    }

    var authTokenValue: String? {
        KeychainService.load(key: "auth_token")
    }

    private var authToken: String? {
        get { KeychainService.load(key: "auth_token") }
        set {
            if let newValue {
                KeychainService.save(key: "auth_token", value: newValue)
            } else {
                KeychainService.delete(key: "auth_token")
            }
        }
    }

    private var refreshToken: String? {
        get { KeychainService.load(key: "refresh_token") }
        set {
            if let newValue {
                KeychainService.save(key: "refresh_token", value: newValue)
            } else {
                KeychainService.delete(key: "refresh_token")
            }
        }
    }

    func signUp(email: String, password: String, displayName: String) async throws {
        let url = try makeURL("/auth/v1/signup")
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

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw ServiceError.networkError
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ServiceError.invalidResponse
        }

        guard httpResponse.statusCode == 200 else {
            let errorBody = parseSupabaseError(data) ?? "Erro ao criar conta"
            throw ServiceError.authError(errorBody)
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let accessToken = json?["access_token"] as? String {
            authToken = accessToken
            refreshToken = json?["refresh_token"] as? String
            await loadUserProfile()
        }
    }

    func signIn(email: String, password: String) async throws {
        let url = try makeURL("/auth/v1/token?grant_type=password")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")

        let body: [String: String] = ["email": email, "password": password]
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw ServiceError.networkError
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ServiceError.invalidResponse
        }

        guard httpResponse.statusCode == 200 else {
            let errorBody = parseSupabaseError(data) ?? "Email ou senha incorretos"
            throw ServiceError.authError(errorBody)
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let accessToken = json?["access_token"] as? String {
            authToken = accessToken
            refreshToken = json?["refresh_token"] as? String
            await loadUserProfile()
        }
    }

    func signOut() {
        authToken = nil
        refreshToken = nil
        currentUser = nil
        isAuthenticated = false
    }

    func checkSession() async {
        guard authToken != nil else { return }
        await loadUserProfile()
        if !isAuthenticated {
            if await refreshSession() {
                await loadUserProfile()
            }
        }
    }

    func refreshSession() async -> Bool {
        guard let token = refreshToken else { return false }
        do {
            let url = try makeURL("/auth/v1/token?grant_type=refresh_token")
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(anonKey, forHTTPHeaderField: "apikey")

            let body: [String: String] = ["refresh_token": token]
            request.httpBody = try JSONEncoder().encode(body)
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return false
            }
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            if let accessToken = json?["access_token"] as? String {
                authToken = accessToken
                refreshToken = json?["refresh_token"] as? String ?? token
                return true
            }
        } catch {
            return false
        }
        return false
    }

    private func loadUserProfile() async {
        guard let token = authToken else { return }
        do {
            let url = try makeURL("/auth/v1/user")
            var request = URLRequest(url: url)
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            request.setValue(anonKey, forHTTPHeaderField: "apikey")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return }

            if httpResponse.statusCode == 401 {
                return
            }

            guard httpResponse.statusCode == 200 else { return }

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
            // Network error — don't sign out, keep existing session
        }
    }

    func signInWithAppleToken(idToken: String, nonce: String, fullName: String?) async throws {
        let url = try makeURL("/auth/v1/token?grant_type=id_token")
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
            refreshToken = json?["refresh_token"] as? String
            if let name = fullName {
                try? await updateUserMetadata(displayName: name)
            }
            await loadUserProfile()
        }
    }

    func signInWithGoogleOAuth() async throws -> URL {
        let redirectURL = "app.rork.rumoagente://login-callback"
        guard let encodedRedirect = redirectURL.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw ServiceError.networkError
        }
        let urlString = "\(baseURL)/auth/v1/authorize?provider=google&redirect_to=\(encodedRedirect)"
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
            refreshToken = params["refresh_token"]
            await loadUserProfile()
        } else {
            throw ServiceError.authError("Token não encontrado na resposta")
        }
    }

    private func exchangeCodeForSession(code: String) async throws {
        let url = try makeURL("/auth/v1/token?grant_type=authorization_code")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")

        let body: [String: String] = ["auth_code": code, "code_verifier": ""]
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Erro desconhecido"
            throw ServiceError.authError(errorBody)
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let accessToken = json?["access_token"] as? String {
            authToken = accessToken
            refreshToken = json?["refresh_token"] as? String
            await loadUserProfile()
        }
    }

    private func updateUserMetadata(displayName: String) async throws {
        guard let token = authToken else { return }
        let url = try makeURL("/auth/v1/user")
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let body: [String: Any] = ["data": ["display_name": displayName]]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        _ = try await URLSession.shared.data(for: request)
    }

    func resetPassword(email: String) async throws {
        let url = try makeURL("/auth/v1/recover")
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

    private var fetchProfileRetried = false
    private var fetchTasksRetried = false
    private var fetchTransactionsRetried = false
    private var fetchAppsRetried = false
    func fetchProfile() async {
        guard let userId = currentUser?.id else { return }
        guard let request = makeRESTRequest(urlString: "\(baseURL)/rest/v1/profiles?id=eq.\(userId)&select=*&limit=1") else { return }

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return }

            // Auto-refresh token on 401 (one retry only)
            if httpResponse.statusCode == 401 {
                if !fetchProfileRetried, await refreshSession() {
                    fetchProfileRetried = true
                    await fetchProfile()
                }
                fetchProfileRetried = false
                return
            }

            guard httpResponse.statusCode == 200 else { return }

            let profiles = try iso8601Decoder.decode([UserProfile].self, from: data)
            if let profile = profiles.first {
                currentUser = profile
            }
        } catch {
            // Profile fetch failed — user stays with auth-based profile data
        }
    }

    func fetchTasks() async -> [AgentTask] {
        guard let userId = currentUser?.id else { return [] }
        guard let request = makeRESTRequest(urlString: "\(baseURL)/rest/v1/agent_tasks?user_id=eq.\(userId)&select=*&order=created_at.desc&limit=20") else { return [] }

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return [] }

            if httpResponse.statusCode == 401 {
                if !fetchTasksRetried, await refreshSession() {
                    fetchTasksRetried = true
                    let result = await fetchTasks()
                    fetchTasksRetried = false
                    return result
                }
                return []
            }

            guard httpResponse.statusCode == 200 else { return [] }

            return try iso8601Decoder.decode([AgentTask].self, from: data)
        } catch {
            return []
        }
    }

    func deleteAccount() async throws {
        guard let token = authToken else {
            throw ServiceError.authError("Usuário não autenticado")
        }

        guard let url = URL(string: "\(Config.VERCEL_API_URL)/delete-account") else {
            throw ServiceError.networkError
        }
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = Config.defaultRequestTimeout

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw ServiceError.networkError
        }

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Erro desconhecido"
            throw ServiceError.authError("Falha ao excluir conta: \(errorBody)")
        }

        signOut()
    }

    func fetchTransactions() async -> [Transaction] {
        guard let userId = currentUser?.id else { return [] }
        guard let request = makeRESTRequest(urlString: "\(baseURL)/rest/v1/credit_transactions?user_id=eq.\(userId)&select=*&order=created_at.desc&limit=50") else { return [] }

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return [] }

            if httpResponse.statusCode == 401 {
                if !fetchTransactionsRetried, await refreshSession() {
                    fetchTransactionsRetried = true
                    let result = await fetchTransactions()
                    fetchTransactionsRetried = false
                    return result
                }
                return []
            }

            guard httpResponse.statusCode == 200 else { return [] }

            return try iso8601Decoder.decode([Transaction].self, from: data)
        } catch {
            return []
        }
    }

    func fetchApps() async -> [CloudApp] {
        guard let request = makeRESTRequest(urlString: "\(baseURL)/rest/v1/cloud_apps?select=*&order=name.asc") else { return [] }

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return [] }

            if httpResponse.statusCode == 401 {
                if !fetchAppsRetried, await refreshSession() {
                    fetchAppsRetried = true
                    let result = await fetchApps()
                    fetchAppsRetried = false
                    return result
                }
                return []
            }

            guard httpResponse.statusCode == 200 else { return [] }

            return try iso8601Decoder.decode([CloudApp].self, from: data)
        } catch {
            return []
        }
    }

    private func parseSupabaseError(_ data: Data) -> String? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        if let msg = json["msg"] as? String { return msg }
        if let msg = json["error_description"] as? String { return msg }
        if let msg = json["message"] as? String { return msg }
        return nil
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
        case .authError(let msg): msg
        case .networkError: "Erro de conexão. Verifique sua internet."
        case .invalidResponse: "Resposta inválida do servidor."
        case .insufficientCredits: "Créditos insuficientes."
        case .agentOffline: "O agente está offline no momento."
        }
    }
}
