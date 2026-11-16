using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Kodosi.Data.Migrations
{
    /// <inheritdoc />
    public partial class DeleteAndClearMessages : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<long>(
                name: "Position",
                table: "project_items",
                type: "bigint",
                nullable: false,
                defaultValue: 0L);

            migrationBuilder.Sql("""UPDATE "project_items" SET "Position" = "Sequence";""");

            migrationBuilder.CreateTable(
                name: "cleared_conversations",
                columns: table => new
                {
                    UserId = table.Column<Guid>(type: "uuid", nullable: false),
                    ProjectId = table.Column<Guid>(type: "uuid", nullable: false),
                    Position = table.Column<long>(type: "bigint", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_cleared_conversations", x => new { x.ProjectId, x.UserId });
                    table.ForeignKey(
                        name: "FK_cleared_conversations_projects_ProjectId",
                        column: x => x.ProjectId,
                        principalTable: "projects",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_cleared_conversations_users_UserId",
                        column: x => x.UserId,
                        principalTable: "users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_project_items_ProjectId_Kind_Position",
                table: "project_items",
                columns: new[] { "ProjectId", "Kind", "Position" });

            migrationBuilder.CreateIndex(
                name: "IX_cleared_conversations_UserId",
                table: "cleared_conversations",
                column: "UserId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "cleared_conversations");

            migrationBuilder.DropIndex(
                name: "IX_project_items_ProjectId_Kind_Position",
                table: "project_items");

            migrationBuilder.DropColumn(
                name: "Position",
                table: "project_items");
        }
    }
}
