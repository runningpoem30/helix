package io.helix.examples;

import io.helix.cluster.HelixTcpClient;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demonstrates cache-aside: miss → simulated DB → populate cache → hit.
 */
public final class CacheAsideDemo {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        try (HelixTcpClient client = new HelixTcpClient(host, port)) {
            client.connect();

            long hits = 0;
            long misses = 0;

            for (int i = 0; i < 6; i++) {
                String userId = "42";
                String key = "user:" + userId;

                List<String> cached = client.send("GET " + key);
                if (isHit(cached)) {
                    hits++;
                    System.out.println("HIT  " + key + " → " + cached);
                } else {
                    misses++;
                    String fromDb = fetchFromDatabase(userId);
                    client.send("SET " + key + " " + fromDb + " EX 300");
                    System.out.println("MISS " + key + " → DB → cached " + fromDb);
                }
            }

            double ratio = hits + misses == 0 ? 0 : (double) hits / (hits + misses);
            System.out.printf("%nHit ratio: %.1f%% (%d hits, %d misses)%n", ratio * 100, hits, misses);
        }
    }

    private static boolean isHit(List<String> response) {
        return !response.isEmpty() && !response.getFirst().equals("$-1");
    }

    private static String fetchFromDatabase(String userId) throws InterruptedException {
        Thread.sleep(20 + ThreadLocalRandom.current().nextInt(30));
        return "User-" + userId;
    }
}
