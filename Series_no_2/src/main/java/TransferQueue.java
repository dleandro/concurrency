import utils.LockFreeQueue;
import utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TransferQueue<E> {

    private final LockFreeQueue<E> dataQueue = new LockFreeQueue<>();
    private AtomicInteger waitingThreadsCounter = new AtomicInteger(0);
    private final ReentrantLock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();

    // deliver messages to the end of the queue so that we can have a FIFO order
    public void put(E message) {

        // no threads incremented the counter so there are no threads waiting
        // simply enqueue the message and return
        if (waitingThreadsCounter.get() == 0) {
            dataQueue.enqueue(message);

        } else {
            // threads are waiting for messages so we should
            try {
                monitor.lock();
                dataQueue.enqueue(message);
                condition.signal();

            } finally {
                monitor.unlock();
            }
        }
    }

    public E take(long timeout) {

        // happy path
        if (dataQueue.isNotEmpty()) {
            return dataQueue.dequeue();
        }

        try {
            monitor.lock();

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return null;
            }

            // prepare wait
            long limit = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(limit);

            // increment counter to notify that this thread is going to start waiting for a message
            waitingThreadsCounter.incrementAndGet();
            // keep waiting for messages until we surpass the timeout
            while (true) {

                try {
                    condition.await(remaining, TimeUnit.MILLISECONDS);

                } catch (InterruptedException e) {
                    // give up
                    waitingThreadsCounter.decrementAndGet();
                    Thread.currentThread().interrupt();
                    return null;

                }
                if (dataQueue.isNotEmpty()) {
                    waitingThreadsCounter.decrementAndGet();
                    return dataQueue.dequeue();
                }

                // check if timeout has ended
                remaining = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remaining)) {
                    // give up and return
                    waitingThreadsCounter.decrementAndGet();
                    return null;
                }
            }
        } finally {
            monitor.unlock();
        }
    }
}