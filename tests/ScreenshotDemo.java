import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

/** Optional: captures a demonstrative screenshot under a graphical desktop. */
public final class ScreenshotDemo {
    private static JButton button(ThreadPoolApp a, String f) throws Exception {
        Field field = ThreadPoolApp.class.getDeclaredField(f);
        field.setAccessible(true);
        return (JButton) field.get(a);
    }
    public static void main(String[] args) throws Exception {
        ThreadPoolApp[] instance = new ThreadPoolApp[1];
        SwingUtilities.invokeAndWait(() -> { instance[0] = new ThreadPoolApp(); instance[0].setVisible(true); });
        ThreadPoolApp app = instance[0];
        SwingUtilities.invokeAndWait(button(app, "startButton")::doClick);
        SwingUtilities.invokeAndWait(button(app, "addButton")::doClick);
        Thread.sleep(950);
        Rectangle bounds = app.getBounds();
        BufferedImage image = new Robot().createScreenCapture(bounds);
        File file = new File("docs/screenshots/worker-activity-demo.png");
        ImageIO.write(image, "png", file);
        System.out.println("Ажиллаж буй үеийн зураг хадгаллаа: " + file.getAbsolutePath());
        Thread.sleep(5800);
        BufferedImage finished = new Robot().createScreenCapture(bounds);
        File finishedFile = new File("docs/screenshots/worker-activity-completed.png");
        ImageIO.write(finished, "png", finishedFile);
        System.out.println("Дууссан үеийн зураг хадгаллаа: " + finishedFile.getAbsolutePath());
        SwingUtilities.invokeAndWait(app::dispose);
        System.exit(0);
    }
}
