#if os(macOS)
import AppKit
import GhosttyKit

extension AppTerminalView {
    private static let pointers: [ghostty_action_mouse_shape_e: NSCursor] = [
        GHOSTTY_MOUSE_SHAPE_TEXT: .iBeam,
        GHOSTTY_MOUSE_SHAPE_VERTICAL_TEXT: .iBeamCursorForVerticalLayout,
        GHOSTTY_MOUSE_SHAPE_POINTER: .pointingHand,
        GHOSTTY_MOUSE_SHAPE_CONTEXT_MENU: .contextualMenu,
        GHOSTTY_MOUSE_SHAPE_CROSSHAIR: .crosshair,
        GHOSTTY_MOUSE_SHAPE_CELL: .crosshair,
        GHOSTTY_MOUSE_SHAPE_ALIAS: .dragLink,
        GHOSTTY_MOUSE_SHAPE_COPY: .dragCopy,
        GHOSTTY_MOUSE_SHAPE_NOT_ALLOWED: .operationNotAllowed,
        GHOSTTY_MOUSE_SHAPE_NO_DROP: .operationNotAllowed,
        GHOSTTY_MOUSE_SHAPE_GRAB: .openHand,
        GHOSTTY_MOUSE_SHAPE_GRABBING: .closedHand,
        GHOSTTY_MOUSE_SHAPE_COL_RESIZE: .columnResize,
        GHOSTTY_MOUSE_SHAPE_EW_RESIZE: .columnResize,
        GHOSTTY_MOUSE_SHAPE_ROW_RESIZE: .rowResize,
        GHOSTTY_MOUSE_SHAPE_NS_RESIZE: .rowResize,
        GHOSTTY_MOUSE_SHAPE_W_RESIZE: .columnResize(directions: .left),
        GHOSTTY_MOUSE_SHAPE_E_RESIZE: .columnResize(directions: .right),
        GHOSTTY_MOUSE_SHAPE_N_RESIZE: .rowResize(directions: .up),
        GHOSTTY_MOUSE_SHAPE_S_RESIZE: .rowResize(directions: .down),
        GHOSTTY_MOUSE_SHAPE_NE_RESIZE: .frameResize(position: .topRight, directions: .all),
        GHOSTTY_MOUSE_SHAPE_NESW_RESIZE: .frameResize(position: .topRight, directions: .all),
        GHOSTTY_MOUSE_SHAPE_NW_RESIZE: .frameResize(position: .topLeft, directions: .all),
        GHOSTTY_MOUSE_SHAPE_NWSE_RESIZE: .frameResize(position: .topLeft, directions: .all),
        GHOSTTY_MOUSE_SHAPE_SE_RESIZE: .frameResize(position: .bottomRight, directions: .all),
        GHOSTTY_MOUSE_SHAPE_SW_RESIZE: .frameResize(position: .bottomLeft, directions: .all),
        GHOSTTY_MOUSE_SHAPE_ZOOM_IN: .zoomIn,
        GHOSTTY_MOUSE_SHAPE_ZOOM_OUT: .zoomOut,
    ]

    override open func resetCursorRects() {
        addCursorRect(bounds, cursor: pointer)
    }

    static func pointer(for shape: ghostty_action_mouse_shape_e) -> NSCursor {
        pointers[shape] ?? .arrow
    }
}
#endif
