import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;

public class TestTransferQueue {

    private static final Logger logger = LoggerFactory.getLogger(SafeMessageBox.class);

    private void test(Runnable put, Supplier<Integer> consume, int nOfThreads) throws InterruptedException {

        final List<Thread> ths = new ArrayList<>();
        final AtomicBoolean error = new AtomicBoolean();
        final AtomicInteger counter = new AtomicInteger(0);
        final List<Integer> results = new LinkedList<>();

        for (int i = 0; i < nOfThreads; i++) {
            int observedCounter = counter.incrementAndGet();

            if (observedCounter % 2 == 0) {
                Thread th = new Thread(put);
                th.start();
                ths.add(th);
            } else {
                Thread th = new Thread(() -> results.add(consume.get()));
                th.start();
                ths.add(th);
            }
        }

        if (results.stream().anyMatch(Objects::isNull))  {
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
    public void testTransferQueue() throws InterruptedException {

        TransferQueue<Integer> tq = new TransferQueue<>();
        final int[] data = {0};
        final int[] nOfThreads = {20};

        test(() -> tq.put(data[0]++), () -> tq.take(100), nOfThreads[0]);

    }
}
