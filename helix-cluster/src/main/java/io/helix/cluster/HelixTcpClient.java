package io.helix.cluster;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal TCP client for Helix wire protocol (used by cluster router and examples).
 */
public final class HelixTcpClient implements AutoCloseable {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private OutputStream out;

    public HelixTcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws Exception {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = socket.getOutputStream();
        if (reader.ready()) {
            String banner = reader.readLine();
            if (banner != null && banner.startsWith("#")) {
                // consume helix banner
            }
        }
    }

    public List<String> send(String commandLine) throws Exception {
        out.write((commandLine + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        return readResponse();
    }

    private List<String> readResponse() throws Exception {
        List<String> lines = new ArrayList<>();
        String first = reader.readLine();
        if (first == null) {
            return lines;
        }
        lines.add(first);
        if (first.startsWith("$") && !first.equals("$-1")) {
            int len = Integer.parseInt(first.substring(1));
            char[] buf = new char[len];
            int read = reader.read(buf, 0, len);
            if (read > 0) {
                lines.add(new String(buf, 0, read));
            }
            String crlf = reader.readLine();
            if (crlf != null) {
                lines.add(crlf);
            }
        }
        return lines;
    }

    @Override
    public void close() throws Exception {
        if (socket != null) {
            socket.close();
        }
    }
}
