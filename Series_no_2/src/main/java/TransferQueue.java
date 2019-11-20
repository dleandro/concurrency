import utils.LockFreeQueue;
import utils.Timeouts;

public class TransferQueue<E> {

    private final LockFreeQueue<E> dataQueue = new LockFreeQueue<>();

    // deliver messages to the end of the queue so that we can have a FIFO order
    public void put(E message) {
        dataQueue.enqueue(message);
    }

    public E take(long timeout) {

        if (dataQueue.isNotEmpty()) {
            return dataQueue.dequeue();
        }

        // check if it's supposed to wait
        if (Timeouts.noWait(timeout)) {
            return null;
        }

        // prepare wait
        long limit = Timeouts.start(timeout);

        // keep checking if there are any messages until we surpass the timeout
        while (true) {

            if (dataQueue.isNotEmpty()) {
                return dataQueue.dequeue();
            }

            // check if timeout has ended
            long remaining = Timeouts.remaining(limit);
            if (Timeouts.isTimeout(remaining)) {
                return null;
            }
        }
    }
}