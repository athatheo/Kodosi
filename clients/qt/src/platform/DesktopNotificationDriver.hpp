#pragma once

#include "app/DeepLinkRouter.hpp"

#include <QObject>
#include <QString>
#include <QStringList>

namespace kodosi {

struct DesktopNotification {
    QString key;
    QString title;
    QString body;
    QString sessionId;
    QString computer;
    bool interrupt = false;

    [[nodiscard]] bool valid() const noexcept
    {
        return !key.isEmpty() && key.size() <= 256 && !title.isEmpty() && title.size() <= 320
            && body.size() <= 480 && computer.size() <= 256 && DeepLinkRouter::sessionId(sessionId);
    }
};

class DesktopNotificationDriver : public QObject {
    Q_OBJECT

public:
    using QObject::QObject;
    ~DesktopNotificationDriver() override = default;

    // A post under the key of a notification that is still shown replaces it in place.
    virtual void post(const DesktopNotification& notification) = 0;
    virtual void withdraw(const QString& key) = 0;

signals:
    void actionInvoked(
        QString key,
        QString action,
        QString activationToken);
    void notificationClosed(QString key);
};

}
