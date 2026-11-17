import SwiftUI

struct TerminalRoute: Hashable {
    let sessionId: String
}

struct ProjectRoute: Hashable {
    let projectId: String
}

struct OpenRouteAction: Equatable {
    private var tab: MainTab?
    private var push: (any Hashable) -> Void = { _ in }

    init() {}

    init(in tab: MainTab, push: @escaping (any Hashable) -> Void) {
        self.tab = tab
        self.push = push
    }

    func callAsFunction(_ route: some Hashable) {
        push(route)
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.tab == rhs.tab
    }
}

extension EnvironmentValues {
    @Entry var openRoute = OpenRouteAction()
}
