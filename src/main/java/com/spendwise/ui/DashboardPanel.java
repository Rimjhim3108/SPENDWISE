package com.spendwise.ui;

import com.spendwise.Services;
import com.spendwise.db.DataException;
import com.spendwise.model.BudgetStatus;
import com.spendwise.model.CategoryTotal;
import com.spendwise.model.User;
import com.spendwise.util.Dates;
import com.spendwise.util.Money;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Overview: key numbers, spending by category, 6-month trend, recent expenses and budget alerts. */
public class DashboardPanel extends JPanel implements Refreshable {
    private final Services services;
    private final User user;

    private final JLabel monthTotal = Theme.label("-", Font.BOLD, 24, Theme.TEXT);
    private final JLabel todayTotal = Theme.label("-", Font.BOLD, 24, Theme.TEXT);
    private final JLabel budgetLeft = Theme.label("-", Font.BOLD, 24, Theme.TEXT);
    private final JLabel dailyAvg = Theme.label("-", Font.BOLD, 24, Theme.TEXT);
    private final PieChart pie = new PieChart();
    private final BarChart bar = new BarChart();
    private final ExpenseTableModel recentModel = new ExpenseTableModel();
    private final JPanel alerts = new JPanel();

    public DashboardPanel(Services services, User user) {
        this.services = services;
        this.user = user;
        setLayout(new BorderLayout(0, 16));
        setBorder(Theme.pad(24, 24, 24, 24));
        setBackground(Theme.BG);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel titles = new JPanel(new GridLayout(2, 1));
        titles.setOpaque(false);
        titles.add(Theme.label("Hello, " + firstName(user.fullName()) + "!", Font.BOLD, 24, Theme.TEXT));
        titles.add(Theme.label(Dates.DISPLAY.format(LocalDate.now()) + "  \u2022  here's your money at a glance",
                Font.PLAIN, 13, Theme.MUTED));
        head.add(titles, BorderLayout.WEST);
        JButton sample = Theme.neutral("Load sample data");
        sample.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Add ~30 demo expenses and two budgets to your account?\n(You can delete them later.)",
                    "Load sample data", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                services.expenses().loadSampleData(user.id());
                refresh();
            }
        });
        head.add(sample, BorderLayout.EAST);

        JPanel stats = new JPanel(new GridLayout(1, 4, 16, 0));
        stats.setOpaque(false);
        stats.add(statCard("Spent this month", monthTotal));
        stats.add(statCard("Spent today", todayTotal));
        stats.add(statCard("Budget left (this month)", budgetLeft));
        stats.add(statCard("Daily average (this month)", dailyAvg));

        JPanel top = new JPanel(new BorderLayout(0, 16));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(stats, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 16, 16));
        grid.setOpaque(false);
        grid.add(chartCard("Spending by category (this month)", pie));
        grid.add(chartCard("Last 6 months", bar));

        JTable recent = new JTable(recentModel);
        Theme.styleTable(recent);
        TableRenderers.install(recent);
        recent.setRowSelectionAllowed(false);
        recent.removeColumn(recent.getColumnModel().getColumn(3)); // hide payment column on the dashboard
        recent.removeColumn(recent.getColumnModel().getColumn(2)); // hide description column too (narrow card)
        recent.getColumnModel().getColumn(0).setPreferredWidth(100);
        recent.getColumnModel().getColumn(1).setPreferredWidth(140);
        JScrollPane rs = Theme.scroll(recent);
        grid.add(chartCard("Recent expenses", rs));

        alerts.setLayout(new BoxLayout(alerts, BoxLayout.Y_AXIS));
        alerts.setOpaque(false);
        grid.add(chartCard("Budget status", alerts));
        add(grid, BorderLayout.CENTER);

        refresh();
    }

    private static String firstName(String full) {
        int i = full.indexOf(' ');
        return i > 0 ? full.substring(0, i) : full;
    }

    private static JPanel statCard(String title, JLabel value) {
        JPanel c = Theme.card();
        JPanel inner = new JPanel(new GridLayout(2, 1, 0, 4));
        inner.setOpaque(false);
        inner.add(Theme.label(title, Font.PLAIN, 12, Theme.MUTED));
        inner.add(value);
        c.add(inner, BorderLayout.CENTER);
        return c;
    }

    private static JPanel chartCard(String title, JComponent body) {
        JPanel c = Theme.card();
        c.add(Theme.label(title, Font.BOLD, 14, Theme.TEXT), BorderLayout.NORTH);
        c.add(body, BorderLayout.CENTER);
        return c;
    }

    @Override public void refresh() {
        try {
            LocalDate today = LocalDate.now();
            YearMonth ym = YearMonth.from(today);
            long month = services.expenses().total(user.id(), ym.atDay(1), ym.atEndOfMonth());
            long day = services.expenses().total(user.id(), today, today);
            monthTotal.setText(Money.format(month));
            todayTotal.setText(Money.format(day));
            dailyAvg.setText(Money.format(month / today.getDayOfMonth()));

            List<BudgetStatus> statuses = services.expenses().budgetStatuses(user.id(), ym);
            BudgetStatus overall = statuses.stream().filter(s -> s.budget().isOverall()).findFirst().orElse(null);
            if (overall == null) {
                budgetLeft.setText("Not set");
                budgetLeft.setForeground(Theme.MUTED);
            } else {
                budgetLeft.setText(Money.format(overall.remainingPaise()));
                budgetLeft.setForeground(overall.remainingPaise() < 0 ? Theme.DANGER : Theme.OK);
            }

            List<PieChart.Slice> slices = new ArrayList<>();
            for (CategoryTotal t : services.expenses().breakdown(user.id(), ym.atDay(1), ym.atEndOfMonth())) {
                slices.add(new PieChart.Slice(t.name(), t.totalPaise(), Theme.hex(t.color())));
            }
            pie.setData(slices);

            List<BarChart.Bar> bars = new ArrayList<>();
            for (Map.Entry<YearMonth, Long> e : services.expenses().monthlyTrend(user.id(), ym, 6).entrySet()) {
                bars.add(new BarChart.Bar(Dates.MONTH_LABEL.format(e.getKey()), e.getValue()));
            }
            bar.setData(bars);

            recentModel.setRows(services.expenses().recent(user.id(), 6));
            rebuildAlerts(statuses);
        } catch (DataException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void rebuildAlerts(List<BudgetStatus> statuses) {
        alerts.removeAll();
        if (statuses.isEmpty()) {
            alerts.add(Theme.label("No budgets set for this month.", Font.PLAIN, 13, Theme.MUTED));
            alerts.add(Box.createVerticalStrut(4));
            alerts.add(Theme.label("Open the Budgets tab to add one.", Font.PLAIN, 13, Theme.MUTED));
        }
        for (BudgetStatus s : statuses) {
            String name = s.budget().isOverall() ? "Overall" : s.budget().categoryName();
            Color color = switch (s.level()) { case OK -> Theme.OK; case WARNING -> Theme.WARN; case EXCEEDED -> Theme.DANGER; };
            JLabel l = Theme.label(name + ": " + Money.format(s.spentPaise()) + " of " + Money.format(s.budget().limitPaise())
                    + " (" + s.percent() + "%)", Font.PLAIN, 13, Theme.TEXT);
            JProgressBar pb = BudgetPanel.progressBar(s);
            pb.setAlignmentX(LEFT_ALIGNMENT);
            l.setAlignmentX(LEFT_ALIGNMENT);
            l.setForeground(color == Theme.OK ? Theme.TEXT : color);
            alerts.add(l);
            alerts.add(Box.createVerticalStrut(3));
            alerts.add(pb);
            alerts.add(Box.createVerticalStrut(10));
        }
        alerts.revalidate();
        alerts.repaint();
    }
}
