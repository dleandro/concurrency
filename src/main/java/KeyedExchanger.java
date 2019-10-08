import utils.Timeouts;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class KeyedExchanger<T> {

    private static class Request<T> {
        private boolean isDone = false;
        private final Optional<T> data;

        Request(T data) {
            this.data = Optional.of(data);
        }
    }

    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private HashMap<Integer, Request<T>> requestsMap = new HashMap<>();

    public Optional<T> exchange(int key, T mydata, int timeout) throws InterruptedException {
        monitor.lock();

        try {

            Request<T> dataRequest = requestsMap.get(key);

            // easy path
            // is data waiting to be collected
            if (dataRequest != null) {
                dataRequest.isDone = true;
                completeRequests(key, mydata, dataRequest);

                return dataRequest.data;
            }

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return Optional.empty();
            }

            // prepare wait
            long start = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(start);

            while (true) {

                try {
                    // if the thread got to this point it means that the pair thread didn't put the dataRequest in the map yet
                    // so it's time to wait
                    condition.await(remaining, TimeUnit.MILLISECONDS);

                    remaining = Timeouts.remaining(start);
                    if (Timeouts.isTimeout(remaining)) {
                        // TODO:
                        // give up
                        return Optional.empty();
                    }

                    if (requestsMap.get(key).isDone) {
                        return (Optional<T>) Optional.of(requestsMap.get(key).data);
                    }


                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // TODO:
                    // give up
                    throw e;
                }
            }
        } finally {

            monitor.unlock();
        }
    }

    private void completeRequests(int key, T myData, Request<T> dataRequest) {

        while (dataRequest.isDone) {
            // add to the list the new data to be exchanged only if it's flagged,
            // if it isn't flagged the old data hasn't been collected so we can't proceed with the overwriting of data
            requestsMap.put(key, new Request<>(myData));
            // reset the state
            requestsMap.get(key).isDone = false;
            // signal the pair thread that their requested data is ready to be acquired
            condition.signal();
        }
    }
}
