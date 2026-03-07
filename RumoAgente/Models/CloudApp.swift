import Foundation

nonisolated struct CloudApp: Codable, Identifiable, Sendable, Hashable {
    let id: String
    var name: String
    var iconName: String
    var status: AppStatus
    var category: AppCategory
    var isSelected: Bool

    enum CodingKeys: String, CodingKey {
        case id, name
        case iconName = "icon_name"
        case status, category
        case isSelected = "is_selected"
    }

    nonisolated func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    nonisolated static func == (lhs: CloudApp, rhs: CloudApp) -> Bool {
        lhs.id == rhs.id
    }
}

nonisolated enum AppStatus: String, Codable, Sendable {
    case installed
    case installing
    case notInstalled = "not_installed"
    case running

    var displayName: String {
        switch self {
        case .installed: "Instalado"
        case .installing: "Instalando..."
        case .notInstalled: "Não instalado"
        case .running: "Em uso"
        }
    }
}

nonisolated enum AppCategory: String, Codable, Sendable, CaseIterable {
    case agro = "Agronegócio"
    case finance = "Financeiro"
    case productivity = "Produtividade"
    case communication = "Comunicação"
    case other = "Outros"
}
