#if os(macOS)
    import AppKit
    @testable import KodosiTerminal
    import Testing

    @MainActor
    private final class CopyRecorder: NSObject {
        private(set) var count = 0

        @objc func record() {
            count += 1
        }
    }

    private final class FocusTaker: NSView {
        override var acceptsFirstResponder: Bool {
            true
        }
    }

    private func command(_ character: String, keyCode: UInt16, shift: Bool = false) throws -> NSEvent {
        try #require(NSEvent.keyEvent(
            with: .keyDown, location: .zero, modifierFlags: shift ? [.command, .shift] : .command, timestamp: 1, windowNumber: 0,
            context: nil, characters: character, charactersIgnoringModifiers: character, isARepeat: false, keyCode: keyCode,
        ))
    }

    @MainActor
    @Test func `a terminal takes its keys only with the focus, also one that a person only watches`() throws {
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 240), styleMask: [.titled], backing: .buffered, defer: false)
        let content = NSView(frame: NSRect(x: 0, y: 0, width: 400, height: 240))
        let watched = TerminalRendererHostedView(frame: NSRect(x: 0, y: 0, width: 200, height: 240))
        watched.allowsInput = false
        var shared = 0
        watched.shareShortcut = { shared += 1 }
        let other = FocusTaker(frame: NSRect(x: 200, y: 0, width: 200, height: 240))
        content.addSubview(watched)
        content.addSubview(other)
        window.contentView = content
        let recorder = CopyRecorder()
        let copyItem = NSMenuItem(title: "Copy", action: #selector(CopyRecorder.record), keyEquivalent: "c")
        copyItem.target = recorder
        let edit = NSMenuItem()
        edit.submenu = NSMenu()
        edit.submenu?.addItem(copyItem)
        let bar = NSMenu()
        bar.addItem(edit)
        let earlierMenu = NSApplication.shared.mainMenu
        NSApplication.shared.mainMenu = bar
        defer {
            NSApplication.shared.mainMenu = earlierMenu
            window.contentView = nil
        }
        let copy = try command("c", keyCode: 8)
        let share = try command("s", keyCode: 1, shift: true)

        #expect(window.makeFirstResponder(other))
        #expect(!window.performKeyEquivalent(with: copy))
        #expect(!window.performKeyEquivalent(with: share))
        #expect(shared == 0)

        #expect(window.makeFirstResponder(watched))
        #expect(window.performKeyEquivalent(with: copy))
        #expect(recorder.count == 1)
    }
#endif
