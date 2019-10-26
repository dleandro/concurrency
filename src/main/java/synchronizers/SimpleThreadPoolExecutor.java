package synchronizers;

import utils.*;

import java.util.Optional;
import java.util.concurrent.Callable;
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
    private final NodeLinkedList<Request> requestWorkQueue = new NodeLinkedList<>();

    public SimpleThreadPoolExecutor(int maxPoolSize, int keepAliveTime) {
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
    }

    public <T> Result<T> execute(Callable<T> command) throws InterruptedException {

        return new Result<T>() {

            NodeLinkedList.Node<WorkerThread> workerThread;

            private void manageExecutor() {
                monitor.lock();

                try {

                    // if the executor is not open to tasks then exception is thrown
                    // else the executor will proceed to create threads or use already available threads
                    // to execute the desired tasks
                    if (state != SynchronizerState.isOpen) {
                        throw new RejectedExecutionException();
                    }

                    // check if there are already available threads
                    if (threadPool.isEmpty()) {
                        manageThreads();
                    }

                    // time to select a thread
                    availableThreads--;
                    workerThread = threadPool.pull();
                    workerThread.value.start();

                } finally {
                    monitor.unlock();
                }
            }

            @Override
            public boolean isComplete() {
                return workerThread.value.hasBeenExecuted;
            }

            @Override
            public boolean tryCancel() {
                workerThread.value.interrupt();

                if (workerThread.value.isAlive()) {
                    return false;
                }

                workerThread.value.hasBeenCancelled = true;
                return true;
            }

            @Override
            public Optional<T> get(int timeout) throws Exception {
                final Optional[] result = {Optional.empty()};
                final Exception[] exceptionThrown = {null};

                manageWork(() -> {
                    try {
                        result[0] = Optional.of(new Lazy<>(command).get(timeout));
                    } catch (Exception e) {
                        exceptionThrown[0] = e;
                        System.out.println(exceptionThrown);
                    } finally {
                        workerThread.value.hasBeenExecuted = true;
                    }
                });

                manageExecutor();

                // if thread has finished we should put it back in the threadPool to avoid creating
                // more threads for further tasks
                if (workerThread.value.hasBeenExecuted || workerThread.value.isInterrupted()) {
                    threadPool.push(workerThread.value);
                }

                // wait for task to be executed
                while (!workerThread.value.hasBeenExecuted);

                if (exceptionThrown[0] != null) {
                    throw exceptionThrown[0];
                }

                return result[0];
            }
        };
    }

    private void manageThreads() {

        // should we create a new workerThread
        if (existingThreads < maxPoolSize && state == SynchronizerState.isOpen) {
            existingThreads++;
            availableThreads++;
            threadPool.push(ThreadFactory.newWorkerThread(() -> {

                try {
                    monitor.lock();

                    // is there work to do? if so run it
                    if (workQueue.isNotEmpty()) {
                        workQueue.pull().value.work.run();
                        checkIfExecutorIsAwaitingTermination();
                        return;
                    }

                    // check if it's supposed to wait
                    if (Timeouts.noWait(keepAliveTime)) {
                        checkIfExecutorIsAwaitingTermination();
                        Thread.currentThread().interrupt();
                        return;
                    }

                    // request work
                    NodeLinkedList.Node<Request> workRequest = requestWorkQueue.push(new Request(monitor));
                    // prepare wait
                    long limit = Timeouts.start(keepAliveTime);
                    long remaining = Timeouts.remaining(limit);

                    while (true) {

                        try {
                            workRequest.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            if (workRequest.value.isDone) {
                                // couldn't give up because request was already fulfilled
                                workQueue.pull().value.work.run();
                                checkIfExecutorIsAwaitingTermination();
                                return;
                            }
                            Thread.currentThread().interrupt();
                        }

                        if (workRequest.value.isDone) {
                            availableThreads--;
                            workQueue.pull().value.work.run();
                            // check if i am the last thread so that i can notify awaitTermination method
                            checkIfExecutorIsAwaitingTermination();
                            return;
                        }

                        // check if timeout has ended
                        remaining = Timeouts.remaining(limit);
                        if (Timeouts.isTimeout(remaining)) {
                            Thread.currentThread().interrupt();
                            checkIfExecutorIsAwaitingTermination();
                            return;
                        }
                    }

                } finally {
                    monitor.unlock();
                }
            }));
        }
    }

    private void checkIfExecutorIsAwaitingTermination() {

        // means that a shutdown request has been initiated
        // if both workQueue and threadPool are empty then all
        // the tasks have been executed and we can change state and signal AwaitTermination method
        if (state == SynchronizerState.isClosed && workQueue.isEmpty() && threadPool.isEmpty()) {
            state = SynchronizerState.isShutdown;
            condition.signal();
        }
    }

    private void manageWork(Runnable runnable) {
        monitor.lock();

        try {
            // check if there are any work requests to be fulfilled
            if (requestWorkQueue.isNotEmpty()) {
                NodeLinkedList.Node<Request> requestNode = requestWorkQueue.pull();
                workQueue.push(new Work(runnable));
                requestNode.value.isDone = true;
                requestNode.value.condition.signal();
                return;
            }

            if (state == SynchronizerState.isOpen) {
                workQueue.push(new Work(runnable));
            }

        } finally {
            monitor.unlock();
        }
    }


    private void shutdown() {
        monitor.lock();

        try {
            // if there are no threads working we can just switch to shut down mode
            if (workQueue.isEmpty() && existingThreads == availableThreads) {
                state = SynchronizerState.isShutdown;
                return;
            }
            // if not the remaining threads need to finish their work normally before shutting down the executor
            state = SynchronizerState.isClosed;
        } finally {
            monitor.unlock();
        }
    }

    public boolean awaitTermination(int timeout) throws InterruptedException {
        monitor.lock();

        try {

            // happy path
            if (state == SynchronizerState.isShutdown) {
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

    private static class Request {
        private boolean isDone = false;
        private final Condition condition;

        Request(Lock monitor) {
            this.condition = monitor.newCondition();
        }
    }
}
