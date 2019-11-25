package utils;

import java.util.concurrent.atomic.AtomicReference;

public class LockFreeQueue<T> {

    static class Node<T> {
        final T value;
        final AtomicReference<Node<T>> next = new AtomicReference<>();
        private Node(T value) {
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

    public T dequeue() {
        AtomicReference<T> valueToReturn = new AtomicReference<>();
        AtomicReference<Node<T>> observedHead;
        Node<T> observedHeadNext;
        AtomicReference<Node<T>> observedTail;

        // loop until dequeue is successful by returning actual value if it's present
        // or null if queue is empty
        do {

            observedHead = head;
            observedTail = tail;
            observedHeadNext = observedHead.get().next.get();

            // is the queue empty or tail isn't on right node
            if (observedHead == observedTail) {

                if (observedHeadNext == null) {
                    // queue is empty
                    return null;
                }

                // Cas to update tail
                tail.compareAndSet(observedTail.get(), observedTail.get().next.get());

            } else {
                valueToReturn.set(observedHeadNext.value);
            }

        } while (!head.compareAndSet(observedHead.get(), observedHeadNext));

        return valueToReturn.get();
    }

    public boolean isNotEmpty() {
        return head.get().next.get() != null;
    }
}