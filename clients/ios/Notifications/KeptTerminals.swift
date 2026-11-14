import Foundation
import UserNotifications

/// A notification stays only while its terminal has a mark. Each push names every terminal that
/// has a push now, and a terminal that it leaves out was seen on another device.
nonisolated struct KeptTerminals {
    let terminals: Set<String>

    init(_ terminals: Set<String>) {
        self.terminals = terminals
    }

    init?(pushed userInfo: [AnyHashable: Any]) {
        guard let fields = userInfo["kodosi"] as? [String: Any], let pushed = fields["pushed"] as? [String] else { return nil }
        terminals = Set(pushed)
    }

    func withdrawn(from delivered: [UNNotificationRequest]) -> [String] {
        delivered.filter { request in
            TerminalAlert(request.content.userInfo).map { !terminals.contains($0.terminal) } ?? false
        }.map(\.identifier)
    }

    func waiting(in delivered: [UNNotificationRequest]) -> Int {
        let alerts = delivered.compactMap { TerminalAlert($0.content.userInfo) }
        return Set(alerts.filter { terminals.contains($0.terminal) && $0.state == "blocked" }.map(\.terminal)).count
    }
}
