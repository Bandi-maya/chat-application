package com.example.chat.ui.chats.detail

/** Exact port of lib/ui/core/commands/chat_command_parser.dart. */

enum class ChatCommandType { task, poll, remind, unknown }

data class ParsedChatCommand(
    val type: ChatCommandType,
    val rawCommand: String,
    val argument: String,
) {
    val isCommand: Boolean get() = type != ChatCommandType.unknown
}

object ChatCommandParser {

    fun parse(input: String): ParsedChatCommand {
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) {
            return ParsedChatCommand(ChatCommandType.unknown, "", "")
        }
        val spaceIndex = trimmed.indexOf(' ')
        val command = if (spaceIndex == -1) {
            trimmed.substring(1)
        } else {
            trimmed.substring(1, spaceIndex)
        }
        val argument = if (spaceIndex == -1) "" else trimmed.substring(spaceIndex + 1).trim()
        return when (command.lowercase()) {
            "task" -> ParsedChatCommand(ChatCommandType.task, command, argument)
            "poll" -> ParsedChatCommand(ChatCommandType.poll, command, argument)
            "remind" -> ParsedChatCommand(ChatCommandType.remind, command, argument)
            else -> ParsedChatCommand(ChatCommandType.unknown, command, argument)
        }
    }
}
