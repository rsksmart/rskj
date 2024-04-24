package co.rsk.util;

import java.util.concurrent.CountDownLatch;

public class ThreadTimerHelper {

    public static void waitForSeconds(int amountOfSeconds) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                Thread.sleep(amountOfSeconds * 1000);
                latch.countDown();
            } catch (InterruptedException e) {
                System.out.println("Some error happened: " + e.getLocalizedMessage());
            }
        }).start();

        latch.await();
    }
}
