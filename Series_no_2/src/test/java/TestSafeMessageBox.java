import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;

public class TestSafeMessageBox {

    private static final Logger logger = LoggerFactory.getLogger(SafeMessageBox.class);
    private static final Duration TEST_DURATION = Duration.ofSeconds(60);


    private void test(Consumer<Integer> publish, Supplier<Integer> consume, int consumeRepetitions,
                      int nOfThreads) throws InterruptedException {

        final List<Thread> ths = new ArrayList<>();
        final AtomicBoolean error = new AtomicBoolean();
        final AtomicInteger counter = new AtomicInteger(0);
        final List<Integer> results = new LinkedList<>();

        for (int i = 0; i < nOfThreads; i++) {
            int observedCounter = counter.incrementAndGet();

            if (observedCounter == 1) {
                Thread th = new Thread(() -> publish.accept(2));
                th.start();
                ths.add(th);
                th.join();
                continue;
            }

            Thread th = new Thread(() -> results.add(consume.get()));
            th.start();
            ths.add(th);
        }

        if (results.stream().filter(Objects::isNull).count() != 1)  {
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

        test(message -> smb.publish(message, 10),
                smb::tryConsume, 11, 12);

    }
}
