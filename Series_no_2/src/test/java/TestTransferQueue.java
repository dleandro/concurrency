import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;

public class TestTransferQueue {

    private static final Logger logger = LoggerFactory.getLogger(SafeMessageBox.class);

    private void test(Runnable put, Supplier<Integer> consume, int nOfThreads,
                      Predicate<Integer> whenTrueRunPut) throws InterruptedException {

        final List<Thread> ths = new ArrayList<>();
        final AtomicBoolean error = new AtomicBoolean();
        final AtomicInteger counter = new AtomicInteger(0);
        final List<Object> results = new LinkedList<>();

        for (int i = 0; i < nOfThreads; i++) {
            int observedCounter = counter.incrementAndGet();

            if (whenTrueRunPut.test(observedCounter)) {
                Thread th = new Thread(() -> {
                    put.run();
                    logger.info("Thread {} finished inserting a message to TransferQueue",
                            Thread.currentThread().getName());
                });
                th.start();
                ths.add(th);
            } else {
                Thread th = new Thread(() -> {
                    Object result = consume.get();
                    results.add(result);
                    logger.info("Thread {} finished consuming a message from TransferQueue and got {} as a result",
                            Thread.currentThread().getName(), result);
                    if (result == null) {
                        logger.info("Thread {} returned null", Thread.currentThread().getName());
                    }
                });
                th.start();
                ths.add(th);
            }
        }

        for (Thread th : ths) {
            th.join(1000);
            if (th.isAlive()) {
                logger.error("Test didn't stop when it was supposed to");
            }
        }

        long numberOfFailedThreads = results.stream().filter(Objects::isNull).count();
        logger.info("{} Threads failed", numberOfFailedThreads);

        if (numberOfFailedThreads > 0) {
            error.set(true);
        }

        assertFalse(error.get());

    }

    @Test
    public void testTransferQueue() throws InterruptedException {

        TransferQueue<Integer> tq = new TransferQueue<>();
        final int[] data = {0};
        final int[] nOfThreads = {20};

        test(() -> tq.put(data[0]++), () -> tq.take(1000), nOfThreads[0],
                i -> i % 2 == 0);

    }

    @Test
    public void executeManyPutsBeforeTakes() throws InterruptedException {

        TransferQueue<Integer> tq = new TransferQueue<>();

        final int[] data = {0};
        final int[] nOfThreads = {30};

        test(() -> tq.put(data[0]++), () -> tq.take(1000), nOfThreads[0],
                integer -> integer <= 15);
    }

    @Test
    public void executeManyTakesBeforePuts() throws InterruptedException {

        TransferQueue<Integer> tq = new TransferQueue<>();

        final int[] data = {0};
        final int[] nOfThreads = {30};

        test(() -> tq.put(data[0]++), () -> tq.take(1000), nOfThreads[0],
                integer -> integer >= 15);
    }

    @Test
    public void testLossUpdateOnPut() throws InterruptedException {
        TransferQueue<Integer> tq = new TransferQueue<>();

        final int[] data = {0};
        final int[] nOfThreads = {2};

        test(() -> tq.put(data[0]++), () -> {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return tq.take(1000);
                }, nOfThreads[0],
                integer -> integer % 2 != 0);
    }
}