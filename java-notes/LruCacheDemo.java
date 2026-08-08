import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An LRU cache holds at most N entries and, when full, drops the one that was used least recently.
 * Both get and put are O(1): a hash map finds the entry, a doubly linked list tracks recency order.
 */
class LruCacheDemo {

    static class LruCache<K, V> {

        private static final class Node<K, V> {
            private final K key;
            private V value;
            private Node<K, V> prev;
            private Node<K, V> next;

            Node(K key, V value) {
                this.key = key;
                this.value = value;
            }
        }

        private final int capacity;
        private final Map<K, Node<K, V>> index = new HashMap<>();
        private final Node<K, V> head = new Node<>(null, null); // most recently used side
        private final Node<K, V> tail = new Node<>(null, null); // least recently used side

        LruCache(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
            head.next = tail;
            tail.prev = head;
        }

        V get(K key) {
            Node<K, V> node = index.get(key);
            if (node == null) {
                return null;
            }
            moveToFront(node);      // reading counts as use, so it becomes the newest
            return node.value;
        }

        void put(K key, V value) {
            Node<K, V> existing = index.get(key);
            if (existing != null) {
                existing.value = value;
                moveToFront(existing);
                return;
            }
            if (index.size() == capacity) {
                Node<K, V> leastRecentlyUsed = tail.prev;
                unlink(leastRecentlyUsed);
                index.remove(leastRecentlyUsed.key);
            }
            Node<K, V> node = new Node<>(key, value);
            index.put(key, node);
            linkFront(node);
        }

        /** Keys from most to least recently used. */
        List<K> keys() {
            List<K> keys = new ArrayList<>(index.size());
            for (Node<K, V> node = head.next; node != tail; node = node.next) {
                keys.add(node.key);
            }
            return keys;
        }

        private void moveToFront(Node<K, V> node) {
            unlink(node);
            linkFront(node);
        }

        private void unlink(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        private void linkFront(Node<K, V> node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
        }
    }

    /** The same policy for free: LinkedHashMap in access order, with a size cap. */
    static <K, V> Map<K, V> linkedHashMapCache(int capacity) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public static void main(String[] args) {
        LruCache<String, Integer> cache = new LruCache<>(3);

        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        System.out.println("after put a, b, c : " + cache.keys());

        cache.get("a");
        System.out.println("after get a       : " + cache.keys() + "   <- a is newest again");

        cache.put("d", 4);
        System.out.println("after put d       : " + cache.keys() + "   <- b evicted, it was oldest");

        System.out.println("get b             : " + cache.get("b") + "   <- gone");
        System.out.println("get c             : " + cache.get("c"));
        System.out.println("final order       : " + cache.keys());

        Map<String, Integer> builtIn = linkedHashMapCache(3);
        builtIn.put("a", 1);
        builtIn.put("b", 2);
        builtIn.put("c", 3);
        builtIn.get("a");
        builtIn.put("d", 4);
        System.out.println("LinkedHashMap     : " + builtIn.keySet() + "   <- oldest first, same eviction");
    }
}
