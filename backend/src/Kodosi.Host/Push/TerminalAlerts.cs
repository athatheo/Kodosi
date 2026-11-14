using Kodosi.TerminalConnections;

namespace Kodosi.Push;

internal sealed record PushedTerminal(Guid TerminalId, TerminalStatus Status);

internal sealed record PushAlert(Guid TerminalId, TerminalStatus Status, bool Sound, int Blocked);

internal sealed record PushNotice(Guid OwnerId, IReadOnlyList<PushedTerminal> Pushed, PushAlert? Alert = null, string? DeviceId = null);

internal sealed partial class TerminalAlerts(ConnectionDirectory directory, TimeProvider clock, ILogger<TerminalAlerts> logger)
{
    internal static readonly TimeSpan Delay = TimeSpan.FromSeconds(10);
    internal static readonly TimeSpan SoundSpacing = TimeSpan.FromSeconds(30);
    internal static readonly TimeSpan Hour = TimeSpan.FromHours(1);
    internal static readonly TimeSpan Remembered = TimeSpan.FromDays(1);
    internal const int MostEachHour = 120;
    internal const int MostListed = 32;
    internal const int MostRemembered = 65_536;
    private readonly Lock sync = new();
    private readonly Dictionary<Guid, Waiting> waiting = [];
    private readonly Dictionary<Guid, Dictionary<Guid, Pushed>> pushed = [];
    private readonly Dictionary<Guid, Queue<DateTimeOffset>> sent = [];
    private readonly Dictionary<Guid, DateTimeOffset> sounded = [];
    private List<PushNotice> outbox = [];
    private int remembered;

    public void Follow()
    {
        directory.Reported += Observe;
        directory.PersonAway += Away;
    }

    public void Unfollow()
    {
        directory.Reported -= Observe;
        directory.PersonAway -= Away;
    }

    private void Observe(TerminalReport report)
    {
        var status = report.Status;
        lock (sync)
        {
            waiting.Remove(report.SessionId);
            var push = pushed.GetValueOrDefault(report.OwnerId)?.GetValueOrDefault(report.SessionId);
            if (push is not null && (status is not { Attention: true } || status.State != push.Status.State))
            {
                Forget(report.OwnerId, report.SessionId);
                outbox.Add(new PushNotice(report.OwnerId, Listed(report.OwnerId)));
                push = null;
            }
            if (status is { Attention: true } && push is null && waiting.Count < ConnectionDirectory.MaximumHostedTerminals)
                waiting[report.SessionId] = new Waiting(report.OwnerId, status, clock.GetUtcNow() + Delay);
        }
    }

    private void Away(Guid ownerId)
    {
        lock (sync)
        {
            if (!directory.Away(ownerId)) return;
            var now = clock.GetUtcNow();
            foreach (var (terminalId, status) in directory.WithAttention(ownerId))
                if (status.State == "blocked" && pushed.GetValueOrDefault(ownerId)?.GetValueOrDefault(terminalId) is not { Status.State: "blocked" })
                {
                    waiting.Remove(terminalId);
                    Send(terminalId, ownerId, status, now);
                }
        }
    }

    public List<PushNotice> Take()
    {
        lock (sync)
        {
            var now = clock.GetUtcNow();
            foreach (var (terminalId, wait) in waiting.Where(x => x.Value.Due <= now).ToArray())
            {
                waiting.Remove(terminalId);
                if (directory.Live(terminalId) == (true, wait.Status) && directory.Away(wait.OwnerId)) Send(terminalId, wait.OwnerId, wait.Status, now);
            }
            ForgetOld(now);
            var taken = outbox;
            outbox = [];
            return taken;
        }
    }

    public IReadOnlyList<PushedTerminal> PushedOf(Guid ownerId)
    {
        lock (sync) return Listed(ownerId);
    }

    private void Send(Guid terminalId, Guid ownerId, TerminalStatus status, DateTimeOffset now)
    {
        if (!Allow(ownerId, now))
        {
            Capped(logger, ownerId, MostEachHour);
            return;
        }
        var sound = status.State == "blocked" && (!sounded.TryGetValue(ownerId, out var last) || now - last >= SoundSpacing);
        if (sound) sounded[ownerId] = now;
        Remember(ownerId, terminalId, new Pushed(status, now));
        outbox.Add(new PushNotice(ownerId, Listed(ownerId), new PushAlert(terminalId, status, sound, directory.Blocked(ownerId))));
    }

    private bool Allow(Guid ownerId, DateTimeOffset now)
    {
        if (!sent.TryGetValue(ownerId, out var times)) sent[ownerId] = times = new Queue<DateTimeOffset>();
        while (times.TryPeek(out var oldest) && now - oldest >= Hour) times.Dequeue();
        if (times.Count >= MostEachHour) return false;
        times.Enqueue(now);
        return true;
    }

    private List<PushedTerminal> Listed(Guid ownerId) =>
        pushed.TryGetValue(ownerId, out var terminals)
            ? terminals.OrderByDescending(x => x.Value.At).ThenBy(x => x.Key).Take(MostListed)
                .Select(x => new PushedTerminal(x.Key, x.Value.Status)).ToList()
            : [];

    private void Remember(Guid ownerId, Guid terminalId, Pushed push)
    {
        if (!pushed.TryGetValue(ownerId, out var terminals)) pushed[ownerId] = terminals = [];
        if (terminals.TryAdd(terminalId, push)) remembered++;
        else terminals[terminalId] = push;
        if (remembered <= MostRemembered) return;
        var oldest = pushed.SelectMany(owner => owner.Value.Select(terminal => (Owner: owner.Key, Terminal: terminal.Key, terminal.Value.At)))
            .MinBy(x => x.At);
        Forget(oldest.Owner, oldest.Terminal);
    }

    private void Forget(Guid ownerId, Guid terminalId)
    {
        if (!pushed.TryGetValue(ownerId, out var terminals) || !terminals.Remove(terminalId)) return;
        remembered--;
        if (terminals.Count == 0) pushed.Remove(ownerId);
    }

    private void ForgetOld(DateTimeOffset now)
    {
        var old = pushed.SelectMany(owner => owner.Value.Where(terminal => now - terminal.Value.At >= Remembered).Select(terminal => (owner.Key, terminal.Key)))
            .ToList();
        foreach (var (ownerId, terminalId) in old) Forget(ownerId, terminalId);
        foreach (var (ownerId, times) in sent.ToArray())
        {
            while (times.TryPeek(out var oldest) && now - oldest >= Hour) times.Dequeue();
            if (times.Count == 0) sent.Remove(ownerId);
        }
        foreach (var (ownerId, at) in sounded.ToArray())
            if (now - at >= SoundSpacing) sounded.Remove(ownerId);
    }

    private sealed record Waiting(Guid OwnerId, TerminalStatus Status, DateTimeOffset Due);

    private sealed record Pushed(TerminalStatus Status, DateTimeOffset At);

    [LoggerMessage(Level = LogLevel.Information, Message = "A push notification of account {Account} was dropped, because it had {Most} in the last hour")]
    private static partial void Capped(ILogger logger, Guid account, int most);
}
