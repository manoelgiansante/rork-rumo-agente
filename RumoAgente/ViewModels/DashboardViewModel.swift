import Foundation

@Observable
@MainActor
class DashboardViewModel {
    var agentOnline = false
    var recentTasks: [AgentTask] = []
    var isLoading = false

    let supabase: SupabaseService

    let agentService: AgentService

    init(supabase: SupabaseService, agentService: AgentService) {
        self.supabase = supabase
        self.agentService = agentService
    }

    func loadDashboard() async {
        isLoading = true

        async let statusCheck = agentService.checkStatus()
        async let tasksLoad = supabase.fetchTasks()
        await supabase.fetchProfile()

        agentOnline = await statusCheck
        recentTasks = await tasksLoad

        isLoading = false
    }

    var creditsUsedToday: Int {
        recentTasks.reduce(0) { $0 + $1.creditsUsed }
    }
}
