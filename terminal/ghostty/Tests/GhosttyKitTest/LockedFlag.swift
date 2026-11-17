import Synchronization

final class LockedFlag: Sendable {
    private let storage = Mutex(false)

    var value: Bool {
        get {
            storage.withLock { $0 }
        }
        set {
            storage.withLock { $0 = newValue }
        }
    }
}
