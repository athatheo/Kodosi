import Foundation
@testable import KodosiBridge
import Testing

@MainActor
@Test func `each copy that the browser sends reaches the pasteboard, also the same words again`() {
    let feed = CanvasStore(commands: CommandSink()).feed("terminal")
    var copies: [String] = []
    feed.copied = { copies.append($0) }
    let copy = Data([0]) + Data(#"{"width":1280,"height":720,"shape":"","pointers":[],"copied":"word"}"#.utf8)
    let later = Data([0]) + Data(#"{"width":1280,"height":720,"shape":"text","pointers":[]}"#.utf8)

    feed.receive(copy)
    feed.receive(copy)
    feed.receive(later)

    #expect(copies == ["word", "word"])
}
