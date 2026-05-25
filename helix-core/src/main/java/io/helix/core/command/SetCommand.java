package io.helix.core.command;

import io.helix.core.Key;

public record SetCommand(Key key, byte[] value) implements Command {
}
