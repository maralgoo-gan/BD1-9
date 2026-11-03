import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A custom educational thread pool based on Operating System Concepts (10e),
 * Chapter 7, Programming Project 1. Deliberately does not use ExecutorService.
 *
 * The task queue, worker states, and task transitions are guarded by ONE lock.
 * Listener callbacks receive immutable snapshots; the Swing listener posts
 * those snapshots to the Event Dispatch Thread.
 */
public final class ThreadPool {
    public enum TaskState { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }
    public enum WorkerState { IDLE, RUNNING, STOPPED }

    public interface NamedTask extends Runnable {
        String displayName();
        int plannedDurationMillis();
    }

    @FunctionalInterface
    public interface Listener {
        void onUpdate(Snapshot snapshot);
    }

    public static final class TaskView {
        public final int id;
        public final String label;
        public final TaskState state;
        public final int workerId;
        public final int durationMillis;
        public final long startMillis;
        public final long finishMillis;
        public final String error;
        private TaskView(Job j) {
            id = j.id; label = j.label; state = j.state;
            workerId = j.workerId; durationMillis = j.durationMillis;
            startMillis = j.startMillis; finishMillis = j.finishMillis;
            error = j.error;
        }
    }

    public static final class WorkerView {
        public final int id;
        public final WorkerState state;
        public final int currentTaskId;
        public final int completedTasks;
        public final String lastResult;
        private WorkerView(int id, WorkerState state, int taskId, int count, String lastResult) {
            this.id = id; this.state = state; this.currentTaskId = taskId;
            this.completedTasks = count; this.lastResult = lastResult;
        }
    }

    public static final class Event {
        public final long timeMillis;
        public final int workerId;
        public final int taskId;
        public final String kind;
        public final String detail;
        private Event(long at, int worker, int task, String kind, String detail) {
            this.timeMillis = at; this.workerId = worker; this.taskId = task;
            this.kind = kind; this.detail = detail;
        }
    }

    public static final class Snapshot {
        public final List<TaskView> tasks;
        public final List<WorkerView> workers;
        public final List<Event> events;
        public final List<Integer> queueTaskIds;
        public final int queueCapacity;
        public final int submitted, queued, running, completed, failed, cancelled;
        public final boolean shuttingDown, terminated;
        public final long elapsedMillis;
        private Snapshot(List<TaskView> tasks, List<WorkerView> workers,
                         List<Event> events, List<Integer> queueIds,
                         int cap, int submitted, int queued, int running,
                         int completed, int failed, int cancelled,
                         boolean shuttingDown, boolean terminated, long elapsedMillis) {
            this.tasks = Collections.unmodifiableList(tasks);
            this.workers = Collections.unmodifiableList(workers);
            this.events = Collections.unmodifiableList(events);
            this.queueTaskIds = Collections.unmodifiableList(queueIds);
            this.queueCapacity = cap;
            this.submitted = submitted; this.queued = queued; this.running = running;
            this.completed = completed; this.failed = failed; this.cancelled = cancelled;
            this.shuttingDown = shuttingDown; this.terminated = terminated;
            this.elapsedMillis = elapsedMillis;
        }
    }

    private static final class Job {
        final int id;
        final Runnable runnable;
        final String label;
        final int durationMillis;
        TaskState state = TaskState.QUEUED;
        int workerId = -1;
        long startMillis = -1, finishMillis = -1;
        String error = "";
        Job(int id, Runnable runnable) {
            this.id = id;
            this.runnable = runnable;
            if (runnable instanceof NamedTask) {
                NamedTask n = (NamedTask) runnable;
                label = n.displayName();
                durationMillis = n.plannedDurationMillis();
            } else {
                label = runnable.getClass().getSimpleName();
                durationMillis = 0;
            }
        }
    }

    private final Object lock = new Object();
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final Map<Integer, Job> allTasks = new LinkedHashMap<>();
    private final ArrayList<Event> events = new ArrayList<>();
    private final Thread[] threads;
    private final WorkerState[] workerStates;
    private final int[] activeTaskIds;
    private final int[] workerCompleted;
    private final String[] lastResults;
    private final int queueCapacity;
    private final long startNanos = System.nanoTime();
    private volatile boolean shuttingDown;
    private int nextTaskId = 1;
    private int stoppedCount = 0;
    private Listener listener;

    public ThreadPool() { this(3, 100); }
    public ThreadPool(int size) { this(size, 100); }
    public ThreadPool(int size, int queueCapacity) {
        if (size < 1 || size > 64) throw new IllegalArgumentException("Ажиллагчдын тоо 1–64 хооронд байх ёстой");
        if (queueCapacity < 1 || queueCapacity > 10000)
            throw new IllegalArgumentException("Дарааллын багтаамж 1–10000 хооронд байх ёстой");
        this.queueCapacity = queueCapacity;
        this.threads = new Thread[size];
        this.workerStates = new WorkerState[size];
        this.activeTaskIds = new int[size];
        this.workerCompleted = new int[size];
        this.lastResults = new String[size];
        for (int i = 0; i < size; i++) {
            workerStates[i] = WorkerState.IDLE;
            lastResults[i] = "—";
            logLocked(i + 1, -1, "IDLE", "Ажиллагч ажил авахад бэлэн");
        }
        for (int i = 0; i < size; i++) {
            final int workerIndex = i;
            threads[i] = new Thread(() -> workerLoop(workerIndex), "ThreadPool-Worker-" + (i + 1));
            threads[i].setDaemon(true); // avoids orphan processes when closing the demo window
            threads[i].start();
        }
    }

    public void setListener(Listener newListener) {
        synchronized (lock) {
            listener = newListener;
            publishLocked();
        }
    }

    /** Book API: submit a Runnable to the pool. */
    public void add(Runnable task) { submit(task); }

    /** Extra API for the GUI: returns the assigned task ID. */
    public int submit(Runnable task) {
        if (task == null) throw new IllegalArgumentException("Ажил хоосон (null) байж болохгүй");
        synchronized (lock) {
            if (shuttingDown) throw new RejectedExecutionException("Урсгалын сан зогсож байгаа тул ажил нэмж болохгүй");
            if (queue.size() >= queueCapacity)
                throw new RejectedExecutionException("Ажлын дараалал дүүрсэн (багтаамж: " + queueCapacity + ")");
            Job job = new Job(nextTaskId++, task);
            allTasks.put(job.id, job);
            queue.addLast(job);
            logLocked(-1, job.id, "QUEUED", job.label);
            lock.notifyAll();
            publishLocked();
            return job.id;
        }
    }

    /** Book API: interrupts workers and cancels tasks that have not started. */
    public void shutdown() {
        synchronized (lock) {
            if (shuttingDown) return;
            shuttingDown = true;
            while (!queue.isEmpty()) {
                Job j = queue.removeFirst();
                j.state = TaskState.CANCELLED;
                j.finishMillis = elapsedMillis();
                j.error = "Эхлэхээс өмнө санг зогсоосон тул цуцалсан";
                logLocked(-1, j.id, "CANCELLED", j.error);
            }
            logLocked(-1, -1, "SHUTDOWN", "Ажиллагч урсгалуудад interrupt дохио илгээлээ");
            lock.notifyAll();
            publishLocked();
        }
        for (Thread t : threads) t.interrupt();
    }

    /** Extra API for tests/reset. Never call this on the Swing UI thread. */
    public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis < 0) throw new IllegalArgumentException("Хүлээх хугацаа сөрөг байж болохгүй");
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        for (Thread t : threads) {
            long remain = deadline - System.nanoTime();
            if (remain <= 0) return !t.isAlive() && allWorkersStopped();
            long waitMillis = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remain));
            t.join(waitMillis);
        }
        return allWorkersStopped();
    }

    private boolean allWorkersStopped() {
        for (Thread t : threads) if (t.isAlive()) return false;
        return true;
    }

    public Snapshot getSnapshot() {
        synchronized (lock) { return snapshotLocked(); }
    }

    private void workerLoop(int index) {
        int workerId = index + 1;
        try {
            while (true) {
                Job job;
                synchronized (lock) {
                    while (queue.isEmpty() && !shuttingDown) lock.wait();
                    if (shuttingDown) break;
                    job = queue.removeFirst();
                    job.state = TaskState.RUNNING;
                    job.workerId = workerId;
                    job.startMillis = elapsedMillis();
                    workerStates[index] = WorkerState.RUNNING;
                    activeTaskIds[index] = job.id;
                    logLocked(workerId, job.id, "RUNNING", "Ажил эхэллээ");
                    publishLocked();
                }
                TaskState result = TaskState.COMPLETED;
                String error = "";
                try {
                    job.runnable.run();
                    // An interrupted task that ignores interruption cannot be claimed completed.
                    if (Thread.currentThread().isInterrupted()) {
                        result = TaskState.CANCELLED;
                        error = "Зогсоох үед ажил тасалдсан";
                    }
                } catch (java.util.concurrent.CancellationException ex) {
                    result = TaskState.CANCELLED;
                    error = ex.getMessage() == null ? "Цуцлагдсан" : ex.getMessage();
                } catch (Throwable ex) {
                    result = TaskState.FAILED;
                    error = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
                }
                synchronized (lock) {
                    job.finishMillis = elapsedMillis();
                    job.state = result;
                    job.error = error;
                    if (result == TaskState.COMPLETED) workerCompleted[index]++;
                    lastResults[index] = "T" + job.id + " → " + taskResultMongolian(result);
                    logLocked(workerId, job.id, result.name(),
                              result == TaskState.COMPLETED ? "Ажил амжилттай дууслаа" : error);
                    // COMPLETED is recorded as a timestamped event. The worker then
                    // becomes IDLE, ready for the next job; task remains COMPLETED.
                    workerStates[index] = WorkerState.IDLE;
                    activeTaskIds[index] = 0;
                    logLocked(workerId, -1, "IDLE", "Дараагийн ажлыг хүлээж байна");
                    publishLocked();
                }
                if (Thread.currentThread().isInterrupted()) break;
            }
        } catch (InterruptedException ignored) {
            // Interrupt is expected when shutdown wakes an idle worker.
            Thread.currentThread().interrupt();
        } finally {
            synchronized (lock) {
                workerStates[index] = WorkerState.STOPPED;
                activeTaskIds[index] = 0;
                stoppedCount++;
                logLocked(workerId, -1, "STOPPED", "Ажиллагч урсгал зогслоо");
                publishLocked();
            }
        }
    }

    private static String taskResultMongolian(TaskState state) {
        switch (state) {
            case COMPLETED: return "ДУУССАН";
            case FAILED: return "АЛДААТАЙ";
            case CANCELLED: return "ЦУЦЛАГДСАН";
            case RUNNING: return "АЖИЛЛАЖ БУЙ";
            default: return "ХҮЛЭЭЖ БУЙ";
        }
    }

    private long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private void logLocked(int worker, int task, String kind, String detail) {
        // Call only while holding lock, or from constructor before threads start.
        events.add(new Event(elapsedMillis(), worker, task, kind, detail));
        if (events.size() > 4000) events.remove(0);
    }

    private Snapshot snapshotLocked() {
        List<TaskView> tasks = new ArrayList<>(allTasks.size());
        int running = 0, completed = 0, failed = 0, cancelled = 0;
        for (Job j : allTasks.values()) {
            tasks.add(new TaskView(j));
            switch (j.state) {
                case RUNNING: running++; break;
                case COMPLETED: completed++; break;
                case FAILED: failed++; break;
                case CANCELLED: cancelled++; break;
                default: break;
            }
        }
        List<Integer> pending = new ArrayList<>(queue.size());
        for (Job j : queue) pending.add(j.id);
        List<WorkerView> workers = new ArrayList<>(threads.length);
        for (int i = 0; i < threads.length; i++)
            workers.add(new WorkerView(i + 1, workerStates[i], activeTaskIds[i],
                                       workerCompleted[i], lastResults[i]));
        return new Snapshot(tasks, workers, new ArrayList<>(events), pending,
                            queueCapacity, allTasks.size(), queue.size(), running,
                            completed, failed, cancelled,
                            shuttingDown, shuttingDown && stoppedCount == threads.length,
                            elapsedMillis());
    }

    private void publishLocked() {
        if (listener == null) return;
        try {
            // The listener is invoked serially under the lock. It MUST only enqueue
            // UI work, not synchronously modify the pool.
            listener.onUpdate(snapshotLocked());
        } catch (RuntimeException ignored) { /* UI listener cannot terminate workers */ }
    }
}
