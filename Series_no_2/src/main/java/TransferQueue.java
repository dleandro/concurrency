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
/*
    private static class Request {
        private boolean isDone = false;
        private final Condition condition;

        Request(Lock monitor) {
            this.condition = monitor.newCondition();
        }
    }

    private final NodeLinkedList<E> dataQueue = new NodeLinkedList<>();
    private final Lock monitor = new ReentrantLock();
    private final NodeLinkedList<Request> takeRequestQueue = new NodeLinkedList<>();
    private final NodeLinkedList<Request> transferRequestQueue = new NodeLinkedList<>();

    // Non blocking
    public void put(E message) {
        // add to tail to make sure that take doesn't signal transfer while consuming put's messages instead of
        // transfer's messages
        if (transferRequestQueue.isNotEmpty()) {
            dataQueue.addToTail(message);
        }
        else {
            dataQueue.push(message);
        }
    }

    public E take(int timeout) throws InterruptedException {
        monitor.lock();

        try {

            // easy path
            if (dataQueue.isNotEmpty() && transferRequestQueue.isEmpty()) {
                // means that this data was sent by the put method so there is no need to signal
                // or complete any request
                return dataQueue.pull().value;
            } else if (transferRequestQueue.isNotEmpty()){
                completeRequest(transferRequestQueue);
                return dataQueue.pull().value;
            }

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return null;
            }

            NodeLinkedList.Node<Request> takeRequest = takeRequestQueue.push(new Request(monitor));
            // prepare wait
            long limit = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(limit);

            while (true) {

                try {
                    takeRequest.value.condition.await(remaining, TimeUnit.MILLISECONDS);

                } catch (InterruptedException e) {
                    if (takeRequest.value.isDone) {
                        // couldn't give up because request was already fulfilled
                        Thread.currentThread().interrupt();
                        completeRequest(transferRequestQueue);
                        return dataQueue.pull().value;
                    }
                    takeRequestQueue.remove(takeRequest);
                    throw e;
                }

                if (takeRequest.value.isDone) {
                    completeRequest(transferRequestQueue);
                    return dataQueue.pull().value;
                }

                // check if timeout has ended
                remaining = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remaining)) {
                    takeRequestQueue.remove(takeRequest);
                    return null;
                }
            }

        } finally {
            monitor.unlock();
        }
    }

    private void completeRequest(NodeLinkedList<Request> listToCompleteRequest) {
        NodeLinkedList.Node<Request> nodeToComplete = listToCompleteRequest.pull();
        nodeToComplete.value.isDone = true;
        nodeToComplete.value.condition.signal();
    }*/
