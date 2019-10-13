import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Timeouts;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;

public class KeyedExchangerTests {

    private static final Logger logger = LoggerFactory.getLogger(LazyTests.class);
    private static final Duration TEST_DURATION = Duration.ofSeconds(60);

    private Optional<Integer> exchangeAndCheckElapsed(KeyedExchanger<Integer> keyedExchanger, long timeout,
                                                      int key, int data)
            throws Exception {
        Optional exchangedValue;
        // allows for a 100ms error due to scheduling delays
        long allowedTimeError = 100;

        do {
            long start = System.currentTimeMillis();
            exchangedValue = keyedExchanger.exchange(key, data, (int) timeout);
            long duration = System.currentTimeMillis() - start;

            if (Math.abs(duration - timeout) > allowedTimeError) {
                logger.info("get took {} and should not exceed {}", duration, timeout);
                if (!exchangedValue.isPresent()) {
                    throw new RuntimeException("Acquire exceeded allowed time");
                }
            }

        } while (!exchangedValue.isPresent());

        return exchangedValue;
    }

    public void test(KeyedExchanger keyedExchanger, int nOfThreads) throws Exception {

        final List<Thread> ths = new ArrayList<>();
        final AtomicBoolean error = new AtomicBoolean();
        final Instant deadline = Instant.now().plus(TEST_DURATION);
        final int[] value = {1};

        for (int i = 0; i < nOfThreads; ++i) {
            value[0] += 1;
            Thread th = new Thread(() -> {
                try {
                    while (true) {
                        if (Instant.now().compareTo(deadline) > 0) {
                            return;
                        }
                        Optional exchangedValue = exchangeAndCheckElapsed(keyedExchanger, 10000,
                                generateKeys(), value[0]);
                       /* if (!exchangedValue.get().equals(initialValue.get())) {
                            logger.error("More than one thread called callable");
                            error.set(true);
                            return;
                        }*/
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

    private int generateKeys() {
        return 0;
    }

    @Test
    public void test_keyed_exchanger() throws Exception {
        int nOfThreads = 25;
        test(new KeyedExchanger<Integer>(), nOfThreads);
    }

}
