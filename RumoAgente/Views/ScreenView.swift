import SwiftUI
import WebKit

struct ScreenView: View {
    let supabase: SupabaseService
    @State private var isConnected = false
    @State private var isFullScreen = false
    @State private var showConnectionError = false

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
                        ScreenPlaceholderView(isConnected: $isConnected)
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

            Spacer()

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
}

struct ScreenPlaceholderView: View {
    @Binding var isConnected: Bool
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
                Text("Tela do Agente")
                    .font(.title3.bold())
                    .foregroundStyle(.white)

                Text("A visualização em tempo real\naparecerá aqui quando conectado.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.subtleText)
                    .multilineTextAlignment(.center)
            }

            VStack(spacing: 12) {

                Button {
                    withAnimation { isConnected.toggle() }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: isConnected ? "stop.fill" : "play.fill")
                        Text(isConnected ? "Desconectar" : "Conectar")
                    }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Theme.accent, in: .capsule)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
