package utils;

public class WorkerThread extends Thread {

    public boolean hasBeenCancelled = false;
    public boolean hasBeenExecuted = false;
    private final Runnable r;

    WorkerThread(Runnable r) {
        super();
        this.r = r;
    }

    public void start() {
        r.run();
    }

}
