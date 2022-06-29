package co.rsk.datasources;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class RWLockedCollections {
    public static <K, V> Map<K, V> rwSynchronizedMap(Map<K, V> m) {
        return new RWSynchronizedMap(m);
    }

    private static class RWSynchronizedMap<K, V> implements Map<K, V>, Serializable {
        private static final long serialVersionUID = 1978198479659022715L;
        private final Map<K, V> m;

        protected  ReadWriteLock rwLock ;

        private transient Set<K> keySet;
        private transient Set<Entry<K, V>> entrySet;
        private transient Collection<V> values;

        RWSynchronizedMap(Map<K, V> m) {
            this.m = (Map) Objects.requireNonNull(m);
            this.rwLock = new ReentrantReadWriteLock();
        }

        RWSynchronizedMap(Map<K, V> m, ReadWriteLock rwLock ) {
            this.m = m;
            this.rwLock = rwLock;
        }

        public int size() {
            rwLock.readLock().lock(); try {
                return this.m.size();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public boolean isEmpty() {
            rwLock.readLock().lock(); try {
                return this.m.isEmpty();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public boolean containsKey(Object key) {
            rwLock.readLock().lock(); try {
                return this.m.containsKey(key);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public boolean containsValue(Object value) {
            rwLock.readLock().lock(); try {
                return this.m.containsValue(value);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public V get(Object key) {
            rwLock.readLock().lock(); try {
                return this.m.get(key);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public V put(K key, V value) {
            rwLock.writeLock().lock(); try {
                return this.m.put(key, value);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public V remove(Object key) {
            rwLock.writeLock().lock(); try {
                return this.m.remove(key);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public void putAll(Map<? extends K, ? extends V> map) {
            rwLock.writeLock().lock(); try {
                this.m.putAll(map);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public void clear() {
            rwLock.writeLock().lock(); try {
                this.m.clear();
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public Set<K> keySet() {
            rwLock.readLock().lock(); try {
                if (this.keySet == null) {
                    // TO DO: Note that the set returned is using a different
                    // exclusive lock, not the RWLock
                    // To use the same lock, we have to add here:
                    // SyncrhronizedSet and
                    // SyncrhronizedCollection
                    this.keySet = Collections.synchronizedSet(this.m.keySet());
                }

                return this.keySet;
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public Set<Entry<K, V>> entrySet() {
            rwLock.readLock().lock(); try {
                if (this.entrySet == null) {
                    // TO DO: See keySet() note: same here
                    this.entrySet = Collections.synchronizedSet(this.m.entrySet());
                }

                return this.entrySet;
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public Collection<V> values() {
            rwLock.readLock().lock(); try {
                if (this.values == null) {
                    // TO DO: See keySet() note: same here
                    this.values = Collections.synchronizedCollection(this.m.values());
                }

                return this.values;
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else {
                rwLock.readLock().lock(); try {
                    return this.m.equals(o);
                } finally {
                    rwLock.readLock().unlock();
                }
            }
        }

        public int hashCode() {
            rwLock.readLock().lock(); try {
                return this.m.hashCode();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public String toString() {
            rwLock.readLock().lock(); try {
                return this.m.toString();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public V getOrDefault(Object k, V defaultValue) {
            rwLock.readLock().lock(); try {
                return this.m.getOrDefault(k, defaultValue);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public void forEach(BiConsumer<? super K, ? super V> action) {
            rwLock.readLock().lock(); try {
                this.m.forEach(action);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
            rwLock.writeLock().lock(); try {
                this.m.replaceAll(function);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public V putIfAbsent(K key, V value) {
            rwLock.writeLock().lock(); try {
                return this.m.putIfAbsent(key, value);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public boolean remove(Object key, Object value) {
            rwLock.writeLock().lock(); try {
                return this.m.remove(key, value);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public boolean replace(K key, V oldValue, V newValue) {
            rwLock.writeLock().lock(); try {
                return this.m.replace(key, oldValue, newValue);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public V replace(K key, V value) {
            rwLock.writeLock().lock(); try {
                return this.m.replace(key, value);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
            rwLock.writeLock().lock(); try {
                return this.m.computeIfAbsent(key, mappingFunction);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            rwLock.writeLock().lock(); try {
                return this.m.computeIfPresent(key, remappingFunction);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            rwLock.writeLock().lock(); try {
                return this.m.compute(key, remappingFunction);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
            rwLock.writeLock().lock(); try {
                return this.m.merge(key, value, remappingFunction);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        private void writeObject(ObjectOutputStream s) throws IOException {
            rwLock.readLock().lock(); try {
                s.defaultWriteObject();
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }
}
