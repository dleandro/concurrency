package utils;

public class ThreadFactory {




    public static WorkerThread newWorkerThread(Runnable r) {
        return new WorkerThread(r);
    }

}
