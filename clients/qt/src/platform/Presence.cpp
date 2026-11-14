#include "platform/Presence.hpp"

#include "runtime/RuntimeBridge.hpp"

#include <QCoreApplication>
#include <QEvent>
#include <QJsonDocument>
#include <QUuid>

#include <utility>

Q_LOGGING_CATEGORY(kodosiPresence, "kodosi.presence")

namespace kodosi {
namespace {

QString name(const Presence presence)
{
    switch (presence) {
    case Presence::Kodosi:
        return QStringLiteral("kodosi");
    case Presence::Here:
        return QStringLiteral("here");
    case Presence::Idle:
        return QStringLiteral("idle");
    case Presence::Away:
        return QStringLiteral("away");
    }
    return QStringLiteral("away");
}

}

PresenceDecision decidePresence(const PresenceFacts& facts, const std::chrono::milliseconds awayTime)
{
    if (facts.away)
        return { .state = Presence::Away, .next = std::nullopt };
    if (facts.system) {
        if (facts.system->quiet)
            return { .state = Presence::Idle, .next = std::nullopt };
        return { .state = facts.active ? Presence::Kodosi : Presence::Here, .next = facts.system->quietIn };
    }
    if (facts.active && facts.kodosiInputAge < awayTime)
        return { .state = Presence::Kodosi, .next = awayTime - facts.kodosiInputAge };
    return { .state = Presence::Idle, .next = std::nullopt };
}

PresenceReporter::PresenceReporter(CommandDispatcher& commands, PresenceSource& source, QObject* parent)
    : QObject(parent)
    , m_commands(commands)
    , m_source(source)
{
    m_next.setSingleShot(true);
    connect(&m_next, &QTimer::timeout, this, &PresenceReporter::decide);
    connect(&m_source, &PresenceSource::changed, this, &PresenceReporter::decide);
    m_kodosiInput.start();
    if (!m_source.reportsInput())
        QCoreApplication::instance()->installEventFilter(this);
}

void PresenceReporter::apply(const QJsonObject& event)
{
    if (event.value(QStringLiteral("type")).toString() != QStringLiteral("system.ready"))
        return;
    const auto seconds = event.value(QStringLiteral("awaySeconds")).toInteger();
    m_reported.reset();
    m_awayTime.reset();
    if (seconds > 0) {
        m_awayTime = std::chrono::seconds(seconds);
        m_source.setAwayTime(*m_awayTime);
    }
    decide();
}

void PresenceReporter::setActive(const bool active)
{
    if (std::exchange(m_active, active) != active)
        decide();
}

bool PresenceReporter::eventFilter(QObject* watched, QEvent* event)
{
    switch (event->type()) {
    case QEvent::KeyPress:
    case QEvent::MouseButtonPress:
    case QEvent::MouseMove:
    case QEvent::Wheel:
    case QEvent::TouchBegin:
    case QEvent::TabletPress:
        m_kodosiInput.restart();
        if (m_reported != Presence::Kodosi)
            decide();
        break;
    default:
        break;
    }
    return QObject::eventFilter(watched, event);
}

void PresenceReporter::decide()
{
    m_next.stop();
    if (!m_awayTime)
        return;
    const auto decision = decidePresence(
        {
            .active = m_active,
            .away = m_source.away(),
            .system = m_source.reportsInput() ? std::optional(m_source.input()) : std::nullopt,
            .kodosiInputAge = std::chrono::milliseconds(m_kodosiInput.elapsed()),
        },
        *m_awayTime);
    if (decision.next)
        m_next.start(*decision.next);
    if (m_reported == decision.state)
        return;
    const QJsonObject command {
        { QStringLiteral("type"), QStringLiteral("app.presence") },
        { QStringLiteral("requestId"), QUuid::createUuidV7().toString(QUuid::WithoutBraces) },
        { QStringLiteral("state"), name(decision.state) },
    };
    if (!m_commands.send(QJsonDocument(command).toJson(QJsonDocument::Compact)))
        return;
    m_reported = decision.state;
    qCInfo(kodosiPresence).noquote() << "The person is" << name(decision.state);
}

}
