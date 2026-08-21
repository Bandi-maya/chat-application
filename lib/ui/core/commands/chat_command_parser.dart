enum ChatCommandType { task, poll, remind, unknown }

class ParsedChatCommand {
  final ChatCommandType type;
  final String rawCommand;
  final String argument;

  const ParsedChatCommand({
    required this.type,
    required this.rawCommand,
    required this.argument,
  });

  bool get isCommand => type != ChatCommandType.unknown;
}

class ChatCommandParser {
  static ParsedChatCommand parse(String input) {
    final trimmed = input.trim();
    if (!trimmed.startsWith('/')) {
      return const ParsedChatCommand(
        type: ChatCommandType.unknown,
        rawCommand: '',
        argument: '',
      );
    }

    final spaceIndex = trimmed.indexOf(' ');
    final command = spaceIndex == -1
        ? trimmed.substring(1)
        : trimmed.substring(1, spaceIndex);
    final argument = spaceIndex == -1
        ? ''
        : trimmed.substring(spaceIndex + 1).trim();

    switch (command.toLowerCase()) {
      case 'task':
        return ParsedChatCommand(
          type: ChatCommandType.task,
          rawCommand: command,
          argument: argument,
        );
      case 'poll':
        return ParsedChatCommand(
          type: ChatCommandType.poll,
          rawCommand: command,
          argument: argument,
        );
      case 'remind':
        return ParsedChatCommand(
          type: ChatCommandType.remind,
          rawCommand: command,
          argument: argument,
        );
      default:
        return ParsedChatCommand(
          type: ChatCommandType.unknown,
          rawCommand: command,
          argument: argument,
        );
    }
  }
}
