import SwiftUI

struct AuthView: View {
    @State private var viewModel: AuthViewModel
    @State private var agreedToTerms = false
    @FocusState private var focusedField: AuthField?

    enum AuthField { case name, email, password }

    init(supabase: SupabaseService) {
        _viewModel = State(initialValue: AuthViewModel(supabase: supabase))
    }

    var body: some View {
        ZStack {
            Theme.darkBg.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 32) {
                    Spacer().frame(height: 40)

                    VStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(
                                    RadialGradient(
                                        colors: [Theme.accent.opacity(0.3), Theme.accentBlue.opacity(0.15), Theme.darkBg],
                                        center: .center,
                                        startRadius: 10,
                                        endRadius: 60
                                    )
                                )
                                .frame(width: 110, height: 110)

                            Image(systemName: "brain.head.profile.fill")
                                .font(.system(size: 52, weight: .light))
                                .foregroundStyle(
                                    LinearGradient(
                                        colors: [Theme.accent, Theme.accentBlue],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .symbolEffect(.pulse, options: .repeating)
                        }

                        Text("Rumo Agente")
                            .font(.title.bold())
                            .foregroundStyle(.white)

                        Text(viewModel.isSignUp ? "Crie sua conta" : "Entre na sua conta")
                            .font(.subheadline)
                            .foregroundStyle(Theme.subtleText)
                    }

                    VStack(spacing: 16) {
                        if viewModel.isSignUp {
                            AuthTextField(
                                icon: "person.fill",
                                placeholder: "Nome completo",
                                text: $viewModel.displayName,
                                focused: $focusedField,
                                field: .name,
                                nextField: .email
                            )
                            .textContentType(.name)
                        }

                        AuthTextField(
                            icon: "envelope.fill",
                            placeholder: "Email",
                            text: $viewModel.email,
                            focused: $focusedField,
                            field: .email,
                            nextField: .password
                        )
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)

                        AuthSecureField(
                            icon: "lock.fill",
                            placeholder: "Senha",
                            text: $viewModel.password,
                            focused: $focusedField,
                            field: .password
                        )
                        .textContentType(viewModel.isSignUp ? .newPassword : .password)
                    }
                    .padding(.horizontal, 24)

                    if let error = viewModel.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }

                    if viewModel.isSignUp {
                        HStack(alignment: .top, spacing: 10) {
                            Button {
                                agreedToTerms.toggle()
                            } label: {
                                Image(systemName: agreedToTerms ? "checkmark.square.fill" : "square")
                                    .font(.title3)
                                    .foregroundStyle(agreedToTerms ? Theme.accent : Theme.subtleText)
                            }

                            Text("Li e concordo com a [Política de Privacidade](https://agente.agrorumo.com/privacidade) e os [Termos de Uso](https://agente.agrorumo.com/termos)")
                                .font(.caption)
                                .foregroundStyle(Theme.subtleText)
                                .tint(Theme.accentBlue)
                                .multilineTextAlignment(.leading)
                        }
                        .padding(.horizontal, 24)
                    }

                    VStack(spacing: 12) {
                        Button {
                            Task {
                                if viewModel.isSignUp {
                                    await viewModel.signUp()
                                } else {
                                    await viewModel.signIn()
                                }
                            }
                        } label: {
                            HStack(spacing: 8) {
                                if viewModel.isLoading {
                                    ProgressView().tint(.black)
                                }
                                Text(viewModel.isSignUp ? "Criar Conta" : "Entrar")
                                    .font(.headline)
                            }
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(Theme.accent.opacity(viewModel.isSignUp && !agreedToTerms ? 0.4 : 1.0), in: .rect(cornerRadius: 16))
                        }
                        .disabled(viewModel.isLoading || (viewModel.isSignUp && !agreedToTerms))
                        .padding(.horizontal, 24)

                        if !viewModel.isSignUp {
                            Button("Esqueci minha senha") {
                                viewModel.showResetPassword = true
                            }
                            .font(.subheadline)
                            .foregroundStyle(Theme.accentBlue)
                        }
                    }

                    dividerRow

                    VStack(spacing: 12) {
                        Button {
                            Task { await viewModel.signInWithApple() }
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "apple.logo")
                                    .font(.title3)
                                Text("Continuar com Apple")
                                    .font(.subheadline.weight(.medium))
                            }
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(.white, in: .rect(cornerRadius: 14))
                        }
                        .disabled(viewModel.isLoading)

                        Button {
                            Task { await viewModel.signInWithGoogle() }
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "g.circle.fill")
                                    .font(.title3)
                                Text("Continuar com Google")
                                    .font(.subheadline.weight(.medium))
                            }
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(Color(red: 0.15, green: 0.15, blue: 0.18), in: .rect(cornerRadius: 14))
                        }
                        .disabled(viewModel.isLoading)
                    }
                    .padding(.horizontal, 24)

                    Button {
                        withAnimation(.snappy) {
                            viewModel.isSignUp.toggle()
                            viewModel.errorMessage = nil
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Text(viewModel.isSignUp ? "Já tem conta?" : "Não tem conta?")
                                .foregroundStyle(Theme.subtleText)
                            Text(viewModel.isSignUp ? "Entrar" : "Criar conta")
                                .foregroundStyle(Theme.accent)
                        }
                        .font(.subheadline)
                    }

                    Spacer().frame(height: 20)
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $viewModel.showResetPassword) {
            ResetPasswordSheet(viewModel: viewModel)
        }
    }

    private var dividerRow: some View {
        HStack {
            Rectangle().fill(Theme.cardBorder).frame(height: 1)
            Text("ou").font(.caption).foregroundStyle(Theme.subtleText)
            Rectangle().fill(Theme.cardBorder).frame(height: 1)
        }
        .padding(.horizontal, 24)
    }

}

struct AuthTextField: View {
    let icon: String
    let placeholder: String
    @Binding var text: String
    var focused: FocusState<AuthView.AuthField?>.Binding
    let field: AuthView.AuthField
    let nextField: AuthView.AuthField?

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Theme.subtleText)
                .frame(width: 20)
            TextField(placeholder, text: $text)
                .focused(focused, equals: field)
                .onSubmit {
                    if let next = nextField {
                        focused.wrappedValue = next
                    }
                }
                .submitLabel(nextField != nil ? .next : .done)
        }
        .padding(16)
        .background(Theme.cardBg, in: .rect(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Theme.cardBorder, lineWidth: 1)
        )
    }
}

struct AuthSecureField: View {
    let icon: String
    let placeholder: String
    @Binding var text: String
    var focused: FocusState<AuthView.AuthField?>.Binding
    let field: AuthView.AuthField
    @State private var showPassword = false

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Theme.subtleText)
                .frame(width: 20)
            if showPassword {
                TextField(placeholder, text: $text)
                    .focused(focused, equals: field)
                    .submitLabel(.done)
            } else {
                SecureField(placeholder, text: $text)
                    .focused(focused, equals: field)
                    .submitLabel(.done)
            }
            Button {
                showPassword.toggle()
            } label: {
                Image(systemName: showPassword ? "eye.slash.fill" : "eye.fill")
                    .foregroundStyle(Theme.subtleText)
            }
        }
        .padding(16)
        .background(Theme.cardBg, in: .rect(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Theme.cardBorder, lineWidth: 1)
        )
    }
}

struct ResetPasswordSheet: View {
    let viewModel: AuthViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Image(systemName: "envelope.badge.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(Theme.accentBlue)

                if viewModel.resetSent {
                    Text("Email enviado!")
                        .font(.title2.bold())
                    Text("Verifique sua caixa de entrada para redefinir sua senha.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                } else {
                    Text("Recuperar Senha")
                        .font(.title2.bold())
                    Text("Digite seu email e enviaremos um link para redefinir sua senha.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                    TextField("Email", text: Bindable(viewModel).resetEmail)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .padding(16)
                        .background(Color(.secondarySystemBackground), in: .rect(cornerRadius: 14))

                    Button {
                        Task { await viewModel.resetPassword() }
                    } label: {
                        Text("Enviar Link")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.accentBlue)
                    .disabled(viewModel.isLoading)
                }
            }
            .padding(24)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fechar") { dismiss() }
                }
            }
        }
    }
}
