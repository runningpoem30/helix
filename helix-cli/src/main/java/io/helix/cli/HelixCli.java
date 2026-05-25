package io.helix.cli;

import io.helix.cluster.HelixTcpClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Interactive TCP client for Helix demos. */
public final class HelixCli {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        try (HelixTcpClient client = new HelixTcpClient(host, port);
                BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            client.connect();
            System.out.println("Connected to Helix at " + host + ":" + port + " (type QUIT to exit)");
            while (true) {
                System.out.print("helix> ");
                String line = stdin.readLine();
                if (line == null || line.equalsIgnoreCase("QUIT")) {
                    client.send("QUIT");
                    break;
                }
                List<String> response = client.send(line);
                response.forEach(System.out::println);
            }
        }
    }
}
