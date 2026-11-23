import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
/** Instrumented P4 visual probe, compatible with BD1 and BD2 source. */
public final class TimelineBeforeAfterProbe {
    public static void main(String[] args) throws Exception {
        ThreadPool p = new ThreadPool(1, 5000);
        p.add(new WorkTask(WorkTask.Mode.SLEEP, 350));
        for (int i = 0; i < 3000; i++) p.add(() -> { });
        long deadline = System.nanoTime()+20_000_000_000L;
        while (System.nanoTime()<deadline && p.getSnapshot().completed<3001) Thread.sleep(10);
        p.shutdown();
        if (!p.awaitTermination(5000)) throw new AssertionError("shutdown timeout");
        ThreadPool.Snapshot s = p.getSnapshot();
        if (s.completed != 3001) throw new AssertionError("incomplete workload");
        ThreadPoolApp.TimelinePanel panel = new ThreadPoolApp.TimelinePanel();
        panel.setSize(760,210); panel.setSnapshot(s);
        BufferedImage image = new BufferedImage(760,210,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics(); panel.paint(g); g.dispose();
        long elapsed=ThreadPoolApp.TimelinePanel.displayTimeMillis(s);
        long max=Math.max(8000, ((elapsed+1999)/2000)*2000);
        long t=s.tasks.get(0).startMillis+100;
        int x=52+(int)(691*t/(double)max);
        boolean actualRunning=(image.getRGB(x,34)&0xFFFFFF)==(new Color(17,150,103).getRGB()&0xFFFFFF);
        System.out.println("P4 probe | completed="+s.completed+", retainedEvents="+s.events.size()+", firstStartMs="+
            s.tasks.get(0).startMillis+", firstFinishMs="+s.tasks.get(0).finishMillis+
            ", oldestRetainedEventMs="+s.events.get(0).timeMillis+", earlyActivityShown="+actualRunning);
    }
}
