package org.ethereum.datasource;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TransientMap<K, V> implements Map<K, V>, Serializable {
    private static final long serialVersionUID = -1034234728574286014L;
    private Map<K, V> m;
    private transient Set<K> keySet;
    private transient Set<Entry<K, V>> entrySet;
    private transient Collection<V> values;
    private transient Map<K, V> added;
    private transient Map<K, V> changed;
    private transient Set<K> deleted;

    TransientMap(Map<? extends K, ? extends V> m) {
        if (m == null) {
            throw new NullPointerException();
        } else {
            this.m = (Map<K, V>) m;
            added = new HashMap<>();
            changed = new HashMap<>();
            deleted = new HashSet<>();

        }
    }

    public int size() {

        if (m == null) {
            return added.size();
        } else {
            // todo: mix
            return this.m.size() - deleted.size() + added.size();
        }
    }

    public boolean isEmpty() {
        if (m == null) {
            return added.isEmpty();
        }
        return this.m.isEmpty() && added.isEmpty();
    }

    public boolean containsKey(Object key) {
        if (added.containsKey(key)) {
            return true;
        }
        if (m == null) {
            return false;
        }
        if (changed.containsKey(key)) {
            return true;
        }
        return this.m.containsKey(key) && (!deleted.contains(key));

    }

    public boolean containsValue(Object val) {
        throw new UnsupportedOperationException();
    }

    public V get(Object key) {
        if (m == null) {
            return added.get(key);
        }

        if (deleted.contains(key)) {
            return null;
        }
        V ret = added.get(key);
        if (ret == null) {
            ret = changed.get(key);
        }
        if (ret == null) {
            ret = this.m.get(key);
        }
        return ret;
    }

    public V put(K key, V value) {
        if (m == null) return added.put(key, value);

        V ret = m.get(key);
        if (ret != null) {
            if (deleted.contains(key)) {
                deleted.remove(key);
                changed.put(key, value);
                return null;
            }
            return changed.put(key, value);
        } else return added.put(key, value);
    }

    public V remove(Object key) {
        V ret;
        if (m != null) {
            if (!deleted.contains(key)) {
                deleted.add((K) key);
                ret = changed.remove(key);
                if (ret == null) ret = added.remove(key);
                if (ret == null) ret = m.get(key);
            } else
                // double delete
                ret = null;
        } else {
            ret = added.remove(key);
        }
        return ret;
    }

    public void putAll(Map<? extends K, ? extends V> am) {
        if (m == null) {
            return;
        }
        for (Map.Entry<? extends K, ? extends V> entry : am.entrySet()) {
            deleted.remove(entry.getKey());
            if (m.containsKey(entry.getKey())) {
                changed.put(entry.getKey(), entry.getValue());
            } else {
                added.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public void clear() {
        m = null;
        deleted.clear();
        added.clear();
        changed.clear();
    }

    public Set<K> keySet() {
        if (this.keySet == null) {
            //TODO:  this.keySet = Collections.unmodifiableSet(this.m.keySet());
        }

        return this.keySet;
    }

    public Set<Entry<K, V>> entrySet() {
        if (this.entrySet == null) {
            //TODO: this.entrySet = unmodifiableEntrySet(this.m.entrySet());
        }

        return this.entrySet;
    }

    public Collection<V> values() {
        if (this.values == null) {
            //TODO:  this.values = Collections.unmodifiableCollection(this.m.values());
        }

        return this.values;
    }


    public V getOrDefault(Object k, V defaultValue) {
        V ret = get(k);
        if (k == null) ret = defaultValue;
        return ret;
    }

    public void forEach(BiConsumer<? super K, ? super V> action) {
        throw new UnsupportedOperationException();
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException();
    }

    public V putIfAbsent(K key, V value) {
        if (containsKey(key)) {
            return get(key);
        } else {
            return put(key, value);
        }
    }

    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    public boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException();
    }

    public V replace(K key, V value) {
        throw new UnsupportedOperationException();
    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }


    public static <K, V> Map<K, V> transientMap(Map<? extends K, ? extends V> m) {
        return new TransientMap(m);
    }
}
