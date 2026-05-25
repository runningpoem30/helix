package io.helix.core.command;

import io.helix.core.Key;

import java.util.Optional;

public record SetCommand(Key key, byte[] value, Optional<Long> ttlSeconds) implements Command {

    public SetCommand(Key key, byte[] value) {
        this(key, value, Optional.empty());
    }
}
