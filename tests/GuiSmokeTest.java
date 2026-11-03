import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/** Optional graphical smoke test. Requires a desktop / Xvfb. */
public final class GuiSmokeTest {
    private static Object get(Object target, String field) throws Exception {
        Field f = ThreadPoolApp.class.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
    }
    private static void invoke(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }
    public static void main(String[] args) throws Exception {
        final ThreadPoolApp[] frames = new ThreadPoolApp[1];
        invoke(() -> { frames[0] = new ThreadPoolApp(); frames[0].setVisible(true); });
        ThreadPoolApp app = frames[0];
        try {
            JButton start = (JButton) get(app, "startButton");
            JButton add = (JButton) get(app, "addButton");
            JButton reset = (JButton) get(app, "resetButton");
            invoke(start::doClick);
            ThreadPool pool = (ThreadPool) get(app, "activePool");
            if (pool == null || pool.getSnapshot().workers.size() != 3)
                throw new AssertionError("Сан үүсгэх товч 3 ажиллагч үүсгэсэнгүй");
            invoke(add::doClick);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && pool.getSnapshot().completed < 8)
                Thread.sleep(25);
            if (pool.getSnapshot().completed != 8) throw new AssertionError("UI-ээс нэмсэн 8 ажил бүрэн дууссангүй");
            invoke(reset::doClick);
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
            while (System.nanoTime() < deadline && get(app, "activePool") != null)
                Thread.sleep(25);
            if (get(app, "activePool") != null) throw new AssertionError("Шинэчлэх үйлдэл амжилтгүй");
            System.out.println("UI-ИЙН ТЕСТ ТЭНЦСЭН: сан үүсгэх, 8 бодит ажил, дуусах, шинэчлэх");
        } finally {
            invoke(app::dispose);
            System.exit(0);
        }
    }
}
