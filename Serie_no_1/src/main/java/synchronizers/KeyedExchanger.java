package synchronizers;

import utils.NodeLinkedList;
import utils.Timeouts;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class KeyedExchanger<T> {

    private static class Request {

        private boolean isDone = false;
        private final int key;
        private final Condition condition;

        Request(int key, Lock monitor) {
            this.key = key;
            condition = monitor.newCondition();
        }
    }

    private static class Data<T> {

        private final int key;
        private final T data;

        Data(int key, T data) {
            this.key = key;
            this.data = data;
        }
    }

    private final Lock monitor = new ReentrantLock();
    private final NodeLinkedList<Request> requestsList = new NodeLinkedList<>();
    private final NodeLinkedList<Data<T>> dataQueue = new NodeLinkedList<>();

    public Optional<T> exchange(int key, T myData, int timeout) throws InterruptedException {
        monitor.lock();

        try {

            NodeLinkedList.Node<Request> dataRequest;

            // check if pair thread already pushed my future data, if so
            // complete pair thread's request, pull my future data from data queue and push my data
            // for the pair thread
            if (requestsList.isNotEmpty() && requestsList.contains(node -> node.key == key,
                    requestsList.getHeadNode())) {
                dataRequest = requestsList.searchNodeAndReturn(node -> node.key == key, null,
                        requestsList.getHeadNode());
                dataRequest.value.isDone = true;
                // push my data
                dataQueue.push(new Data<>(key, myData));
                dataRequest.value.condition.signal();
                NodeLinkedList.Node<Data<T>> dataToReturn = dataQueue.pullSpecificNodeAndReturn(tData -> tData.key == key,
                        null, dataQueue.getHeadNode());
                return Optional.of(dataToReturn.value.data);
            }

            // push my data
            dataQueue.push(new Data<>(key, myData));

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return Optional.empty();
            }

            // prepare wait
            dataRequest = requestsList.push(new Request(key, monitor));
            long start = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(start);

            while (true) {

                try {
                    // if the thread got to this point it means that the pair thread didn't put the data in the map yet
                    // so it's time to wait
                    dataRequest.value.condition.await(remaining, TimeUnit.MILLISECONDS);

                } catch (InterruptedException e) {
                    // giving up
                    if (dataRequest.value.isDone) {
                        NodeLinkedList.Node<Data<T>> dataToReturn = dataQueue
                                .pullSpecificNodeAndReturn(tData -> tData.key == key, null, dataQueue.getHeadNode());
                        return Optional.of(dataToReturn.value.data);
                    }
                    requestsList.remove(dataRequest);
                    Thread.currentThread().interrupt();
                    throw e;
                }

                if (dataRequest.value.isDone) {
                    NodeLinkedList.Node<Data<T>> dataToReturn = dataQueue
                            .pullSpecificNodeAndReturn(tData -> tData.key == key, null, dataQueue.getHeadNode());
                    return Optional.of(dataToReturn.value.data);
                }

                remaining = Timeouts.remaining(start);
                if (Timeouts.isTimeout(remaining)) {

                    if (dataRequest.value.isDone) {
                        NodeLinkedList.Node<Data<T>> dataToReturn = dataQueue
                                .pullSpecificNodeAndReturn(tData -> tData.key == key, null, dataQueue.getHeadNode());
                        return Optional.of(dataToReturn.value.data);
                    }

                    requestsList.remove(dataRequest);
                    return Optional.empty();
                }
            }
        } finally {
            monitor.unlock();
        }
    }
}
