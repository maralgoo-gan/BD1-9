import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

/** Regression: stopped workers are an instant marker, not a growing red duration. */
public final class TimelineTests {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static BufferedImage draw(ThreadPool.Snapshot snapshot) {
        ThreadPoolApp.TimelinePanel panel = new ThreadPoolApp.TimelinePanel();
        panel.setSize(760, 210);
        panel.setSnapshot(snapshot);
        BufferedImage img = new BufferedImage(760, 210, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = img.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return img;
    }
    private static boolean colorMatches(int rgb, Color color) {
        return (rgb & 0x00FFFFFF) == (color.getRGB() & 0x00FFFFFF);
    }
    private static void waitCompleted(ThreadPool p, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline && p.getSnapshot().completed < expected) Thread.sleep(10);
        check(p.getSnapshot().completed == expected, "Туршилтын ажил хугацаандаа дууссангүй");
    }
    public static void main(String[] args) throws Exception {
        ThreadPool pool = new ThreadPool(2, 10);
        pool.add(new WorkTask(WorkTask.Mode.SLEEP, 400));
        pool.add(new WorkTask(WorkTask.Mode.SLEEP, 400));
        waitCompleted(pool, 2);
        pool.shutdown();
        check(pool.awaitTermination(3000), "Урсгалууд зогссонгүй");
        ThreadPool.Snapshot first = pool.getSnapshot();
        check(first.terminated, "STOPPED төлөв батлагдаагүй");
        long frozen = ThreadPoolApp.TimelinePanel.displayTimeMillis(first);
        Thread.sleep(500);
        ThreadPool.Snapshot later = pool.getSnapshot();
        check(later.elapsedMillis > first.elapsedMillis,
              "Тестийн бодит цаг урагшилсан байх ёстой");
        check(ThreadPoolApp.TimelinePanel.displayTimeMillis(later) == frozen,
              "Сан зогссоны дараа timeline-ийн хугацаа өсөх ёсгүй");
        BufferedImage before = draw(first);
        BufferedImage after = draw(later);
        int bluePixels = 0, redPixels = 0;
        for (int y = 0; y < before.getHeight(); y++) {
            for (int x = 0; x < before.getWidth(); x++) {
                check(before.getRGB(x, y) == after.getRGB(x, y),
                      "Зогссон timeline цааш өөрчлөгдөж байна (x=" + x + ", y=" + y + ")");
                if (colorMatches(before.getRGB(x,y), new Color(45, 99, 215))) bluePixels++;
                if (colorMatches(before.getRGB(x,y), new Color(207, 65, 75))) redPixels++;
            }
        }
        check(bluePixels > 0, "Цэнхэр дууссан тэмдэг харагдахгүй байна");
        check(redPixels > 0, "Улаан зогссон тэмдэг харагдахгүй байна");
        for (int w = 1; w <= 2; w++) {
            long stopped = -1;
            for (ThreadPool.Event e : later.events)
                if (e.workerId == w && "STOPPED".equals(e.kind)) stopped = e.timeMillis;
            check(stopped >= 0, "W" + w + " STOPPED event олдсонгүй");
            long maxMs = Math.max(8000L, ((frozen + 1999L) / 2000L) * 2000L);
            int stopX = 52 + (int)(691 * Math.min(maxMs, stopped) / (double)maxMs);
            int yStart = 19 + (w - 1) * 30;
            for (int x = stopX + 10; x < 743; x++) {
                for (int y = yStart + 3; y <= yStart + 24; y++) {
                    check(!colorMatches(after.getRGB(x, y), new Color(207, 65, 75)),
                          "W" + w + "-ийн STOPPED төлөв улаанаар сунгагдсан байна");
                }
            }
        }
        System.out.println("ТЭНЦСЭН: Timeline зогссоны дараа царцана, улаан суналтгүй, цэнхэр дууссан тэмдэгтэй");
    }
}
