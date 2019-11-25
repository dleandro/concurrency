import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;

public class TestSafeMessageBox {

    private static final Logger logger = LoggerFactory.getLogger(SafeMessageBox.class);

    private void test(Runnable publish, Supplier<Integer> consume,
                      int nOfThreads, Predicate<Long> pred, Predicate<Integer> whenToRunPublish) throws InterruptedException {

        final List<Thread> ths = new ArrayList<>();
        final AtomicBoolean error = new AtomicBoolean();
        final AtomicInteger counter = new AtomicInteger(0);
        final ConcurrentLinkedQueue<Integer> results = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < nOfThreads; i++) {
            int observedCounter = counter.incrementAndGet();

            if (whenToRunPublish.test(observedCounter)) {
                Thread th = new Thread(() -> {
                    publish.run();
                    logger.info("Thread {} published a message", Thread.currentThread().getName());
                });
                th.start();
                th.join();
            } else {
                Thread th = new Thread(() -> {
                    int result = 0;
                    try {
                        result = consume.get();
                    } catch (Exception e) {
                        logger.info("lives were extinguished");
                    }
                    if (result != 0) {
                        results.add(result);
                    }
                    logger.info("Thread {} consumed a message and got {}", Thread.currentThread().getName(), result);
                });
                th.start();
                ths.add(th);
            }
        }

        if (pred.test((long) results.size()))  {
            error.set(true);
        }

        for (Thread th : ths) {
            th.join(1000);
            if (th.isAlive()) {
                logger.error("Test didn't stop when it was supposed to");
            }
        }

        assertFalse(error.get());
    }



    @Test
    public void overLoadLivesOnSafeMessageBox() throws InterruptedException {

        SafeMessageBox<Integer> smb = new SafeMessageBox<>();
        final int[] valuesToPublish = {1};

        test(() -> smb.publish(valuesToPublish[0], 20),
                smb::tryConsume, 22, (resultsSize) -> resultsSize == 20,
                counter -> counter == 1);
    }

    @Test
    public void useLessLivesThanExistingOnes() throws InterruptedException {

        SafeMessageBox<Integer> smb = new SafeMessageBox<>();
        final int[] valuesToPublish = {1};

        test(() -> smb.publish(valuesToPublish[0], 30),
                smb::tryConsume, 20, (resultsSize) -> resultsSize == 19,
                counter -> counter == 1);
    }

    @Test
    public void changeMessageMidway() throws InterruptedException {

        SafeMessageBox<Integer> smb = new SafeMessageBox<>();
        final int[] valuesToPublish = {1};

        test(() -> smb.publish(valuesToPublish[0], 15),
                smb::tryConsume, 30, resultsSize -> resultsSize == 28,
                counter -> counter == 1 || counter == 16);
    }
}
