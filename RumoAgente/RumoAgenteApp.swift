import SwiftUI

@main
struct RumoAgenteApp: App {
    @State private var supabase = SupabaseService()
    @State private var claudeService = ClaudeService()
    @AppStorage("hasOnboarded") private var hasOnboarded = false

    var body: some Scene {
        WindowGroup {
            Group {
                if !hasOnboarded {
                    OnboardingView(hasOnboarded: $hasOnboarded)
                } else if !supabase.isAuthenticated {
                    AuthView(supabase: supabase)
                } else {
                    ContentView(supabase: supabase, claudeService: claudeService)
                }
            }
            .animation(.snappy, value: hasOnboarded)
            .animation(.snappy, value: supabase.isAuthenticated)
            .task { await supabase.checkSession() }
            .onOpenURL { url in
                Task {
                    try? await supabase.handleOAuthCallback(url: url)
                }
            }
        }
    }
}
