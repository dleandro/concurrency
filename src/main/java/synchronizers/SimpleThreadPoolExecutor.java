package synchronizers;

import utils.NodeLinkedList;
import utils.Result;
import utils.SynchronizerState;
import utils.Timeouts;

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

            return new Result<T>() {
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
                    final Optional[] result = {Optional.empty()};

                    result[0] = new Lazy<>(command).get(timeout);
                    workerThread.value.hasBeenExecuted = true;

                    return result[0];
                }
            };

        } finally {
            monitor.unlock();
        }
    }

    private void manageThreads() {

        if (existingThreads < maxPoolSize && availableThreads == 0
                && state == SynchronizerState.isOpen) {
            existingThreads++;
            availableThreads++;
            NodeLinkedList.Node<WorkerThread> threadNode = threadPool.push(new WorkerThread(null));
            threadNode.value.thread = new Thread(() -> {

                try {
                    monitor.lock();

                    if (workQueue.isNotEmpty()) {
                        availableThreads--;
                        workQueue.pull().value.work.run();
                        return;
                    }

                    // check if it's supposed to wait
                    if (Timeouts.noWait(keepAliveTime)) {
                        Thread.currentThread().interrupt();
                        threadPool.remove(threadNode);
                        return;
                    }

                    NodeLinkedList.Node<Request> workRequest = requestWorkQueue.push(new Request(monitor));
                    // prepare wait
                    long limit = Timeouts.start(keepAliveTime);
                    long remaining = Timeouts.remaining(limit);

                    while (true) {

                        try {
                            availableThreads++;
                            workRequest.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            if (workRequest.value.isDone && workQueue.isNotEmpty()) {
                                // couldn't give up because request was already fulfilled
                                availableThreads--;
                                workQueue.pull().value.work.run();
                                return;
                            }
                            threadPool.remove(threadNode);
                            Thread.currentThread().interrupt();
                        }

                        if (workRequest.value.isDone && workQueue.isNotEmpty()) {
                            availableThreads--;
                            workQueue.pull().value.work.run();
                            return;
                        }

                        // check if timeout has ended
                        remaining = Timeouts.remaining(limit);
                        if (Timeouts.isTimeout(remaining)) {
                            threadPool.remove(threadNode);
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                } finally {
                    monitor.unlock();
                }
            });
        }
    }


    public void manageWork(Runnable runnable) {
        monitor.lock();

        try {
            if (requestWorkQueue.isNotEmpty() && state == SynchronizerState.isOpen) {
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



    public void shutdown() {
        monitor.lock();

        try {
            threadPool.foreach(workerThreadNode -> {
                try {
                    workerThreadNode.value.thread.join();
                    threadPool.remove(workerThreadNode);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, threadPool.getHeadNode());
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
        private Thread thread;

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
