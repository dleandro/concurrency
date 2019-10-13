import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Test;
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
    private static final Duration TEST_DURATION = Duration.ofSeconds(60);

    private Optional calculateAndCheckElapsed(Lazy lazy, long timeout)
            throws Exception {
        Optional calculatedValue;
        // allows for a 100ms error due to scheduling delays
        long allowedTimeError = 100;

        do {
            long start = System.currentTimeMillis();
            calculatedValue = lazy.get(timeout);
            long duration = System.currentTimeMillis() - start;

            if (Math.abs(duration - timeout) > allowedTimeError) {
                logger.info("get took {} and should not exceed {}", duration, timeout);
                if (!calculatedValue.isPresent()) {
                    throw new RuntimeException("Acquire exceeded allowed time");
                }
            }

        } while (!calculatedValue.isPresent());

        return calculatedValue;
    }

    public void test(Lazy lazy, int nOfThreads, Optional initialValue) throws Exception {

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
                        Optional calculatedValue = calculateAndCheckElapsed(lazy, 1000);
                        if (!calculatedValue.get().equals(initialValue.get())) {
                            logger.error("More than one thread called callable");
                            error.set(true);
                            return;
                        }
                        logger.info("succeeded");
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    logger.info("interruped, giving up");
                } catch (Exception e) {
                    error.set(false);
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
            if (th.isAlive()) {
                logger.error("Test didn't stop when it was supposed to");
            }
        }
        assertFalse(error.get());
    }

    @Test
    public void test_lazy() throws Exception {
        int nOfThreads = 100;
        final int[] initialValue = {0};
        test(new Lazy<>(() -> initialValue[0]++), nOfThreads, Optional.of(0));
    }

    @Test(expected = Exception.class)
    public void test_lazy_with_exception_on_callable() throws Exception {
        calculateAndCheckElapsed(new Lazy<>(() -> {
            throw new Exception();
        }), 1000);
    }

    @Test(expected = RuntimeException.class)
    public void test_lazy_with_no_timeout() throws Exception {
        final int[] initialValue = {3};
        calculateAndCheckElapsed(new Lazy<>(() -> initialValue[0]++), 0);
    }
}

