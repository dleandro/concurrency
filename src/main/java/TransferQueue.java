import utils.NodeLinkedList;
import utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransferQueue<E> {

    private static class Request<E> {
        private boolean isDone = false;
        private final E data;
        private final Condition condition;

        Request(E data, Lock monitor) {
            this.data = data;
            this.condition = monitor.newCondition();
        }
    }

    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();

    // Non blocking
    public void put(E message) {
        queue.push(new Request(message, monitor));
    }

    public boolean transfer(E message, long timeout) throws InterruptedException {
        monitor.lock();

        try {
            NodeLinkedList.Node<Request> node = queue.push(new Request<>(message, monitor));

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                queue.remove(node);
                return false;
            }

            // prepare wait
            long start = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(start);

            while (true) {

                node.value.condition.await(remaining, TimeUnit.MILLISECONDS);

                // check if timeout has ended
                remaining = Timeouts.remaining(start);
                if (Timeouts.isTimeout(remaining)) {
                    queue.remove(node);
                    return false;
                }

                // check if the value has been consumed, if so the transfer was executed succesfully
                if(node.value.isDone) {
                    queue.remove(node);
                    return true;
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;

        } finally {
            monitor.unlock();
        }
    }

    public E take(int timeout) throws InterruptedException {
        monitor.lock();

        try {
            NodeLinkedList.Node<Request> node = queue.pull();

            // happy path
            if (transferCompleted(node, node.value.condition)) {
                return (E) node.value.data;
            }

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
               return null;
            }

            // prepare wait
            long start = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(start);

            while (true) {
                condition.await(remaining, TimeUnit.MILLISECONDS);

                // check if timeout has ended
                remaining = Timeouts.remaining(start);
                if (Timeouts.isTimeout(remaining)) {
                    return null;
                }

                node = queue.pull();

                if (transferCompleted(node, node.value.condition)) {
                    return (E) node.value.data;
                }

            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;

        } finally {
            monitor.unlock();
        }

    }

    private boolean transferCompleted(NodeLinkedList.Node<Request> node, Condition condition) {
        if (node != null) {
            node.value.isDone = true;
            condition.signal();
            return true;
        }
        return false;
    }
}
