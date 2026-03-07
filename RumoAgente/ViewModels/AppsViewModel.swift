import Foundation

@Observable
@MainActor
class AppsViewModel {
    var apps: [CloudApp] = []
    var selectedCategory: AppCategory?
    var isLoading = false

    var supabase: SupabaseService?

    private let fallbackApps: [CloudApp] = [
        CloudApp(id: "1", name: "Ponta do S", iconName: "leaf.fill", status: .installed, category: .agro, isSelected: false),
        CloudApp(id: "2", name: "Rumo Máquinas", iconName: "gearshape.2.fill", status: .installed, category: .agro, isSelected: false),
        CloudApp(id: "3", name: "Aegro", iconName: "chart.bar.fill", status: .installed, category: .agro, isSelected: false),
        CloudApp(id: "4", name: "Conta Azul", iconName: "creditcard.fill", status: .installed, category: .finance, isSelected: false),
        CloudApp(id: "5", name: "Excel Online", iconName: "tablecells.fill", status: .installed, category: .productivity, isSelected: false),
        CloudApp(id: "6", name: "Google Sheets", iconName: "doc.text.fill", status: .installed, category: .productivity, isSelected: false),
        CloudApp(id: "7", name: "WhatsApp Web", iconName: "message.fill", status: .installed, category: .communication, isSelected: false),
        CloudApp(id: "8", name: "Slack", iconName: "bubble.left.and.bubble.right.fill", status: .notInstalled, category: .communication, isSelected: false),
        CloudApp(id: "9", name: "Siagri", iconName: "building.2.fill", status: .installed, category: .agro, isSelected: false),
        CloudApp(id: "10", name: "Totvs Agro", iconName: "tractor.fill", status: .notInstalled, category: .agro, isSelected: false),
    ]

    func loadApps() async {
        isLoading = true

        if let supabase {
            let fetched = await supabase.fetchApps()
            apps = fetched.isEmpty ? fallbackApps : fetched
        } else {
            apps = fallbackApps
        }

        isLoading = false
    }

    var filteredApps: [CloudApp] {
        guard let category = selectedCategory else { return apps }
        return apps.filter { $0.category == category }
    }

    func selectApp(_ app: CloudApp) {
        for i in apps.indices {
            apps[i].isSelected = (apps[i].id == app.id) ? !apps[i].isSelected : false
        }
    }

    var currentSelectedApp: CloudApp? {
        apps.first(where: { $0.isSelected })
    }
}
