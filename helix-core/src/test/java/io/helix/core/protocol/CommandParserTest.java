package io.helix.core.protocol;

import io.helix.core.HelixConfig;
import io.helix.core.command.ExpireCommand;
import io.helix.core.command.GetCommand;
import io.helix.core.command.PingCommand;
import io.helix.core.command.SetCommand;
import io.helix.core.command.TtlCommand;
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
        assertTrue(setCmd.ttlSeconds().isEmpty());

        var get = parser.parse("GET user:1");
        assertTrue(get.command().isPresent());
        assertTrue(get.command().get() instanceof GetCommand);
    }

    @Test
    void parsesSetWithEx() {
        var set = parser.parse("SET token abc EX 5");
        SetCommand cmd = (SetCommand) set.command().orElseThrow();
        assertEquals(5L, cmd.ttlSeconds().orElseThrow());
    }

    @Test
    void parsesExpireAndTtl() {
        assertTrue(parser.parse("EXPIRE user:1 60").command().get() instanceof ExpireCommand);
        assertTrue(parser.parse("TTL user:1").command().get() instanceof TtlCommand);
    }

    @Test
    void rejectsUnknownCommand() {
        var result = parser.parse("FOO bar");
        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().encode()[0] == '-');
    }
}
