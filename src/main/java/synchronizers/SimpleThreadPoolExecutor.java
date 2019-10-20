package synchronizers;

import utils.NodeLinkedList;
import utils.SynchronizerState;
import utils.Timeouts;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleThreadPoolExecutor {

    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private final int maxPoolSize;
    private final int keepAliveTime;
    private int existingThreads = 0;
    private int availableThreads = 0;
    private SynchronizerState state = SynchronizerState.isOpen;
    private final NodeLinkedList<WorkerThread> threadPool = new NodeLinkedList<>();
    private final NodeLinkedList<Work> workQueue = new NodeLinkedList<>();
    private final NodeLinkedList<Request> workRequestQueue = new NodeLinkedList<>();

    public SimpleThreadPoolExecutor(int maxPoolSize, int keepAliveTime) {
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
    }

    private void manageThreads() {
        monitor.lock();
        try {
            if (existingThreads < maxPoolSize && availableThreads == 0
                    && state == SynchronizerState.isOpen) {
                existingThreads++;
                threadPool.push(new WorkerThread(new Thread(() -> {

                    monitor.lock();

                    try {

                        if (workQueue.isNotEmpty()) {
                            NodeLinkedList.Node<Work> workNode = workQueue.pull();
                            workNode.value.work.run();
                        }

                        // check if it's supposed to wait
                        if (Timeouts.noWait(keepAliveTime)) {
                            Thread.currentThread().interrupt();
                            return;
                        }

                        NodeLinkedList.Node<Request> workRequest = workRequestQueue.push(new Request(monitor));
                        // prepare wait
                        long limit = Timeouts.start(keepAliveTime);
                        long remaining = Timeouts.remaining(limit);

                        while (true) {

                            try {
                                availableThreads++;
                                workRequest.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException e) {
                                if (workRequest.value.isDone) {
                                    workQueue.pull().value.work.run();
                                    availableThreads--;
                                    return;
                                }
                                Thread.currentThread().interrupt();
                            }

                            if (workRequest.value.isDone) {
                                workQueue.pull().value.work.run();
                                availableThreads--;
                                return;
                            }

                            // check if timeout has ended
                            remaining = Timeouts.remaining(limit);
                            if (Timeouts.isTimeout(remaining)) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }

                    } finally {
                        monitor.unlock();
                    }

                })));
            }
        } finally {
            monitor.unlock();
        }
    }

    private void manageWork(Result result) {

        if (workRequestQueue.isNotEmpty()) {
            NodeLinkedList.Node<Request> requestNode = workRequestQueue.pull();
            requestNode.value.isDone = true;
            requestNode.value.condition.signal();
        }

        workQueue.push(new Work(() -> {
            try {
                result.get(1000);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    public <T> Result<T> execute(Callable<T> command) throws InterruptedException {
        monitor.lock();

        try {

            if (state == SynchronizerState.isShutdown) {
                throw new RejectedExecutionException();
            }

            if (threadPool.isEmpty()) {
                manageThreads();
            }

            availableThreads--;
            NodeLinkedList.Node<WorkerThread> workerThread = threadPool.pull();
            workerThread.value.thread.start();

            Result<T> result = new Result<T>() {
                @Override
                public boolean isComplete() {
                    return workerThread.value.hasBeenExecuted;
                }

                @Override
                public boolean tryCancel() {
                    return !isComplete();
                }

                @Override
                public Optional<T> get(int timeout) throws Exception {

                    try {
                        // check if it's supposed to wait
                        if (Timeouts.noWait(timeout)) {
                            return Optional.empty();
                        }

                        long start = Timeouts.start(timeout);
                        long remaining = Timeouts.remaining(start);

                        Callable<T> callableWithTimeout = () -> {

                            if (Timeouts.isTimeout(remaining)) {
                                return null;
                            }
                            return command.call();
                        };

                        T calculatedValue = callableWithTimeout.call();

                        workerThread.value.hasBeenExecuted = true;

                        if (calculatedValue == null) {
                            return Optional.empty();
                        }

                        return Optional.of(calculatedValue);

                    } catch (Exception e) {
                        if (workerThread.value.hasBeenCancelled) {
                            throw new CancellationException();
                        }
                        throw e;
                    }
                }
            };

            manageWork(result);
            return result;

        } finally {
            monitor.unlock();
        }
    }


    public void shutdown() {
        monitor.lock();

        try {
            state = SynchronizerState.isShutdown;
            condition.signal();
        } finally {
            monitor.unlock();
        }
    }

    public boolean awaitTermination(int timeout) throws InterruptedException {

        monitor.lock();

        try {

            if (state == SynchronizerState.isShutdown && threadPool.isEmpty()) {
                return true;
            }

            state = SynchronizerState.isClosed;

            // check if it's supposed to wait
            if (Timeouts.noWait(timeout)) {
                return false;
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

                if (state == SynchronizerState.isShutdown) {
                    return true;
                }

                if (Timeouts.isTimeout(remaining)) {
                    return false;
                }
            }
        } finally {

            monitor.unlock();
        }

    }

    private static class Work {

        private final Runnable work;

        Work(Runnable work) {
            this.work = work;
        }
    }

    private static class WorkerThread {

        private boolean hasBeenCancelled = false;
        private boolean hasBeenExecuted = false;
        private final Thread thread;

        WorkerThread(Thread thread) {
            this.thread = thread;
        }
    }

    private static class Request {
        private boolean isDone = false;
        private final Condition condition;

        Request(Lock monitor) {
            this.condition = monitor.newCondition();
        }
    }
}
