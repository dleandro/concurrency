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

        // easy path
        if (value.isPresent()) {
            return value;
        }

        monitor.lock();
        try {

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return Optional.empty();
            }

            // prepare wait
            long start = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(start);

            // calculate value
            if (value.isEmpty() && !Timeouts.isTimeout(remaining)) {
                getValue();
            }

            while (true) {
                try {
                    condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }

                remaining = Timeouts.remaining(start);
                if (Timeouts.isTimeout(remaining)) {
                    return Optional.empty();
                }

                if (value.isPresent()) {
                    return value;
                }
            }

        } finally {
            monitor.unlock();
        }
    }

    private void getValue() throws Exception {

        if (value.isPresent()) {
            return;
        }

        Optional<E> calculatedValue = Optional.of(provider.call());

        monitor.lock();
        try {
            this.value = calculatedValue;
            condition.signalAll();
        } finally {
            monitor.unlock();
        }
    }

}
