import Foundation
@preconcurrency import UserNotifications

/// A push from the Kodosi server carries only identifiers, so the names and the words come from
/// this device.
final class NotificationService: UNNotificationServiceExtension {
    override func didReceive(
        _ request: UNNotificationRequest, withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void,
    ) {
        guard let content = request.content.mutableCopy() as? UNMutableNotificationContent,
              let alert = TerminalAlert(request.content.userInfo)
        else {
            contentHandler(request.content)
            return
        }
        let names = TerminalNames.read()?.terminals[alert.terminal]
        if let names {
            content.title = names.name
            content.subtitle = names.computer ?? ""
        }
        content.body = alert.body(in: .app)
        content.threadIdentifier = names?.computerId ?? ""
        guard let pushed = KeptTerminals(pushed: request.content.userInfo) else {
            contentHandler(content)
            return
        }
        // The system takes the content on any thread, but its SDK does not mark the handler so.
        nonisolated(unsafe) let deliver = contentHandler
        let center = UNUserNotificationCenter.current()
        center.getDeliveredNotifications { delivered in
            center.removeDeliveredNotifications(withIdentifiers: pushed.withdrawn(from: delivered.map(\.request)))
            deliver(content)
        }
    }
}

private extension Bundle {
    /// The app that holds this extension, whose words the extension shows.
    static let app = Bundle(url: Bundle.main.bundleURL.deletingLastPathComponent().deletingLastPathComponent()) ?? .main
}
