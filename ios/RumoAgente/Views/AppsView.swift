import SwiftUI

struct AppsView: View {
    @State private var appsViewModel: AppsViewModel
    @State private var searchText = ""

    init(supabase: SupabaseService) {
        _appsViewModel = State(initialValue: AppsViewModel(supabase: supabase))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.darkBg.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        categoryFilter
                        appsGrid
                    }
                    .padding(.bottom, 24)
                }
                .refreshable {
                    await appsViewModel.loadApps()
                }
                .overlay {
                    if appsViewModel.isLoading && appsViewModel.apps.isEmpty {
                        ProgressView()
                            .tint(Theme.accent)
                            .scaleEffect(1.2)
                    }
                }
            }
            .navigationTitle("Apps")
            .searchable(text: $searchText, prompt: "Buscar aplicativos...")
            .task { await appsViewModel.loadApps() }
        }
        .preferredColorScheme(.dark)
    }

    private var categoryFilter: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                categoryChip(name: "Todos", isSelected: appsViewModel.selectedCategory == nil) {
                    withAnimation(.snappy) { appsViewModel.selectedCategory = nil }
                }
                ForEach(AppCategory.allCases, id: \.self) { category in
                    categoryChip(name: category.rawValue, isSelected: appsViewModel.selectedCategory == category) {
                        withAnimation(.snappy) { appsViewModel.selectedCategory = category }
                    }
                }
            }
        }
        .contentMargins(.horizontal, 16)
        .scrollIndicators(.hidden)
    }

    private func categoryChip(name: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(name)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(isSelected ? Theme.accent.opacity(0.2) : Theme.cardBg, in: .capsule)
                .overlay(
                    Capsule().stroke(isSelected ? Theme.accent.opacity(0.5) : Theme.cardBorder, lineWidth: 1)
                )
                .foregroundStyle(isSelected ? Theme.accent : Theme.subtleText)
        }
    }

    private var appsGrid: some View {
        let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
        let filtered = searchText.isEmpty ? appsViewModel.filteredApps : appsViewModel.filteredApps.filter {
            $0.name.localizedStandardContains(searchText)
        }

        return Group {
            if filtered.isEmpty && !appsViewModel.isLoading {
                ContentUnavailableView(
                    searchText.isEmpty ? "Nenhum app encontrado" : "Sem resultados",
                    systemImage: searchText.isEmpty ? "square.grid.2x2" : "magnifyingglass",
                    description: Text(searchText.isEmpty ? "Os aplicativos aparecerão aqui" : "Nenhum app corresponde a \"\(searchText)\"")
                )
                .frame(minHeight: 200)
            } else {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(filtered) { app in
                        AppCard(app: app) {
                            appsViewModel.selectApp(app)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

struct AppCard: View {
    let app: CloudApp
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(iconColor.opacity(0.12))
                        .frame(width: 56, height: 56)
                    Image(systemName: app.iconName)
                        .font(.title2)
                        .foregroundStyle(iconColor)
                }

                VStack(spacing: 4) {
                    Text(app.name)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(app.status.displayName)
                        .font(.caption)
                        .foregroundStyle(statusColor)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 20)
            .background(app.isSelected ? Theme.accent.opacity(0.08) : Theme.cardBg, in: .rect(cornerRadius: 18))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(app.isSelected ? Theme.accent.opacity(0.5) : Theme.cardBorder, lineWidth: app.isSelected ? 2 : 1)
            )
        }
    }

    private var iconColor: Color {
        switch app.category {
        case .agro: Theme.accent
        case .finance: .blue
        case .productivity: .orange
        case .communication: .purple
        case .other: .gray
        }
    }

    private var statusColor: Color {
        switch app.status {
        case .installed: Theme.subtleText
        case .running: .green
        case .installing: .orange
        case .notInstalled: .red.opacity(0.6)
        }
    }
}
