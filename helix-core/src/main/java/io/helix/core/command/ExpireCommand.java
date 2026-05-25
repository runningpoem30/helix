package io.helix.core.command;

import io.helix.core.Key;

public record ExpireCommand(Key key, long ttlSeconds) implements Command {
}
