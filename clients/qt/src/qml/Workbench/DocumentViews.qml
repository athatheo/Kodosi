pragma Singleton
pragma ComponentBehavior: Bound

import QtQuick

// A view belongs to the parking place of the window, so no canvas takes it along when the canvas
// goes. A view off the screen waits there with its place in the file and its undo.
QtObject {
    id: root

    // An item of the window that never shows.
    property Item parking: null
    property var pages: ({})

    readonly property Component page: Component {
        CanvasDocument {
            id: document

            Timer {
                interval: 300000
                running: document.parent === root.parking
                onTriggered: root.close(document.sessionId, document.key)
            }
        }
    }

    function nameOf(sessionId: string, key: string): string {
        return sessionId + "\n" + key;
    }
    function show(sessionId: string, key: string, view: string, place: Item): void {
        const name = nameOf(sessionId, key);
        if (!pages[name])
            pages[name] = page.createObject(parking, { sessionId: sessionId, key: key, view: view });
        const shown = pages[name];
        shown.parent = place;
        shown.anchors.fill = place;
    }
    function hide(sessionId: string, key: string): void {
        const shown = pages[nameOf(sessionId, key)];
        if (!shown)
            return;
        shown.anchors.fill = undefined;
        shown.parent = parking;
    }
    function selection(sessionId: string, key: string, done: var): void {
        const shown = pages[nameOf(sessionId, key)];
        if (shown)
            shown.selection(done);
        else
            done("");
    }
    function close(sessionId: string, key: string): void {
        const name = nameOf(sessionId, key);
        const shown = pages[name];
        delete pages[name];
        if (shown)
            shown.destroy();
    }
}
