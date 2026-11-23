import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** BD2 peer-review regression for P4 (complete timeline), P8 (listener lock), FIFO and race. */
public final class BD2RegressionTests {
    private static int passed;
    private static void check(boolean b, String error) { if (!b) throw new AssertionError(error); }
    private static void pass(String label) { passed++; System.out.println("BD2 ТЭНЦСЭН: " + label); }
    private static void waitFor(ThreadPool p, int expected, long timeout) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            ThreadPool.Snapshot s = p.getSnapshot();
            if (s.completed + s.failed + s.cancelled == expected) return;
            Thread.sleep(8);
        }
        throw new AssertionError("Ажил дууссангүй: " + p.getSnapshot().completed + "/" + expected);
    }
    private static boolean rgb(int value, Color c) {
        return (value & 0xFFFFFF) == (c.getRGB() & 0xFFFFFF);
    }
    private static void timelineAfterLogTrim() throws Exception {
        ThreadPool p = new ThreadPool(1, 5000);
        p.add(new WorkTask(WorkTask.Mode.SLEEP, 350)); // distinguishable early green segment
        for (int i = 0; i < 3000; i++) p.add(() -> { });
        waitFor(p, 3001, 20000);
        p.shutdown();
        check(p.awaitTermination(5000), "Том ачааллын дараа урсгал зогссонгүй");
        ThreadPool.Snapshot s = p.getSnapshot();
        check(s.events.size() == 4000, "Recent log-ийн хязгаар 4000 байх ёстой");
        ThreadPool.TaskView first = s.tasks.get(0);
        check(first.startMillis >= 0 && first.finishMillis - first.startMillis >= 290,
            "Эхний ажил 350 мс орчим үргэлжлээгүй");
        check(s.events.get(0).timeMillis >= first.finishMillis,
            "Тест эхний үйл явдлыг log-оос хасагдах хэмжээнд хүрээгүй");
        check(s.workers.get(0).stoppedMillis >= 0, "STOPPED marker must survive log trimming");
        ThreadPoolApp.TimelinePanel panel = new ThreadPoolApp.TimelinePanel();
        panel.setSize(760, 210);
        panel.setSnapshot(s);
        BufferedImage image = new BufferedImage(760, 210, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g); g.dispose();
        long maxMs = Math.max(8000, ((ThreadPoolApp.TimelinePanel.displayTimeMillis(s) + 1999) / 2000) * 2000);
        long time = first.startMillis + 100;
        int x = 52 + (int)(691 * time / (double)maxMs);
        check(rgb(image.getRGB(x, 19 + 15), new Color(17, 150, 103)),
            "P4: Эхний 100 мс орчимд бодит RUNNING байсан ч саарал/сул зурж байна");
        pass("P4: 3001 ажлын дараа эхний түүх timeline-д хадгалагдсан, log эргэлдсэн");
    }
    private static void listenerDoesNotHoldLock() throws Exception {
        ThreadPool p = new ThreadPool(1, 100);
        CountDownLatch insideListener = new CountDownLatch(1);
        Thread installer = new Thread(() -> p.setListener(s -> {
            insideListener.countDown();
            try { Thread.sleep(400); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        installer.start();
        check(insideListener.await(2, TimeUnit.SECONDS), "Listener эхэлсэнгүй");
        long start = System.nanoTime();
        p.add(() -> { });
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        check(elapsedMs < 250, "P8: Удаан listener pool lock барьж байна: " + elapsedMs + " мс");
        installer.join(1500);
        waitFor(p, 1, 3000);
        p.shutdown(); check(p.awaitTermination(3000), "Listener test shutdown failed");
        pass("P8: 400 мс listener байхад submit lock-гүй хурдан ажиллав (" + elapsedMs + " мс)");
    }
    private static void concurrentProducers() throws Exception {
        ThreadPool p = new ThreadPool(4, 1000);
        AtomicInteger executed = new AtomicInteger();
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> producers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                for (int j = 0; j < 200; j++) {
                    try { p.add(executed::incrementAndGet); accepted.incrementAndGet(); }
                    catch (java.util.concurrent.RejectedExecutionException ex) { rejected.incrementAndGet(); }
                }
            });
            t.start(); producers.add(t);
        }
        check(ready.await(3, TimeUnit.SECONDS), "Producer threads not ready");
        start.countDown();
        for (Thread t : producers) t.join(5000);
        check(accepted.get() + rejected.get() == 1600, "Accepted + rejected != 1600");
        waitFor(p, accepted.get(), 10000);
        ThreadPool.Snapshot snap = p.getSnapshot();
        check(snap.completed == accepted.get() && executed.get() == accepted.get(), "Lost or duplicate task");
        Set<Integer> ids = new HashSet<>();
        for (ThreadPool.TaskView task : snap.tasks)
            check(ids.add(task.id), "ID давхардсан");
        p.shutdown(); check(p.awaitTermination(5000), "Producer test shutdown failed");
        pass("P9: 8 producer × 200 add, duplicate/lost 0, capacity respected");
    }
    private static void fifoAndRepeatedLifecycle() throws Exception {
        ThreadPool p = new ThreadPool(1, 100);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 30; i++) {
            final int number = i;
            p.add(() -> order.add(number));
        }
        waitFor(p, 30, 3000);
        for (int i = 0; i < 30; i++) check(order.get(i) == i, "FIFO зөрсөн: " + i);
        p.shutdown(); check(p.awaitTermination(3000), "FIFO cleanup failed");
        for (int k = 0; k < 30; k++) {
            ThreadPool next = new ThreadPool(2, 20);
            next.shutdown(); check(next.awaitTermination(3000), "Lifecycle loop leak: " + k);
        }
        pass("P9: FIFO 30 ажил, давтан үүсгэх/зогсоох 30 удаа");
    }
    private static void snapshotInvariant() throws Exception {
        ThreadPool p = new ThreadPool(4, 4000);
        for (int i=0;i<2000;i++) p.add(() -> { });
        for (int i=0;i<100;i++) {
            ThreadPool.Snapshot s = p.getSnapshot();
            check(s.submitted == s.queued + s.running + s.completed + s.failed + s.cancelled,
                "Snapshot invariants violated");
        }
        waitFor(p,2000,10000);
        p.shutdown(); check(p.awaitTermination(3000), "Invariant shutdown failed");
        pass("P9: 2000 ажлын concurrent snapshot 100 удаа invariants зөв");
    }
    public static void main(String[] args) throws Exception {
        timelineAfterLogTrim();
        listenerDoesNotHoldLock();
        concurrentProducers();
        fifoAndRepeatedLifecycle();
        snapshotInvariant();
        System.out.println("БД2-ЫН БҮХ " + passed + " НЭМЭЛТ ТЕСТ ТЭНЦЛЭЭ");
    }
}
