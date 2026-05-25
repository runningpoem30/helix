package io.helix.core.protocol;

import io.helix.core.HelixConfig;
import io.helix.core.Key;
import io.helix.core.command.Command;
import io.helix.core.command.DeleteCommand;
import io.helix.core.command.ExistsCommand;
import io.helix.core.command.GetCommand;
import io.helix.core.command.PingCommand;
import io.helix.core.command.QuitCommand;
import io.helix.core.command.SetCommand;
import java.util.function.Function;
import io.helix.core.response.ErrorResponse;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Parses a single line into a {@link Command} or error message.
 */
public final class CommandParser {

    private final HelixConfig config;

    public CommandParser(HelixConfig config) {
        this.config = config;
    }

    public ParseResult parse(String line) {
        if (line == null || line.isBlank()) {
            return ParseResult.empty();
        }

        String trimmed = line.trim();
        int space = trimmed.indexOf(' ');
        String cmdName = (space < 0 ? trimmed : trimmed.substring(0, space)).toUpperCase();
        String remainder = space < 0 ? "" : trimmed.substring(space + 1).trim();

        return switch (cmdName) {
            case "PING" -> remainder.isEmpty()
                    ? ParseResult.ok(new PingCommand())
                    : ParseResult.error(ErrorResponse.syntax());
            case "QUIT" -> remainder.isEmpty()
                    ? ParseResult.ok(new QuitCommand())
                    : ParseResult.error(ErrorResponse.syntax());
            case "GET" -> parseUnaryKey(remainder, GetCommand::new);
            case "DELETE", "DEL" -> parseUnaryKey(remainder, DeleteCommand::new);
            case "EXISTS" -> parseUnaryKey(remainder, ExistsCommand::new);
            case "SET" -> parseSet(remainder);
            default -> ParseResult.error(ErrorResponse.unknownCommand(cmdName));
        };
    }

    private ParseResult parseUnaryKey(String remainder, Function<Key, Command> factory) {
        if (remainder.isEmpty()) {
            return ParseResult.error(ErrorResponse.syntax());
        }
        Optional<ErrorResponse> keyError = validateKeyUtf8(remainder);
        if (keyError.isPresent()) {
            return ParseResult.error(keyError.get());
        }
        return ParseResult.ok(factory.apply(Key.ofUtf8(remainder)));
    }

    private ParseResult parseSet(String remainder) {
        if (remainder.isEmpty()) {
            return ParseResult.error(ErrorResponse.syntax());
        }
        int firstSpace = remainder.indexOf(' ');
        if (firstSpace < 0) {
            return ParseResult.error(ErrorResponse.syntax());
        }
        String keyStr = remainder.substring(0, firstSpace);
        String valueStr = remainder.substring(firstSpace + 1);
        if (valueStr.isEmpty()) {
            return ParseResult.error(ErrorResponse.syntax());
        }
        // Phase 2: EX ttl — reject in Phase 1 with clear error if present
        if (valueStr.contains(" EX ") || valueStr.endsWith(" EX")) {
            return ParseResult.error(new ErrorResponse("EX option not supported yet (Phase 2)"));
        }

        Optional<ErrorResponse> keyError = validateKeyUtf8(keyStr);
        if (keyError.isPresent()) {
            return ParseResult.error(keyError.get());
        }
        byte[] valueBytes = valueStr.getBytes(StandardCharsets.UTF_8);
        if (valueBytes.length > config.maxValueBytes()) {
            return ParseResult.error(ErrorResponse.valueTooLarge());
        }
        return ParseResult.ok(new SetCommand(Key.ofUtf8(keyStr), valueBytes));
    }

    private Optional<ErrorResponse> validateKeyUtf8(String keyStr) {
        byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length > config.maxKeyBytes()) {
            return Optional.of(ErrorResponse.keyTooLong());
        }
        return Optional.empty();
    }

    public record ParseResult(Optional<Command> command, Optional<ErrorResponse> error) {

        public static ParseResult ok(Command command) {
            return new ParseResult(Optional.of(command), Optional.empty());
        }

        public static ParseResult error(ErrorResponse error) {
            return new ParseResult(Optional.empty(), Optional.of(error));
        }

        public static ParseResult empty() {
            return new ParseResult(Optional.empty(), Optional.empty());
        }

        public boolean isEmpty() {
            return command.isEmpty() && error.isEmpty();
        }
    }
}
