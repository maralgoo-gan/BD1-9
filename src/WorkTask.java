import java.util.concurrent.CancellationException;

/** A real Runnable. Sleep simulates I/O waiting; CPU mode computes on the CPU. */
public final class WorkTask implements ThreadPool.NamedTask {
    public enum Mode { SLEEP, CPU, FAIL }
    private final Mode mode;
    private final int durationMillis;

    public WorkTask(Mode mode, int durationMillis) {
        if (mode == null) throw new IllegalArgumentException("Ажлын төрөл сонгох шаардлагатай");
        if (durationMillis < 100 || durationMillis > 15000)
            throw new IllegalArgumentException("Ажлын хугацаа 100–15000 мс хооронд байх ёстой");
        this.mode = mode;
        this.durationMillis = durationMillis;
    }

    @Override public String displayName() {
        return (mode == Mode.SLEEP ? "Хүлээлт" : mode == Mode.CPU ? "CPU тооцоолол" : "Алдаа шалгах")
                + " / " + durationMillis + " мс";
    }

    @Override public int plannedDurationMillis() { return durationMillis; }

    @Override public void run() {
        if (mode == Mode.FAIL) throw new IllegalStateException("Алдаа шалгах зорилгоор зориуд үүсгэсэн алдаа");
        if (mode == Mode.SLEEP) {
            try {
                Thread.sleep(durationMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Зогсоох үед хүлээж байсан ажил тасалдсан");
            }
            return;
        }
        // CPU demonstration (actual computations, not fabricated progress).
        long deadline = System.nanoTime() + durationMillis * 1_000_000L;
        double accumulator = 0.5;
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted())
                throw new CancellationException("Зогсоох үед CPU ажил тасалдсан");
            for (int i = 1; i <= 4096; i++)
                accumulator = Math.sqrt(accumulator + i * 0.00001);
        }
        if (Double.isNaN(accumulator)) throw new IllegalStateException("CPU тооцооллын үр дүн буруу");
    }
}
