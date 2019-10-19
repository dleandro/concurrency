import utils.TestHelper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.NodeLinkedList;
import utils.Timeouts;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.junit.Assert.assertFalse;

public class KeyedExchangerTests {

    private static final Logger logger = LoggerFactory.getLogger(KeyedExchangerTests.class);
    private static final Duration TEST_DURATION = Duration.ofSeconds(60);

    private Optional<Integer> exchangeAndCheckElapsed(KeyedExchanger<Integer> keyedExchanger,
                                                      int key, int data)
            throws InterruptedException {
        Optional<Integer> exchangedValue;
        // allows for a 100ms error due to scheduling delays
        long allowedTimeError = 100;
        long timeout = 20000;

        long start = System.currentTimeMillis();
        exchangedValue = keyedExchanger.exchange(key, data, (int) timeout);
        long duration = System.currentTimeMillis() - start;

        if (duration - timeout > allowedTimeError) {
            logger.info("exchange took {} and should not exceed {}", duration, timeout);
            if (!exchangedValue.isPresent()) {
                throw new RuntimeException("exchange exceeded allowed time");
            }
        }

        return exchangedValue;
    }

    private void test(KeyedExchanger<Integer> keyedExchanger, int nOfThreads) throws InterruptedException{

        final List<Thread> ths = new ArrayList<>();
        final AtomicBoolean error = new AtomicBoolean();
        final Instant deadline = Instant.now().plus(TEST_DURATION);
        NodeLinkedList<ThreadInfo> threadInfoInitialValues = new NodeLinkedList<>();

        for (int i = 0; i < nOfThreads; ++i) {
            final int nextIntKey = TestHelper.generateIntKeys();
            Thread th = new Thread(() -> {
                try {
                    if (Instant.now().compareTo(deadline) > 0) {
                        return;
                    }

                    NodeLinkedList.Node<ThreadInfo> currentThreadNode = threadInfoInitialValues
                            .push(new ThreadInfo(nextIntKey, TestHelper.generateRandomValue()));

                    Optional<Integer> exchangedValue = exchangeAndCheckElapsed(keyedExchanger,
                            currentThreadNode.value.key, currentThreadNode.value.data);

                    if (!checkIfValuesWereExchanged(threadInfoInitialValues, exchangedValue, currentThreadNode)) {
                        logger.error("Thread didn't receive the pair thread's value");
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

    // if more than two threads have the same key synchronizer doesn't choose who gets the values
    private boolean checkIfValuesWereExchanged(NodeLinkedList<ThreadInfo> threadInfoInitialValues,
                                               Optional<Integer> exchangedValue,
                                               NodeLinkedList.Node<ThreadInfo> currentThreadNode) {

        Predicate<ThreadInfo> isPairThreadsNode = pairThreadInfo -> pairThreadInfo.key == currentThreadNode.value.key;

        NodeLinkedList.Node<ThreadInfo> pairThreadNode = threadInfoInitialValues
                .searchNodeAndReturn(isPairThreadsNode, currentThreadNode, threadInfoInitialValues.getHeadNode());

        return exchangedValue.isPresent() && pairThreadNode.value != null || exchangedValue.equals(Optional.empty());
    }

    @Test
    public void test_keyed_exchanger() throws InterruptedException {
        int nOfThreads = 30;
        test(new KeyedExchanger<>(), nOfThreads);
    }

    private static class ThreadInfo {

        private final int data;
        private final int key;

        ThreadInfo(int key, int data) {
            this.data = data;
            this.key = key;
        }
    }
}
