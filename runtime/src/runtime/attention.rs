use std::{
    collections::{BTreeSet, HashMap},
    time::Duration,
};

use tokio::time::Instant;
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

use crate::{
    network::{Elsewhere, TerminalStatus},
    protocol::Presence,
    terminal::Waiting,
};

/// An agent that asks and goes on by itself within this time does not need its person.
const SETTLE: Duration = Duration::from_secs(2);
const QUIET_AFTER_INTERRUPT: Duration = Duration::from_secs(30);
/// A lost frame must not keep a mark that its person saw.
const SEEN_AGAIN: Duration = Duration::from_secs(10);

#[derive(Default)]
pub(super) struct Attention {
    kept: HashMap<String, Kept>,
    reported: HashMap<String, Reported>,
    started: Vec<(String, Waiting)>,
    clients: HashMap<Uuid, Client>,
    presence: Presence,
    elsewhere: Elsewhere,
    interrupted: Option<Instant>,
    listed: Option<u64>,
}

/// A mark that this runtime keeps: for the terminals that it hosts, and for friends' terminals.
struct Kept {
    wait: Option<Waiting>,
    since: Instant,
    seen: bool,
    marked: bool,
}

/// A mark that the host of one of the person's terminals reports.
struct Reported {
    wait: Option<Waiting>,
    marked: bool,
    told: Option<Instant>,
}

struct Client {
    presence: Presence,
    lifetime: CancellationToken,
}

impl Attention {
    pub(super) fn marked(&self, id: &str) -> bool {
        self.kept.get(id).is_some_and(|kept| kept.marked)
            || self
                .reported
                .get(id)
                .is_some_and(|reported| reported.marked)
    }

    #[cfg(feature = "host")]
    pub(super) fn marked_state(&self, id: &str) -> Option<Waiting> {
        self.kept
            .get(id)
            .filter(|kept| kept.marked)
            .and_then(|kept| kept.wait)
    }

    /// The state that a terminal has when this runtime first sees it is not news.
    pub(super) fn observe(
        &mut self,
        id: &str,
        wait: Option<Waiting>,
        calls: bool,
        watched: bool,
        now: Instant,
    ) {
        match self.kept.get_mut(id) {
            Some(kept) if kept.wait == wait => {}
            Some(kept) => {
                *kept = Kept {
                    wait,
                    since: now,
                    seen: wait.is_none() || !calls || watched,
                    marked: false,
                };
            }
            None => {
                self.kept.insert(
                    id.to_owned(),
                    Kept {
                        wait,
                        since: now,
                        seen: true,
                        marked: false,
                    },
                );
            }
        }
    }

    pub(super) fn settle(&mut self, now: Instant, watched: &BTreeSet<String>) -> bool {
        let mut started = false;
        for (id, kept) in &mut self.kept {
            let Some(wait) = kept.wait.filter(|_| !kept.marked && !kept.seen) else {
                continue;
            };
            if now.saturating_duration_since(kept.since) < SETTLE {
                continue;
            }
            if watched.contains(id) {
                kept.seen = true;
            } else {
                kept.marked = true;
                self.started.push((id.clone(), wait));
                started = true;
            }
        }
        started
    }

    pub(super) fn look(&mut self, watched: &BTreeSet<String>) -> bool {
        let mut ended = false;
        for id in watched {
            if let Some(kept) = self.kept.get_mut(id) {
                kept.seen = true;
                ended |= std::mem::take(&mut kept.marked);
            }
        }
        ended
    }

    #[cfg(feature = "host")]
    pub(super) fn seen_elsewhere(&mut self, id: &str) -> bool {
        self.kept.get_mut(id).is_some_and(|kept| {
            kept.seen = true;
            std::mem::take(&mut kept.marked)
        })
    }

    /// A list of the terminals is news only when an earlier list came on the same device
    /// connection: the first list after a start or a reconnect holds the marks that were made
    /// while this runtime did not hear.
    pub(super) fn listed(&mut self, connects: u64) -> bool {
        self.listed.replace(connects) == Some(connects)
    }

    pub(super) fn hears_live(&self, connects: u64) -> bool {
        self.listed == Some(connects)
    }

    /// The caller leaves out the reports of a host that is not connected, so a terminal that comes
    /// back keeps its mark and makes no new one.
    pub(super) fn report(&mut self, id: &str, status: Option<&TerminalStatus>, live: bool) {
        let wait = status.and_then(TerminalStatus::waiting);
        let marked = wait.is_some() && status.is_some_and(|status| status.attention);
        let before = self
            .reported
            .get(id)
            .filter(|before| before.marked && before.wait == wait);
        let told = before.filter(|_| marked).and_then(|before| before.told);
        if let Some(wait) = wait.filter(|_| marked && before.is_none() && live) {
            self.started.push((id.to_owned(), wait));
        }
        self.reported
            .insert(id.to_owned(), Reported { wait, marked, told });
    }

    pub(super) fn to_tell(&self, watched: &BTreeSet<String>, now: Instant) -> Vec<String> {
        watched
            .iter()
            .filter(|id| {
                self.reported.get(*id).is_some_and(|reported| {
                    reported.marked
                        && reported
                            .told
                            .is_none_or(|at| now.saturating_duration_since(at) >= SEEN_AGAIN)
                })
            })
            .cloned()
            .collect()
    }

    pub(super) fn told(&mut self, id: &str, now: Instant) {
        if let Some(reported) = self.reported.get_mut(id) {
            reported.told = Some(now);
        }
    }

    pub(super) fn retain(&mut self, ids: &BTreeSet<String>) {
        self.kept.retain(|id, _| ids.contains(id));
        self.reported.retain(|id, _| ids.contains(id));
    }

    pub(super) fn take_started(&mut self) -> Vec<(String, Waiting)> {
        std::mem::take(&mut self.started)
    }

    pub(super) fn present(
        &mut self,
        client: Uuid,
        presence: Presence,
        lifetime: CancellationToken,
    ) {
        self.clients.insert(client, Client { presence, lifetime });
    }

    /// A client that never says where its person is, such as an attached command, is a person
    /// at the terminal.
    pub(super) fn looks(&self, client: &Uuid) -> bool {
        self.clients
            .get(client)
            .is_none_or(|client| client.presence == Presence::Kodosi)
    }

    fn device(&self) -> Presence {
        self.clients
            .values()
            .filter(|client| !client.lifetime.is_cancelled())
            .map(|client| client.presence)
            .max()
            .unwrap_or_default()
    }

    pub(super) fn changed_presence(&mut self) -> Option<Presence> {
        self.clients
            .retain(|_, client| !client.lifetime.is_cancelled());
        let presence = self.device();
        (std::mem::replace(&mut self.presence, presence) != presence).then_some(presence)
    }

    pub(super) fn hear_elsewhere(&mut self, elsewhere: Elsewhere) {
        self.elsewhere = elsewhere;
    }

    pub(super) fn forget_account(&mut self) {
        self.elsewhere = Elsewhere::default();
        self.listed = None;
    }

    /// Whether this device shows a notification for a mark that started, and whether it
    /// interrupts. A person in Kodosi on any device sees the sign there, and a person at another
    /// computer gets the notification there.
    pub(super) fn cue(&mut self, wait: Waiting, now: Instant) -> Option<bool> {
        let may_interrupt = match self.device() {
            Presence::Here if !self.elsewhere.kodosi => true,
            Presence::Away if !self.elsewhere.kodosi && !self.elsewhere.here => false,
            _ => return None,
        };
        Some(may_interrupt && wait == Waiting::Blocked && self.interrupt(now))
    }

    #[cfg(feature = "host")]
    pub(super) fn note(&mut self, now: Instant) -> bool {
        let interrupt = self.device() != Presence::Away;
        if interrupt {
            self.interrupted = Some(now);
        }
        interrupt
    }

    fn interrupt(&mut self, now: Instant) -> bool {
        if self
            .interrupted
            .is_some_and(|at| now.saturating_duration_since(at) < QUIET_AFTER_INTERRUPT)
        {
            return false;
        }
        self.interrupted = Some(now);
        true
    }
}

#[cfg(test)]
mod tests;
