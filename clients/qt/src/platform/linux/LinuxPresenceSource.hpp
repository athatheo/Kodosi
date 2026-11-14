#pragma once

#include "platform/Presence.hpp"
#include "platform/linux/IdleWatch.hpp"

#include <QDBusConnection>
#include <QStringList>
#include <QVariantMap>

#include <memory>

namespace kodosi {

class LinuxPresenceSource final : public PresenceSource {
    Q_OBJECT

public:
    explicit LinuxPresenceSource(QObject* parent = nullptr);
    ~LinuxPresenceSource() override;

    [[nodiscard]] bool reportsInput() const override { return m_idle != nullptr; }
    [[nodiscard]] Input input() override { return { .quiet = m_quiet, .quietIn = std::nullopt }; }
    [[nodiscard]] bool away() const override { return m_locked || m_sleeping; }
    void setAwayTime(std::chrono::milliseconds time) override;

private slots:
    void prepareForSleep(bool start);
    void lock();
    void unlock();
    void sessionChanged(const QString& interface, const QVariantMap& changed, const QStringList& invalidated);

private:
    QDBusConnection m_system;
    QString m_session;
    std::unique_ptr<IdleWatch> m_idle;
    bool m_quiet = false;
    bool m_locked = false;
    bool m_sleeping = false;

    void watchSession();
    void readLockedHint();
    void set(bool& fact, bool value);
};

}
