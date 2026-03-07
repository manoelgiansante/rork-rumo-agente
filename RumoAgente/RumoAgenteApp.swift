import SwiftUI

@main
struct RumoAgenteApp: App {
    @State private var supabase = SupabaseService()
    @State private var claudeService = ClaudeService()
    @State private var agentService = AgentService()
    @AppStorage("hasOnboarded") private var hasOnboarded = false

    var body: some Scene {
        WindowGroup {
            Group {
                if !hasOnboarded {
                    OnboardingView(hasOnboarded: $hasOnboarded)
                } else if !supabase.isAuthenticated {
                    AuthView(supabase: supabase)
                } else {
                    ContentView(supabase: supabase, claudeService: claudeService, agentService: agentService)
                }
            }
            .animation(.snappy, value: hasOnboarded)
            .animation(.snappy, value: supabase.isAuthenticated)
            .task {
                await supabase.checkSession()
                agentService.configure(baseURL: "http://216.238.111.253")
            }
            .onOpenURL { url in
                Task {
                    try? await supabase.handleOAuthCallback(url: url)
                }
            }
        }
    }
}
