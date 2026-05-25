package io.helix.server;

import io.helix.core.HelixConfig;
import io.helix.metrics.CacheMetrics;
import io.helix.network.NettyHelixServer;
import io.helix.storage.CacheService;
import io.helix.storage.SegmentedCacheEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelixServerIntegrationTest {

    private NettyHelixServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        HelixConfig config = HelixConfig.forTest(0);
        CacheService service = new CacheService(new SegmentedCacheEngine(config), new CacheMetrics());
        server = new NettyHelixServer(config, service);
        server.start();
        port = server.port();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void telnetStyleSetAndGet() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            drainBanner(in);

            out.write("SET user:1 Arya\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            String setLine = readLineSkippingComments(in);
            assertTrue(setLine != null && setLine.contains("OK"), "set response: " + setLine);

            out.write("GET user:1\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            String lenLine = in.readLine();
            assertTrue(lenLine != null && lenLine.startsWith("$"), "bulk header: " + lenLine);
            String valueLine = in.readLine();
            assertTrue(valueLine != null && valueLine.equals("Arya"), "value: " + valueLine);

            out.write("QUIT\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static String readLineSkippingComments(BufferedReader in) throws Exception {
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.startsWith("#")) {
                return line;
            }
        }
        return null;
    }

    private static void drainBanner(BufferedReader in) throws Exception {
        in.mark(256);
        if (in.ready()) {
            String line = in.readLine();
            if (line != null && line.startsWith("#")) {
                return;
            }
        }
        in.reset();
    }
}
