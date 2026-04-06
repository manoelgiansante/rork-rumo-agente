import Foundation

@Observable
@MainActor
class AgentService {
    var isExecuting = false
    var lastError: String?

    private var agentBaseURL: String = ""
    private var authToken: String?

    func configure(baseURL: String, token: String? = nil) {
        self.agentBaseURL = baseURL
        self.authToken = token
    }

    func updateToken(_ token: String?) {
        self.authToken = token
    }

    var isConfigured: Bool {
        !agentBaseURL.isEmpty
    }

    private func authorizedRequest(url: URL, method: String = "GET") -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 120
        if let token = authToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    func executeCommand(_ command: AgentCommand) async throws -> AgentResult {
        isExecuting = true
        lastError = nil
        defer { isExecuting = false }

        guard isConfigured else {
            throw AgentError.notConfigured
        }

        let url = URL(string: "\(agentBaseURL)/execute")!
        var request = authorizedRequest(url: url, method: "POST")

        let encoder = JSONEncoder()
        request.httpBody = try encoder.encode(command)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw AgentError.executionFailed(body)
        }

        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(AgentResult.self, from: data)
    }

    func checkStatus() async -> Bool {
        guard isConfigured else { return false }
        guard let url = URL(string: "\(agentBaseURL)/status") else { return false }

        do {
            let request = authorizedRequest(url: url)
            let (_, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return false }
            return httpResponse.statusCode == 200
        } catch {
            return false
        }
    }

    func takeScreenshot() async throws -> String? {
        guard isConfigured else { return nil }
        let url = URL(string: "\(agentBaseURL)/screenshot")!
        let request = authorizedRequest(url: url)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return nil }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return json?["screenshot_url"] as? String
    }
}

nonisolated struct AgentCommand: Codable, Sendable {
    let action: String
    let appContext: String?
    let parameters: [String: String]?

    init(action: String, appContext: String? = nil, parameters: [String: String]? = nil) {
        self.action = action
        self.appContext = appContext
        self.parameters = parameters
    }
}

nonisolated struct AgentResult: Codable, Sendable {
    let success: Bool
    let message: String
    let screenshotURL: String?
    let taskId: String?

    enum CodingKeys: String, CodingKey {
        case success, message
        case screenshotURL = "screenshot_url"
        case taskId = "task_id"
    }
}

nonisolated enum AgentError: Error, LocalizedError, Sendable {
    case notConfigured
    case executionFailed(String)
    case timeout

    nonisolated var errorDescription: String? {
        switch self {
        case .notConfigured: "O agente ainda não está configurado."
        case .executionFailed(let msg): "Falha ao executar: \(msg)"
        case .timeout: "O comando demorou demais para executar."
        }
    }
}
