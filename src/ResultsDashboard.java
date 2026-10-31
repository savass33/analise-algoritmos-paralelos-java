import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResultsDashboard extends JFrame {
    private final List<CsvAnalyzer.Result> results;
    private final JComboBox<String> algorithm = new JComboBox<>(new String[]{"Quick Sort", "Merge Sort", "Counting Sort", "Insertion Sort"});
    private final JComboBox<String> pattern = new JComboBox<>(new String[]{"RANDOM", "SORTED", "REVERSE", "FEW_UNIQUE"});
    private final ChartPanel chart = new ChartPanel();

    private ResultsDashboard(List<CsvAnalyzer.Result> results) {
        super("Painel dinamico - algoritmos seriais e paralelos");
        this.results = results;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1_050, 680));
        setLocationByPlatform(true);

        JPanel controls = new JPanel();
        controls.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        controls.add(new JLabel("Algoritmo:")); controls.add(algorithm);
        controls.add(new JLabel("Padrao dos dados:")); controls.add(pattern);
        algorithm.addActionListener(event -> refresh());
        pattern.addActionListener(event -> refresh());

        add(controls, BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER);
        refresh();
        pack();
    }

    private void refresh() {
        String selectedAlgorithm = (String) algorithm.getSelectedItem();
        String selectedPattern = (String) pattern.getSelectedItem();
        chart.setData(selectedAlgorithm, selectedPattern, aggregate(selectedAlgorithm, selectedPattern));
    }

    private Map<String, Map<Integer, Double>> aggregate(String selectedAlgorithm, String selectedPattern) {
        Map<String, Map<Integer, List<Double>>> groups = new LinkedHashMap<>();
        results.stream().filter(r -> r.algorithm().equals(selectedAlgorithm) && r.pattern().equals(selectedPattern))
                .sorted(Comparator.comparingInt(CsvAnalyzer.Result::size))
                .forEach(r -> {
                    String series = r.implementation().equals("serial") ? "Serial" : r.threads() + " threads";
                    groups.computeIfAbsent(series, ignored -> new LinkedHashMap<>())
                            .computeIfAbsent(r.size(), ignored -> new ArrayList<>()).add(r.durationMs());
                });
        Map<String, Map<Integer, Double>> means = new LinkedHashMap<>();
        groups.forEach((series, sizes) -> {
            Map<Integer, Double> points = new LinkedHashMap<>();
            sizes.forEach((size, values) -> points.put(size, CsvAnalyzer.mean(values)));
            means.put(series, points);
        });
        return means;
    }

    public static void main(String[] args) throws IOException {
        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("resultados", "resultados.csv");
        List<CsvAnalyzer.Result> results = CsvAnalyzer.read(input);
        SwingUtilities.invokeLater(() -> new ResultsDashboard(results).setVisible(true));
    }

    private static final class ChartPanel extends JPanel {
        private String title = "";
        private Map<String, Map<Integer, Double>> data = Map.of();
        private final Color[] colors = {new Color(26, 82, 191), new Color(0, 145, 140), new Color(224, 117, 0)};

        private ChartPanel() {
            setPreferredSize(new Dimension(1_000, 600));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        }

        void setData(String algorithm, String pattern, Map<String, Map<Integer, Double>> data) {
            this.title = algorithm + " - " + pattern;
            this.data = data;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), left = 100, right = 55, top = 90, bottom = 95;
            g.setColor(new Color(35, 40, 48)); g.setFont(new Font("Arial", Font.BOLD, 24)); g.drawString(title, left, 42);
            g.setFont(new Font("Arial", Font.PLAIN, 15));
            double maxY = data.values().stream().flatMap(m -> m.values().stream()).mapToDouble(Double::doubleValue).max().orElse(1.0) * 1.15;
            g.setColor(Color.DARK_GRAY); g.drawLine(left, top, left, h - bottom); g.drawLine(left, h - bottom, w - right, h - bottom);
            for (int tick = 0; tick <= 5; tick++) {
                int y = h - bottom - (h - top - bottom) * tick / 5;
                g.setColor(new Color(225, 228, 233)); g.drawLine(left, y, w - right, y);
                g.setColor(Color.DARK_GRAY); g.drawString(String.format("%.2f", maxY * tick / 5), 30, y + 5);
            }
            List<Integer> sizes = data.values().stream().findFirst().map(m -> new ArrayList<>(m.keySet())).orElseGet(ArrayList::new);
            for (int i = 0; i < sizes.size(); i++) {
                int x = left + i * (w - left - right) / Math.max(1, sizes.size() - 1);
                g.drawString(String.format("%,d", sizes.get(i)), x - 25, h - bottom + 30);
            }
            int seriesIndex = 0;
            for (Map.Entry<String, Map<Integer, Double>> series : data.entrySet()) {
                Color color = colors[seriesIndex % colors.length];
                g.setColor(color); g.setStroke(new BasicStroke(3f));
                int previousX = -1, previousY = -1, i = 0;
                for (double value : series.getValue().values()) {
                    int x = left + i * (w - left - right) / Math.max(1, sizes.size() - 1);
                    int y = h - bottom - (int) ((h - top - bottom) * value / maxY);
                    if (previousX >= 0) g.drawLine(previousX, previousY, x, y);
                    g.fillOval(x - 5, y - 5, 10, 10);
                    previousX = x; previousY = y; i++;
                }
                g.fillRect(w - 250, 28 + seriesIndex * 24, 22, 5);
                g.setColor(Color.DARK_GRAY); g.drawString(series.getKey(), w - 218, 35 + seriesIndex * 24);
                seriesIndex++;
            }
            g.drawString("Tamanho do vetor", w / 2 - 55, h - 28);
            g.rotate(-Math.PI / 2); g.drawString("Tempo medio (ms)", -h / 2 - 55, 20); g.rotate(Math.PI / 2);
            g.dispose();
        }
    }
}
