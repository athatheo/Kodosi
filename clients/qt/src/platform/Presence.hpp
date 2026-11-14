#pragma once

#include <QElapsedTimer>
#include <QJsonObject>
#include <QLoggingCategory>
#include <QObject>
#include <QTimer>

#include <chrono>
#include <optional>

Q_DECLARE_LOGGING_CATEGORY(kodosiPresence)

namespace kodosi {

class CommandDispatcher;

enum class Presence { Kodosi, Here, Idle, Away };

class PresenceSource : public QObject {
    Q_OBJECT

public:
    struct Input {
        // No input on this computer for the away time.
        bool quiet = false;
        // For a source that does not signal by itself when the input becomes quiet: the time until
        // it is quiet if no input comes.
        std::optional<std::chrono::milliseconds> quietIn;
    };

    using QObject::QObject;

    // Whether the system tells about input outside Kodosi's windows.
    [[nodiscard]] virtual bool reportsInput() const = 0;
    [[nodiscard]] virtual Input input() = 0;
    // The screen is locked or asleep, or the computer goes to sleep.
    [[nodiscard]] virtual bool away() const = 0;
    virtual void setAwayTime(std::chrono::milliseconds time) = 0;

signals:
    void changed();
};

struct PresenceFacts {
    bool active = false;
    bool away = false;
    std::optional<PresenceSource::Input> system;
    std::chrono::milliseconds kodosiInputAge {};
};

struct PresenceDecision {
    Presence state = Presence::Away;
    std::optional<std::chrono::milliseconds> next;

    bool operator==(const PresenceDecision&) const = default;
};

[[nodiscard]] PresenceDecision decidePresence(const PresenceFacts& facts, std::chrono::milliseconds awayTime);

class PresenceReporter final : public QObject {
    Q_OBJECT

public:
    PresenceReporter(CommandDispatcher& commands, PresenceSource& source, QObject* parent = nullptr);

    void apply(const QJsonObject& event);
    void setActive(bool active);

protected:
    bool eventFilter(QObject* watched, QEvent* event) override;

private:
    CommandDispatcher& m_commands;
    PresenceSource& m_source;
    QTimer m_next;
    QElapsedTimer m_kodosiInput;
    std::optional<std::chrono::milliseconds> m_awayTime;
    std::optional<Presence> m_reported;
    bool m_active = false;

    void decide();
};

}
