package io.helix.benchmark;

import io.helix.cluster.HelixTcpClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-client TCP load generator for Helix.
 *
 * <pre>
 * java -cp helix-benchmark.jar io.helix.benchmark.LoadDriver localhost 6379 20 30 90
 * </pre>
 */
public final class LoadDriver {

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: LoadDriver <host> <port> <clients> <seconds> <readPercent>");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int clients = Integer.parseInt(args[2]);
        int seconds = Integer.parseInt(args[3]);
        int readPercent = Integer.parseInt(args[4]);

        ExecutorService pool = Executors.newFixedThreadPool(clients);
        AtomicLong ops = new AtomicLong();
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < clients; i++) {
            int clientId = i;
            pool.submit(() -> {
                try (HelixTcpClient client = new HelixTcpClient(host, port)) {
                    client.connect();
                    start.await();
                    while (System.nanoTime() < end) {
                        String key = "key:" + (clientId % 1000);
                        if (Math.random() * 100 < readPercent) {
                            client.send("GET " + key);
                        } else {
                            client.send("SET " + key + " payload");
                        }
                        ops.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Client error: " + e.getMessage());
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.close();
        double elapsed = seconds;
        long total = ops.get();
        System.out.printf("Throughput: %.0f ops/sec (%d ops, %d clients, %d%% reads)%n",
                total / elapsed, total, clients, readPercent);
    }
}
