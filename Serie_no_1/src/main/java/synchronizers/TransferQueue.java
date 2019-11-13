package synchronizers;

import utils.NodeLinkedList;
import utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransferQueue<E> {

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

    public boolean transfer(E message, long timeout) throws InterruptedException {
        monitor.lock();

        try {

            dataQueue.push(message);

            // check if there is any take request ready to be fulfilled
            if (takeRequestQueue.isNotEmpty()) {
                NodeLinkedList.Node<Request> takeRequest = takeRequestQueue.pull();
                takeRequest.value.isDone = true;
                takeRequest.value.condition.signal();
            }

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return false;
            }

            // send my request
            NodeLinkedList.Node<Request> transferRequest = transferRequestQueue.push(new Request(monitor));
            // prepare wait
            long limit = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(limit);

            while (true) {

                try {
                    transferRequest.value.condition.await(remaining, TimeUnit.MILLISECONDS);

                } catch (InterruptedException e) {
                    if (transferRequest.value.isDone) {
                        // couldn't give up because request was already fulfilled
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    transferRequestQueue.remove(transferRequest);
                    throw e;
                }

                // check if the value has been consumed, if so the transfer was executed successfully
                if (transferRequest.value.isDone) {
                    return true;
                }

                // check if timeout has ended
                remaining = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remaining)) {
                    transferRequestQueue.remove(transferRequest);
                    return false;
                }
            }
        } finally {
            monitor.unlock();
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
    }
}
