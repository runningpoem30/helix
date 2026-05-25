package io.helix.network;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Runs tasks on the calling thread (for tests and low-latency inline mode). */
public final class DirectExecutorService extends AbstractExecutorService {

    private boolean shutdown;

    public static ExecutorService create() {
        return new DirectExecutorService();
    }

    @Override
    public void execute(Runnable command) {
        if (shutdown) {
            throw new IllegalStateException("Executor shut down");
        }
        command.run();
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }
}
