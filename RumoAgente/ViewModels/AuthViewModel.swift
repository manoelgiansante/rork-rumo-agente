import Foundation
import AuthenticationServices

@Observable
@MainActor
class AuthViewModel {
    var email = ""
    var password = ""
    var displayName = ""
    var isSignUp = false
    var isLoading = false
    var errorMessage: String?
    var showResetPassword = false
    var resetEmail = ""
    var resetSent = false

    let supabase: SupabaseService
    private let appleSignIn = AppleSignInService()
    private var webAuthSession: ASWebAuthenticationSession?

    init(supabase: SupabaseService) {
        self.supabase = supabase
    }

    private func isValidEmail(_ email: String) -> Bool {
        let pattern = #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"#
        return email.range(of: pattern, options: .regularExpression) != nil
    }

    func signIn() async {
        guard !email.isEmpty, !password.isEmpty else {
            errorMessage = "Preencha todos os campos."
            return
        }
        guard isValidEmail(email) else {
            errorMessage = "Digite um email válido."
            return
        }
        isLoading = true
        errorMessage = nil
        do {
            try await supabase.signIn(email: email, password: password)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func signUp() async {
        guard !email.isEmpty, !password.isEmpty, !displayName.isEmpty else {
            errorMessage = "Preencha todos os campos."
            return
        }
        guard isValidEmail(email) else {
            errorMessage = "Digite um email válido."
            return
        }
        guard password.count >= 6 else {
            errorMessage = "A senha deve ter pelo menos 6 caracteres."
            return
        }
        isLoading = true
        errorMessage = nil
        do {
            try await supabase.signUp(email: email, password: password, displayName: displayName)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func signInWithApple() async {
        isLoading = true
        errorMessage = nil
        do {
            let result = try await appleSignIn.signIn()
            try await supabase.signInWithAppleToken(
                idToken: result.idToken,
                nonce: result.nonce,
                fullName: result.fullName
            )
        } catch let error as ASAuthorizationError where error.code == .canceled {
            // User cancelled
        } catch {
            errorMessage = "Falha no login com Apple: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func signInWithGoogle() async {
        isLoading = true
        errorMessage = nil
        do {
            let authURL = try await supabase.signInWithGoogleOAuth()
            await openGoogleOAuth(url: authURL)
        } catch {
            errorMessage = "Falha no login com Google: \(error.localizedDescription)"
        }
        isLoading = false
    }

    private func openGoogleOAuth(url: URL) async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let session = ASWebAuthenticationSession(
                url: url,
                callback: .customScheme("app.rork.rumoagente")
            ) { [weak self] callbackURL, error in
                Task { @MainActor [weak self] in
                    guard let self else {
                        continuation.resume()
                        return
                    }
                    self.webAuthSession = nil
                    if let callbackURL {
                        do {
                            try await self.supabase.handleOAuthCallback(url: callbackURL)
                        } catch {
                            self.errorMessage = "Falha ao autenticar com Google."
                        }
                    } else if let error {
                        let nsError = error as NSError
                        if nsError.domain == ASWebAuthenticationSessionError.errorDomain,
                           nsError.code == ASWebAuthenticationSessionError.canceledLogin.rawValue {
                            // User cancelled
                        } else {
                            self.errorMessage = "Erro no login com Google."
                        }
                    }
                    continuation.resume()
                }
            }
            session.prefersEphemeralWebBrowserSession = false
            self.webAuthSession = session
            session.start()
        }
    }

    func resetPassword() async {
        guard !resetEmail.isEmpty else {
            errorMessage = "Digite seu email."
            return
        }
        isLoading = true
        do {
            try await supabase.resetPassword(email: resetEmail)
            resetSent = true
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
