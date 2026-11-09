package com.spendwise.ui;

import com.spendwise.Services;
import com.spendwise.db.DataException;
import com.spendwise.model.CategoryTotal;
import com.spendwise.model.Expense;
import com.spendwise.model.ReportSummary;
import com.spendwise.model.User;
import com.spendwise.service.ValidationException;
import com.spendwise.util.Dates;
import com.spendwise.util.Money;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reports for any date range: summary numbers, category breakdown and CSV export. */
public class ReportsPanel extends JPanel implements Refreshable {
    private static final String[] PRESETS = {"This month", "Last month", "Last 3 months", "This year", "Custom"};

    private final Services services;
    private final User user;
    private final JComboBox<String> preset = new JComboBox<>(PRESETS);
    private final JSpinner from = Theme.dateSpinner(LocalDate.now().withDayOfMonth(1));
    private final JSpinner to = Theme.dateSpinner(LocalDate.now());
    private final JLabel total = Theme.label("-", Font.BOLD, 20, Theme.TEXT);
    private final JLabel count = Theme.label("-", Font.BOLD, 20, Theme.TEXT);
    private final JLabel avg = Theme.label("-", Font.BOLD, 20, Theme.TEXT);
    private final JLabel top = Theme.label("-", Font.BOLD, 20, Theme.TEXT);
    private final JLabel largest = Theme.label("-", Font.BOLD, 20, Theme.TEXT);
    private final PieChart pie = new PieChart();
    private final DefaultTableModel breakdownModel = new DefaultTableModel(
            new String[]{"Category", "Count", "Total", "Share"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private boolean adjusting;

    public ReportsPanel(Services services, User user) {
        this.services = services;
        this.user = user;
        setLayout(new BorderLayout(0, 14));
        setBorder(Theme.pad(24, 24, 24, 24));
        setBackground(Theme.BG);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Theme.label("Reports", Font.BOLD, 24, Theme.TEXT), BorderLayout.WEST);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        controls.add(preset);
        controls.add(from);
        controls.add(Theme.label("to", Font.PLAIN, 12, Theme.MUTED));
        controls.add(to);
        JButton run = Theme.primary("Generate");
        JButton export = Theme.neutral("Export CSV");
        run.addActionListener(e -> reload());
        export.addActionListener(e -> exportCsv());
        controls.add(run);
        controls.add(export);
        head.add(controls, BorderLayout.EAST);
        preset.addActionListener(e -> applyPreset());
        from.addChangeListener(e -> { if (!adjusting) preset.setSelectedItem("Custom"); });
        to.addChangeListener(e -> { if (!adjusting) preset.setSelectedItem("Custom"); });

        JPanel stats = new JPanel(new GridLayout(1, 5, 12, 0));
        stats.setOpaque(false);
        stats.add(stat("Total spent", total));
        stats.add(stat("Transactions", count));
        stats.add(stat("Average / day", avg));
        stats.add(stat("Top category", top));
        stats.add(stat("Largest expense", largest));

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.setOpaque(false);
        north.add(head, BorderLayout.NORTH);
        north.add(stats, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        JTable table = new JTable(breakdownModel);
        Theme.styleTable(table);
        TableRenderers.install(table);
        JPanel left = Theme.card();
        left.add(Theme.label("Category breakdown", Font.BOLD, 14, Theme.TEXT), BorderLayout.NORTH);
        left.add(pie, BorderLayout.CENTER);
        JPanel right = Theme.card();
        right.add(Theme.label("Details", Font.BOLD, 14, Theme.TEXT), BorderLayout.NORTH);
        right.add(Theme.scroll(table), BorderLayout.CENTER);
        JPanel grid = new JPanel(new GridLayout(1, 2, 16, 0));
        grid.setOpaque(false);
        grid.add(left);
        grid.add(right);
        add(grid, BorderLayout.CENTER);

        refresh();
    }

    private static JPanel stat(String title, JLabel value) {
        JPanel c = Theme.card();
        JPanel in = new JPanel(new GridLayout(2, 1, 0, 4));
        in.setOpaque(false);
        in.add(Theme.label(title, Font.PLAIN, 12, Theme.MUTED));
        value.setFont(Theme.font(Font.BOLD, 17));
        in.add(value);
        c.add(in, BorderLayout.CENTER);
        return c;
    }

    @Override public void refresh() { reload(); }

    private void applyPreset() {
        String p = (String) preset.getSelectedItem();
        LocalDate now = LocalDate.now();
        LocalDate f = null, t = now;
        switch (p == null ? "" : p) {
            case "This month" -> f = now.withDayOfMonth(1);
            case "Last month" -> { f = now.minusMonths(1).withDayOfMonth(1); t = f.withDayOfMonth(f.lengthOfMonth()); }
            case "Last 3 months" -> f = now.minusMonths(2).withDayOfMonth(1);
            case "This year" -> f = now.withDayOfYear(1);
            default -> { return; }
        }
        adjusting = true;
        from.setValue(Dates.toDate(f));
        to.setValue(Dates.toDate(t));
        adjusting = false;
        reload();
    }

    private void reload() {
        try {
            LocalDate f = Theme.dateOf(from), t = Theme.dateOf(to);
            ReportSummary s = services.expenses().summary(user.id(), f, t);
            total.setText(Money.format(s.totalPaise()));
            count.setText(String.valueOf(s.count()));
            avg.setText(Money.format(s.averagePerDayPaise()));
            top.setText(s.topCategory() == null ? "-" : s.topCategory().name());
            largest.setText(s.largest() == null ? "-" : Money.format(s.largest().amountPaise()));

            List<CategoryTotal> cats = services.expenses().breakdown(user.id(), f, t);
            breakdownModel.setRowCount(0);
            List<PieChart.Slice> slices = new ArrayList<>();
            for (CategoryTotal c : cats) {
                long share = s.totalPaise() == 0 ? 0 : Math.round(100.0 * c.totalPaise() / s.totalPaise());
                breakdownModel.addRow(new Object[]{c.name(), c.count(), Money.format(c.totalPaise()), share + "%"});
                slices.add(new PieChart.Slice(c.name(), c.totalPaise(), Theme.hex(c.color())));
            }
            pie.setData(slices);
        } catch (ValidationException ex) {
            Theme.error(this, ex.getMessage());
        } catch (DataException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void exportCsv() {
        try {
            List<Expense> rows = services.expenses().search(user.id(), Theme.dateOf(from), Theme.dateOf(to), null, null);
            if (rows.isEmpty()) { Theme.info(this, "No expenses in this range."); return; }
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new java.io.File("spendwise-report.csv"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path path = fc.getSelectedFile().toPath();
            if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "Overwrite " + path.getFileName() + "?",
                    "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            services.export().exportCsv(rows, path);
            Theme.info(this, "Exported " + rows.size() + " rows to\n" + path);
        } catch (ValidationException ex) {
            Theme.error(this, ex.getMessage());
        } catch (IOException ex) {
            Theme.error(this, "Could not write file: " + ex.getMessage());
        }
    }
}
