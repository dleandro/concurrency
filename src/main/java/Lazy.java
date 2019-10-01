import utils.Timeouts;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Lazy<E> {

    private Callable<E> provider;
    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private E value = null;

    public Lazy(Callable<E> provider) {
        try {
            this.value = provider.call();
        } catch (Exception e) {
            System.out.println("value hasn't been calculated");
        }
    }

    public Optional<E> get(long timeout) throws InterruptedException {
        monitor.lock();
        try {
            if (value != null) {
                return Optional.of(value);
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
                    condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }

                remaining = Timeouts.remaining(start);
                if(Timeouts.isTimeout(remaining)) {
                    return Optional.empty();
                }

                return value != null ? Optional.of(value) : getValue();
            }

        } finally {
            monitor.unlock();
        }
    }

    private Optional<E> getValue() {
        monitor.lock();
        try {
            this.value = provider.call();
            condition.signal();
            return Optional.of(value);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            monitor.unlock();
        }
        return Optional.empty();
    }

}
