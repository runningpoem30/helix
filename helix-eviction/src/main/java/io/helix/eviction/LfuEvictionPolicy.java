package io.helix.eviction;

import io.helix.core.Key;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LFU with frequency buckets (O(1) access and eviction from minimum frequency).
 */
public final class LfuEvictionPolicy implements EvictionPolicy {

    private final Map<Key, Node> nodes = new HashMap<>();
    private final Map<Integer, LinkedHashSet<Key>> freqBuckets = new HashMap<>();
    private int minFreq = 0;

    @Override
    public String name() {
        return "lfu";
    }

    @Override
    public void onInsert(Key key) {
        if (nodes.containsKey(key)) {
            onAccess(key);
            return;
        }
        Node node = new Node(key, 1);
        nodes.put(key, node);
        freqBuckets.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }

    @Override
    public void onAccess(Key key) {
        Node node = nodes.get(key);
        if (node == null) {
            return;
        }
        Set<Key> oldBucket = freqBuckets.get(node.freq);
        if (oldBucket != null) {
            oldBucket.remove(key);
        }
        node.freq++;
        freqBuckets.computeIfAbsent(node.freq, k -> new LinkedHashSet<>()).add(key);
        if (minFreq < node.freq) {
            minFreq = node.freq;
        }
    }

    @Override
    public void onRemove(Key key) {
        Node node = nodes.remove(key);
        if (node == null) {
            return;
        }
        Set<Key> bucket = freqBuckets.get(node.freq);
        if (bucket != null) {
            bucket.remove(key);
            if (bucket.isEmpty()) {
                freqBuckets.remove(node.freq);
                if (minFreq == node.freq) {
                    minFreq = freqBuckets.keySet().stream().min(Integer::compare).orElse(0);
                }
            }
        }
    }

    @Override
    public List<Key> selectVictims(int count) {
        List<Key> victims = new ArrayList<>(count);
        while (victims.size() < count && !nodes.isEmpty()) {
            LinkedHashSet<Key> bucket = freqBuckets.get(minFreq);
            if (bucket == null || bucket.isEmpty()) {
                minFreq = freqBuckets.keySet().stream().min(Integer::compare).orElse(1);
                if (freqBuckets.isEmpty()) {
                    break;
                }
                continue;
            }
            Key key = bucket.iterator().next();
            victims.add(key);
        }
        return victims;
    }

    @Override
    public int size() {
        return nodes.size();
    }

    private static final class Node {
        final Key key;
        int freq;

        Node(Key key, int freq) {
            this.key = key;
            this.freq = freq;
        }
    }
}
