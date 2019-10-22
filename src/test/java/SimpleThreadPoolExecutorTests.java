import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import synchronizers.KeyedExchanger;
import synchronizers.SimpleThreadPoolExecutor;
import utils.NodeLinkedList;
import utils.Result;
import utils.TestHelper;
import utils.Timeouts;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.assertFalse;

public class SimpleThreadPoolExecutorTests<T> {

    private static final Logger logger = LoggerFactory.getLogger(SimpleThreadPoolExecutorTests.class);
    private static final Duration TEST_DURATION = Duration.ofSeconds(60);

    private Optional<Integer> executeAndCheckElapsed(SimpleThreadPoolExecutor simpleThreadPoolExecutor)
            throws Exception {
        Optional<Integer> result;
        final int[] initialValue = {0};
        // allows for a 100ms error due to scheduling delays
        long allowedTimeError = 100;
        int timeout = 20000;

        long start = System.currentTimeMillis();
        result = simpleThreadPoolExecutor.execute(() -> ++initialValue[0]).get(timeout);
        long duration = System.currentTimeMillis() - start;

        if (duration - timeout > allowedTimeError) {
            logger.info("execute took {} and should not exceed {}", duration, timeout);
            if (!result.isPresent()) {
                throw new RuntimeException("execute exceeded allowed time");
            }
        }

        return result;
    }

    private void test(SimpleThreadPoolExecutor simpleThreadPoolExecutor, int nOfThreads) throws InterruptedException{

        final List<Thread> ths = new ArrayList<>();
        final AtomicBoolean error = new AtomicBoolean();
        final Instant deadline = Instant.now().plus(TEST_DURATION);


        for (int i = 0; i < nOfThreads; ++i) {
            Thread th = new Thread(() -> {
                try {
                    if (Instant.now().compareTo(deadline) > 0) {
                        return;
                    }

                    Optional<Integer> result = executeAndCheckElapsed(simpleThreadPoolExecutor);

                    if (!result.isPresent()) {
                        logger.info("executor failed");
                        error.set(true);
                        return;
                    }

                    Thread.sleep(100);

                } catch (InterruptedException e) {
                    logger.info("interrupted, giving up");
                } catch (Exception e) {
                    error.set(true);
                }
            });
            th.start();
            ths.add(th);
        }

        // join them all
        long testDeadline = Timeouts.start(
                TEST_DURATION.plusSeconds(5).getSeconds(),
                TimeUnit.SECONDS);
        for (Thread th : ths) {
            long remaining = Timeouts.remaining(testDeadline);
            th.join(remaining);
            if (th.isAlive()) {
                logger.error("Test didn't stop when it was supposed to");
            }
        }
        assertFalse(error.get());
    }

    @Test
    public void testThreadPoolExecutor() throws InterruptedException {

        SimpleThreadPoolExecutor threadPoolExecutor = new SimpleThreadPoolExecutor(20, 2000);
        threadPoolExecutor.manageWork(() -> logger.info("work done"));
        threadPoolExecutor.manageWork(() -> logger.info("work done"));

        test(threadPoolExecutor, 1);
    }
}


