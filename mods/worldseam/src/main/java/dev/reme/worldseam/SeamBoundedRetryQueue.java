package dev.reme.worldseam;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Server-thread-only, bounded native-terrain upload scheduler.
 *
 * A section can temporarily be absent from the authoritative target chunk
 * cache; the polled entry is then deferred without forcing a load. A second
 * dirty section must not disappear just because all upload slots are full:
 * overflow remembers its identity and admits it when an upload completes.
 * Neither queue owns or creates any Rapier body or native collider.
 */
final class SeamBoundedRetryQueue<T> {
    private final int capacity;
    private final ArrayDeque<T> pending = new ArrayDeque<>();
    private final Set<T> queued = new HashSet<>();
    private final Set<T> overflow = new LinkedHashSet<>();
    // Native collider callbacks can mark the section dirty while its JNI
    // upload is still on the stack. Replay after complete(), never lose it.
    private final Set<T> replayAfterUpload = new HashSet<>();

    SeamBoundedRetryQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    void enqueue(T item) {
        if (item == null) throw new IllegalArgumentException("item");
        if (queued.contains(item) || overflow.contains(item)) return;
        if (pending.size() == capacity) {
            overflow.add(item);
            return;
        }
        queued.add(item);
        pending.addLast(item);
    }

    /**
     * A real block edit or native witness must not wait behind the entire
     * initial 5x5x4 foreign terrain window. Promote that actual section to
     * the next available JNI upload slot; displace only queued work, never
     * discard it. This does NOT alter the authoritative Rapier body.
     *
     * If Sable reenters while this section is already polled for JNI, keep
     * a replay request so the newer real block state is uploaded afterwards.
     */
    void promote(T item) {
        if (item == null) throw new IllegalArgumentException("item");
        if (queued.contains(item)) {
            if (pending.remove(item)) {
                pending.addFirst(item);
            } else {
                replayAfterUpload.add(item);
            }
            return;
        }
        overflow.remove(item);
        if (pending.size() == capacity) {
            T displaced = pending.removeLast();
            if (!queued.remove(displaced))
                throw new IllegalStateException("Displaced section not queued");
            overflow.add(displaced);
        }
        if (!queued.add(item))
            throw new IllegalStateException("Duplicate promoted section");
        pending.addFirst(item);
    }

    T poll() {
        return pending.pollFirst();
    }

    /**
     * A missing authoritative chunk must not monopolize every native upload
     * slot while a resident, edited chunk waits in overflow. Rotate the oldest
     * overflow entry into the vacated slot, and put this unloaded section at
     * the back of overflow. This is fair across the entire window without
     * forcing a chunk load or changing any Rapier body.
     */
    void defer(T item) {
        if (item == null || !queued.remove(item))
            throw new IllegalStateException("Cannot defer a nonqueued section");
        // An unloaded section is already scheduled for retry; a second
        // replay would only multiply native work when it finally loads.
        replayAfterUpload.remove(item);
        if (pending.size() >= capacity) {
            queued.add(item);
            throw new IllegalStateException("Retry queue capacity exceeded");
        }
        if (overflow.isEmpty()) {
            queued.add(item);
            pending.addLast(item);
            return;
        }
        overflow.add(item);
        var iterator = overflow.iterator();
        T next = iterator.next();
        iterator.remove();
        if (!queued.add(next))
            throw new IllegalStateException("Duplicate native upload entry");
        pending.addLast(next);
    }

    /** Native upload completed (or was deliberately discarded on failure). */
    void complete(T item) {
        if (!queued.remove(item))
            throw new IllegalStateException("Cannot complete a nonqueued section");
        boolean replay = replayAfterUpload.remove(item);
        while (pending.size() < capacity && !overflow.isEmpty()) {
            var it = overflow.iterator();
            T deferred = it.next();
            it.remove();
            if (queued.add(deferred)) pending.addLast(deferred);
        }
        if (replay) promote(item);
    }

    int size() { return pending.size(); }
    int deferredCount() { return overflow.size(); }

    void clear() {
        pending.clear();
        queued.clear();
        overflow.clear();
        replayAfterUpload.clear();
    }
}
