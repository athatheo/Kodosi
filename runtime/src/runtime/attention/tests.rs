use super::*;

const ID: &str = "11111111-1111-4111-8111-111111111111";

fn nobody() -> BTreeSet<String> {
    BTreeSet::new()
}

fn looking() -> BTreeSet<String> {
    BTreeSet::from([ID.to_owned()])
}

fn after(start: Instant, millis: u64) -> Instant {
    start + Duration::from_millis(millis)
}

#[test]
fn a_new_state_marks_its_terminal_once_it_lasted_two_seconds_without_a_look() {
    let mut attention = Attention::default();
    let start = Instant::now();
    attention.observe(ID, Some(Waiting::Done), true, false, start);
    assert!(!attention.settle(after(start, 5000), &nobody()));

    attention.observe(ID, Some(Waiting::Blocked), true, false, start);
    assert!(!attention.settle(after(start, 1999), &nobody()));
    assert!(attention.settle(after(start, 2000), &nobody()));
    assert!(attention.marked(ID));
    assert_eq!(
        attention.take_started(),
        [(ID.to_owned(), Waiting::Blocked)]
    );
    attention.observe(ID, Some(Waiting::Blocked), true, false, after(start, 3000));
    assert!(attention.marked(ID));
    attention.observe(ID, Some(Waiting::Error), true, false, after(start, 3000));
    assert!(!attention.marked(ID));

    attention.observe(ID, Some(Waiting::Done), true, false, after(start, 4000));
    attention.observe(ID, None, true, false, after(start, 5000));
    assert!(!attention.settle(after(start, 9000), &nobody()));
    assert_eq!(attention.take_started(), Vec::new());
}

#[test]
fn a_look_while_a_state_settles_or_after_its_mark_ends_the_call() {
    let mut attention = Attention::default();
    let start = Instant::now();
    attention.observe(ID, None, true, false, start);
    attention.observe(ID, Some(Waiting::Done), true, false, start);
    assert!(!attention.look(&looking()));
    assert!(!attention.settle(after(start, 3000), &nobody()));

    attention.observe(ID, Some(Waiting::Error), true, false, after(start, 3000));
    assert!(!attention.settle(after(start, 5000), &looking()));
    assert!(!attention.settle(after(start, 6000), &nobody()));

    attention.observe(ID, Some(Waiting::Blocked), true, false, after(start, 6000));
    assert!(attention.settle(after(start, 8000), &nobody()));
    assert!(attention.look(&looking()));
    assert!(!attention.marked(ID));

    attention.observe(ID, Some(Waiting::Done), false, false, after(start, 9000));
    assert!(!attention.settle(after(start, 12000), &nobody()));
}

#[cfg(feature = "host")]
#[test]
fn a_device_of_the_owner_that_saw_the_terminal_ends_its_mark() {
    let mut attention = Attention::default();
    let start = Instant::now();
    attention.observe(ID, None, true, false, start);
    attention.observe(ID, Some(Waiting::Done), true, false, start);
    assert!(attention.settle(after(start, 2000), &nobody()));
    assert_eq!(attention.marked_state(ID), Some(Waiting::Done));
    assert!(attention.seen_elsewhere(ID));
    assert_eq!(attention.marked_state(ID), None);

    attention.observe(ID, Some(Waiting::Blocked), true, false, after(start, 3000));
    assert!(!attention.seen_elsewhere(ID));
    assert!(!attention.settle(after(start, 6000), &nobody()));
}

fn present(attention: &mut Attention, presence: Presence) -> CancellationToken {
    let lifetime = CancellationToken::new();
    attention.present(Uuid::now_v7(), presence, lifetime.clone());
    lifetime
}

#[test]
fn the_device_is_where_its_most_present_app_is_and_away_without_one() {
    let mut attention = Attention::default();
    assert_eq!(attention.changed_presence(), None);
    let attached = Uuid::now_v7();
    assert!(attention.looks(&attached));
    let here = present(&mut attention, Presence::Here);
    let kodosi = present(&mut attention, Presence::Kodosi);
    let _away = present(&mut attention, Presence::Away);
    assert_eq!(attention.changed_presence(), Some(Presence::Kodosi));
    assert_eq!(attention.changed_presence(), None);
    kodosi.cancel();
    assert_eq!(attention.changed_presence(), Some(Presence::Here));
    here.cancel();
    assert_eq!(attention.changed_presence(), Some(Presence::Away));

    let app = Uuid::now_v7();
    attention.present(app, Presence::Here, CancellationToken::new());
    assert!(!attention.looks(&app));
    attention.present(app, Presence::Kodosi, CancellationToken::new());
    assert!(attention.looks(&app));
    assert_eq!(attention.changed_presence(), Some(Presence::Kodosi));
}

fn cues(presence: Presence, elsewhere: Elsewhere) -> Option<bool> {
    let mut attention = Attention::default();
    let _app = present(&mut attention, presence);
    attention.hear_elsewhere(elsewhere);
    attention.cue(Waiting::Blocked, Instant::now())
}

#[test]
fn a_call_shows_on_a_computer_where_its_person_is_and_quietly_where_nobody_is() {
    let nowhere = Elsewhere::default();
    let in_kodosi = Elsewhere {
        kodosi: true,
        here: false,
    };
    let at_a_computer = Elsewhere {
        kodosi: false,
        here: true,
    };
    assert_eq!(cues(Presence::Here, nowhere), Some(true));
    assert_eq!(cues(Presence::Here, at_a_computer), Some(true));
    assert_eq!(cues(Presence::Here, in_kodosi), None);
    assert_eq!(cues(Presence::Away, nowhere), Some(false));
    assert_eq!(cues(Presence::Away, at_a_computer), None);
    assert_eq!(cues(Presence::Away, in_kodosi), None);
    assert_eq!(cues(Presence::Kodosi, nowhere), None);
}

#[test]
fn only_a_wait_interrupts_and_at_most_once_in_thirty_seconds() {
    let mut attention = Attention::default();
    let _app = present(&mut attention, Presence::Here);
    let start = Instant::now();
    assert_eq!(attention.cue(Waiting::Done, start), Some(false));
    assert_eq!(attention.cue(Waiting::Error, start), Some(false));
    assert_eq!(attention.cue(Waiting::Blocked, start), Some(true));
    assert_eq!(
        attention.cue(Waiting::Blocked, after(start, 29_999)),
        Some(false)
    );
    assert_eq!(
        attention.cue(Waiting::Blocked, after(start, 30_000)),
        Some(true)
    );
}

#[test]
fn a_mark_that_the_host_reports_calls_only_when_it_is_news() {
    let mut attention = Attention::default();
    let status = |state, attention| TerminalStatus {
        state: Some(state),
        attention,
        ..TerminalStatus::default()
    };
    let done = status(crate::terminal::ProgramState::Done, true);
    attention.report(ID, Some(&done), false);
    assert!(attention.marked(ID));
    assert_eq!(attention.take_started(), Vec::new());
    attention.report(ID, Some(&done), true);
    assert_eq!(attention.take_started(), Vec::new());
    let seen = status(crate::terminal::ProgramState::Done, false);
    attention.report(ID, Some(&seen), true);
    assert!(!attention.marked(ID));
    attention.report(ID, Some(&done), true);
    assert_eq!(attention.take_started(), [(ID.to_owned(), Waiting::Done)]);
    let blocked = status(crate::terminal::ProgramState::Blocked, true);
    attention.report(ID, Some(&blocked), true);
    assert_eq!(
        attention.take_started(),
        [(ID.to_owned(), Waiting::Blocked)]
    );
}

#[test]
fn a_look_at_a_reported_mark_tells_its_host_again_until_the_mark_ends() {
    let mut attention = Attention::default();
    let start = Instant::now();
    let done = TerminalStatus {
        state: Some(crate::terminal::ProgramState::Done),
        attention: true,
        ..TerminalStatus::default()
    };
    attention.report(ID, Some(&done), true);
    assert_eq!(attention.to_tell(&nobody(), start), Vec::<String>::new());
    assert_eq!(attention.to_tell(&looking(), start), [ID]);
    attention.told(ID, start);
    attention.report(ID, Some(&done), true);
    assert_eq!(
        attention.to_tell(&looking(), after(start, 9_999)),
        Vec::<String>::new()
    );
    assert_eq!(attention.to_tell(&looking(), after(start, 10_000)), [ID]);
    attention.report(ID, None, true);
    assert_eq!(
        attention.to_tell(&looking(), after(start, 20_000)),
        Vec::<String>::new()
    );
}
