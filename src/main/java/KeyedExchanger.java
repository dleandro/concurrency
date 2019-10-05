import utils.Timeouts;

import java.util.ArrayList;
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
    private HashMap<Integer, Request> requestsList = new HashMap<>();

    public Optional<T> exchange(int key, T mydata, int timeout) throws InterruptedException {
        monitor.lock();

        try {

            // is data waiting to be returned by the pair thread
            if (completeRequests(key, mydata)) return (Optional<T>) Optional.of(requestsList.get(key).data);

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return Optional.empty();
            }

            // prepare wait
            long start = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(start);

            while (true) {

                // is the data still not there?
                if (completeRequests(key, mydata)) return (Optional<T>) Optional.of(requestsList.get(key).data);

                // if the thread got to this point it means that the pair thread didn't put the data in the map yet
                // so it's time to wait
                requestsList.put(key, new Request(mydata));
                condition.await(remaining, TimeUnit.MILLISECONDS);

                if (Timeouts.isTimeout(remaining)) {
                    return Optional.empty();
                }

                if (requestsList.get(key).isDone) {
                    return (Optional<T>) Optional.of(requestsList.get(key).data);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;

        } finally {
            monitor.unlock();
        }
    }

    private boolean completeRequests(int key, T mydata) {
        if (requestsList.get(key) != null) {
            // add to the list the new data to be exchanged
            requestsList.put(key, new Request(mydata));

            // signal the other thread that their requested data is ready to be acquired
            requestsList.get(key).isDone = true;
            condition.signal();
            return true;
        }
        return false;
    }
}
