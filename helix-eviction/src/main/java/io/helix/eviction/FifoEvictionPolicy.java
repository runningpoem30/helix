package io.helix.eviction;

import io.helix.core.Key;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FifoEvictionPolicy implements EvictionPolicy {

    private final Deque<Key> queue = new ArrayDeque<>();
    private final Set<Key> index = new HashSet<>();

    @Override
    public String name() {
        return "fifo";
    }

    @Override
    public void onInsert(Key key) {
        if (index.add(key)) {
            queue.addLast(key);
        }
    }

    @Override
    public void onAccess(Key key) {
        // FIFO ignores access frequency
    }

    @Override
    public void onRemove(Key key) {
        index.remove(key);
        queue.remove(key);
    }

    @Override
    public List<Key> selectVictims(int count) {
        List<Key> victims = new ArrayList<>(Math.min(count, queue.size()));
        while (!queue.isEmpty() && victims.size() < count) {
            Key key = queue.pollFirst();
            if (index.remove(key)) {
                victims.add(key);
            }
        }
        return victims;
    }

    @Override
    public int size() {
        return index.size();
    }
}
