import SwiftUI

struct ContentView: View {
    let supabase: SupabaseService
    let claudeService: ClaudeService
    let agentService: AgentService
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("Dashboard", systemImage: "square.grid.2x2.fill", value: 0) {
                DashboardView(supabase: supabase, agentService: agentService, selectedTab: $selectedTab)
            }
            Tab("Tela", systemImage: "desktopcomputer", value: 1) {
                ScreenView(supabase: supabase)
            }
            Tab("Chat", systemImage: "message.fill", value: 2) {
                ChatView(claudeService: claudeService, supabase: supabase)
            }
            Tab("Apps", systemImage: "square.grid.2x2", value: 3) {
                AppsView(supabase: supabase)
            }
            Tab("Perfil", systemImage: "person.fill", value: 4) {
                ProfileView(supabase: supabase)
            }
        }
        .tint(Theme.accent)
        .preferredColorScheme(.dark)
    }
}
