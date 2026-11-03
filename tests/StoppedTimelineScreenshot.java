import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

/** Screenshot showing completed tasks and a stopped pool with only thin red markers. */
public final class StoppedTimelineScreenshot {
    private static Object control(ThreadPoolApp app, String name) throws Exception {
        Field field = ThreadPoolApp.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(app);
    }
    public static void main(String[] args) throws Exception {
        final ThreadPoolApp[] ref = new ThreadPoolApp[1];
        SwingUtilities.invokeAndWait(() -> {
            ref[0] = new ThreadPoolApp();
            ref[0].setVisible(true);
        });
        ThreadPoolApp app = ref[0];
        try {
            JSpinner duration = (JSpinner) control(app, "durationSpinner");
            JButton start = (JButton) control(app, "startButton");
            JButton add = (JButton) control(app, "addButton");
            JButton stop = (JButton) control(app, "stopButton");
            SwingUtilities.invokeAndWait(() -> duration.setValue(600));
            SwingUtilities.invokeAndWait(start::doClick);
            ThreadPool pool = (ThreadPool) control(app, "activePool");
            SwingUtilities.invokeAndWait(add::doClick);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && pool.getSnapshot().completed != 8)
                Thread.sleep(20);
            if (pool.getSnapshot().completed != 8) throw new AssertionError("8 ажил дууссангүй");
            SwingUtilities.invokeAndWait(stop::doClick);
            if (!pool.awaitTermination(3000)) throw new AssertionError("Зогсоох амжилтгүй");
            Thread.sleep(1800); // post-stop behavior must remain frozen
            Rectangle bounds = app.getBounds();
            BufferedImage image = new Robot().createScreenCapture(bounds);
            File file = new File("docs/screenshots/worker-activity-stopped-fixed.png");
            ImageIO.write(image, "png", file);
            System.out.println("Зассан timeline-ийн бодит screenshot: " + file.getAbsolutePath());
        } finally {
            SwingUtilities.invokeAndWait(app::dispose);
            System.exit(0);
        }
    }
}
