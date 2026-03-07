import SwiftUI

struct DashboardView: View {
    let supabase: SupabaseService
    @Binding var selectedTab: Int
    @State private var viewModel: DashboardViewModel
    @State private var animateCards = false
    @State private var selectedTask: AgentTask?

    init(supabase: SupabaseService, selectedTab: Binding<Int>) {
        self.supabase = supabase
        _selectedTab = selectedTab
        _viewModel = State(initialValue: DashboardViewModel(supabase: supabase))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    headerSection
                    agentStatusCard
                    creditsCard
                    quickActionsRow
                    recentTasksSection
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Theme.darkBg)
            .navigationTitle("Dashboard")
            .navigationBarTitleDisplayMode(.large)
            .task {
                await viewModel.loadDashboard()
                withAnimation(.spring(response: 0.6)) {
                    animateCards = true
                }
            }
            .sheet(item: $selectedTask) { task in
                TaskDetailSheet(task: task)
            }
        }
        .preferredColorScheme(.dark)
    }

    private var headerSection: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Olá, \(supabase.currentUser?.displayName ?? "Produtor")!")
                    .font(.title2.bold())
                    .foregroundStyle(.white)
                Text(Date.now, format: .dateTime.weekday(.wide).day().month(.wide))
                    .font(.subheadline)
                    .foregroundStyle(Theme.subtleText)
            }
            Spacer()
            ZStack {
                Circle()
                    .fill(Theme.accent.opacity(0.15))
                    .frame(width: 44, height: 44)
                Text(String((supabase.currentUser?.displayName ?? "U").prefix(1)).uppercased())
                    .font(.headline)
                    .foregroundStyle(Theme.accent)
            }
        }
        .padding(.top, 8)
    }

    private var agentStatusCard: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Theme.accent.opacity(0.2), Theme.accentBlue.opacity(0.15)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 56, height: 56)
                Image(systemName: "brain.head.profile.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(Theme.accent)
            }

            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Text("Agente")
                        .font(.headline)
                        .foregroundStyle(.white)
                    HStack(spacing: 5) {
                        Circle()
                            .fill(viewModel.agentOnline ? Color.green : Color.orange)
                            .frame(width: 8, height: 8)
                            .shadow(color: viewModel.agentOnline ? .green.opacity(0.5) : .clear, radius: 4)
                        Text(viewModel.agentOnline ? "Pronto" : "Iniciando")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(viewModel.agentOnline ? .green : .orange)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(viewModel.agentOnline ? Color.green.opacity(0.1) : Color.orange.opacity(0.1), in: .capsule)
                }
                Text("Envie comandos pelo chat e o agente executa para você")
                    .font(.caption)
                    .foregroundStyle(Theme.subtleText)
                    .lineLimit(2)
            }

            Spacer()
        }
        .padding(20)
        .background(Theme.cardBg, in: .rect(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20).stroke(Theme.cardBorder, lineWidth: 1)
        )
        .opacity(animateCards ? 1 : 0)
        .offset(y: animateCards ? 0 : 20)
    }

    private var creditsCard: some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Créditos")
                    .font(.subheadline)
                    .foregroundStyle(Theme.subtleText)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("\(supabase.currentUser?.credits ?? 10)")
                        .font(.system(.largeTitle, design: .rounded, weight: .bold))
                        .foregroundStyle(.white)
                    Text("restantes")
                        .font(.caption)
                        .foregroundStyle(Theme.subtleText)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 8) {
                Text("Usados hoje")
                    .font(.subheadline)
                    .foregroundStyle(Theme.subtleText)
                Text("\(viewModel.creditsUsedToday)")
                    .font(.system(.title, design: .rounded, weight: .bold))
                    .foregroundStyle(Theme.accentBlue)
            }

            CircularProgressView(
                progress: Double(viewModel.creditsUsedToday) / Double(max(supabase.currentUser?.plan.includedCredits ?? 10, 1)),
                lineWidth: 6,
                size: 52
            )
        }
        .padding(20)
        .background(
            LinearGradient(
                colors: [Theme.accent.opacity(0.08), Theme.cardBg],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: .rect(cornerRadius: 20)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20).stroke(Theme.cardBorder, lineWidth: 1)
        )
        .opacity(animateCards ? 1 : 0)
        .offset(y: animateCards ? 0 : 20)
    }

    private var quickActionsRow: some View {
        HStack(spacing: 12) {
            QuickActionButton(icon: "desktopcomputer", title: "Ver Tela", color: Theme.accentBlue) {
                selectedTab = 1
            }
            QuickActionButton(icon: "message.fill", title: "Chat", color: Theme.accent) {
                selectedTab = 2
            }
            QuickActionButton(icon: "square.grid.2x2.fill", title: "Apps", color: .orange) {
                selectedTab = 3
            }
            QuickActionButton(icon: "creditcard.fill", title: "Planos", color: .purple) {
                selectedTab = 4
            }
        }
        .opacity(animateCards ? 1 : 0)
        .offset(y: animateCards ? 0 : 20)
    }

    private var recentTasksSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Tarefas Recentes")
                .font(.headline)
                .foregroundStyle(.white)

            if viewModel.recentTasks.isEmpty {
                ContentUnavailableView(
                    "Nenhuma tarefa",
                    systemImage: "checkmark.circle",
                    description: Text("Suas tarefas aparecerão aqui")
                )
                .frame(height: 150)
            } else {
                ForEach(viewModel.recentTasks) { task in
                    Button {
                        selectedTask = task
                    } label: {
                        TaskRow(task: task)
                    }
                    .sensoryFeedback(.selection, trigger: selectedTask?.id)
                }
            }
        }
        .opacity(animateCards ? 1 : 0)
        .offset(y: animateCards ? 0 : 20)
    }
}

struct CircularProgressView: View {
    let progress: Double
    let lineWidth: CGFloat
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white.opacity(0.08), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: min(progress, 1.0))
                .stroke(Theme.accent, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: size, height: size)
    }
}

struct QuickActionButton: View {
    let icon: String
    let title: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(color.opacity(0.12))
                        .frame(width: 52, height: 52)
                    Image(systemName: icon)
                        .font(.title3)
                        .foregroundStyle(color)
                }
                Text(title)
                    .font(.caption2)
                    .foregroundStyle(Theme.subtleText)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

struct TaskRow: View {
    let task: AgentTask

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(statusColor.opacity(0.12))
                    .frame(width: 40, height: 40)
                Image(systemName: task.status.iconName)
                    .font(.body)
                    .foregroundStyle(statusColor)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(task.title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                HStack(spacing: 8) {
                    if let app = task.appName {
                        Text(app)
                            .font(.caption)
                            .foregroundStyle(Theme.accent)
                    }
                    Text(task.createdAt, format: .dateTime.hour().minute())
                        .font(.caption)
                        .foregroundStyle(Theme.subtleText)
                }
            }

            Spacer()

            HStack(spacing: 4) {
                VStack(alignment: .trailing, spacing: 2) {
                    Text("-\(task.creditsUsed)")
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                        .foregroundStyle(Theme.subtleText)
                    Text("créditos")
                        .font(.caption2)
                        .foregroundStyle(Theme.subtleText.opacity(0.6))
                }
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(Theme.subtleText.opacity(0.4))
            }
        }
        .padding(14)
        .background(Theme.cardBg, in: .rect(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14).stroke(Theme.cardBorder, lineWidth: 1)
        )
    }

    private var statusColor: Color {
        switch task.status {
        case .completed: .green
        case .running: Theme.accentBlue
        case .failed: .red
        case .pending, .waitingConfirmation: .orange
        }
    }
}

struct TaskDetailSheet: View {
    let task: AgentTask
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    ZStack {
                        Circle()
                            .fill(statusColor.opacity(0.12))
                            .frame(width: 72, height: 72)
                        Image(systemName: task.status.iconName)
                            .font(.system(size: 32))
                            .foregroundStyle(statusColor)
                    }
                    .padding(.top, 8)

                    VStack(spacing: 6) {
                        Text(task.title)
                            .font(.title3.bold())
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.center)
                        Text(task.status.displayName)
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(statusColor)
                    }

                    VStack(spacing: 0) {
                        detailRow(icon: "app.fill", label: "Aplicativo", value: task.appName ?? "Geral")
                        Divider().foregroundStyle(Theme.cardBorder)
                        detailRow(icon: "creditcard.fill", label: "Créditos usados", value: "\(task.creditsUsed)")
                        Divider().foregroundStyle(Theme.cardBorder)
                        detailRow(icon: "clock.fill", label: "Iniciada em", value: task.createdAt.formatted(.dateTime.day().month().hour().minute()))
                        if let completedAt = task.completedAt {
                            Divider().foregroundStyle(Theme.cardBorder)
                            detailRow(icon: "checkmark.circle.fill", label: "Concluída em", value: completedAt.formatted(.dateTime.day().month().hour().minute()))
                        }
                    }
                    .background(Theme.cardBg, in: .rect(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16).stroke(Theme.cardBorder, lineWidth: 1)
                    )

                    if task.status == .running {
                        HStack(spacing: 8) {
                            ProgressView()
                                .tint(Theme.accentBlue)
                            Text("Tarefa em execução...")
                                .font(.subheadline)
                                .foregroundStyle(Theme.accentBlue)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity)
                        .background(Theme.accentBlue.opacity(0.08), in: .rect(cornerRadius: 14))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
            .background(Theme.darkBg)
            .navigationTitle("Detalhes da Tarefa")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(Theme.subtleText)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func detailRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Theme.accent)
                .frame(width: 20)
            Text(label)
                .font(.subheadline)
                .foregroundStyle(Theme.subtleText)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.white)
        }
        .padding(16)
    }

    private var statusColor: Color {
        switch task.status {
        case .completed: .green
        case .running: Theme.accentBlue
        case .failed: .red
        case .pending, .waitingConfirmation: .orange
        }
    }
}
