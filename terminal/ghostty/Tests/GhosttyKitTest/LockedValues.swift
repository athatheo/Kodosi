import Synchronization

final class LockedValues<Value: Sendable>: Sendable {
    private let storage = Mutex<[Value]>([])

    var values: [Value] {
        storage.withLock { $0 }
    }

    func append(_ value: Value) {
        storage.withLock { $0.append(value) }
    }
}
