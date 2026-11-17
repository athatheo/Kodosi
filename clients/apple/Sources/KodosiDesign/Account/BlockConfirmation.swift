import SwiftUI

public extension View {
    func blockConfirmation(isPresented: Binding<Bool>, name: String, block: @escaping () -> Void) -> some View {
        confirmationDialog(BlockConfirmation.title(name), isPresented: isPresented, titleVisibility: .visible) {
            BlockConfirmation.button(block)
        } message: {
            BlockConfirmation.message
        }
    }

    func blockConfirmation<Person>(item: Binding<Person?>, name: (Person) -> String, block: @escaping (Person) -> Void) -> some View {
        confirmationDialog(BlockConfirmation.title(item.wrappedValue.map(name) ?? ""), item: item, titleVisibility: .visible) { person in
            BlockConfirmation.button { block(person) }
        } message: { _ in
            BlockConfirmation.message
        }
    }
}

private enum BlockConfirmation {
    static func title(_ name: String) -> Text {
        Text("Block \(name)?", bundle: .module)
    }

    static func button(_ block: @escaping () -> Void) -> some View {
        Button(role: .destructive, action: block) { Text("Block", bundle: .module) }
    }

    static var message: Text {
        Text("""
        They cannot send you friend requests. If you are friends, you stop sharing terminals with each other, \
        and each of you leaves the projects that the other owns.
        """, bundle: .module)
    }
}
