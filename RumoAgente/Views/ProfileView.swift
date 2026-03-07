import SwiftUI

struct ProfileView: View {
    let supabase: SupabaseService
    @State private var showSubscription = false
    @State private var showLogoutAlert = false

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.darkBg.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 24) {
                        profileHeader
                        planCard
                        settingsSection
                        supportSection
                        logoutButton
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle("Perfil")
            .sheet(isPresented: $showSubscription) {
                SubscriptionView(supabase: supabase)
            }
            .alert("Sair da conta?", isPresented: $showLogoutAlert) {
                Button("Cancelar", role: .cancel) {}
                Button("Sair", role: .destructive) { supabase.signOut() }
            } message: {
                Text("Você precisará fazer login novamente.")
            }
        }
        .preferredColorScheme(.dark)
    }

    private var profileHeader: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(colors: [Theme.accent, Theme.accentBlue], startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .frame(width: 80, height: 80)
                Text(String((supabase.currentUser?.displayName ?? "U").prefix(1)).uppercased())
                    .font(.system(.largeTitle, weight: .bold))
                    .foregroundStyle(.white)
            }

            VStack(spacing: 4) {
                Text(supabase.currentUser?.displayName ?? "Usuário")
                    .font(.title3.bold())
                    .foregroundStyle(.white)
                Text(supabase.currentUser?.email ?? "")
                    .font(.subheadline)
                    .foregroundStyle(Theme.subtleText)
            }
        }
        .padding(.top, 8)
    }

    private var planCard: some View {
        Button { showSubscription = true } label: {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Theme.accent.opacity(0.12))
                        .frame(width: 44, height: 44)
                    Image(systemName: "crown.fill")
                        .foregroundStyle(Theme.accent)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Plano \(supabase.currentUser?.plan.displayName ?? "Gratuito")")
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text("\(supabase.currentUser?.credits ?? 10) créditos restantes")
                        .font(.subheadline)
                        .foregroundStyle(Theme.subtleText)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.subheadline)
                    .foregroundStyle(Theme.subtleText)
            }
            .padding(16)
            .background(
                LinearGradient(
                    colors: [Theme.accent.opacity(0.06), Theme.cardBg],
                    startPoint: .leading,
                    endPoint: .trailing
                ),
                in: .rect(cornerRadius: 18)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18).stroke(Theme.cardBorder, lineWidth: 1)
            )
        }
    }

    private var settingsSection: some View {
        VStack(spacing: 0) {
            SectionHeader(title: "Configurações")
            SettingsRow(icon: "globe", title: "Idioma", value: "Português")
            SettingsRow(icon: "bell.fill", title: "Notificações", value: "Ativadas")
            SettingsRow(icon: "paintbrush.fill", title: "Aparência", value: "Automático")
            SettingsRow(icon: "lock.shield.fill", title: "Privacidade", value: nil)
        }
    }

    private var supportSection: some View {
        VStack(spacing: 0) {
            SectionHeader(title: "Suporte")
            SettingsRow(icon: "questionmark.circle", title: "Central de Ajuda", value: nil)
            SettingsRow(icon: "envelope.fill", title: "Contato", value: nil)
            SettingsRow(icon: "doc.text", title: "Termos de Uso", value: nil)
        }
    }

    private var logoutButton: some View {
        Button {
            showLogoutAlert = true
        } label: {
            HStack {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                Text("Sair da Conta")
            }
            .font(.body.weight(.medium))
            .foregroundStyle(.red)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(Color.red.opacity(0.08), in: .rect(cornerRadius: 14))
        }
    }
}

struct SectionHeader: View {
    let title: String

    var body: some View {
        HStack {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.subtleText)
            Spacer()
        }
        .padding(.bottom, 8)
    }
}

struct SettingsRow: View {
    let icon: String
    let title: String
    let value: String?

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .foregroundStyle(Theme.accentBlue)
                .frame(width: 20)

            Text(title)
                .font(.body)
                .foregroundStyle(.white)

            Spacer()

            if let value {
                Text(value)
                    .font(.subheadline)
                    .foregroundStyle(Theme.subtleText)
            }

            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(Theme.subtleText.opacity(0.5))
        }
        .padding(.vertical, 14)
        .padding(.horizontal, 16)
        .background(Theme.cardBg, in: .rect(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14).stroke(Theme.cardBorder, lineWidth: 1)
        )
        .padding(.bottom, 4)
    }
}
