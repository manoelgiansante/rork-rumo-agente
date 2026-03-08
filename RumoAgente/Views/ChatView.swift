import SwiftUI

struct ChatView: View {
    @State private var viewModel: ChatViewModel
    @State private var appsViewModel: AppsViewModel
    @FocusState private var isInputFocused: Bool

    init(claudeService: ClaudeService, supabase: SupabaseService) {
        _viewModel = State(initialValue: ChatViewModel(claudeService: claudeService, supabase: supabase))
        _appsViewModel = State(initialValue: AppsViewModel(supabase: supabase))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.darkBg.ignoresSafeArea()

                VStack(spacing: 0) {
                    appSelectorBar
                    Divider().foregroundStyle(Theme.cardBorder)
                    messagesScrollView
                    inputBar
                }
            }
            .navigationTitle("Agente IA")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("Limpar Conversa", systemImage: "trash") {
                            viewModel.clearConversation()
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .foregroundStyle(Theme.subtleText)
                    }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("OK") {
                        isInputFocused = false
                    }
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.accent)
                }
            }
            .task {
                viewModel.loadInitialMessages()
                await appsViewModel.loadApps()
            }
        }
        .preferredColorScheme(.dark)
    }

    private var appSelectorBar: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                appChip(name: "Geral", icon: "sparkles", isSelected: appsViewModel.currentSelectedApp == nil) {
                    for i in appsViewModel.apps.indices {
                        appsViewModel.apps[i].isSelected = false
                    }
                    viewModel.selectedApp = nil
                }

                ForEach(appsViewModel.apps.filter { $0.status == .installed || $0.status == .running }) { app in
                    appChip(name: app.name, icon: app.iconName, isSelected: app.isSelected) {
                        appsViewModel.selectApp(app)
                        viewModel.selectedApp = appsViewModel.currentSelectedApp
                    }
                }
            }
        }
        .contentMargins(.horizontal, 16)
        .scrollIndicators(.hidden)
        .padding(.vertical, 10)
    }

    private func appChip(name: String, icon: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.caption)
                Text(name)
                    .font(.caption.weight(.medium))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(isSelected ? Theme.accent.opacity(0.2) : Theme.cardBg, in: .capsule)
            .overlay(
                Capsule().stroke(isSelected ? Theme.accent.opacity(0.5) : Theme.cardBorder, lineWidth: 1)
            )
            .foregroundStyle(isSelected ? Theme.accent : Theme.subtleText)
        }
    }

    private var messagesScrollView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(viewModel.messages) { message in
                        MessageBubble(message: message) {
                            Task { await viewModel.confirmAction() }
                        } onCancel: {
                            Task { await viewModel.cancelAction() }
                        }
                        .id(message.id)
                    }

                    if viewModel.isTyping {
                        TypingIndicator()
                            .id("typing")
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: viewModel.messages.count) { _, _ in
                withAnimation(.snappy) {
                    if viewModel.isTyping {
                        proxy.scrollTo("typing", anchor: .bottom)
                    } else if let last = viewModel.messages.last {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
            .onChange(of: viewModel.isTyping) { _, newValue in
                if newValue {
                    withAnimation(.snappy) {
                        proxy.scrollTo("typing", anchor: .bottom)
                    }
                }
            }
        }
    }

    private var inputBar: some View {
        HStack(spacing: 12) {
            TextField("Digite um comando...", text: $viewModel.inputText, axis: .vertical)
                .font(.body)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Theme.cardBg, in: .rect(cornerRadius: 22))
                .overlay(
                    RoundedRectangle(cornerRadius: 22).stroke(Theme.cardBorder, lineWidth: 1)
                )
                .lineLimit(1...5)
                .focused($isInputFocused)
                .submitLabel(.send)
                .onSubmit {
                    Task { await viewModel.sendMessage() }
                }

            Button {
                Task { await viewModel.sendMessage() }
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 36))
                    .foregroundStyle(viewModel.inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Theme.subtleText : Theme.accent)
            }
            .disabled(viewModel.inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isTyping)
            .sensoryFeedback(.impact(weight: .light), trigger: viewModel.messages.count)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
    }
}

struct MessageBubble: View {
    let message: ChatMessage
    let onConfirm: () -> Void
    let onCancel: () -> Void
    @State private var showFullScreenshot = false

    private var isUser: Bool { message.role == .user }

    var body: some View {
        HStack {
            if isUser { Spacer(minLength: 60) }

            VStack(alignment: isUser ? .trailing : .leading, spacing: 8) {
                if !isUser {
                    HStack(spacing: 6) {
                        Image(systemName: "brain.head.profile.fill")
                            .font(.caption)
                            .foregroundStyle(Theme.accent)
                        Text("Agente")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(Theme.accent)
                    }
                }

                Text(message.content)
                    .font(.body)
                    .foregroundStyle(isUser ? .black : .white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(
                        isUser ? Theme.accent : Theme.cardBg,
                        in: .rect(
                            topLeadingRadius: isUser ? 20 : 6,
                            bottomLeadingRadius: 20,
                            bottomTrailingRadius: isUser ? 6 : 20,
                            topTrailingRadius: 20
                        )
                    )

                if let screenshotURL = message.screenshotURL, !screenshotURL.isEmpty {
                    Button {
                        showFullScreenshot = true
                    } label: {
                        Color(.secondarySystemBackground)
                            .frame(height: 180)
                            .overlay {
                                AsyncImage(url: URL(string: screenshotURL)) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .allowsHitTesting(false)
                                    case .failure:
                                        VStack(spacing: 8) {
                                            Image(systemName: "photo.badge.exclamationmark")
                                                .font(.title3)
                                            Text("Erro ao carregar")
                                                .font(.caption)
                                        }
                                        .foregroundStyle(Theme.subtleText)
                                    default:
                                        ProgressView()
                                            .tint(Theme.accent)
                                    }
                                }
                            }
                            .clipShape(.rect(cornerRadius: 12))
                            .overlay(alignment: .bottomTrailing) {
                                Image(systemName: "arrow.up.left.and.arrow.down.right")
                                    .font(.caption)
                                    .foregroundStyle(.white)
                                    .padding(6)
                                    .background(.black.opacity(0.5), in: Circle())
                                    .padding(8)
                            }
                    }
                    .frame(maxWidth: 260)
                }

                if message.isConfirmation && !isUser {
                    HStack(spacing: 10) {
                        Button(action: onConfirm) {
                            HStack(spacing: 4) {
                                Image(systemName: "checkmark")
                                Text("Confirmar")
                            }
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Theme.accent, in: .capsule)
                        }

                        Button(action: onCancel) {
                            HStack(spacing: 4) {
                                Image(systemName: "xmark")
                                Text("Cancelar")
                            }
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Color.red.opacity(0.6), in: .capsule)
                        }
                    }
                }

                Text(message.createdAt, format: .dateTime.hour().minute())
                    .font(.caption2)
                    .foregroundStyle(Theme.subtleText)
            }

            if !isUser { Spacer(minLength: 60) }
        }
        .fullScreenCover(isPresented: $showFullScreenshot) {
            if let screenshotURL = message.screenshotURL, let url = URL(string: screenshotURL) {
                ScreenshotFullView(url: url)
            }
        }
    }
}

struct ScreenshotFullView: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss
    @State private var zoomScale: CGFloat = 1.0
    @State private var lastZoomScale: CGFloat = 1.0

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            AsyncImage(url: url) { phase in
                if let image = phase.image {
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .scaleEffect(zoomScale)
                        .gesture(
                            MagnifyGesture()
                                .onChanged { value in
                                    zoomScale = min(max(lastZoomScale * value.magnification, 1.0), 5.0)
                                }
                                .onEnded { _ in
                                    lastZoomScale = zoomScale
                                    if zoomScale <= 1.0 {
                                        withAnimation(.spring(response: 0.3)) {
                                            zoomScale = 1.0
                                            lastZoomScale = 1.0
                                        }
                                    }
                                }
                        )
                } else if phase.error != nil {
                    ContentUnavailableView("Erro ao carregar imagem", systemImage: "photo.badge.exclamationmark")
                } else {
                    ProgressView().tint(.white)
                }
            }
        }
        .overlay(alignment: .topTrailing) {
            Button { dismiss() } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.white.opacity(0.7))
                    .padding(16)
            }
        }
        .statusBarHidden()
    }
}

struct TypingIndicator: View {
    @State private var dotScale: [CGFloat] = [0.4, 0.4, 0.4]

    var body: some View {
        HStack {
            HStack(spacing: 6) {
                Image(systemName: "brain.head.profile.fill")
                    .font(.caption2)
                    .foregroundStyle(Theme.accent.opacity(0.6))

                HStack(spacing: 5) {
                    ForEach(0..<3, id: \.self) { index in
                        Circle()
                            .fill(Theme.subtleText)
                            .frame(width: 8, height: 8)
                            .scaleEffect(dotScale[index])
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Theme.cardBg, in: .rect(cornerRadius: 20))
            .onAppear { animateDots() }

            Spacer()
        }
    }

    private func animateDots() {
        for i in 0..<3 {
            withAnimation(
                .easeInOut(duration: 0.5)
                .repeatForever(autoreverses: true)
                .delay(Double(i) * 0.15)
            ) {
                dotScale[i] = 1.0
            }
        }
    }
}
