package io.helix.core.command;

/**
 * Parsed client command (immutable).
 */
public sealed interface Command
        permits PingCommand, QuitCommand, SetCommand, GetCommand, DeleteCommand, ExistsCommand {
}
