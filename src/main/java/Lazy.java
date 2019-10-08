import utils.Timeouts;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Lazy<E> {

    private final Callable<E> provider;
    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private Optional<E> value = Optional.empty();

    public Lazy(Callable<E> provider) {
        this.provider = provider;
    }

    public Optional<E> get(long timeout) throws Exception {
        monitor.lock();

        try {

            // easy path
            if (value.isPresent()) {
                return value;
            }

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return Optional.empty();
            }

            // prepare wait
            long start = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(start);

            while (true) {
                monitor.unlock();

                try {
                    // calculate value
                    if (getValue() == Optional.empty()) {
                        condition.await(remaining, TimeUnit.MILLISECONDS);
                    }

                    monitor.lock();

                    // check if timeout has ended
                    remaining = Timeouts.remaining(start);
                    if (Timeouts.isTimeout(remaining)) {
                        return Optional.empty();
                    }

                    // hardest path
                    if (value.isPresent()) {
                        return value;
                    }
                }

                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }

        } finally {
            monitor.unlock();
        }
    }


    private Optional<E> getValue() throws Exception {

        // Means that value has been calculated and it's time to wait and return
        if (value.isPresent()) {
            return Optional.empty();
        }

        Optional<E> calculatedValue = Optional.of(provider.call());

        monitor.lock();
        try {
            if(value.isEmpty())
            {
                this.value = calculatedValue;
            }
            condition.signalAll();
            return value;
        } finally {
            monitor.unlock();
        }
    }

}