namespace Kodosi;

internal static class ContractVersions
{
    public const int Api = 34;
    public const int OldestApi = 34;
    public const int Auth = 1;
    public const int TerminalConnection = 26;
    public const int OldestTerminalConnection = 26;

    public static object Report() => new
    {
        status = "ok",
        apiContractVersion = Api,
        oldestApiContractVersion = OldestApi,
        authContractVersion = Auth,
        terminalConnectionProtocolVersion = TerminalConnection,
        oldestTerminalConnectionProtocolVersion = OldestTerminalConnection,
    };
}
