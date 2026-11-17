import Foundation
import KodosiBridge
import Synchronization

public final class TerminalConnectionToken: Sendable {
    private struct State {
        var active = true
        var inputViews: Set<UUID> = []
    }

    private static let generation = Mutex<UInt64>(0)

    private let state = Mutex(State())
    public let subscriptionId: String
    public let subscriptionGeneration: UInt64

    public init() {
        subscriptionId = UUIDv7.generate()
        subscriptionGeneration = Self.nextGeneration()
    }

    public var isActive: Bool {
        state.withLock { $0.active }
    }

    public var canSendInput: Bool {
        state.withLock { $0.active && !$0.inputViews.isEmpty }
    }

    public func setInputAllowed(_ allowed: Bool, by view: UUID) {
        state.withLock {
            if allowed {
                $0.inputViews.insert(view)
            } else {
                $0.inputViews.remove(view)
            }
        }
    }

    @discardableResult
    public func deactivate() -> Bool {
        state.withLock {
            let wasActive = $0.active
            $0.active = false
            return wasActive
        }
    }

    private static func nextGeneration() -> UInt64 {
        generation.withLock {
            precondition($0 < UInt64.max, "terminal subscription generation exhausted")
            $0 += 1
            return $0
        }
    }
}
