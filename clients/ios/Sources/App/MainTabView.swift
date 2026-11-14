import KodosiBridge
import KodosiDesign
import SwiftUI
import UIKit

enum MainTab: Hashable {
    case terminals, projects, dosi, settings
}

struct MainTabView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.theme) private var theme
    @State private var tab = MainTab.terminals
    @State private var terminals = NavigationPath()
    @State private var projects = NavigationPath()
    @State private var dosi = NavigationPath()
    @State private var settings = NavigationPath()

    var body: some View {
        TabView(selection: $tab) {
            Tab("Terminals", systemImage: "apple.terminal", value: .terminals) {
                NavigationStack(path: $terminals) { TerminalsHome() }
                    .environment(\.openRoute, OpenRouteAction { terminals.append($0) })
                    .tint(theme.colors.ink)
            }
            .badge(model.terminals.needsYou.count)
            .accessibilityIdentifier("tab.terminals")

            Tab("Projects", systemImage: "square.grid.2x2", value: .projects) {
                NavigationStack(path: $projects) { ProjectsHome() }
                    .environment(\.openRoute, OpenRouteAction { projects.append($0) })
                    .tint(theme.colors.ink)
            }
            .badge(model.projects.invitations.count + model.projects.entries.count(where: \.unread))
            .accessibilityIdentifier("tab.projects")

            Tab("Dosi", systemImage: "eyes", value: .dosi) {
                NavigationStack(path: $dosi) { DosiHome { tab = $0 } }
                    .environment(\.openRoute, OpenRouteAction { dosi.append($0) })
                    .tint(theme.colors.ink)
            }
            .badge(dosiCalls)
            .accessibilityIdentifier("tab.dosi")

            Tab("Settings", systemImage: "gearshape", value: .settings) {
                NavigationStack(path: $settings) { SettingsHome() }
                    .environment(\.openRoute, OpenRouteAction { settings.append($0) })
                    .tint(theme.colors.ink)
            }
            .badge(model.people.incoming.count + model.account.deviceRequests.count + (model.account.otherDeviceReset == nil ? 0 : 1))
            .accessibilityIdentifier("tab.settings")
        }
        .tabViewStyle(.sidebarAdaptable)
        .tabBarMinimizeBehavior(.onScrollDown)
        .tint(theme.colors.accentStrong)
        .onChange(of: model.terminals.sessions, initial: true) { _, sessions in model.push.remember(sessions) }
        .task(id: model.push.opened) {
            guard let terminal = model.push.opened else { return }
            let target = await model.terminals.notificationTarget(terminal)
            guard !Task.isCancelled else { return }
            model.push.opened = nil
            PresentedScreens.close()
            tab = .terminals
            terminals = NavigationPath(target.map { [TerminalRoute(sessionId: $0.id)] } ?? [])
        }
        .onChange(of: model.openedInvite != nil, initial: true) { _, opened in
            guard opened else { return }
            tab = .settings
            settings = NavigationPath([PeopleRoute()])
        }
    }

    private var dosiCalls: Int {
        let questions = model.dosi.openQuestions.count
        return questions > 0 ? questions : model.dosi.unseen ? 1 : 0
    }
}

private enum PresentedScreens {
    /// The lock's cover has a window of its own above the normal level, and it must stay.
    static func close() {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        for window in windows where window.windowLevel == .normal && window.rootViewController?.presentedViewController != nil {
            window.rootViewController?.dismiss(animated: false)
        }
    }
}
