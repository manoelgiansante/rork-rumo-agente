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

    init(id: String, name: String, iconName: String, status: AppStatus, category: AppCategory, isSelected: Bool = false) {
        self.id = id
        self.name = name
        self.iconName = iconName
        self.status = status
        self.category = category
        self.isSelected = isSelected
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        iconName = try container.decodeIfPresent(String.self, forKey: .iconName) ?? "app.fill"
        status = try container.decodeIfPresent(AppStatus.self, forKey: .status) ?? .installed
        category = try container.decodeIfPresent(AppCategory.self, forKey: .category) ?? .other
        isSelected = try container.decodeIfPresent(Bool.self, forKey: .isSelected) ?? false
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
