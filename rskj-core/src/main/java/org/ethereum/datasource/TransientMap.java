package org.ethereum.datasource;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TransientMap<K, V> implements Map<K, V>, Serializable {
    private static final long serialVersionUID = -1034234728574286014L;
    @SuppressWarnings({"squid:S1948"}) // ensuring serializable in constructor
    private Map<K, V> base;
    private transient Set<K> keySet;
    private transient Set<Entry<K, V>> entrySet;
    private transient Collection<V> values;
    private final transient Map<K, V> added;
    private final transient Map<K, V> changed;
    private final transient Set<K> deleted;

    TransientMap(Map<? extends K, ? extends V> base) {
        if (base == null) {
            throw new NullPointerException();
        } else {
            if (!(base instanceof Serializable)) {
                throw new IllegalArgumentException("Map to make transient should be serializable");
            }

            this.base = (Map<K, V>) base;
            added = new HashMap<>();
            changed = new HashMap<>();
            deleted = new HashSet<>();
        }
    }

    @Override
    public int size() {
        if (base == null) {
            return added.size();
        } else {
            return this.base.size() - deleted.size() + added.size();
        }
    }

    @Override
    public boolean isEmpty() {
        if (base == null) {
            return added.isEmpty();
        }
        return this.base.isEmpty() && added.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        if (added.containsKey(key)) {
            return true;
        }
        if (base == null) {
            return false;
        }
        if (changed.containsKey(key)) {
            return true;
        }
        return this.base.containsKey(key) && (!deleted.contains(key));
    }

    @Override
    public boolean containsValue(Object val) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V get(Object key) {
        if (base == null) {
            return added.get(key);
        }

        if (deleted.contains(key)) {
            return null;
        }

        V ret = added.get(key);
        if (ret != null) {
            return ret;
        }

        ret = changed.get(key);
        if (ret != null) {
            return ret;
        }

        return this.base.get(key);
    }

    /**
     * Adds the received element to this instance map.
     * Note that null key or value is not allowed.
     *
     * @param key entry key
     * @param value entry value
     *
     * @return The previous value or null if none
     */
    @Override
    public V put(K key, V value) throws IllegalArgumentException {
        if (key == null) {
            throw new IllegalArgumentException("null key");
        }

        if (value == null) {
            throw new IllegalArgumentException("null value");
        }

        if (base == null) {
            return added.put(key, value);
        }

        V retBase = base.get(key);
        if (retBase == null) {
            return added.put(key, value);
        }

        // do before following checks, call is required
        V retChanged = changed.put(key, value);

        // put should return previous value, following code deals with that

        boolean beenDeleted = deleted.remove(key);
        if (beenDeleted) {
            // null, as it was deleted before and re-added now
            return null;
        }

        boolean beenChangedAlready = retChanged != null;
        if (beenChangedAlready) {
            // result from changed.put(), as it had been changed before already
            return retChanged;
        }

        // base one, as this is the first time it is changed
        return retBase;
    }

    @Override
    public V remove(Object key) {
        if (base == null) {
            return added.remove(key);
        }

        if (deleted.contains(key)) {
            // double delete
            return null;
        }

        if (base.containsKey(key)) {
            deleted.add((K) key);
        }

        V ret = changed.remove(key);
        if (ret != null) {
            return ret;
        }

        ret = added.remove(key);
        if (ret != null) {
            return ret;
        }

        return base.get(key);
    }


    /**
     * Adds all received elements to this instance map.
     * Note that null key or value is not allowed. The method will fail as soon as one of such conditions is met,
     * previously added entries will remain stored
     *
     * @param am mappings to be stored in this map
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> am) throws IllegalArgumentException {
        // TODO https://github.com/rsksmart/rskj/pull/1863/files#r975431915
        // TODO https://github.com/rsksmart/rskj/pull/1863/files#r975434204
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        base = null; // release base reference, so it continues its own life out of this wrapper

        deleted.clear();
        added.clear();
        changed.clear();
    }

    @Override
    public Set<K> keySet() {
        //TODO https://github.com/rsksmart/rskj/pull/1863/files#r975456885
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        //TODO https://github.com/rsksmart/rskj/pull/1863/files#r975456885
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<V> values() {
        //TODO https://github.com/rsksmart/rskj/pull/1863/files#r975456885
        throw new UnsupportedOperationException();
    }


    @Override
    public V getOrDefault(Object k, V defaultValue) {
        V ret = get(k);
        // TODO https://github.com/rsksmart/rskj/pull/1863/files#r975728331
        if (ret == null) {
            ret = defaultValue;
        }
        return ret;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V replace(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }


    public static <K, V> Map<K, V> transientMap(Map<? extends K, ? extends V> m) {
        return new TransientMap(m);
    }
}
