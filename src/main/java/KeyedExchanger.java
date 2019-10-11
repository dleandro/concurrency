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
            if (dataRequest != null && !dataRequest.isDone) {
                requestsMap.put(key, new Request<>(mydata));
                condition.signal();
                requestsMap.get(key).isDone = true;
                return dataRequest.data;
            } else {
                requestsMap.put(key, new Request<T>(mydata));
            }

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                requestsMap.remove(key);
                return Optional.empty();
            }

            // prepare wait
            long start = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(start);

            while (true) {

                try {
                    // if the thread got to this point it means that the pair thread didn't put the data in the map yet
                    // so it's time to wait
                    condition.await(remaining, TimeUnit.MILLISECONDS);

                    remaining = Timeouts.remaining(start);
                    if (Timeouts.isTimeout(remaining)) {
                        // giving up
                        requestsMap.remove(key);
                        return Optional.empty();
                    }

                    if (requestsMap.get(key).isDone) {
                        return requestsMap.get(key).data;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // giving up
                    requestsMap.remove(key);
                    throw e;
                }
            }
        } finally {
            monitor.unlock();
        }
    }
}
