pragma Singleton

import QtQuick

QtObject {
    property var spots: []
    property var flying: ({})
    property Item stage: null
    property point pointer: Qt.point(0, 0)
    property bool pointing: false

    function enroll(spot: Item): void {
        spots.push(spot)
    }
    function leave(spot: Item): void {
        const index = spots.indexOf(spot)
        if (index >= 0)
            spots.splice(index, 1)
    }
    function hides(key: string, place: string): bool {
        return flying[key + "@" + place] === true
    }
    function launch(keys: var): void {
        const next = Object.assign({}, flying)
        for (const key of keys)
            next[key] = true
        flying = next
    }
    function land(key: string): void {
        if (flying[key] !== true)
            return
        const next = Object.assign({}, flying)
        delete next[key]
        flying = next
    }
    function look(item: Item, follows: bool): point {
        if (!follows || !pointing || !stage || !item)
            return Qt.point(0, 0)
        const center = item.mapToItem(stage, item.width / 2, item.height / 2)
        const dx = pointer.x - center.x
        const dy = pointer.y - center.y
        const distance = Math.hypot(dx, dy)
        if (distance <= item.width * 0.6)
            return Qt.point(0, 0)
        const strength = Math.min(1, distance / 200)
        const step = value => Math.round(value * 4) / 4
        return Qt.point(step(dx / distance * strength), step(dy / distance * strength))
    }
}
