/*import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import synchronizers.SimpleThreadPoolExecutor;
import utils.Result;
import utils.Timeouts;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.assertFalse;

public class SimpleThreadPoolExecutorTests {

    private static final Logger logger = LoggerFactory.getLogger(SimpleThreadPoolExecutorTests.class);
    private static final Duration TEST_DURATION = Duration.ofSeconds(20);

    private Optional<Integer> executeAndCheckElapsed(SimpleThreadPoolExecutor simpleThreadPoolExecutor)
            throws Exception {
        Optional<Integer> result;
        final int[] initialValue = {0};
        // allows for a 100ms error due to scheduling delays
        long allowedTimeError = 100;
        int timeout = 20000;

        long start = System.currentTimeMillis();

        do {
            result = simpleThreadPoolExecutor.execute(() -> ++initialValue[0]).get(timeout);

            long duration = System.currentTimeMillis() - start;

            if (duration - timeout > allowedTimeError) {
                logger.info("execute took {} and should not exceed {}", duration, timeout);
                if (!result.isPresent()) {
                    throw new RuntimeException("execute exceeded allowed time");
                }
            }
        } while (!result.isPresent());
        return result;
    }

    private void test(SimpleThreadPoolExecutor simpleThreadPoolExecutor, int nOfTasks) throws InterruptedException{

        final AtomicBoolean error = new AtomicBoolean();
        final Instant deadline = Instant.now().plus(TEST_DURATION);
        final List<Thread> ths = new ArrayList<>();

        for (int i = 0; i < nOfTasks; ++i) {
            Thread th = new Thread( () -> {

                try {
                    while (true) {

                        if (Instant.now().compareTo(deadline) > 0) {
                            return;
                        }

                        Optional<Integer> result = executeAndCheckElapsed(simpleThreadPoolExecutor);
                        logger.info("succeeded " + result.toString());

                        if (!result.isPresent()) {
                            logger.info("executor failed");
                            error.set(true);
                            return;
                        }

                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    logger.info("interrupted, giving up");
                } catch (Exception e) {
                    logger.info("exception was caught");
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
            if (th.isAlive()) {
                th.join(remaining);
            }
            if (th.isAlive()) {
                logger.error("Test didn't stop when it was supposed to");
            }
        }

        assertFalse(error.get());
    }

    @Test
    public void testThreadPoolExecutor() throws InterruptedException {

        SimpleThreadPoolExecutor threadPoolExecutor = new SimpleThreadPoolExecutor(20, 2000);

        test(threadPoolExecutor, 10);
    }
}


 */
