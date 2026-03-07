import SwiftUI

struct ScreenView: View {
    let supabase: SupabaseService
    @State private var isConnected = false
    @State private var isFullScreen = false
    @State private var screenImage: UIImage?
    @State private var isLoading = false
    @State private var refreshTimer: Timer?
    @State private var errorMessage: String?

    private let backendURL = Config.EXPO_PUBLIC_AGENT_BACKEND_URL

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.darkBg.ignoresSafeArea()

                VStack(spacing: 0) {
                    if !isFullScreen {
                        connectionStatusBar
                    }

                    ZStack {
                        Color.black

                        if isConnected, let image = screenImage {
                            Image(uiImage: image)
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                        } else if isLoading {
                            ProgressView("Conectando ao seu desktop...")
                                .tint(Theme.accent)
                                .foregroundStyle(.white)
                        } else {
                            ScreenPlaceholderView(isConnected: $isConnected, onConnect: {
                                connect()
                            })
                        }
                    }
                    .clipShape(.rect(cornerRadius: isFullScreen ? 0 : 12))
                    .padding(isFullScreen ? 0 : 16)
                    .onTapGesture(count: 2) {
                        withAnimation(.snappy) { isFullScreen.toggle() }
                    }
                }
            }
            .navigationTitle(isFullScreen ? "" : "Tela")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(isFullScreen ? .hidden : .visible, for: .navigationBar)
            .toolbar(isFullScreen ? .hidden : .visible, for: .tabBar)
            .statusBarHidden(isFullScreen)
        }
        .preferredColorScheme(.dark)
        .onDisappear { stopRefreshing() }
    }

    private var connectionStatusBar: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(isConnected ? .green : .red)
                .frame(width: 8, height: 8)
                .shadow(color: isConnected ? .green.opacity(0.5) : .clear, radius: 4)

            Text(isConnected ? "Conectado" : "Desconectado")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.white)

            if let error = errorMessage {
                Text(error)
                    .font(.caption2)
                    .foregroundStyle(.red)
                    .lineLimit(1)
            }

            Spacer()

            if isConnected {
                Button { disconnect() } label: {
                    Text("Desconectar")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.red)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(.red.opacity(0.15), in: .capsule)
                }
            }

            Button {
                withAnimation(.snappy) { isFullScreen = true }
            } label: {
                Image(systemName: "arrow.up.left.and.arrow.down.right")
                    .font(.subheadline)
                    .foregroundStyle(Theme.subtleText)
                    .padding(8)
                    .background(Theme.cardBg, in: Circle())
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private func connect() {
        guard let token = supabase.authTokenValue else {
            errorMessage = "Faça login primeiro"
            return
        }

        isLoading = true
        errorMessage = nil

        Task {
            do {
                // Check user's desktop status
                guard let statusURL = URL(string: "\(backendURL)/desktop/status") else {
                    await MainActor.run { errorMessage = "URL inválida"; isLoading = false }
                    return
                }

                var statusReq = URLRequest(url: statusURL)
                statusReq.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

                let (statusData, statusResponse) = try await URLSession.shared.data(for: statusReq)
                guard let httpResponse = statusResponse as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                    await MainActor.run { errorMessage = "Servidor offline"; isLoading = false }
                    return
                }

                let statusJson = try JSONSerialization.jsonObject(with: statusData) as? [String: Any]
                let desktopRunning = statusJson?["desktop"] as? Bool ?? false

                if !desktopRunning {
                    // Start user's isolated desktop
                    var startReq = URLRequest(url: URL(string: "\(backendURL)/start-desktop")!)
                    startReq.httpMethod = "POST"
                    startReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    startReq.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

                    let (startData, startResponse) = try await URLSession.shared.data(for: startReq)
                    guard let startHttp = startResponse as? HTTPURLResponse, startHttp.statusCode == 200 else {
                        let errorBody = String(data: startData, encoding: .utf8) ?? "Erro"
                        await MainActor.run { errorMessage = errorBody; isLoading = false }
                        return
                    }

                    // Wait for desktop to boot
                    try await Task.sleep(for: .seconds(4))
                }

                await fetchScreenshot(token: token)

                await MainActor.run {
                    isConnected = true
                    isLoading = false
                    startRefreshing(token: token)
                }
            } catch {
                await MainActor.run {
                    errorMessage = "Sem conexão"
                    isLoading = false
                }
            }
        }
    }

    private func disconnect() {
        stopRefreshing()

        // Stop user's desktop container
        if let token = supabase.authTokenValue {
            Task {
                var req = URLRequest(url: URL(string: "\(backendURL)/stop-desktop")!)
                req.httpMethod = "POST"
                req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
                _ = try? await URLSession.shared.data(for: req)
            }
        }

        isConnected = false
        screenImage = nil
    }

    private func stopRefreshing() {
        refreshTimer?.invalidate()
        refreshTimer = nil
    }

    private func startRefreshing(token: String) {
        refreshTimer?.invalidate()
        refreshTimer = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: true) { _ in
            Task { await fetchScreenshot(token: token) }
        }
    }

    private func fetchScreenshot(token: String) async {
        guard let url = URL(string: "\(backendURL)/screenshot") else { return }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200,
                  let image = UIImage(data: data) else { return }
            await MainActor.run { self.screenImage = image }
        } catch {}
    }
}

struct ScreenPlaceholderView: View {
    @Binding var isConnected: Bool
    var onConnect: () -> Void
    @State private var pulseAnimation = false

    var body: some View {
        VStack(spacing: 24) {
            ZStack {
                Circle()
                    .fill(Theme.accent.opacity(0.06))
                    .frame(width: 160, height: 160)
                    .scaleEffect(pulseAnimation ? 1.15 : 1.0)
                    .opacity(pulseAnimation ? 0.3 : 0.6)

                Circle()
                    .fill(Theme.accent.opacity(0.1))
                    .frame(width: 110, height: 110)

                Image(systemName: "display")
                    .font(.system(size: 44))
                    .foregroundStyle(Theme.accent.opacity(0.7))
            }
            .onAppear {
                withAnimation(.easeInOut(duration: 2).repeatForever(autoreverses: true)) {
                    pulseAnimation = true
                }
            }

            VStack(spacing: 8) {
                Text("Seu Computador na Nuvem")
                    .font(.title3.bold())
                    .foregroundStyle(.white)

                Text("Desktop privado e isolado.\nSeus dados ficam seguros e separados.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.subtleText)
                    .multilineTextAlignment(.center)
            }

            Button {
                onConnect()
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "play.fill")
                    Text("Conectar")
                }
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.black)
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
                .background(Theme.accent, in: .capsule)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
