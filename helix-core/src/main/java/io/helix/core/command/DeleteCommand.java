package io.helix.core.command;

import io.helix.core.Key;

public record DeleteCommand(Key key) implements Command {
}
