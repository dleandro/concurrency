import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.stream.IntStream;

public class LazyTests {
    public Callable prov = new Callable() {
        private int i = 2;
        @Override
        public Object call() throws Exception {
            return i++;
        }
    };
    Lazy lazy = new Lazy(prov);

    private void run(){
        try {
            System.out.println(lazy.get(1000L));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void joiner(Thread t){
        try {
            t.join();
        } catch (InterruptedException e) {
            System.out.println("thread interrompida: "+t.getName());
        }
    }


    @Test
    public void multiple_thread_example() {
        IntStream
                .range(1,5000)
                .mapToObj(t->{
                    Thread th = new Thread(this::run);
                    th.start();
                    return th;
                })
                .forEach(this::joiner);

    }

}

