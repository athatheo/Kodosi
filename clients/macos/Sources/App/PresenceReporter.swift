import Foundation

nonisolated enum Presence: String, Sendable {
    case kodosi, here, idle, away
}

struct PresenceSource {
    typealias Cancel = @MainActor () -> Void

    var isActive: @MainActor () -> Bool
    var secondsSinceInput: @MainActor () -> TimeInterval
    var after: @MainActor (TimeInterval, @escaping @MainActor () -> Void) -> Cancel
    var nextInput: @MainActor (@escaping @MainActor () -> Void) -> Cancel
}

final class PresenceReporter {
    enum Absence: Hashable {
        case locked, screensAsleep, asleep, switchedOut
    }

    private let source: PresenceSource
    private let send: @MainActor (Presence) -> Bool
    private var awaySeconds: TimeInterval?
    private var absences: Set<Absence> = []
    private var reported: Presence?
    private var cancelWait: PresenceSource.Cancel?

    init(source: PresenceSource, send: @escaping @MainActor (Presence) -> Bool) {
        self.source = source
        self.send = send
    }

    func connected(awaySeconds: TimeInterval) {
        self.awaySeconds = awaySeconds
        reported = nil
        update()
    }

    func disconnected() {
        awaySeconds = nil
        reported = nil
        wait(nil)
    }

    func left(_ absence: Absence) {
        guard absences.insert(absence).inserted else { return }
        update()
    }

    func returned(_ absence: Absence) {
        guard absences.remove(absence) != nil else { return }
        update()
    }

    func update() {
        guard let awaySeconds else { return }
        let sinceInput = source.secondsSinceInput()
        let presence = Self.presence(active: source.isActive(), sinceInput: sinceInput, absent: !absences.isEmpty, awaySeconds: awaySeconds)
        switch presence {
        case .kodosi, .here:
            wait(source.after(awaySeconds - sinceInput) { [weak self] in self?.update() })
        case .idle:
            wait(source.nextInput { [weak self] in self?.update() })
        case .away:
            wait(nil)
        }
        if presence != reported, send(presence) {
            reported = presence
        }
    }

    private func wait(_ cancel: PresenceSource.Cancel?) {
        cancelWait?()
        cancelWait = cancel
    }

    nonisolated static func presence(active: Bool, sinceInput: TimeInterval, absent: Bool, awaySeconds: TimeInterval) -> Presence {
        if absent {
            return .away
        }
        if sinceInput >= awaySeconds {
            return .idle
        }
        return active ? .kodosi : .here
    }
}
