package net.inetalliance.potion.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class NullCache<K, V>
    implements Map<K, V> {

  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public boolean containsKey(final Object key) {
    return false;
  }

  @Override
  public boolean containsValue(final Object value) {
    return false;
  }

  @Override
  public V get(final Object key) {
    return null;
  }

  @Override
  public V put(final K key, final V value) {
    return null;
  }

  @Override
  public V remove(final Object key) {
    return null;
  }

  @Override
  public void putAll(final Map<? extends K, ? extends V> m) {

  }

  @Override
  public void clear() {

  }

  @Override
  public Set<K> keySet() {
    return Collections.emptySet();
  }

  @Override
  public Collection<V> values() {
    return Collections.emptySet();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return Collections.emptySet();
  }
}
