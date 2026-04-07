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
    static let privacyPolicyURL = URL(string: "https://agente.agrorumo.com/privacidade")!
    static let termsOfServiceURL = URL(string: "https://agente.agrorumo.com/termos")!
}
