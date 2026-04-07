import SwiftUI

struct ScreenView: View {
    let supabase: SupabaseService
    @State private var isConnected = false
    @State private var isFullScreen = false
    @State private var screenImage: UIImage?
    @State private var isLoading = false
    @State private var refreshTask: Task<Void, Never>?
    @State private var errorMessage: String?
    @State private var zoomScale: CGFloat = 1.0
    @State private var lastZoomScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var consecutiveErrors = 0

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
                                .scaleEffect(zoomScale)
                                .offset(offset)
                                .gesture(
                                    MagnifyGesture()
                                        .onChanged { value in
                                            let newScale = lastZoomScale * value.magnification
                                            zoomScale = min(max(newScale, 1.0), 5.0)
                                        }
                                        .onEnded { _ in
                                            lastZoomScale = zoomScale
                                            if zoomScale <= 1.0 {
                                                withAnimation(.spring(response: 0.3)) {
                                                    zoomScale = 1.0
                                                    lastZoomScale = 1.0
                                                    offset = .zero
                                                    lastOffset = .zero
                                                }
                                            }
                                        }
                                )
                                .simultaneousGesture(
                                    DragGesture()
                                        .onChanged { value in
                                            guard zoomScale > 1.0 else { return }
                                            offset = CGSize(
                                                width: lastOffset.width + value.translation.width,
                                                height: lastOffset.height + value.translation.height
                                            )
                                        }
                                        .onEnded { _ in
                                            lastOffset = offset
                                        }
                                )
                                .onTapGesture(count: 2) {
                                    withAnimation(.spring(response: 0.3)) {
                                        if zoomScale > 1.0 {
                                            zoomScale = 1.0
                                            lastZoomScale = 1.0
                                            offset = .zero
                                            lastOffset = .zero
                                        } else {
                                            zoomScale = 2.5
                                            lastZoomScale = 2.5
                                        }
                                    }
                                }
                        } else if isLoading {
                            VStack(spacing: 16) {
                                ProgressView()
                                    .tint(Theme.accent)
                                    .scaleEffect(1.2)
                                Text("Conectando ao seu desktop...")
                                    .font(.subheadline)
                                    .foregroundStyle(Theme.subtleText)
                            }
                        } else {
                            ScreenPlaceholderView(onConnect: {
                                connect()
                            })
                        }
                    }
                    .clipShape(.rect(cornerRadius: isFullScreen ? 0 : 12))
                    .padding(isFullScreen ? 0 : 16)

                    if !isFullScreen && isConnected {
                        screenToolbar
                    }
                }
            }
            .navigationTitle(isFullScreen ? "" : "Tela")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(isFullScreen ? .hidden : .visible, for: .navigationBar)
            .toolbar(isFullScreen ? .hidden : .visible, for: .tabBar)
            .statusBarHidden(isFullScreen)
            .overlay(alignment: .topTrailing) {
                if isFullScreen {
                    Button {
                        withAnimation(.snappy) { isFullScreen = false }
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundStyle(.white.opacity(0.7))
                            .padding(16)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
        .onDisappear {
            refreshTask?.cancel()
            refreshTask = nil
        }
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

                if !isConnected {
                    Button { connect() } label: {
                        Text("Reconectar")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(Theme.accent)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Theme.accent.opacity(0.15), in: .capsule)
                    }
                }
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
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var screenToolbar: some View {
        HStack(spacing: 16) {
            Button {
                withAnimation(.snappy) { isFullScreen = true }
            } label: {
                Label("Tela Cheia", systemImage: "arrow.up.left.and.arrow.down.right")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(Theme.subtleText)
            }

            Spacer()

            if zoomScale > 1.0 {
                Button {
                    withAnimation(.spring(response: 0.3)) {
                        zoomScale = 1.0
                        lastZoomScale = 1.0
                        offset = .zero
                        lastOffset = .zero
                    }
                } label: {
                    Label("Resetar Zoom", systemImage: "arrow.counterclockwise")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(Theme.accent)
                }
            }

            Text("\(Int(zoomScale * 100))%")
                .font(.caption.monospacedDigit())
                .foregroundStyle(Theme.subtleText)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial)
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
                guard let statusURL = URL(string: "\(backendURL)/desktop/status") else {
                    errorMessage = "URL inválida"
                    isLoading = false
                    return
                }

                var statusReq = URLRequest(url: statusURL)
                statusReq.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

                let (statusData, statusResponse) = try await URLSession.shared.data(for: statusReq)
                guard let httpResponse = statusResponse as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                    errorMessage = "Servidor offline"
                    isLoading = false
                    return
                }

                let statusJson = try JSONSerialization.jsonObject(with: statusData) as? [String: Any]
                let desktopRunning = statusJson?["desktop"] as? Bool ?? false

                if !desktopRunning {
                    guard let startURL = URL(string: "\(backendURL)/start-desktop") else {
                        errorMessage = "URL inválida"
                        isLoading = false
                        return
                    }
                    var startReq = URLRequest(url: startURL)
                    startReq.httpMethod = "POST"
                    startReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    startReq.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

                    let (startData, startResponse) = try await URLSession.shared.data(for: startReq)
                    guard let startHttp = startResponse as? HTTPURLResponse, startHttp.statusCode == 200 else {
                        errorMessage = String(data: startData, encoding: .utf8) ?? "Erro ao iniciar desktop"
                        isLoading = false
                        return
                    }

                    try await Task.sleep(for: .seconds(4))
                }

                await fetchScreenshot(token: token)

                isConnected = true
                isLoading = false
                startRefreshing(token: token)
            } catch is CancellationError {
                isLoading = false
            } catch {
                errorMessage = "Sem conexão"
                isLoading = false
            }
        }
    }

    private func disconnect() {
        refreshTask?.cancel()
        refreshTask = nil

        if let token = supabase.authTokenValue {
            Task {
                guard let stopURL = URL(string: "\(backendURL)/stop-desktop") else { return }
                var req = URLRequest(url: stopURL)
                req.httpMethod = "POST"
                req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
                _ = try? await URLSession.shared.data(for: req)
            }
        }

        isConnected = false
        screenImage = nil
        consecutiveErrors = 0
        zoomScale = 1.0
        lastZoomScale = 1.0
        offset = .zero
        lastOffset = .zero
    }

    private func startRefreshing(token: String) {
        refreshTask?.cancel()
        consecutiveErrors = 0
        refreshTask = Task {
            while !Task.isCancelled {
                await fetchScreenshot(token: token)
                try? await Task.sleep(for: .seconds(1.5))
            }
        }
    }

    private func fetchScreenshot(token: String) async {
        guard let url = URL(string: "\(backendURL)/screenshot") else { return }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 10

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return }

            if httpResponse.statusCode == 401 {
                // Try refreshing token before disconnecting
                if await supabase.refreshSession(), let newToken = supabase.authTokenValue {
                    startRefreshing(token: newToken)
                    return
                }
                errorMessage = "Sessão expirada"
                disconnect()
                return
            }

            if httpResponse.statusCode == 404 {
                // Desktop not running — auto-reconnect
                consecutiveErrors += 1
                if consecutiveErrors >= 3 {
                    errorMessage = "Desktop parou. Reconectando..."
                    refreshTask?.cancel()
                    try? await Task.sleep(for: .seconds(2))
                    connect()
                }
                return
            }

            guard httpResponse.statusCode == 200,
                  let image = UIImage(data: data) else { return }
            screenImage = image
            consecutiveErrors = 0 // Reset on success
            errorMessage = nil
        } catch {
            consecutiveErrors += 1
            if consecutiveErrors >= 5 {
                errorMessage = "Conexão instável. Verifique sua internet."
            }
            // Will retry on next cycle
        }
    }
}

struct ScreenPlaceholderView: View {
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
