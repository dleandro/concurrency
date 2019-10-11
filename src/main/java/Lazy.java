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
    private boolean isBeingCalculated = false;

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

            monitor.unlock();

            // calculate value if it isn't being calculated already
            if (!isBeingCalculated) {
                return getValue();
            }

            monitor.lock();

            while (true) {

                try {

                    condition.await(remaining, TimeUnit.MILLISECONDS);

                    // check if timeout has ended
                    remaining = Timeouts.remaining(start);
                    if (Timeouts.isTimeout(remaining)) {
                        return Optional.empty();
                    }

                    // hardest path
                    if (this.value.isPresent()) {
                        return this.value;
                    } else {  // threads are still waiting for the value so we need to retry to calculate the value
                        getValue();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }

        } finally {
            monitor.unlock();
        }
    }


    private Optional<E> getValue() throws Exception {
        isBeingCalculated = true;
        Optional<E> calculatedValue = Optional.of(provider.call());

        monitor.lock();
        try {
            if(!value.isPresent()) {
                this.value = calculatedValue;
            }
            condition.signalAll();
            return value;
        } finally {
            monitor.unlock();
        }
    }

}