import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs without JUnit or a graphical display. */
public final class PoolTests {
    private static int passed = 0;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void pass(String title) { passed++; System.out.println("ТЭНЦСЭН: " + title); }
    private static void untilDone(ThreadPool p, int expected, long timeoutMs) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < end) {
            if (p.getSnapshot().completed >= expected) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Хугацаанд нь " + expected + " ажил дууссангүй");
    }
    public static void main(String[] args) throws Exception {
        // 1: Constructor and workers initially idle.
        ThreadPool p = new ThreadPool(3, 20);
        check(p.getSnapshot().workers.size() == 3, "Ажиллагчдын тоо буруу");
        for (ThreadPool.WorkerView w : p.getSnapshot().workers)
            check(w.state == ThreadPool.WorkerState.IDLE, "Ажиллагч сул төлөвтэй байх ёстой");
        p.shutdown(); check(p.awaitTermination(2000), "Ажиллагч урсгалууд зогссонгүй");
        pass("Сан үүсгэх, сул төлөв");

        // 2: Concurrent task execution: all workers enter run before release.
        p = new ThreadPool(3, 10);
        CountDownLatch entered = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        for (int i = 0; i < 3; i++) p.add(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            executed.incrementAndGet();
        });
        check(entered.await(2, TimeUnit.SECONDS), "Гурван ажиллагч зэрэг ажилласангүй");
        check(p.getSnapshot().running == 3, "Ажиллаж буй ажлын тоо 3 байх ёстой");
        release.countDown(); untilDone(p, 3, 2000);
        check(executed.get() == 3, "Нэг ажил алдагдсан");
        p.shutdown(); check(p.awaitTermination(2000), "Ажлууд дууссаны дараа зогсоож чадсангүй");
        pass("Гурван бодит урсгалын зэрэгцээ ажиллагаа");

        // 3: Bounded queue rejection and FIFO dequeue.
        p = new ThreadPool(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        p.add(() -> { started.countDown(); try { proceed.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        check(started.await(2, TimeUnit.SECONDS), "Завгүй ажиллагч ажиллаж эхэлсэнгүй");
        p.add(() -> { });
        check(p.getSnapshot().queued == 1, "Нэг ажил queue-д хүлээх ёстой");
        boolean rejected = false;
        try { p.add(() -> { }); } catch (RejectedExecutionException expected) { rejected = true; }
        check(rejected, "Дүүрсэн дараалал дараагийн ажлыг татгалзах ёстой");
        proceed.countDown(); untilDone(p, 2, 2000);
        p.shutdown(); check(p.awaitTermination(2000), "Дарааллын тестийн ажиллагч зогссонгүй");
        pass("Дарааллын багтаамж ба илүүдэл ажлыг татгалзах");

        // 4: Exactly once under load, task IDs unique.
        p = new ThreadPool(5, 2000);
        final int n = 500;
        AtomicInteger hit = new AtomicInteger();
        for (int i = 0; i < n; i++) p.add(hit::incrementAndGet);
        untilDone(p, n, 10000);
        check(hit.get() == n, "Ажил алдагдсан эсвэл давхардсан");
        Set<Integer> ids = new HashSet<>();
        for (ThreadPool.TaskView t : p.getSnapshot().tasks) check(ids.add(t.id), "Ажлын ID давхардсан");
        check(p.getSnapshot().queued == 0 && p.getSnapshot().running == 0, "Дуусаагүй ажил үлдсэн");
        p.shutdown(); check(p.awaitTermination(3000), "Олон ажлын тестийн урсгалууд зогссонгүй");
        pass("500 ажлыг тус бүр нэг удаа гүйцэтгэх, давтагдаагүй ID");

        // 5: Failed task doesn't kill the worker.
        p = new ThreadPool(1, 10);
        AtomicInteger afterError = new AtomicInteger();
        p.add(() -> { throw new RuntimeException("intentional"); });
        p.add(afterError::incrementAndGet);
        untilDone(p, 1, 2000);
        check(p.getSnapshot().failed == 1 && afterError.get() == 1, "Алдааны дараах ажиллагаа буруу");
        p.shutdown(); check(p.awaitTermination(2000), "Алдаатай ажлын тест зогссонгүй");
        pass("Алдааны дараах ажиллагаа");

        // 6: Shutdown interrupts a running task and cancels queued work.
        p = new ThreadPool(1, 10);
        CountDownLatch running = new CountDownLatch(1);
        p.add(() -> { running.countDown(); try { Thread.sleep(10000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        check(running.await(2, TimeUnit.SECONDS), "Удаан ажиллах ажил эхэлсэнгүй");
        p.add(() -> { throw new AssertionError("Энэ хүлээж буй ажил гүйцэтгэгдэх ёсгүй"); });
        p.shutdown(); check(p.awaitTermination(2000), "Зогсоох үед ажил тасалдсангүй");
        check(p.getSnapshot().cancelled == 2, "Ажиллаж болон хүлээж буй ажил цуцлагдах ёстой");
        check(p.getSnapshot().terminated, "Сан дууссан гэж тайлагнах ёстой");
        pass("Зогсоох, interrupt, дарааллын ажлыг цуцлах");

        // 7: Invalid arguments and submission after shutdown.
        boolean invalid = false;
        try { new ThreadPool(0); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "0 ажиллагчтай санг хориглох ёстой");
        invalid = false;
        try { new WorkTask(WorkTask.Mode.SLEEP, -1); }
        catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "Сөрөг хугацааг хориглох ёстой");
        invalid = false;
        try { p.add(() -> { }); } catch (RejectedExecutionException expected) { invalid = true; }
        check(invalid, "Зогссоны дараа ажил нэмэхийг хориглох ёстой");
        pass("Буруу оролт, зогссон санд ажил нэмэхийг хориглох");

        System.out.println("БҮХ " + passed + " БҮЛГИЙН ТЕСТ ТЭНЦЛЭЭ");
    }
}
