import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class KeyedExchanger<T> {

    private static class ExchangeableData<T> {
        final int key;
        final T data;

        ExchangeableData(int key, T data){
            this.key = key;
            this.data = data;
        }
    }

    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private LinkedList<ExchangeableData> queue = new LinkedList<>();

    public Optional<T> exchange(int key, T mydata, int timeout) throws InterruptedException {

        if (queue.stream().anyMatch(e -> e.key == key)) {

        }
    }
}
