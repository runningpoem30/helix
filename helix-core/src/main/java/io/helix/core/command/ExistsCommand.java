package io.helix.core.command;

import io.helix.core.Key;

public record ExistsCommand(Key key) implements Command {
}
