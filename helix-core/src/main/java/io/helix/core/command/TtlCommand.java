package io.helix.core.command;

import io.helix.core.Key;

public record TtlCommand(Key key) implements Command {
}
