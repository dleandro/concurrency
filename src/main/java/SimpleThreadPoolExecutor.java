import utils.SynchronizerState;
import utils.Timeouts;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleThreadPoolExecutor {

    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private final int maxPoolSize;
    private final int keepAliveTime;
    private int workingThreads = 0;
    private int availableThreads = 0;
    private SynchronizerState state = SynchronizerState.isOpen;


    public SimpleThreadPoolExecutor(int maxPoolSize, int keepAliveTime) {
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
    }

    public <T> Result<T> execute(Callable<T> command) throws InterruptedException {
        monitor.lock();
        workingThreads += 1;

        try {

            while (workingThreads < maxPoolSize) {

            }

            if (shuttingDownMode) {
                throw new RejectedExecutionException();
            }

            return new Result<T>() {
                @Override
                public boolean isComplete() {
                    return hasBeenExecuted;
                }

                @Override
                public boolean tryCancel() {
                    try {
                        isCompleted??
                        return false;
                        else true;
                        hasBeenCancelled = true;
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }

                @Override
                public Optional<T> get(int timeout) throws Exception {

                    try {
                        // check if it's supposed to wait
                        if (Timeouts.noWait(timeout)) {
                            return Optional.empty();
                        }

                        // prepare wait
                        long start = Timeouts.start(timeout);
                        long remaining = Timeouts.remaining(start);

                        Callable<T> callableWithTimeout = () -> {

                            if (Timeouts.isTimeout(remaining)) {
                                return null;
                            }
                            return command.call();
                        };

                        T calculatedValue = callableWithTimeout.call();

                        hasBeenExecuted = true;

                        if (calculatedValue == null) {
                            return Optional.empty();
                        }

                        return Optional.of(calculatedValue);

                    } catch (Exception e) {
                        if (hasBeenCancelled) {
                            throw new CancellationException();
                        }
                        throw e;
                    }
                }
            };

        } finally {
            monitor.unlock();
        }
    }

    public void shutdown() {
        shuttingDownMode = true;
    }

    public boolean awaitTermination(int timeout) throws InterruptedException {

        monitor.lock();

        try {

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return Optional.empty();
            }

            // prepare wait
            long start = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(start);

            while (true) {

                try {
                } catch (InterruptedException e) {

                }
            }
        } finally {

            monitor.unlock();
        }

    }

}
