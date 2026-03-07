import Foundation

@Observable
@MainActor
class DashboardViewModel {
    var agentOnline = false
    var recentTasks: [AgentTask] = []
    var isLoading = false

    let supabase: SupabaseService

    init(supabase: SupabaseService) {
        self.supabase = supabase
    }

    func loadDashboard() async {
        isLoading = true

        agentOnline = true

        recentTasks = [
            AgentTask(id: "1", userId: "", title: "Abrir Ponta do S e lançar notas", status: .completed, appName: "Ponta do S", creditsUsed: 3, createdAt: Date().addingTimeInterval(-3600), completedAt: Date().addingTimeInterval(-3500)),
            AgentTask(id: "2", userId: "", title: "Baixar relatório mensal", status: .completed, appName: "Rumo Máquinas", creditsUsed: 2, createdAt: Date().addingTimeInterval(-7200), completedAt: Date().addingTimeInterval(-7100)),
            AgentTask(id: "3", userId: "", title: "Atualizar planilha de custos", status: .running, appName: "Excel Online", creditsUsed: 1, createdAt: Date().addingTimeInterval(-300)),
        ]

        isLoading = false
    }

    var creditsUsedToday: Int {
        recentTasks.reduce(0) { $0 + $1.creditsUsed }
    }
}
