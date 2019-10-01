import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Lazy<E> {

    private E value;
    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();

    public Lazy(Callable<E> provider) {
        try {
            value = provider.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Optional<E> get(long timeout) {
        monitor.lock();

        try {
            while (value != null) {
                new Lazy<E>();
                condition.await(timeout, TimeUnit.MILLISECONDS);
                //call provider after leaving monitor.....
                //return E
            }

            return Optional.ofNullable(value);
        } catch (InterruptedException e) {

        }

    }

}
