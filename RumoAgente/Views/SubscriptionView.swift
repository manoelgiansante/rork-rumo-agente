import SwiftUI

struct SubscriptionView: View {
    let supabase: SupabaseService
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var selectedPlan: SubscriptionPlan = .pro
    @State private var isProcessing = false
    @State private var checkoutError: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    usageOverview
                    plansSection
                    extraCreditsSection
                    featuresComparison
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 32)
            }
            .background(Theme.darkBg)
            .navigationTitle("Planos e Créditos")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fechar") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var usageOverview: some View {
        VStack(spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Uso Atual")
                        .font(.subheadline)
                        .foregroundStyle(Theme.subtleText)
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text("\(supabase.currentUser?.credits ?? 10)")
                            .font(.system(.title, design: .rounded, weight: .bold))
                            .foregroundStyle(.white)
                        Text("/ \(supabase.currentUser?.plan.includedCredits ?? 10)")
                            .font(.subheadline)
                            .foregroundStyle(Theme.subtleText)
                    }
                }
                Spacer()
                CircularProgressView(
                    progress: 1.0 - Double(supabase.currentUser?.credits ?? 10) / Double(max(supabase.currentUser?.plan.includedCredits ?? 10, 1)),
                    lineWidth: 8,
                    size: 64
                )
            }

            ProgressView(value: Double(supabase.currentUser?.credits ?? 10), total: Double(max(supabase.currentUser?.plan.includedCredits ?? 10, 1)))
                .tint(Theme.accent)
        }
        .padding(20)
        .background(Theme.cardBg, in: .rect(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20).stroke(Theme.cardBorder, lineWidth: 1)
        )
    }

    private var plansSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Escolha seu Plano")
                .font(.headline)
                .foregroundStyle(.white)

            ForEach(SubscriptionPlan.allCases, id: \.self) { plan in
                PlanCard(
                    plan: plan,
                    isSelected: selectedPlan == plan,
                    isCurrent: supabase.currentUser?.plan == plan
                ) {
                    withAnimation(.snappy) { selectedPlan = plan }
                }
            }

            if selectedPlan != supabase.currentUser?.plan && selectedPlan != .free {
                Button {
                    Task { await subscribe(plan: selectedPlan) }
                } label: {
                    if isProcessing {
                        ProgressView()
                            .tint(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 54)
                            .background(Theme.accent, in: .rect(cornerRadius: 16))
                    } else {
                        Text("Assinar \(selectedPlan.displayName)")
                            .font(.headline)
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 54)
                            .background(Theme.accent, in: .rect(cornerRadius: 16))
                    }
                }
                .disabled(isProcessing)
                .sensoryFeedback(.impact(weight: .medium), trigger: selectedPlan)

                if let checkoutError {
                    Text(checkoutError)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                }
            }
        }
    }

    private var extraCreditsSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Créditos Extras")
                .font(.headline)
                .foregroundStyle(.white)

            HStack(spacing: 12) {
                CreditPackCard(amount: 50, price: "R$ 19,90") {
                    Task { await buyCredits(amount: 50) }
                }
                CreditPackCard(amount: 200, price: "R$ 59,90") {
                    Task { await buyCredits(amount: 200) }
                }
                CreditPackCard(amount: 500, price: "R$ 119,90") {
                    Task { await buyCredits(amount: 500) }
                }
            }
        }
    }

    private var featuresComparison: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("O que está incluído")
                .font(.headline)
                .foregroundStyle(.white)

            VStack(spacing: 8) {
                featureRow(icon: "checkmark.circle.fill", text: "Acesso ao agente inteligente", included: true)
                featureRow(icon: "checkmark.circle.fill", text: "Chat com agente IA", included: true)
                featureRow(icon: "checkmark.circle.fill", text: "Instalação de apps", included: true)
                featureRow(icon: "checkmark.circle.fill", text: "Streaming da tela em tempo real", included: selectedPlan != .free)
                featureRow(icon: "checkmark.circle.fill", text: "Suporte prioritário", included: selectedPlan == .pro || selectedPlan == .enterprise)
                featureRow(icon: "checkmark.circle.fill", text: "Processamento dedicado", included: selectedPlan == .enterprise)
            }
        }
    }

    private func featureRow(icon: String, text: String, included: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: included ? "checkmark.circle.fill" : "xmark.circle")
                .foregroundStyle(included ? Theme.accent : Theme.subtleText.opacity(0.4))
            Text(text)
                .font(.subheadline)
                .foregroundStyle(included ? .white : Theme.subtleText.opacity(0.4))
            Spacer()
        }
        .padding(.vertical, 6)
    }

    private func subscribe(plan: SubscriptionPlan) async {
        guard let webURL = URL(string: "https://rork-rumo-agente.vercel.app/#subscription") else { return }
        openURL(webURL)
    }

    private func buyCredits(amount: Int) async {
        guard let webURL = URL(string: "https://rork-rumo-agente.vercel.app/#subscription") else { return }
        openURL(webURL)
    }
}

struct PlanCard: View {
    let plan: SubscriptionPlan
    let isSelected: Bool
    let isCurrent: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        Text(plan.displayName)
                            .font(.headline)
                            .foregroundStyle(.white)
                        if isCurrent {
                            Text("ATUAL")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(Theme.accent)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Theme.accent.opacity(0.15), in: .capsule)
                        }
                        if plan == .pro {
                            Text("POPULAR")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.orange)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color.orange.opacity(0.15), in: .capsule)
                        }
                    }
                    Text(plan.description)
                        .font(.caption)
                        .foregroundStyle(Theme.subtleText)
                    Text("\(plan.includedCredits) créditos/mês")
                        .font(.caption)
                        .foregroundStyle(Theme.accent)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    if plan.monthlyPrice > 0 {
                        Text("R$ \(plan.monthlyPrice, specifier: "%.2f")")
                            .font(.headline)
                            .foregroundStyle(.white)
                        Text("/mês")
                            .font(.caption)
                            .foregroundStyle(Theme.subtleText)
                    } else {
                        Text("Grátis")
                            .font(.headline)
                            .foregroundStyle(Theme.accent)
                    }
                }
            }
            .padding(16)
            .background(isSelected ? Theme.accent.opacity(0.06) : Theme.cardBg, in: .rect(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? Theme.accent.opacity(0.5) : Theme.cardBorder, lineWidth: isSelected ? 2 : 1)
            )
        }
    }
}

struct CreditPackCard: View {
    let amount: Int
    let price: String
    let onBuy: () -> Void

    var body: some View {
        Button(action: onBuy) {
            VStack(spacing: 8) {
                Text("+\(amount)")
                    .font(.title3.bold())
                    .foregroundStyle(.white)
                Text("créditos")
                    .font(.caption)
                    .foregroundStyle(Theme.subtleText)
                Text(price)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Theme.accent)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(Theme.cardBg, in: .rect(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14).stroke(Theme.cardBorder, lineWidth: 1)
            )
        }
    }
}
