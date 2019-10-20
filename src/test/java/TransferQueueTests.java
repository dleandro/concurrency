import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import synchronizers.TransferQueue;
import utils.TestHelper;
import utils.Timeouts;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static org.junit.Assert.assertFalse;

public class TransferQueueTests {

    private static final Logger logger = LoggerFactory.getLogger(TransferQueueTests.class);
    private static final Duration TEST_DURATION = Duration.ofSeconds(60);

    private boolean executeFunctionAndCheckElapsed(BiFunction<Integer, Integer, Boolean> biFunc, long timeout,
                                                   int threadNo) {
        // allows for a 100ms error due to scheduling delays
        long allowedTimeError = 100;

        long start = System.currentTimeMillis();
        boolean success = biFunc.apply(TestHelper.generateRandomValue(), threadNo);
        long duration = System.currentTimeMillis() - start;
        logger.info(String.valueOf(success));
        if (duration - timeout > allowedTimeError) {
            logger.error("acquire was {} but should not exceed {}", duration, timeout);
            throw new RuntimeException("Acquire exceeded allowed time");
        }

        return success;
    }

    private void test(int nOfThreads, long timeout, BiFunction<Integer, Integer, Boolean> functionsToExecute)
            throws InterruptedException {

        final List<Thread> ths = new ArrayList<>();
        final AtomicBoolean error = new AtomicBoolean();
        final Instant deadline = Instant.now().plus(TEST_DURATION);

        for (int i = 0; i < nOfThreads; ++i) {
            final int[] threadNo = {i};
            Thread th = new Thread(() -> {
                try {
                        if (Instant.now().compareTo(deadline) > 0) {
                            return;
                        }
                        boolean success = executeFunctionAndCheckElapsed(functionsToExecute, timeout, threadNo[0]);
                        if (!success) {
                            logger.error("message wasn't transferred successfully");
                            error.set(true);
                            return;
                        }
                        Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.info("interrupted, giving up");
                } catch (RuntimeException e) {
                    error.set(true);
                }
            });
            th.start();
            ths.add(th);
        }

/*
        // let's interrupt some threads to see what happens
        Duration interruptPeriod = TEST_DURATION.dividedBy(nOfThreads/3);
        for (int i = 0; i < nOfThreads; i += 3) {
            ths.get(i).interrupt();
            Thread.sleep(interruptPeriod.toMillis());
        }
*/
        // join then all
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
    public void testTransferAndTake() throws InterruptedException {
        int nOfThreads = 4;
        long timeout = 1000;
        TransferQueue<Object> objectTransferQueue = new TransferQueue<>();
        test(nOfThreads, timeout, (message, threadNo) -> {
            try {
                if (threadNo % 2 == 0) {
                    return objectTransferQueue.transfer(message, timeout);
                } else {
                    return objectTransferQueue.take((int) timeout) != null;
                }
            } catch (InterruptedException e) {
                logger.info("interrupted, giving up");
            }
            return false;
        });
    }

    @Test
    public void testPutAndTake() throws InterruptedException {
        int nOfThreads = 2;
        int timeout = 1000;

        TransferQueue<Object> objectTransferQueue = new TransferQueue<>();
        test(nOfThreads, timeout, (message, threadNo) -> {
            try {
                if (threadNo % 2 == 0) {
                    objectTransferQueue.put(message);
                    return true;
                } else {
                    return objectTransferQueue.take(timeout) != null;
                }
            } catch (InterruptedException e) {
                logger.info("interrupted, giving up");
            }
            return false;
        });
    }

    @Test
    public void testTransferPutAndTakeInThatOrder() throws InterruptedException {
        int nOfThreads = 6;
        long timeout = 1000;
        TransferQueue<Object> objectTransferQueue = new TransferQueue<>();
        test(nOfThreads, timeout, (message, threadNo) -> {
            try {
                if (threadNo == 2) {
                    objectTransferQueue.put(message);
                    return true;
                }

                if (threadNo == 3) {
                    return objectTransferQueue.take((int) timeout) != null;
                }

                if (threadNo % 2 == 0) {
                    return objectTransferQueue.transfer(message, timeout);
                } else {
                    return objectTransferQueue.take((int) timeout) != null;
                }
            } catch (InterruptedException e) {
                logger.info("interrupted, giving up");
            }
            return false;
        });
    }

    @Test
    public void testTakeAndTransfer() throws InterruptedException {
        int nOfThreads = 4;
        long timeout = 1000;
        TransferQueue<Object> objectTransferQueue = new TransferQueue<>();
        test(nOfThreads, timeout, (message, threadNo) -> {
            try {
                if (threadNo % 2 == 0) {
                    return objectTransferQueue.take((int) timeout) != null;
                } else {
                    return objectTransferQueue.transfer(message, timeout);
                }
            } catch (InterruptedException e) {
                logger.info("interrupted, giving up");
            }
            return false;
        });
    }


}

