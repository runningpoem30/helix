package io.helix.eviction;

import io.helix.core.Key;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact LRU via access-order {@link LinkedHashMap} (O(1) touch and tail eviction).
 */
public final class LruEvictionPolicy implements EvictionPolicy {

    private final LinkedHashMap<Key, Boolean> order = new LinkedHashMap<>(16, 0.75f, true);

    @Override
    public String name() {
        return "lru";
    }

    @Override
    public void onInsert(Key key) {
        order.put(key, Boolean.TRUE);
    }

    @Override
    public void onAccess(Key key) {
        if (order.containsKey(key)) {
            order.get(key);
        }
    }

    @Override
    public void onRemove(Key key) {
        order.remove(key);
    }

    @Override
    public List<Key> selectVictims(int count) {
        List<Key> victims = new ArrayList<>(Math.min(count, order.size()));
        var it = order.keySet().iterator();
        while (it.hasNext() && victims.size() < count) {
            victims.add(it.next());
            it.remove();
        }
        return victims;
    }

    @Override
    public int size() {
        return order.size();
    }
}
