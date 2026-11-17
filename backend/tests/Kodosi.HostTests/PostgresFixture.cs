using Kodosi.Data;
using Microsoft.EntityFrameworkCore;
using Npgsql;
using Testcontainers.PostgreSql;
using Xunit;

namespace Kodosi.HostTests;

[CollectionDefinition("PostgreSQL")]
public sealed class PostgresCollection : ICollectionFixture<PostgresFixture>;

public sealed class PostgresFixture : IAsyncLifetime
{
    private readonly PostgreSqlContainer container = new PostgreSqlBuilder("postgres:18.7-alpine@sha256:b6972e1115a4ff8eba5d7f7d19184926d1e16d7d1136ae1e1a7ebf286da0f015").Build();
    public ValueTask InitializeAsync() => new(container.StartAsync());
    public ValueTask DisposeAsync() => container.DisposeAsync();

    public async Task<string> CreateDatabaseAsync(CancellationToken ct)
    {
        var name = "test_" + Guid.NewGuid().ToString("N");
        await using var connection = new NpgsqlConnection(container.GetConnectionString());
        await connection.OpenAsync(ct);
        await using var command = new NpgsqlCommand($"CREATE DATABASE {name}", connection);
        await command.ExecuteNonQueryAsync(ct);
        return new NpgsqlConnectionStringBuilder(container.GetConnectionString()) { Database = name }.ConnectionString;
    }

    public static KodosiDbContext Context(string connection) => new(new DbContextOptionsBuilder<KodosiDbContext>().UseNpgsql(connection).Options);
}
