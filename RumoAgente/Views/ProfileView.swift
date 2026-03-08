import SwiftUI

struct ProfileView: View {
    let supabase: SupabaseService
    @State private var showSubscription = false
    @State private var showLogoutAlert = false
    @State private var showDeleteAlert = false
    @State private var isDeletingAccount = false
    @State private var deleteError: String?
    @State private var showLanguagePicker = false
    @State private var showNotificationSettings = false
    @AppStorage("app_language") private var appLanguage: String = "pt"
    @AppStorage("notifications_tasks") private var notifyTasks: Bool = true
    @AppStorage("notifications_credits") private var notifyCredits: Bool = true
    @Environment(\.openURL) private var openURL

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
                        deleteAccountButton
                        appVersion
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle("Perfil")
            .sheet(isPresented: $showSubscription) {
                SubscriptionView(supabase: supabase)
            }
            .sheet(isPresented: $showLanguagePicker) {
                LanguagePickerSheet(selectedLanguage: $appLanguage)
                    .presentationDetents([.medium])
            }
            .sheet(isPresented: $showNotificationSettings) {
                NotificationSettingsSheet(notifyTasks: $notifyTasks, notifyCredits: $notifyCredits)
                    .presentationDetents([.medium])
            }
            .alert("Sair da conta?", isPresented: $showLogoutAlert) {
                Button("Cancelar", role: .cancel) {}
                Button("Sair", role: .destructive) { supabase.signOut() }
            } message: {
                Text("Você precisará fazer login novamente.")
            }
            .alert("Excluir conta?", isPresented: $showDeleteAlert) {
                Button("Cancelar", role: .cancel) {}
                Button("Excluir", role: .destructive) {
                    Task {
                        isDeletingAccount = true
                        do {
                            try await supabase.deleteAccount()
                        } catch {
                            deleteError = error.localizedDescription
                        }
                        isDeletingAccount = false
                    }
                }
            } message: {
                Text("Esta ação é irreversível. Todos os seus dados serão apagados permanentemente.")
            }
            .alert("Erro ao excluir conta", isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } })) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(deleteError ?? "")
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

    private var languageDisplayName: String {
        switch appLanguage {
        case "pt": "Português"
        case "en": "English"
        case "es": "Español"
        default: "Português"
        }
    }

    private var settingsSection: some View {
        VStack(spacing: 0) {
            SectionHeader(title: "Configurações")

            Button { showLanguagePicker = true } label: {
                SettingsRowContent(icon: "globe", title: "Idioma", value: languageDisplayName)
            }

            Button { showNotificationSettings = true } label: {
                SettingsRowContent(icon: "bell.fill", title: "Notificações", value: notifyTasks ? "Ativadas" : "Desativadas")
            }

            Button {
                if let url = URL(string: "https://rork-rumo-agente.vercel.app/privacidade") {
                    openURL(url)
                }
            } label: {
                SettingsRowContent(icon: "lock.shield.fill", title: "Privacidade", value: nil)
            }
        }
    }

    private var supportSection: some View {
        VStack(spacing: 0) {
            SectionHeader(title: "Suporte")

            Button {
                if let url = URL(string: "mailto:suporte@rumoagente.com.br") {
                    openURL(url)
                }
            } label: {
                SettingsRowContent(icon: "envelope.fill", title: "Contato", value: nil)
            }

            Button {
                if let url = URL(string: "https://rork-rumo-agente.vercel.app/termos") {
                    openURL(url)
                }
            } label: {
                SettingsRowContent(icon: "doc.text", title: "Termos de Uso", value: nil)
            }
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

    private var deleteAccountButton: some View {
        Button {
            showDeleteAlert = true
        } label: {
            HStack {
                if isDeletingAccount {
                    ProgressView().tint(.red)
                }
                Image(systemName: "trash.fill")
                Text("Excluir Conta")
            }
            .font(.body.weight(.medium))
            .foregroundStyle(.red.opacity(0.7))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
        }
        .disabled(isDeletingAccount)
    }

    private var appVersion: some View {
        Text("Rumo Agente v1.0.0")
            .font(.caption)
            .foregroundStyle(Theme.subtleText.opacity(0.5))
            .padding(.top, 8)
    }
}

struct SettingsRowContent: View {
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

struct LanguagePickerSheet: View {
    @Binding var selectedLanguage: String
    @Environment(\.dismiss) private var dismiss

    private let languages: [(code: String, name: String, flag: String)] = [
        ("pt", "Português", "🇧🇷"),
        ("en", "English", "🇺🇸"),
        ("es", "Español", "🇪🇸"),
    ]

    var body: some View {
        NavigationStack {
            List {
                ForEach(languages, id: \.code) { lang in
                    Button {
                        selectedLanguage = lang.code
                        dismiss()
                    } label: {
                        HStack(spacing: 14) {
                            Text(lang.flag)
                                .font(.title2)
                            Text(lang.name)
                                .font(.body)
                                .foregroundStyle(.primary)
                            Spacer()
                            if selectedLanguage == lang.code {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(Theme.accent)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle("Idioma")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fechar") { dismiss() }
                }
            }
        }
    }
}

struct NotificationSettingsSheet: View {
    @Binding var notifyTasks: Bool
    @Binding var notifyCredits: Bool
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Toggle(isOn: $notifyTasks) {
                        Label("Tarefas Concluídas", systemImage: "checkmark.circle.fill")
                    }
                    .tint(Theme.accent)

                    Toggle(isOn: $notifyCredits) {
                        Label("Créditos Baixos", systemImage: "exclamationmark.triangle.fill")
                    }
                    .tint(Theme.accent)
                } header: {
                    Text("Notificações")
                } footer: {
                    Text("Receba alertas quando tarefas forem concluídas ou quando seus créditos estiverem acabando.")
                }
            }
            .navigationTitle("Notificações")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fechar") { dismiss() }
                }
            }
        }
    }
}
