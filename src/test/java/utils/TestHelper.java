package utils;

import java.util.LinkedList;
import java.util.Random;

public class TestHelper {

    private static LinkedList<Integer> keys = new LinkedList<>();

    public static int generateIntKeys() {
        Random rNumber = new Random();
        int nextKey = rNumber.nextInt(30);

        if (keys.isEmpty()) {
            keys.add(nextKey);
            return nextKey;
        }

        nextKey = keys.getFirst();
        keys.remove(0);
        return nextKey;
    }

    public static int generateRandomValue() {
        Random r = new Random();

        return r.nextInt(100);
    }
}
