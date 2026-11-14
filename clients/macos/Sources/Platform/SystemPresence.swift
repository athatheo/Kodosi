import AppKit
import CoreGraphics

extension PresenceSource {
    static let system = PresenceSource(
        isActive: { NSApp?.isActive == true },
        secondsSinceInput: {
            CGEventSource.secondsSinceLastEventType(.combinedSessionState, eventType: CGEventType(rawValue: ~0) ?? .null)
        },
        after: { seconds, action in
            let task = Task { @MainActor in
                try? await Task.sleep(for: .seconds(max(seconds, 0)))
                guard !Task.isCancelled else { return }
                action()
            }
            return { task.cancel() }
        },
        nextInput: { action in
            let watch = InputWatch(action)
            return { watch.cancel() }
        },
    )
}

/// Without the Accessibility permission macOS gives a global monitor no keys, so the mouse, the
/// keys in Kodosi and a change of the app in front also count as input.
@MainActor
private final class InputWatch {
    private static let input: NSEvent.EventTypeMask = [
        .mouseMoved, .leftMouseDown, .rightMouseDown, .otherMouseDown, .scrollWheel, .keyDown, .flagsChanged,
    ]

    private var monitors: [Any] = []
    private var activation: NSObjectProtocol?

    init(_ action: @escaping @MainActor () -> Void) {
        let fire: @MainActor () -> Void = { [weak self] in
            guard let self, !monitors.isEmpty else { return }
            cancel()
            action()
        }
        monitors = [
            NSEvent.addGlobalMonitorForEvents(matching: Self.input) { _ in MainActor.assumeIsolated(fire) },
            NSEvent.addLocalMonitorForEvents(matching: Self.input) { event in
                MainActor.assumeIsolated(fire)
                return event
            },
        ].compactMap(\.self)
        activation = NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didActivateApplicationNotification, object: nil, queue: .main,
        ) { _ in MainActor.assumeIsolated(fire) }
    }

    func cancel() {
        monitors.forEach(NSEvent.removeMonitor)
        monitors.removeAll()
        if let activation {
            NSWorkspace.shared.notificationCenter.removeObserver(activation)
            self.activation = nil
        }
    }
}

/// macOS has no API for the lock of the screen. Its protected data becomes unavailable when the
/// screen locks, which Apple's engineers name as the nearest supported signal; the notifications
/// of a locked screen that other apps use are not API.
@MainActor
enum SystemPresenceSignals {
    static func observe(_ reporter: PresenceReporter) {
        if NSApp?.isProtectedDataAvailable == false {
            reporter.left(.locked)
        }
        let workspace = NSWorkspace.shared.notificationCenter
        observe(.default, .NSApplicationProtectedDataWillBecomeUnavailable, reporter) { $0.left(.locked) }
        observe(.default, .NSApplicationProtectedDataDidBecomeAvailable, reporter) { $0.returned(.locked) }
        observe(workspace, NSWorkspace.screensDidSleepNotification, reporter) { $0.left(.screensAsleep) }
        observe(workspace, NSWorkspace.screensDidWakeNotification, reporter) { $0.returned(.screensAsleep) }
        observe(workspace, NSWorkspace.willSleepNotification, reporter) { $0.left(.asleep) }
        observe(workspace, NSWorkspace.didWakeNotification, reporter) { $0.returned(.asleep) }
        observe(workspace, NSWorkspace.sessionDidResignActiveNotification, reporter) { $0.left(.switchedOut) }
        observe(workspace, NSWorkspace.sessionDidBecomeActiveNotification, reporter) { $0.returned(.switchedOut) }
    }

    /// The reporter hears each change while it is posted, so that away reaches the runtime before
    /// the Mac sleeps.
    private static func observe(
        _ center: NotificationCenter, _ name: Notification.Name, _ reporter: PresenceReporter,
        _ change: @escaping @MainActor (PresenceReporter) -> Void,
    ) {
        _ = center.addObserver(forName: name, object: nil, queue: .main) { [weak reporter] _ in
            MainActor.assumeIsolated {
                if let reporter {
                    change(reporter)
                }
            }
        }
    }
}
