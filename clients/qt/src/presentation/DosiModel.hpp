#pragma once

#include <QDateTime>
#include <QHash>
#include <QJsonObject>
#include <QObject>
#include <QSet>
#include <QStringList>
#include <QVariantList>
#include <QVariantMap>

#include <optional>

namespace kodosi {
class SessionCatalogModel;
class Workspace;

class DosiModel final : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString state READ state NOTIFY changed FINAL)
    Q_PROPERTY(bool busy READ busy NOTIFY changed FINAL)
    Q_PROPERTY(QString mood READ mood NOTIFY changed FINAL)
    Q_PROPERTY(QString line READ line NOTIFY changed FINAL)
    Q_PROPERTY(bool unseen READ unseen NOTIFY changed FINAL)
    Q_PROPERTY(bool asking READ asking NOTIFY changed FINAL)
    Q_PROPERTY(QVariantList touched READ touched NOTIFY changed FINAL)
    Q_PROPERTY(bool empty READ empty NOTIFY changed FINAL)
    Q_PROPERTY(QString liveTurn READ liveTurn NOTIFY changed FINAL)
    Q_PROPERTY(bool pending READ pending NOTIFY changed FINAL)
    Q_PROPERTY(QVariantList rows READ rows NOTIFY changed FINAL)
    Q_PROPERTY(QVariantMap reading READ reading NOTIFY changed FINAL)
    Q_PROPERTY(QVariantMap terminalQuestions READ terminalQuestions NOTIFY changed FINAL)
    Q_PROPERTY(QVariantMap touches READ touches NOTIFY touchesChanged FINAL)
    Q_PROPERTY(QVariantMap details READ details NOTIFY changed FINAL)
    Q_PROPERTY(QVariantMap instructions READ instructions NOTIFY instructionsChanged FINAL)
    Q_PROPERTY(QString draft READ draft WRITE setDraft NOTIFY draftChanged FINAL)

public:
    DosiModel(Workspace& workspace, SessionCatalogModel& sessions);

    QString state() const;
    bool busy() const;
    QString mood() const;
    QString line() const;
    bool unseen() const;
    bool asking() const;
    QVariantList touched() const;
    bool empty() const { return m_items.isEmpty(); }
    QString liveTurn() const;
    bool pending() const;
    QVariantList rows() const { return m_rows; }
    QVariantMap reading() const;
    QVariantMap terminalQuestions() const { return m_terminalQuestions; }
    QVariantMap touches() const { return m_touches; }
    QVariantMap details() const;
    QVariantMap instructions() const { return m_instructions; }
    QString draft() const { return m_draft; }
    void setDraft(const QString& draft);

    Q_INVOKABLE void send(const QString& text = {});
    Q_INVOKABLE void stop();
    Q_INVOKABLE void unqueue(const QString& id);
    Q_INVOKABLE void use(bool on);
    Q_INVOKABLE void answer(const QString& itemId, const QStringList& choices);
    Q_INVOKABLE void renew();
    Q_INVOKABLE void choose(const QVariantMap& change);
    Q_INVOKABLE void loadInstructions();
    Q_INVOKABLE void saveInstructions(const QString& text);
    Q_INVOKABLE void resetInstructions();
    Q_INVOKABLE void loadEarlier();
    Q_INVOKABLE void open(const QString& place, const QString& value);
    Q_INVOKABLE static QString moodLabel(const QString& name);
    QVariantList buildRows(const QDateTime& now) const;

signals:
    void changed();
    void touchesChanged();
    void instructionsChanged();
    void draftChanged();
    void saved();

private:
    friend class Workspace;

    struct Pending {
        QString operation;
        QString value;
    };

    Workspace& m_workspace;
    SessionCatalogModel& m_sessions;
    QJsonObject m_snapshot;
    QVariantList m_items;
    QVariantList m_rows;
    QVariantMap m_terminalQuestions;
    QVariantMap m_touches;
    QSet<QString> m_touched;
    QVariantMap m_instructions;
    QHash<QString, Pending> m_pending;
    QString m_seenTurn;
    QString m_draft;
    bool m_primed = false;
    bool m_earlier = false;
    bool m_loadingEarlier = false;

    QString terminalId(const QString& reference) const;
    QString projectId(const QString& reference) const;
    void apply(const QString& type, const QJsonObject& event);
    void applySnapshot(const QJsonObject& dosi);
    void upsert(const QVariantMap& item);
    void touch(const QVariantMap& item);
    void receiveResult(const QJsonObject& event);
    void receiveError(const QJsonObject& event);
    void request(const QString& type, QJsonObject values = {}, std::optional<QString> tracked = std::nullopt);
    void rebuild();
    void refreshSeen();
    QJsonObject context() const;
    QVariantMap present(const QVariantMap& tool, const QString& itemId) const;
    QVariantMap row(const QVariantMap& item, const QVariantList& run, bool starts) const;
    void reset();
};

}
