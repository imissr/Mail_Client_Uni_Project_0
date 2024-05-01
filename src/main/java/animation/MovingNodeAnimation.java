package animation;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import util.Utility;


public class MovingNodeAnimation extends Thread{
    private volatile AtomicBoolean running = new AtomicBoolean(false);
    private final String label;

    private final int animationSpeed;
    private final int fieldSize;

    private final CyclicBarrier barrier = new CyclicBarrier(1);

    public MovingNodeAnimation(String label, int animationSpeed, int fieldSize) {
        //setDaemon(true);
        this.label = label;
        this.fieldSize = fieldSize;

        if(animationSpeed > 200) this.animationSpeed = 100;
        else this.animationSpeed = animationSpeed;
    }

    @Override
    public void run() {
        running.set(true);
        String s = "=", space = "\s";

        AtomicInteger counter = new AtomicInteger(1);
        Thread counterThread = new Thread(() -> {
            while (running.get()){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}

                counter.getAndIncrement();
            }

        });

        counterThread.start();
        outerloop:
        while(true){
            for (int i = 0; i < fieldSize; i++) {
                System.out.print("\r" + counter.get() + "s " + label + "<" + space.repeat(i) + s + space.repeat(fieldSize - 1 - i) + ">");
                try {
                    Thread.sleep(animationSpeed);
                } catch (InterruptedException ignored) {}
                if(!running.get()) break outerloop;
            }

            for (int i = fieldSize - 1; i > 0; i--) {
                System.out.print("\r" + counter.get() + "s " + label + "<" + space.repeat(i) + s + space.repeat(fieldSize - 1 - i) + ">");
                try {
                    Thread.sleep(animationSpeed);
                } catch (InterruptedException ignored) {}
                if(!running.get()) break outerloop;
            }
        }


        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException ignored) {}
    }

    public CyclicBarrier getBarrier() {
        return barrier;
    }

    public void killThread(){
        running.set(false);
    }

    public static void touchBarrierAndStopThread(MovingNodeAnimation instance){
        instance.killThread();

        try {
            instance.getBarrier().await();
        } catch (InterruptedException | BrokenBarrierException ignored) {}
        Utility.clearLine();
    }
}
