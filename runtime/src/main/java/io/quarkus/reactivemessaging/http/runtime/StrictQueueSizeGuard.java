package io.quarkus.reactivemessaging.http.runtime;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A non-blocking utility class to keep the amount of enqueued emissions under a certain number ({@code queueSize})
 */
class StrictQueueSizeGuard {
    private final int queueSize;
    private final AtomicInteger enqueued = new AtomicInteger();
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    /**
     * @param queueSize size of the queue
     */
    StrictQueueSizeGuard(int queueSize) {
        this.queueSize = queueSize;
    }

    /**
     * mark an emission as dequeued. In other words, remove it from the count
     */
    void dequeue() {
        enqueued.decrementAndGet();
    }

    /**
     *
     * @return if the message can be emitted or not
     */
    boolean prepareToEmit() {
        while (true) {
            int oldVal = enqueued.get();
            int newVal = oldVal + 1;
            if (newVal <= queueSize) {
                if (enqueued.compareAndSet(oldVal, newVal)) {
                    return true;
                } // else try again
            } else {
                return false; // too many messages to enqueue
            }
        }
    }

    void putInQueue(Runnable event) {
        if (event == null) {
            throw new IllegalArgumentException("Element can't be null!");
        }
        queue.offer(event);
    }

    void removeFromQueue(long count) {
        for (long i = 0; i < count; i++) {
            Runnable event = queue.poll();
            if (event == null) {
                return;
            }
            event.run();
        }
    }
}
