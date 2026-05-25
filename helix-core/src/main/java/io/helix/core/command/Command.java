package io.helix.core.command;

import io.helix.core.Key;

/**
 * Parsed client command (immutable).
 */
public sealed interface Command
        permits PingCommand, QuitCommand, SetCommand, GetCommand, DeleteCommand, ExistsCommand {

    record PingCommand() implements Command {}

    record QuitCommand() implements Command {}

    record SetCommand(Key key, byte[] value) implements Command {}

    record GetCommand(Key key) implements Command {}

    record DeleteCommand(Key key) implements Command {}

    record ExistsCommand(Key key) implements Command {}
}
