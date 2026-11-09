package com.spendwise.ui;

import com.spendwise.Services;
import com.spendwise.db.DataException;
import com.spendwise.model.BudgetStatus;
import com.spendwise.model.Category;
import com.spendwise.model.User;
import com.spendwise.service.ValidationException;
import com.spendwise.util.Money;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Monthly budgets with usage bars, plus category management. */
public class BudgetPanel extends JPanel implements Refreshable {
    private final Services services;
    private final User user;
    private final JSpinner month = Theme.monthSpinner(YearMonth.now());
    private final StatusModel model = new StatusModel();
    private final JTable table = new JTable(model);
    private final DefaultListModel<Category> catModel = new DefaultListModel<>();
    private final JList<Category> catList = new JList<>(catModel);

    public BudgetPanel(Services services, User user) {
        this.services = services;
        this.user = user;
        setLayout(new BorderLayout(16, 12));
        setBorder(Theme.pad(24, 24, 24, 24));
        setBackground(Theme.BG);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Theme.label("Budgets", Font.BOLD, 24, Theme.TEXT), BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(month);
        JButton set = Theme.primary("Set budget");
        JButton del = Theme.danger("Remove");
        set.addActionListener(e -> setBudget());
        del.addActionListener(e -> removeBudget());
        right.add(set);
        right.add(del);
        head.add(right, BorderLayout.EAST);
        month.addChangeListener(e -> reload());
        add(head, BorderLayout.NORTH);

        Theme.styleTable(table);
        table.setRowHeight(36);
        table.setDefaultRenderer(Long.class, (t, v, s, f, r, c) -> {
            JLabel l = new JLabel(Money.format((Long) v), SwingConstants.RIGHT);
            l.setOpaque(true);
            l.setBorder(Theme.pad(0, 10, 0, 10));
            l.setBackground(s ? t.getSelectionBackground() : Color.WHITE);
            l.setFont(Theme.font(Font.PLAIN, 13));
            if (c == 3 && (Long) v < 0) l.setForeground(Theme.DANGER);
            return l;
        });
        table.setDefaultRenderer(String.class, (t, v, s, f, r, c) -> {
            JLabel l = new JLabel(String.valueOf(v));
            l.setOpaque(true);
            l.setBorder(Theme.pad(0, 10, 0, 10));
            l.setBackground(s ? t.getSelectionBackground() : Color.WHITE);
            l.setFont(Theme.font(Font.BOLD, 13));
            return l;
        });
        table.setDefaultRenderer(BudgetStatus.class, new ProgressRenderer());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] widths = {210, 120, 120, 120, 200};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        add(Theme.scroll(table), BorderLayout.CENTER);

        // categories
        JPanel cats = Theme.card();
        cats.setPreferredSize(new Dimension(250, 0));
        cats.add(Theme.label("Categories", Font.BOLD, 14, Theme.TEXT), BorderLayout.NORTH);
        catList.setCellRenderer((list, c, i, sel, foc) -> {
            JLabel l = new JLabel("  " + c.name());
            l.setOpaque(true);
            l.setIcon(new ColorDot(Theme.hex(c.color())));
            l.setBorder(Theme.pad(4, 4, 4, 4));
            l.setBackground(sel ? new Color(0xE0E7FF) : Color.WHITE);
            return l;
        });
        cats.add(new JScrollPane(catList), BorderLayout.CENTER);
        JPanel catButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        catButtons.setOpaque(false);
        catButtons.setBorder(Theme.pad(10, 0, 0, 0));
        JButton addCat = Theme.primary("Add");
        JButton delCat = Theme.neutral("Delete");
        addCat.addActionListener(e -> addCategory());
        delCat.addActionListener(e -> deleteCategory());
        catButtons.add(addCat);
        catButtons.add(delCat);
        cats.add(catButtons, BorderLayout.SOUTH);
        add(cats, BorderLayout.EAST);

        refresh();
    }

    @Override public void refresh() {
        catModel.clear();
        for (Category c : services.expenses().categories(user.id())) catModel.addElement(c);
        reload();
    }

    private void reload() {
        try {
            model.setRows(services.expenses().budgetStatuses(user.id(), Theme.monthOf(month)));
        } catch (DataException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void setBudget() {
        List<Object> choices = new ArrayList<>();
        choices.add("Overall (all categories)");
        for (int i = 0; i < catModel.size(); i++) choices.add(catModel.get(i));
        JComboBox<Object> box = new JComboBox<>(choices.toArray());
        JTextField limit = Theme.field(new JTextField(12));
        JPanel p = new JPanel(new GridLayout(4, 1, 0, 6));
        p.add(new JLabel("Budget for"));
        p.add(box);
        p.add(new JLabel("Monthly limit (" + Money.SYMBOL + ")"));
        p.add(limit);
        int ok = JOptionPane.showConfirmDialog(this, p, "Set budget for " + Theme.monthOf(month),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            Object sel = box.getSelectedItem();
            long catId = sel instanceof Category c ? c.id() : 0;
            services.expenses().setBudget(user.id(), catId, Theme.monthOf(month), Money.parse(limit.getText()));
            reload();
        } catch (ValidationException | IllegalArgumentException ex) {
            Theme.error(this, ex.getMessage());
        } catch (DataException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void removeBudget() {
        int row = table.getSelectedRow();
        if (row < 0) { Theme.info(this, "Select a budget first."); return; }
        services.expenses().deleteBudget(user.id(), model.rows.get(table.convertRowIndexToModel(row)).budget().id());
        reload();
    }

    private void addCategory() {
        String name = JOptionPane.showInputDialog(this, "New category name:", "Add category", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        try {
            services.expenses().addCategory(user.id(), name);
            refresh();
        } catch (ValidationException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private void deleteCategory() {
        Category c = catList.getSelectedValue();
        if (c == null) { Theme.info(this, "Select a category first."); return; }
        if (JOptionPane.showConfirmDialog(this, "Delete category '" + c.name() + "' and its budgets?",
                "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            services.expenses().deleteCategory(user.id(), c);
            refresh();
        } catch (ValidationException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    /** Shared with the dashboard: a coloured usage bar for one budget. */
    static JProgressBar progressBar(BudgetStatus s) {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(Math.min(100, s.percent()));
        pb.setStringPainted(false);
        pb.setBorderPainted(false);
        pb.setForeground(switch (s.level()) { case OK -> Theme.OK; case WARNING -> Theme.WARN; case EXCEEDED -> Theme.DANGER; });
        pb.setBackground(new Color(0xE5E7EB));
        pb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        pb.setPreferredSize(new Dimension(100, 10));
        return pb;
    }

    private static final class ColorDot implements Icon {
        private final Color color;
        ColorDot(Color c) { color = c; }
        public int getIconWidth() { return 12; }
        public int getIconHeight() { return 12; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y, 12, 12);
            g2.dispose();
        }
    }

    /** Paints a rounded usage bar with the percentage; drawn by hand so it needs no layout. */
    private static final class ProgressRenderer extends JComponent implements TableCellRenderer {
        private BudgetStatus status;
        private boolean selected;
        private Color selectionColor = Color.WHITE;

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            status = (BudgetStatus) v;
            selected = sel;
            selectionColor = t.getSelectionBackground();
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(selected ? selectionColor : Color.WHITE);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (status != null) {
                int x = 10, w = getWidth() - 20, h = 16, y = (getHeight() - h) / 2;
                g2.setColor(new Color(0xE5E7EB));
                g2.fillRoundRect(x, y, w, h, h, h);
                Color fill = switch (status.level()) {
                    case OK -> Theme.OK; case WARNING -> Theme.WARN; case EXCEEDED -> Theme.DANGER; };
                int fw = (int) (w * Math.min(100, status.percent()) / 100.0);
                if (fw > 0) {
                    g2.setColor(fill);
                    g2.fillRoundRect(x, y, Math.max(fw, h), h, h, h);
                }
                g2.setFont(Theme.font(Font.BOLD, 11));
                String label = status.percent() + "%";
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Theme.TEXT);
                g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + (h + fm.getAscent()) / 2 - 2);
            }
            g2.dispose();
        }
    }

    private static final class StatusModel extends AbstractTableModel {
        private static final String[] COLS = {"Budget", "Limit", "Spent", "Remaining", "Usage"};
        List<BudgetStatus> rows = new ArrayList<>();

        void setRows(List<BudgetStatus> r) { rows = r; fireTableDataChanged(); }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Class<?> getColumnClass(int c) {
            return c == 0 ? String.class : c == 4 ? BudgetStatus.class : Long.class;
        }
        public Object getValueAt(int r, int c) {
            BudgetStatus s = rows.get(r);
            return switch (c) {
                case 0 -> s.budget().categoryName();
                case 1 -> s.budget().limitPaise();
                case 2 -> s.spentPaise();
                case 3 -> s.remainingPaise();
                default -> s;
            };
        }
    }
}
