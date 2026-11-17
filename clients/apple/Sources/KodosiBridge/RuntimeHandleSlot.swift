import Foundation
import Synchronization

struct RuntimeHandleSnapshot: @unchecked Sendable {
    let pointer: UnsafeMutableRawPointer
}

final class RuntimeHandleSlot: Sendable {
    private struct State {
        var handle: RuntimeHandleSnapshot?
        var generation: UInt64 = 0
    }

    private let state = Mutex(State())

    var isRunning: Bool {
        state.withLock { $0.handle != nil }
    }

    func currentGeneration() -> UInt64? {
        state.withLock {
            guard $0.handle != nil else { return nil }
            return $0.generation
        }
    }

    @discardableResult
    func install(_ pointer: UnsafeMutableRawPointer) -> UInt64 {
        let handle = RuntimeHandleSnapshot(pointer: pointer)
        return state.withLock {
            $0.generation &+= 1
            $0.handle = handle
            return $0.generation
        }
    }

    func snapshot(matching expectedGeneration: UInt64) -> RuntimeHandleSnapshot? {
        state.withLock {
            guard $0.generation == expectedGeneration else { return nil }
            return $0.handle
        }
    }

    func tombstone() -> RuntimeHandleSnapshot? {
        state.withLock {
            defer { $0.handle = nil }
            return $0.handle
        }
    }
}
