package com.kodosi.mobile.projects

import com.kodosi.mobile.runtime.CommandSink
import com.kodosi.mobile.runtime.RuntimeEvent
import com.kodosi.mobile.runtime.UuidV7
import com.kodosi.mobile.runtime.clippedUtf8
import com.kodosi.mobile.runtime.fields
import com.kodosi.mobile.runtime.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

class ProjectStore(private val commands: CommandSink) {
    val state: StateFlow<ProjectsState>
        field = MutableStateFlow(ProjectsState())
    val drafts = mutableMapOf<String, String>()
    private val seen = mutableMapOf<String, Long>()
    private var userId: String? = null
    private var generation = 0
    var reading: String? = null

    fun reset(userId: String?) {
        generation++
        this.userId = userId
        state.value = ProjectsState()
        drafts.clear()
        seen.clear()
        reading = null
    }

    fun project(id: String): Project? =
        state.value.details[id]?.project ?: state.value.projects.firstOrNull { it.id == id }

    fun receive(event: RuntimeEvent) {
        when (event.type) {
            "projects.snapshot" -> {
                val projects = event.value<List<Project>>("projects")
                val truncated = event.bool("projectsTruncated") == true
                val present = projects.map { it.id }.toSet()
                state.update {
                    it.copy(
                        projects = projects,
                        invitations = event.value("invitations"),
                        loaded = true,
                        truncated = truncated || event.bool("invitationsTruncated") == true,
                        snapshots = if (truncated) it.snapshots else it.snapshots.filterKeys(present::contains),
                        details = if (truncated) it.details else it.details.filterKeys(present::contains),
                        unread = it.unread.intersect(present)
                    )
                }
            }

            "project.snapshot", "project.result" -> if (event["content"] is JsonObject) apply(event.value("content"))
        }
    }

    private fun apply(next: ProjectSnapshot) {
        state.update { current ->
            val previous = current.snapshots[next.projectId]
            val fresh = previous != null && next.messages.any {
                it.position > (previous.messages.maxOfOrNull { message -> message.position } ?: 0) &&
                    !it.deleted && !it.isOwn(userId)
            }
            val cleared = previous != null && next.clearedPosition > previous.clearedPosition
            current.copy(
                snapshots = current.snapshots + (next.projectId to (previous?.let(next::merging) ?: next)),
                unread = when {
                    fresh && reading != next.projectId -> current.unread + next.projectId
                    cleared -> current.unread - next.projectId
                    else -> current.unread
                }
            )
        }
    }

    fun refresh() = commands.perform("project.list")

    suspend fun open(id: String) {
        val epoch = generation
        val reply = commands.request("project.open", fields("projectId" to id))
        if (generation != epoch) return
        state.update {
            it.copy(
                details =
                    it.details + (id to ProjectDetail(reply.value("project"), reply.value("members")))
            )
        }
    }

    suspend fun read(id: String, before: Long? = null) {
        act(id, if (before == null) fields("type" to "read") else fields("type" to "read", "before" to before))
    }

    fun markRead(id: String) {
        val position = state.value.snapshots[id]?.messages?.maxOfOrNull { it.position } ?: return
        if (position > (seen[id] ?: 0)) {
            seen[id] = position
            commands.perform("project.seen", fields("projectId" to id, "position" to position))
        }
        state.update {
            it.copy(
                unread = it.unread - id,
                projects = it.projects.map { project ->
                    if (project.id ==
                        id
                    ) {
                        project.copy(unread = false)
                    } else {
                        project
                    }
                }
            )
        }
    }

    suspend fun act(id: String, action: JsonObject): RuntimeEvent =
        commands.request("project.act", fields("projectId" to id, "action" to action))

    suspend fun delete(id: String, message: ProjectMessage) {
        act(id, fields("type" to "deleteMessage", "messageId" to message.id))
    }

    suspend fun clearChat(id: String) {
        act(id, fields("type" to "clearConversation"))
    }

    suspend fun post(id: String, text: String) {
        val message = text.trim()
        if (message.isEmpty()) return
        act(id, fields("type" to "post", "text" to message))
        if (drafts[id] == text) drafts.remove(id)
    }

    suspend fun create(name: String): String {
        val id = UuidV7.generate()
        commands.request("project.create", fields("requestId" to id, "name" to name.trim()))
        return id
    }

    suspend fun respond(invitation: ProjectInvitation, accept: Boolean) {
        commands.request(
            if (accept) "project.invitation.accept" else "project.invitation.reject",
            fields("invitationId" to invitation.id)
        )
    }

    suspend fun createTask(
        id: String,
        title: String,
        description: String,
        repositories: Set<String>,
        issueRepository: String? = null
    ) {
        val action = fields(
            "type" to "createTask",
            "title" to title.trim().clippedUtf8(256),
            "description" to description.trim().clippedUtf8(16384),
            "repositoryIds" to repositories.sorted()
        )
        act(
            id,
            if (issueRepository ==
                null
            ) {
                action
            } else {
                JsonObject(action + fields("issueRepositoryId" to issueRepository))
            }
        )
    }

    suspend fun updateTask(id: String, task: ProjectTask, change: String, note: String? = null) {
        val action = fields("type" to "updateTask", "taskId" to task.id, "change" to change)
        act(id, if (note.isNullOrBlank()) action else JsonObject(action + fields("note" to note.trim())))
    }

    suspend fun addRepository(id: String, address: String) {
        act(id, fields("type" to "addRepository", "url" to address.trim()))
    }

    suspend fun issues(id: String, repository: String): List<ProjectIssue> =
        act(id, fields("type" to "issues", "repositoryId" to repository)).value("issues")

    suspend fun importIssue(id: String, issue: ProjectIssue) {
        act(id, fields("type" to "importIssue", "repositoryId" to issue.repositoryId, "number" to issue.number))
    }
}
