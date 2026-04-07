import Foundation

enum Config {
    // MARK: - Supabase Configuration
    // NOTE: Anon key is safe to embed in client apps (protected by RLS)
    static let EXPO_PUBLIC_SUPABASE_URL = "https://jxcnfyeemdltdfqtgbcl.supabase.co"
    static let EXPO_PUBLIC_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imp4Y25meWVlbWRsdGRmcXRnYmNsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njg1MDQwNTksImV4cCI6MjA4NDA4MDA1OX0.MEqgaUHb0cDVoDrXY6rc1F6YJLxzbpNiks-SFRCg2go"

    // MARK: - Stripe (publishable key is safe for client)
    static let EXPO_PUBLIC_STRIPE_PUBLISHABLE_KEY = "pk_live_51SPmgmEa6xGSraYxUhsiEmYdJ1nnCcGgMdQanHyIkfQYeJh9wACn11YyPqQvR1gjdfNEjUKC6mbN8nLEHFXxdZNu0001um2OTO"

    // MARK: - Backend VPS
    static let EXPO_PUBLIC_AGENT_BACKEND_URL = "https://vps.agrorumo.com"

    // MARK: - Vercel API
    static let VERCEL_API_URL = "https://agente.agrorumo.com/api"

    // MARK: - App Store Product IDs
    static let appStoreSubscriptionGroupID = "rumoagente_subscriptions"

    // MARK: - App Info
    static let appBundleID = "app.rork.rumoagente"
    // swiftlint:disable:next force_unwrapping — compile-time constant, guaranteed valid
    static let privacyPolicyURL = URL(string: "https://agente.agrorumo.com/privacidade")!
    // swiftlint:disable:next force_unwrapping — compile-time constant, guaranteed valid
    static let termsOfServiceURL = URL(string: "https://agente.agrorumo.com/termos")!

    // MARK: - Timeouts & Limits
    static let defaultRequestTimeout: TimeInterval = 30
    static let chatRequestTimeout: TimeInterval = 60
    static let agentRequestTimeout: TimeInterval = 120
    static let screenshotRefreshInterval: TimeInterval = 1.5
    static let screenshotRequestTimeout: TimeInterval = 10
    static let maxChatRetries = 2
    static let maxConsecutiveScreenErrors = 5
    static let maxConsecutiveDesktopErrors = 3
    static let desktopBootDelay: TimeInterval = 4
}
