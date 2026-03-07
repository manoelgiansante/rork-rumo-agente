import SwiftUI

struct OnboardingPage: Identifiable {
    let id = UUID()
    let icon: String
    let title: String
    let subtitle: String
    let gradient: [Color]
}

struct OnboardingView: View {
    @Binding var hasOnboarded: Bool
    @State private var currentPage = 0

    private let pages: [OnboardingPage] = [
        OnboardingPage(
            icon: "desktopcomputer",
            title: "Seu Computador\nem Nuvem",
            subtitle: "Controle um computador completo direto do seu iPhone. Instale apps, navegue e trabalhe — de qualquer lugar.",
            gradient: [Theme.accent.opacity(0.3), Theme.darkBg]
        ),
        OnboardingPage(
            icon: "brain.head.profile.fill",
            title: "Agente IA\nInteligente",
            subtitle: "Dê comandos por chat e o agente executa no computador. Sem precisar de secretária — a IA faz por você.",
            gradient: [Theme.accentBlue.opacity(0.3), Theme.darkBg]
        ),
        OnboardingPage(
            icon: "leaf.fill",
            title: "Feito para o\nAgronegócio",
            subtitle: "Ponta do S, Rumo Máquinas, Aegro e muito mais. O agente domina os softwares da sua fazenda.",
            gradient: [Color(red: 0.2, green: 0.6, blue: 0.2).opacity(0.3), Theme.darkBg]
        ),
    ]

    var body: some View {
        ZStack {
            Theme.darkBg.ignoresSafeArea()

            VStack(spacing: 0) {
                TabView(selection: $currentPage) {
                    ForEach(Array(pages.enumerated()), id: \.element.id) { index, page in
                        VStack(spacing: 32) {
                            Spacer()

                            ZStack {
                                Circle()
                                    .fill(
                                        RadialGradient(
                                            colors: page.gradient,
                                            center: .center,
                                            startRadius: 20,
                                            endRadius: 120
                                        )
                                    )
                                    .frame(width: 200, height: 200)

                                Image(systemName: page.icon)
                                    .font(.system(size: 64, weight: .light))
                                    .foregroundStyle(.white)
                                    .symbolEffect(.pulse, options: .repeating)
                            }

                            VStack(spacing: 16) {
                                Text(page.title)
                                    .font(.system(.largeTitle, weight: .bold))
                                    .multilineTextAlignment(.center)
                                    .foregroundStyle(.white)

                                Text(page.subtitle)
                                    .font(.body)
                                    .multilineTextAlignment(.center)
                                    .foregroundStyle(Theme.subtleText)
                                    .padding(.horizontal, 32)
                            }

                            Spacer()
                            Spacer()
                        }
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                VStack(spacing: 20) {
                    HStack(spacing: 8) {
                        ForEach(0..<pages.count, id: \.self) { index in
                            Capsule()
                                .fill(index == currentPage ? Theme.accent : Color.white.opacity(0.2))
                                .frame(width: index == currentPage ? 24 : 8, height: 8)
                                .animation(.snappy, value: currentPage)
                        }
                    }

                    Button {
                        if currentPage < pages.count - 1 {
                            withAnimation(.snappy) { currentPage += 1 }
                        } else {
                            withAnimation { hasOnboarded = true }
                        }
                    } label: {
                        Text(currentPage < pages.count - 1 ? "Próximo" : "Começar")
                            .font(.headline)
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(Theme.accent, in: .rect(cornerRadius: 16))
                    }
                    .sensoryFeedback(.impact(weight: .light), trigger: currentPage)

                    if currentPage < pages.count - 1 {
                        Button("Pular") {
                            withAnimation { hasOnboarded = true }
                        }
                        .font(.subheadline)
                        .foregroundStyle(Theme.subtleText)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 40)
            }
        }
        .preferredColorScheme(.dark)
    }
}
