package synchronizers;

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
            long limit = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(limit);

            // calculate value if it isn't being calculated already
            if (!isBeingCalculated) {
                try {
                    isBeingCalculated = true;
                    return getValue();
                } finally {
                    isBeingCalculated = false;
                }
            }

            while (true) {

                try {

                    condition.await(remaining, TimeUnit.MILLISECONDS);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }

                // hardest path
                if (this.value.isPresent()) {
                    return this.value;
                }
                // threads are still waiting for the value so we need to retry to calculate the value
                if (!isBeingCalculated) {
                    try {
                        isBeingCalculated = true;
                        return getValue();
                    } finally {
                        isBeingCalculated = false;
                    }
                }

                // check if timeout has ended
                remaining = Timeouts.remaining(limit);
                if (Timeouts.isTimeout(remaining)) {
                    return Optional.empty();
                }

            }
        } finally {
            monitor.unlock();
        }
    }


    private Optional<E> getValue() throws Exception {

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