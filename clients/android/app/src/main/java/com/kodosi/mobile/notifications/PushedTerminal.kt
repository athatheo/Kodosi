package com.kodosi.mobile.notifications

import com.kodosi.mobile.terminals.AgentKind
import com.kodosi.mobile.terminals.ProgramStatus
import kotlin.uuid.Uuid

data class PushedTerminal(val terminal: String, val agent: AgentKind, val status: ProgramStatus, val sound: Boolean) {
    companion object {
        fun list(text: String): List<PushedTerminal> = text.split(' ').mapNotNull(::entry).distinctBy { it.terminal }

        private fun entry(text: String): PushedTerminal? {
            val parts = text.split(':')
            if (parts.size != 5) return null
            val (terminal, agent, state, kind, sound) = parts
            if (Uuid.parseHexDashOrNull(terminal) == null) return null
            val status = ProgramStatus(
                when (state) {
                    "blocked" -> ProgramStatus.State.BLOCKED
                    "done" -> ProgramStatus.State.DONE
                    "error" -> ProgramStatus.State.ERROR
                    else -> return null
                },
                when (kind) {
                    "permission" -> ProgramStatus.Kind.PERMISSION
                    "question" -> ProgramStatus.Kind.QUESTION
                    "auth" -> ProgramStatus.Kind.AUTH
                    else -> null
                }
            )
            return PushedTerminal(terminal, AgentKind.of(agent), status, sound == "1")
        }
    }
}
