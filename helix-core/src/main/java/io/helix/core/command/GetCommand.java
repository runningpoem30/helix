package io.helix.core.command;

import io.helix.core.Key;

public record GetCommand(Key key) implements Command {
}
