import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import synchronizers.Lazy;
import utils.Timeouts;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;

public class LazyTests<T> {
    private static final Logger logger = LoggerFactory.getLogger(LazyTests.class);
    private static final Duration TEST_DURATION = Duration.ofSeconds(5);

    private Optional<T> calculateAndCheckElapsed(Lazy lazy, long timeout)
            throws Exception {
        Optional<T> calculatedValue;
        // allows for a 100ms error due to scheduling delays
        long allowedTimeError = 100;

        do {
            long start = System.currentTimeMillis();
            calculatedValue = lazy.get(timeout);
            long duration = System.currentTimeMillis() - start;

            if (duration - timeout > allowedTimeError) {
                logger.info("get took {} and should not exceed {}", duration, timeout);
                if (!calculatedValue.isPresent()) {
                    throw new RuntimeException("get exceeded allowed time");
                }
            }
        } while (!calculatedValue.isPresent());

        return calculatedValue;
    }

    private AtomicBoolean test(Lazy lazy, int nOfThreads, Optional initialValue, long timeout)
            throws Exception {

        final List<Thread> ths = new ArrayList<>();
        final AtomicBoolean error = new AtomicBoolean();
        final Instant deadline = Instant.now().plus(TEST_DURATION);

        for (int i = 0; i < nOfThreads; ++i) {
            Thread th = new Thread(() -> {
                try {
                    while (true) {
                        if (Instant.now().compareTo(deadline) > 0) {
                            return;
                        }
                        Optional<T> calculatedValue = calculateAndCheckElapsed(lazy, timeout);
                        if (!calculatedValue.get().equals(initialValue.get())) {
                            logger.error("More than one thread called callable");
                            error.set(true);
                            return;
                        }
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    logger.info("interrupted, giving up");
                } catch (Exception e) {
                    error.set(true);
                }
            });
            th.start();
            ths.add(th);
        }

        // let's interrupt some threads to see what happens
        Duration interruptPeriod = TEST_DURATION.dividedBy(nOfThreads/3);
        for (int i = 0; i < nOfThreads; i += 3) {
            ths.get(i).interrupt();
            Thread.sleep(interruptPeriod.toMillis());
        }

        // join them all
        long testDeadline = Timeouts.start(
                TEST_DURATION.plusSeconds(5).getSeconds(),
                TimeUnit.SECONDS);
        for (Thread th : ths) {
            long remaining = Timeouts.remaining(testDeadline);
            th.join(remaining);
        }
        return error;
    }

    @Test
    public void test_lazy() throws Exception {
        int nOfThreads = 10;
        final int[] initialValue = {0};
        AtomicBoolean result = test(new Lazy<>(() -> initialValue[0]++), nOfThreads, Optional.of(initialValue[0]),
                1000);
        assertFalse(result.get());
    }

    @Test(expected = RuntimeException.class)
    public void test_lazy_to_produce_timeout() throws Exception {
        final int[] initialValue = {3};
        int nOfThreads = 2;
        test(new Lazy<>(() -> initialValue[0]++), nOfThreads, Optional.of(initialValue[0]), 0);
    }
}

