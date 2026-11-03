import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;

/** Swing control panel and real-time visualization of ThreadPool snapshots. */
public final class ThreadPoolApp extends JFrame {
    private static final Color NAVY = new Color(15, 23, 42);
    private static final Color BACK = new Color(245, 248, 252);
    private static final Color PANEL = Color.WHITE;
    private static final Color SUBTEXT = new Color(100, 116, 139);
    private static final Color GREEN = new Color(17, 150, 103);
    private static final Color BLUE = new Color(45, 99, 215);
    private static final Color AMBER = new Color(214, 137, 25);
    private static final Color RED = new Color(207, 65, 75);
    private static final Color BORDER = new Color(224, 230, 239);

    private final JSpinner workersSpinner = spinner(3, 1, 32, 1);
    private final JSpinner capacitySpinner = spinner(30, 1, 1000, 1);
    private final JSpinner batchSpinner = spinner(8, 1, 100, 1);
    private final JSpinner durationSpinner = spinner(2000, 100, 15000, 100);
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{"Хүлээлт (I/O загвар)", "CPU тооцоолол", "Алдаа шалгах"});
    private final JButton startButton = button("Сан үүсгэх", GREEN);
    private final JButton addButton = button("Ажил нэмэх", BLUE);
    private final JButton stopButton = button("Зогсоох", AMBER);
    private final JButton resetButton = button("Шинэчлэх", NAVY);
    private final JLabel status = new JLabel("Бэлэн. Тохиргоогоо сонгоод «Сан үүсгэх» товчийг дарна уу.");
    private final JPanel workerList = new JPanel();
    private final DefaultListModel<String> queueModel = new DefaultListModel<>();
    private final JTable taskTable;
    private final DefaultTableModel tableModel;
    private final JTextArea eventArea = new JTextArea();
    private final JLabel[] metricValues = new JLabel[6];
    private final JLabel runtimeLabel = new JLabel("Өнгөрсөн хугацаа: 0.0 сек");
    private final TimelinePanel timeline = new TimelinePanel();
    private volatile ThreadPool activePool;
    private ThreadPool.Snapshot current;
    private boolean resetting = false;

    public ThreadPoolApp() {
        super("Урсгалын сан — Ажиллагчдын төлөв | Үйлдлийн систем БД1");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        // Дэлгэцийн бодит хэмжээнд тохируулж цонхыг багтаана (Mac дээр ч ашигтай).
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1460, Math.max(1080, screen.width - 80)),
                Math.min(900, Math.max(720, screen.height - 90)));
        setMinimumSize(new Dimension(1080, 720));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BACK);
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(NAVY);
        header.setBorder(new EmptyBorder(19, 26, 19, 26));
        JLabel title = new JLabel("УРСГАЛЫН САН  /  АЖИЛЛАГЧДЫН ТӨЛӨВ");
        title.setFont(new Font("SansSerif", Font.BOLD, 23));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        JLabel right = new JLabel("Operating System Concepts · 7-р бүлэг · Төсөл 1");
        right.setForeground(new Color(191, 203, 223));
        right.setFont(new Font("SansSerif", Font.PLAIN, 12));
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JPanel everything = new JPanel(new BorderLayout(0, 15));
        everything.setBackground(BACK);
        everything.setBorder(new EmptyBorder(16, 18, 13, 18));
        add(everything, BorderLayout.CENTER);

        JPanel upper = new JPanel();
        upper.setBackground(BACK);
        upper.setLayout(new BoxLayout(upper, BoxLayout.Y_AXIS));
        JPanel controls = panel(new FlowLayout(FlowLayout.LEFT, 11, 7));
        controls.add(field("Ажиллагчдын тоо", workersSpinner));
        controls.add(field("Дарааллын багтаамж", capacitySpinner));
        controls.add(field("Нэмэх ажлын тоо", batchSpinner));
        controls.add(field("Хугацаа (мс)", durationSpinner));
        controls.add(field("Ажлын төрөл", modeBox));
        controls.add(startButton);
        controls.add(addButton);
        controls.add(stopButton);
        controls.add(resetButton);
        upper.add(controls);
        upper.add(Box.createVerticalStrut(13));

        String[] metricNames = {"НИЙТ", "ХҮЛЭЭЖ БУЙ", "АЖИЛЛАЖ БУЙ", "ДУУССАН", "АЛДААТАЙ", "ЦУЦЛАГДСАН"};
        JPanel metrics = new JPanel(new GridLayout(1, 6, 9, 0));
        metrics.setOpaque(false);
        for (int i = 0; i < metricNames.length; i++) {
            JPanel card = panel(new BorderLayout(3, 3));
            card.setBorder(new CompoundBorder(new LineBorder(BORDER), new EmptyBorder(8, 13, 8, 13)));
            JLabel name = new JLabel(metricNames[i]);
            name.setFont(new Font("SansSerif", Font.BOLD, 10));
            name.setForeground(SUBTEXT);
            metricValues[i] = new JLabel("0");
            metricValues[i].setFont(new Font("SansSerif", Font.BOLD, 25));
            metricValues[i].setForeground(i == 3 ? GREEN : i == 4 ? RED : NAVY);
            card.add(name, BorderLayout.NORTH);
            card.add(metricValues[i], BorderLayout.CENTER);
            metrics.add(card);
        }
        upper.add(metrics);
        everything.add(upper, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(13, 0));
        content.setOpaque(false);
        everything.add(content, BorderLayout.CENTER);

        JPanel left = panel(new BorderLayout(0, 9));
        left.setPreferredSize(new Dimension(245, 500));
        left.add(sectionTitle("АЖИЛЛАГЧИД", "Бодит урсгал бүрийн төлөв"), BorderLayout.NORTH);
        workerList.setLayout(new BoxLayout(workerList, BoxLayout.Y_AXIS));
        workerList.setBackground(PANEL);
        workerList.setBorder(new EmptyBorder(6, 7, 6, 7));
        JScrollPane wscroll = new JScrollPane(workerList);
        wscroll.setBorder(null);
        wscroll.getVerticalScrollBar().setUnitIncrement(15);
        left.add(wscroll, BorderLayout.CENTER);
        content.add(left, BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout(0, 13));
        center.setOpaque(false);
        content.add(center, BorderLayout.CENTER);
        JPanel chartCard = panel(new BorderLayout(0, 8));
        chartCard.add(sectionTitle("АЖИЛЛАГЧДЫН ХУГАЦААНЫ ШУГАМ", "Ногоон: ажиллаж буй · Саарал: сул · Цэнхэр: дууссан агшин · Улаан: зогссон агшин"), BorderLayout.NORTH);
        JScrollPane timelineScroll = new JScrollPane(timeline);
        timelineScroll.setBorder(null);
        timelineScroll.getVerticalScrollBar().setUnitIncrement(16);
        chartCard.add(timelineScroll, BorderLayout.CENTER);
        chartCard.add(runtimeLabel, BorderLayout.SOUTH);
        chartCard.setPreferredSize(new Dimension(600, 285));
        center.add(chartCard, BorderLayout.NORTH);

        JPanel tableCard = panel(new BorderLayout(0, 8));
        tableCard.add(sectionTitle("АЖЛЫН ТҮҮХ", "Бодит гүйцэтгэлийн үр дүн"), BorderLayout.NORTH);
        String[] columns = {"Ажил", "Төрөл / хугацаа", "Төлөв", "Ажиллагч", "Эхэлсэн (сек)", "Дууссан (сек)"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        taskTable = new JTable(tableModel);
        taskTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        taskTable.setRowHeight(25);
        taskTable.setFillsViewportHeight(true);
        taskTable.setGridColor(BORDER);
        taskTable.setSelectionBackground(new Color(221, 237, 250));
        taskTable.getTableHeader().setBackground(new Color(237, 242, 249));
        taskTable.getTableHeader().setForeground(NAVY);
        taskTable.getColumnModel().getColumn(2).setCellRenderer(new StateRenderer());
        JScrollPane tableScroll = new JScrollPane(taskTable);
        tableScroll.setBorder(new LineBorder(BORDER));
        tableCard.add(tableScroll, BorderLayout.CENTER);
        center.add(tableCard, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 0, 13));
        rightPanel.setOpaque(false);
        rightPanel.setPreferredSize(new Dimension(265, 450));
        content.add(rightPanel, BorderLayout.EAST);

        JPanel queueCard = panel(new BorderLayout(0, 8));
        queueCard.add(sectionTitle("АЖЛЫН ДАРААЛАЛ", "Сул ажиллагчийг хүлээж буй ажлууд"), BorderLayout.NORTH);
        JList<String> pending = new JList<>(queueModel);
        pending.setFont(new Font("Monospaced", Font.PLAIN, 12));
        pending.setFixedCellHeight(25);
        pending.setSelectionBackground(new Color(221, 237, 250));
        JScrollPane qscroll = new JScrollPane(pending);
        qscroll.setBorder(new LineBorder(BORDER));
        queueCard.add(qscroll, BorderLayout.CENTER);
        rightPanel.add(queueCard);

        JPanel eventCard = panel(new BorderLayout(0, 8));
        eventCard.add(sectionTitle("ҮЙЛ ЯВДЛЫН БҮРТГЭЛ", "Эхэлснээс хойших бодит хугацаагаар"), BorderLayout.NORTH);
        eventArea.setEditable(false);
        eventArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        eventArea.setForeground(new Color(220, 236, 250));
        eventArea.setBackground(NAVY);
        eventArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        eventArea.setLineWrap(true);
        eventArea.setWrapStyleWord(true);
        JScrollPane escroll = new JScrollPane(eventArea);
        escroll.setBorder(null);
        eventCard.add(escroll, BorderLayout.CENTER);
        rightPanel.add(eventCard);

        JPanel foot = new JPanel(new BorderLayout());
        foot.setBackground(BACK);
        status.setForeground(SUBTEXT);
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        foot.add(status, BorderLayout.WEST);
        JLabel note = new JLabel("Бодит Java урсгал · synchronized / wait / notifyAll · interrupt");
        note.setForeground(SUBTEXT);
        note.setFont(new Font("SansSerif", Font.PLAIN, 11));
        foot.add(note, BorderLayout.EAST);
        everything.add(foot, BorderLayout.SOUTH);

        workersSpinner.setToolTipText("Урьдчилан үүсгэх ажиллагч урсгалын тоо (1–32)");
        capacitySpinner.setToolTipText("Зөвхөн хүлээгдэж буй ажлуудын дээд хэмжээ (1–1000)");
        batchSpinner.setToolTipText("Нэг удаа дарахад нэмэх ажлын тоо (1–100)");
        durationSpinner.setToolTipText("Нэг ажлын үргэлжлэх хугацаа, миллисекундээр");
        modeBox.setToolTipText("Хүлээлт, CPU тооцоолол эсвэл алдаа шалгах ажил сонгоно");
        startButton.setToolTipText("Тохируулсан тооны бодит Java урсгал үүсгэнэ");
        addButton.setToolTipText("Ажлуудыг дараалалд нэмнэ");
        stopButton.setToolTipText("Хүлээж буй ажлыг цуцалж, ажиллаж буй урсгалд interrupt илгээнэ");
        resetButton.setToolTipText("Хуучин санг бүрэн зогсоож, шинэ тохиргоо сонгох боломж олгоно");
        startButton.addActionListener(e -> startPool());
        addButton.addActionListener(e -> addBatch());
        stopButton.addActionListener(e -> shutdownPool());
        resetButton.addActionListener(e -> resetPool());
        enableButtons();
        renderWorkers(null);
        new javax.swing.Timer(180, e -> {
            ThreadPool p = activePool;
            if (p != null) {
                current = p.getSnapshot();
                timeline.setSnapshot(current);
                runtimeLabel.setText(String.format(Locale.US, "Өнгөрсөн: %.1f сек · Дарааллын багтаамж: %d",
                    TimelinePanel.displayTimeMillis(current) / 1000.0, current.queueCapacity));
            }
        }).start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                ThreadPool p = activePool;
                if (p != null) p.shutdown();
                dispose();
                System.exit(0);
            }
        });
    }

    private void startPool() {
        if (activePool != null || resetting) return;
        try {
            int count = (Integer) workersSpinner.getValue();
            int cap = (Integer) capacitySpinner.getValue();
            ThreadPool created = new ThreadPool(count, cap);
            activePool = created;
            created.setListener(s -> SwingUtilities.invokeLater(() -> {
                if (activePool == created) applySnapshot(s);
            }));
            status.setText("Сан үүсгэлээ: " + count + " ажиллагч, дарааллын багтаамж " + cap + ". Одоо «Ажил нэмэх» товчийг дарна уу.");
            enableButtons();
        } catch (RuntimeException ex) { showError(ex.getMessage()); }
    }

    private void addBatch() {
        ThreadPool p = activePool;
        if (p == null || resetting) return;
        int count = (Integer) batchSpinner.getValue();
        int duration = (Integer) durationSpinner.getValue();
        int modeIndex = modeBox.getSelectedIndex();
        WorkTask.Mode mode = WorkTask.Mode.values()[modeIndex];
        int accepted = 0;
        try {
            for (int i = 0; i < count; i++) {
                p.add(new WorkTask(mode, duration));
                accepted++;
            }
            status.setText(accepted + " ажил нэмэгдлээ. Дараалал, ажиллагчид, хугацааны шугамыг ажиглаарай.");
        } catch (RejectedExecutionException ex) {
            status.setText(count + " ажлаас " + accepted + " нь нэмэгдлээ. " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                accepted + " ажил нэмэгдсэн. Үлдсэн ажлуудыг хүлээж авсангүй.\n" + ex.getMessage(),
                "Дарааллын хязгаар", JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException ex) { showError(ex.getMessage()); }
    }

    private void shutdownPool() {
        ThreadPool p = activePool;
        if (p == null || resetting) return;
        p.shutdown();
        status.setText("Зогсоох хүсэлт авлаа. Хүлээж буй ажлыг цуцалж, ажиллагчдыг тасалдууллаа. Шинээр эхлэх бол «Шинэчлэх» дар.");
        enableButtons();
    }

    private void resetPool() {
        if (resetting) return;
        ThreadPool old = activePool;
        if (old == null) { clearUi(); return; }
        resetting = true;
        enableButtons();
        status.setText("Өмнөх урсгалын санг зогсоож, шинээр эхлэхэд бэлтгэж байна…");
        Thread resetThread = new Thread(() -> {
            old.shutdown();
            boolean terminated = false;
            try { terminated = old.awaitTermination(5000); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            boolean finalTerminated = terminated;
            SwingUtilities.invokeLater(() -> {
                resetting = false;
                if (finalTerminated) {
                    activePool = null;
                    clearUi();
                    status.setText("Шинэчлэлт дууслаа. Ажиллагчдын тоогоо сонгоод «Сан үүсгэх» дар.");
                } else {
                    status.setText("Зарим ажиллагч хараахан зогсоогүй байна. Зогссоны дараа «Шинэчлэх» дарна уу.");
                }
                enableButtons();
            });
        }, "ThreadPool-Reset-Helper");
        resetThread.setDaemon(true);
        resetThread.start();
    }

    private void clearUi() {
        current = null;
        queueModel.clear();
        tableModel.setRowCount(0);
        eventArea.setText("");
        for (JLabel m : metricValues) m.setText("0");
        timeline.setSnapshot(null);
        runtimeLabel.setText("Өнгөрсөн хугацаа: 0.0 сек");
        renderWorkers(null);
        enableButtons();
    }

    private void applySnapshot(ThreadPool.Snapshot snapshot) {
        current = snapshot;
        int[] counts = {snapshot.submitted, snapshot.queued, snapshot.running,
                        snapshot.completed, snapshot.failed, snapshot.cancelled};
        for (int i = 0; i < counts.length; i++) metricValues[i].setText(String.valueOf(counts[i]));
        queueModel.clear();
        for (Integer id : snapshot.queueTaskIds) queueModel.addElement("T" + id + "  •  ХҮЛЭЭЖ БАЙНА");
        tableModel.setRowCount(0);
        for (ThreadPool.TaskView task : snapshot.tasks) {
            tableModel.addRow(new Object[]{
                "T" + task.id, task.label, taskStateText(task.state),
                task.workerId < 0 ? "—" : "W" + task.workerId,
                task.startMillis < 0 ? "—" : seconds(task.startMillis),
                task.finishMillis < 0 ? "—" : seconds(task.finishMillis)
            });
        }
        StringBuilder logs = new StringBuilder();
        List<ThreadPool.Event> events = snapshot.events;
        for (int i = Math.max(0, events.size() - 65); i < events.size(); i++) {
            ThreadPool.Event e = events.get(i);
            logs.append(String.format(Locale.US, "+%5.2fs ", e.timeMillis / 1000.0));
            if (e.workerId > 0) logs.append("W").append(e.workerId).append(" ");
            if (e.taskId > 0) logs.append("T").append(e.taskId).append(" ");
            logs.append(eventKindText(e.kind)).append(" — ").append(e.detail).append("\n");
        }
        eventArea.setText(logs.toString());
        eventArea.setCaretPosition(eventArea.getDocument().getLength());
        renderWorkers(snapshot.workers);
        timeline.setSnapshot(snapshot);
        enableButtons();
    }

    private void renderWorkers(List<ThreadPool.WorkerView> workers) {
        workerList.removeAll();
        if (workers == null || workers.isEmpty()) {
            JLabel note = new JLabel("Эхлээд «Сан үүсгэх» дарна уу.");
            note.setForeground(SUBTEXT);
            note.setBorder(new EmptyBorder(16, 5, 0, 0));
            workerList.add(note);
        } else {
            for (ThreadPool.WorkerView w : workers) {
                JPanel card = new JPanel(new BorderLayout(0, 6));
                card.setBackground(new Color(249, 251, 255));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 91));
                card.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(9, 10, 9, 10)));
                JLabel name = new JLabel("Ажиллагч " + w.id + (w.currentTaskId > 0 ? "  ·  Ажил T" + w.currentTaskId : ""));
                name.setFont(new Font("SansSerif", Font.BOLD, 13));
                name.setForeground(NAVY);
                card.add(name, BorderLayout.NORTH);
                JLabel state = new JLabel("● " + workerStateText(w.state));
                state.setForeground(w.state == ThreadPool.WorkerState.RUNNING ? GREEN :
                                    w.state == ThreadPool.WorkerState.STOPPED ? RED : SUBTEXT);
                state.setFont(new Font("SansSerif", Font.BOLD, 12));
                card.add(state, BorderLayout.CENTER);
                JLabel sub = new JLabel("Дууссан: " + w.completedTasks + "  |  Сүүлд: " + w.lastResult);
                sub.setForeground(SUBTEXT);
                sub.setFont(new Font("SansSerif", Font.PLAIN, 10));
                card.add(sub, BorderLayout.SOUTH);
                workerList.add(card);
                workerList.add(Box.createVerticalStrut(7));
            }
        }
        workerList.revalidate();
        workerList.repaint();
    }

    private void enableButtons() {
        boolean running = activePool != null && !activePool.getSnapshot().shuttingDown;
        startButton.setEnabled(!resetting && activePool == null);
        addButton.setEnabled(!resetting && running);
        stopButton.setEnabled(!resetting && running);
        resetButton.setEnabled(!resetting);
        workersSpinner.setEnabled(!resetting && activePool == null);
        capacitySpinner.setEnabled(!resetting && activePool == null);
    }

    private void showError(String message) {
        status.setText("Алдаа: " + message);
        JOptionPane.showMessageDialog(this, message, "Оролтын алдаа", JOptionPane.ERROR_MESSAGE);
    }
    private static String seconds(long millis) { return String.format(Locale.US, "%.2f", millis / 1000.0); }
    private static JSpinner spinner(int init, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(init, min, max, step));
        s.setPreferredSize(new Dimension(96, 28));
        return s;
    }
    private static JButton button(String text, Color color) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        b.setBorder(new EmptyBorder(9, 13, 9, 13));
        b.setFocusPainted(false);
        return b;
    }
    private static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(PANEL);
        p.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(10, 11, 10, 11)));
        return p;
    }
    private static JPanel field(String text, JComponent input) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(PANEL);
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        lbl.setForeground(SUBTEXT);
        p.add(lbl, BorderLayout.NORTH);
        p.add(input, BorderLayout.CENTER);
        return p;
    }
    private static JPanel sectionTitle(String title, String subtitle) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setBackground(PANEL);
        JLabel top = new JLabel(title);
        top.setForeground(NAVY);
        top.setFont(new Font("SansSerif", Font.BOLD, 13));
        JLabel bottom = new JLabel(subtitle);
        bottom.setForeground(SUBTEXT);
        bottom.setFont(new Font("SansSerif", Font.PLAIN, 10));
        p.add(top); p.add(bottom);
        return p;
    }

    // Хэрэглэгчид харагдах нэршлийг Монголчилсон; дотоод enum/API нэр өөрчлөгдөхгүй.
    private static String taskStateText(ThreadPool.TaskState state) {
        switch (state) {
            case QUEUED: return "ХҮЛЭЭЖ БУЙ";
            case RUNNING: return "АЖИЛЛАЖ БУЙ";
            case COMPLETED: return "ДУУССАН";
            case FAILED: return "АЛДААТАЙ";
            case CANCELLED: return "ЦУЦЛАГДСАН";
            default: return state.name();
        }
    }
    private static String workerStateText(ThreadPool.WorkerState state) {
        switch (state) {
            case IDLE: return "СУЛ";
            case RUNNING: return "АЖИЛЛАЖ БУЙ";
            case STOPPED: return "ЗОГССОН";
            default: return state.name();
        }
    }
    private static String eventKindText(String kind) {
        switch (kind) {
            case "QUEUED": return "ДАРААЛАЛД НЭМСЭН";
            case "RUNNING": return "АЖИЛ ЭХЭЛСЭН";
            case "COMPLETED": return "АМЖИЛТТАЙ ДУУССАН";
            case "FAILED": return "АЛДААТАЙ ДУУССАН";
            case "CANCELLED": return "ЦУЦАЛСАН";
            case "IDLE": return "СУЛ";
            case "STOPPED": return "ЗОГССОН";
            case "SHUTDOWN": return "ЗОГСООХ ХҮСЭЛТ";
            default: return kind;
        }
    }

    private static final class StateRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object val, boolean selected,
            boolean focus, int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, val, selected, focus, row, col);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            if (!selected) label.setForeground("ДУУССАН".equals(val) ? GREEN :
                "АЖИЛЛАЖ БУЙ".equals(val) ? BLUE :
                "АЛДААТАЙ".equals(val) || "ЦУЦЛАГДСАН".equals(val) ? RED : SUBTEXT);
            return label;
        }
    }

    /** Бодит event-үүдээр зурдаг хугацааны шугам. STOPPED нь үргэлжилсэн төлөв биш, агшин юм. */
    static final class TimelinePanel extends JPanel {
        /** Зогссон сангийн цагийн хуваарийг STOPPED агшинд нь царцаана. */
        static long displayTimeMillis(ThreadPool.Snapshot snap) {
            if (snap == null) return 0;
            if (!snap.terminated) return snap.elapsedMillis;
            long stoppedAt = -1;
            for (ThreadPool.Event ev : snap.events) {
                if ("STOPPED".equals(ev.kind)) stoppedAt = Math.max(stoppedAt, ev.timeMillis);
            }
            return stoppedAt >= 0 ? stoppedAt : snap.elapsedMillis;
        }

        private ThreadPool.Snapshot data;
        TimelinePanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(540, 205));
            setBorder(new LineBorder(BORDER));
        }
        void setSnapshot(ThreadPool.Snapshot snapshot) {
            data = snapshot;
            int count = snapshot == null ? 0 : snapshot.workers.size();
            int requiredHeight = Math.max(205, count * 30 + 65);
            if (getPreferredSize().height != requiredHeight) {
                setPreferredSize(new Dimension(540, requiredHeight));
                revalidate();
            }
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D c = (Graphics2D) g.create();
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ThreadPool.Snapshot snap = data;
            if (snap == null) {
                c.setColor(SUBTEXT);
                c.drawString("Бодит ажиллагааг харахын тулд сан үүсгэж, ажил нэмнэ үү.", 15, 29);
                c.dispose(); return;
            }
            int workerCount = snap.workers.size();
            int left = 52, right = 17, top = 19, row = 30;
            int graphWidth = Math.max(50, getWidth() - left - right);
            long displayMs = displayTimeMillis(snap);
            long maxMs = Math.max(8000, ((displayMs + 1999) / 2000) * 2000);
            c.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int tick = 0; tick <= 4; tick++) {
                int x = left + tick * graphWidth / 4;
                c.setColor(new Color(230, 235, 242));
                c.drawLine(x, top, x, top + Math.max(workerCount, 1) * row + 8);
                c.setColor(SUBTEXT);
                c.drawString(String.format(Locale.US, "%.1f сек", maxMs * tick / 4000.0), x - 4,
                             top + Math.max(workerCount, 1) * row + 22);
            }
            for (int w = 1; w <= workerCount; w++) {
                int y = top + (w - 1) * row;
                c.setFont(new Font("SansSerif", Font.BOLD, 11));
                c.setColor(NAVY);
                c.drawString("W" + w, 15, y + 18);
                c.setColor(new Color(232, 235, 240));
                c.fillRoundRect(left, y + 4, graphWidth, 20, 5, 5);
                String previous = "IDLE";
                long previousTime = 0;
                long stoppedAt = -1;
                for (ThreadPool.Event ev : snap.events) {
                    if (ev.workerId != w) continue;
                    if (ev.kind.equals("RUNNING") || ev.kind.equals("IDLE") || ev.kind.equals("STOPPED")) {
                        // Өмнөх төлөвийг ЗӨВХӨН дараагийн бодит event хүртэл зурна.
                        paintState(c, previous, left, graphWidth, y, maxMs, previousTime, ev.timeMillis);
                        if (ev.kind.equals("STOPPED")) {
                            stoppedAt = ev.timeMillis;
                            break; // STOPPED -> цааш үргэлжлэх улаан зурвас БАЙХГҮЙ.
                        }
                        previous = ev.kind;
                        previousTime = ev.timeMillis;
                    }
                }
                if (stoppedAt < 0) {
                    paintState(c, previous, left, graphWidth, y, maxMs, previousTime, displayMs);
                }
                // Амжилттай дууссан task-ийн агшныг тод цэнхэр тэмдэглэгээгээр ялгана.
                for (ThreadPool.Event ev : snap.events) {
                    if (ev.workerId != w) continue;
                    if (ev.kind.equals("COMPLETED") || ev.kind.equals("FAILED") || ev.kind.equals("CANCELLED")) {
                        int x = xAt(left, graphWidth, maxMs, ev.timeMillis);
                        c.setColor(ev.kind.equals("COMPLETED") ? BLUE : RED);
                        c.fillRoundRect(x - 3, y + 2, 6, 24, 3, 3);
                        c.setColor(Color.WHITE);
                        c.fillOval(x - 1, y + 10, 2, 5);
                    }
                }
                // Worker зогссон үйл явдлыг ганц нарийн улаан босоо зураасаар тэмдэглэнэ.
                if (stoppedAt >= 0) {
                    int x = xAt(left, graphWidth, maxMs, stoppedAt);
                    c.setColor(RED);
                    c.fillRoundRect(x - 2, y + 2, 4, 24, 2, 2);
                    c.fillOval(x - 4, y + 1, 8, 8);
                }
            }
            c.dispose();
        }
        private static int xAt(int left, int width, long max, long time) {
            return left + (int) (width * Math.min(max, Math.max(0, time)) / (double) max);
        }
        private static void paintState(Graphics2D g, String state, int left, int width,
                                       int y, long max, long from, long to) {
            if (to < from) return;
            int x1 = xAt(left, width, max, from);
            int x2 = xAt(left, width, max, to);
            // STOPPED-ийг paintState-ээр хэзээ ч үргэлжилсэн зурвас болгож зурахгүй.
            if (state.equals("STOPPED")) return;
            g.setColor(state.equals("RUNNING") ? GREEN : new Color(176, 185, 197));
            g.fillRoundRect(x1, y + 5, Math.max(1, x2 - x1), 18, 4, 4);
        }
    }
}
