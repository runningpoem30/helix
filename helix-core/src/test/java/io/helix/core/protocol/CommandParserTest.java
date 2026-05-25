package io.helix.core.protocol;

import io.helix.core.HelixConfig;
import io.helix.core.command.GetCommand;
import io.helix.core.command.PingCommand;
import io.helix.core.command.SetCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandParserTest {

    private CommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new CommandParser(HelixConfig.forTest(0));
    }

    @Test
    void parsesPing() {
        var result = parser.parse("PING");
        assertTrue(result.command().isPresent());
        assertTrue(result.command().get() instanceof PingCommand);
    }

    @Test
    void parsesSetAndGet() {
        var set = parser.parse("SET user:1 Arya");
        assertTrue(set.command().isPresent());
        SetCommand setCmd = (SetCommand) set.command().get();
        assertEquals("user:1", setCmd.key().toString());
        assertEquals("Arya", new String(setCmd.value()));

        var get = parser.parse("GET user:1");
        assertTrue(get.command().isPresent());
        assertTrue(get.command().get() instanceof GetCommand);
    }

    @Test
    void rejectsUnknownCommand() {
        var result = parser.parse("FOO bar");
        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().encode()[0] == '-');
    }

    @Test
    void rejectsExInPhase1() {
        var result = parser.parse("SET key value EX 5");
        assertTrue(result.error().isPresent());
    }
}
