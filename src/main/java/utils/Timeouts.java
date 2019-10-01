package utils;

import java.util.concurrent.TimeUnit;

// Using static methods and not instance ones to avoid allocation
public class Timeouts {

    public static boolean noWait(long timeout) {
        return timeout == 0;
    }

    public static long start(long duration, TimeUnit timeUnit) {
        return start(timeUnit.toMillis(duration));
    }

    public static long start(long timeout) {
        return System.currentTimeMillis() + timeout;
    }

    public static long remaining(long target) {
        return target - System.currentTimeMillis();
    }

    public static boolean isTimeout(long remaining) {
        return remaining <= 0;
    }

}

