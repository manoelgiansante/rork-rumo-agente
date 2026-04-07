import SwiftUI

@main
struct RumoAgenteApp: App {
    @State private var supabase = SupabaseService()
    @State private var claudeService = ClaudeService()
    @State private var agentService = AgentService()
    @AppStorage("hasOnboarded") private var hasOnboarded: Bool = false
    @Environment(\.scenePhase) private var scenePhase

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
                agentService.configure(
                    baseURL: Config.EXPO_PUBLIC_AGENT_BACKEND_URL,
                    token: supabase.authTokenValue
                )
            }
            .onChange(of: supabase.isAuthenticated) { _, isAuth in
                if isAuth {
                    agentService.updateToken(supabase.authTokenValue)
                }
            }
            .onOpenURL { url in
                Task {
                    do {
                        try await supabase.handleOAuthCallback(url: url)
                    } catch {
                        #if DEBUG
                        print("[OAuth] Callback error: \(error.localizedDescription)")
                        #endif
                    }
                }
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    Task {
                        await supabase.checkSession()
                        agentService.updateToken(supabase.authTokenValue)
                    }
                }
            }
        }
    }
}
