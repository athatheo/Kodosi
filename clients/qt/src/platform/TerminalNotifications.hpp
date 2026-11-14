#pragma once

#include "presentation/SessionCatalogModel.hpp"
#include "platform/DesktopNotificationDriver.hpp"
#include <QJsonObject>

#include <QHash>
#include <QObject>

namespace kodosi {

class TerminalNotifications final : public QObject {
    Q_OBJECT

public:
    TerminalNotifications(
        SessionCatalogModel& sessions,
        DesktopNotificationDriver& driver,
        QObject* parent = nullptr);

    void apply(const QJsonObject& event);

signals:
    void sessionRequested(QString sessionId, QString activationToken);

private:
    enum class Cue { State, Program };
    struct Context {
        QString sessionId;
        QString runtimeIncarnationId;
        Cue cue = Cue::State;
    };

    SessionCatalogModel& m_sessions;
    DesktopNotificationDriver& m_driver;
    QHash<QString, Context> m_contexts;

    void post(const Context& context, QString title, QString body, bool interrupt);
    void handleAction(
        const QString& key,
        const QString& action,
        const QString& activationToken);
    void remove(const QString& key);
    void prune();
    [[nodiscard]] bool current(const Context& context) const;
};

}
