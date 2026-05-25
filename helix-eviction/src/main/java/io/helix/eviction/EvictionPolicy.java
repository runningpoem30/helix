package io.helix.eviction;

import io.helix.core.Key;

import java.util.List;

public interface EvictionPolicy {

    String name();

    void onInsert(Key key);

    void onAccess(Key key);

    void onRemove(Key key);

    List<Key> selectVictims(int count);

    int size();
}
