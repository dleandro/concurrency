package utils;

import java.util.concurrent.atomic.AtomicReference;

public class LockFreeQueue<T> {

    static class Node<T> {
        final T value;
        final AtomicReference<Node<T>> next = new AtomicReference<>();
        public Node(T value) {
            this.value = value;
        }
    }

    private final AtomicReference<Node<T>> head;
    private final AtomicReference<Node<T>> tail;

    public LockFreeQueue() {
        Node<T> dummy = new Node<T>(null);
        head = new AtomicReference<>(dummy);
        tail = new AtomicReference<>(dummy);
    }

    public void enqueue(T value) {
        Node<T> node = new Node<>(value);

        while (true) {
            Node<T> observedTail = tail.get();
            Node<T> observedTailNext = observedTail.next.get();
            if (observedTailNext != null) {
                tail.compareAndSet(observedTail, observedTailNext);
                continue;
            }
            if (observedTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(observedTail, node);
                return;
            }
        }
    }

    // dequeue head.next, we have to update head.next and check
    // if it's necessary to update tail as well, update tail if
    // tail points to head.next node
    // returns null if there aren't any values in queue
    public T dequeue() {

        Node<T> observedHeadNext;
        Node<T> observedHeadNextDotNext;
        Node<T> observedTail;

        do {
            observedHeadNext = head.get().next.get();
            observedHeadNextDotNext = observedHeadNext.next.get();
            observedTail = tail.get();

            if (observedTail.value == observedHeadNext) {
                tail.compareAndSet(observedHeadNext, null);
            }

        } while (!head.get().next.compareAndSet(observedHeadNext, observedHeadNextDotNext));

        return observedHeadNext.value;

    }

    public boolean isNotEmpty() {
        return tail.get().value != null;
    }
}