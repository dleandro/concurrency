import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class KeyedExchanger<T> {

    private static class Request {
        final Condition condition;
        boolean isDone = false;
        Request(Lock monitor){
            condition = monitor.newCondition();
        }
    }

    public Optional<T> exchange(int key, T mydata, int timeout) throws InterruptedException {

    }
}
