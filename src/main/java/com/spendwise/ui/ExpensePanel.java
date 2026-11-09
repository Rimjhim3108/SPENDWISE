package com.spendwise.ui;

import com.spendwise.Services;
import com.spendwise.db.DataException;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.User;
import com.spendwise.service.ValidationException;
import com.spendwise.util.Money;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** Expense list with filters, add / edit / delete and CSV export. */
public class ExpensePanel extends JPanel implements Refreshable {
    private static final Category ALL = new Category(0, "All categories", "#000000");

    private final Services services;
    private final User user;
    private final ExpenseTableModel model = new ExpenseTableModel();
    private final JTable table = new JTable(model);
    private final JSpinner from = Theme.dateSpinner(LocalDate.now().withDayOfMonth(1));
    private final JSpinner to = Theme.dateSpinner(LocalDate.now());
    private final JComboBox<Category> categoryBox = new JComboBox<>();
    private final JTextField search = Theme.field(new JTextField(14));
    private final JLabel footer = Theme.label(" ", Font.BOLD, 13, Theme.TEXT);
    private boolean updatingCombo;

    public ExpensePanel(Services services, User user) {
        this.services = services;
        this.user = user;
        setLayout(new BorderLayout(0, 12));
        setBorder(Theme.pad(24, 24, 24, 24));
        setBackground(Theme.BG);

        // header
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Theme.label("Expenses", Font.BOLD, 24, Theme.TEXT), BorderLayout.WEST);
        JButton add = Theme.primary("+ Add Expense");
        add.addActionListener(e -> addExpense());
        head.add(add, BorderLayout.EAST);

        // filters
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filters.setOpaque(false);
        filters.add(Theme.label("From", Font.PLAIN, 12, Theme.MUTED));
        filters.add(from);
        filters.add(Theme.label("To", Font.PLAIN, 12, Theme.MUTED));
        filters.add(to);
        filters.add(categoryBox);
        filters.add(Theme.label("Search", Font.PLAIN, 12, Theme.MUTED));
        filters.add(search);
        JButton apply = Theme.primary("Apply");
        JButton reset = Theme.neutral("This month");
        apply.addActionListener(e -> reload());
        reset.addActionListener(e -> resetFilters());
        filters.add(apply);
        filters.add(reset);
        categoryBox.addActionListener(e -> { if (!updatingCombo) reload(); });
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { reload(); }
            public void removeUpdate(DocumentEvent e) { reload(); }
            public void changedUpdate(DocumentEvent e) { }
        });

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(filters, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        // table
        Theme.styleTable(table);
        TableRenderers.install(table);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) editSelected(); }
        });
        add(Theme.scroll(table), BorderLayout.CENTER);

        // footer
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(footer, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton edit = Theme.neutral("Edit");
        JButton del = Theme.danger("Delete");
        JButton export = Theme.neutral("Export CSV");
        edit.addActionListener(e -> editSelected());
        del.addActionListener(e -> deleteSelected());
        export.addActionListener(e -> exportCsv());
        actions.add(edit);
        actions.add(del);
        actions.add(export);
        bottom.add(actions, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        refresh();
    }

    @Override public void refresh() {
        Category selected = (Category) categoryBox.getSelectedItem();
        updatingCombo = true;
        categoryBox.removeAllItems();
        categoryBox.addItem(ALL);
        for (Category c : services.expenses().categories(user.id())) categoryBox.addItem(c);
        if (selected != null) {
            for (int i = 0; i < categoryBox.getItemCount(); i++) {
                if (categoryBox.getItemAt(i).id() == selected.id()) categoryBox.setSelectedIndex(i);
            }
        }
        updatingCombo = false;
        reload();
    }

    private void resetFilters() {
        LocalDate now = LocalDate.now();
        from.setValue(com.spendwise.util.Dates.toDate(now.withDayOfMonth(1)));
        to.setValue(com.spendwise.util.Dates.toDate(now));
        search.setText("");
        categoryBox.setSelectedIndex(0);
        reload();
    }

    private void reload() {
        if (updatingCombo) return;
        try {
            Category c = (Category) categoryBox.getSelectedItem();
            Long catId = (c == null || c.id() == 0) ? null : c.id();
            List<Expense> rows = services.expenses().search(user.id(), Theme.dateOf(from), Theme.dateOf(to),
                    catId, search.getText());
            model.setRows(rows);
            long total = rows.stream().mapToLong(Expense::amountPaise).sum();
            footer.setText(rows.size() + " transaction(s)  \u2022  Total " + Money.format(total));
        } catch (ValidationException ex) {
            model.setRows(List.of());
            footer.setText(ex.getMessage());
        } catch (DataException ex) {
            Theme.error(this, ex.getMessage());
        }
    }

    private Expense selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Theme.info(this, "Select an expense first.");
            return null;
        }
        return model.get(table.convertRowIndexToModel(row));
    }

    private void addExpense() {
        if (ExpenseDialog.show(this, services, user, null)) reload();
    }

    private void editSelected() {
        Expense e = selected();
        if (e != null && ExpenseDialog.show(this, services, user, e)) reload();
    }

    private void deleteSelected() {
        Expense e = selected();
        if (e == null) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Delete " + Money.format(e.amountPaise()) + " (" + e.categoryName() + ")?",
                "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            try {
                services.expenses().delete(user.id(), e.id());
                reload();
            } catch (DataException ex) {
                Theme.error(this, ex.getMessage());
            }
        }
    }

    private void exportCsv() {
        if (model.getRowCount() == 0) {
            Theme.info(this, "There is nothing to export for the current filters.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("spendwise-expenses.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = fc.getSelectedFile().toPath();
        if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "Overwrite " + path.getFileName() + "?",
                "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            services.export().exportCsv(model.all(), path);
            Theme.info(this, "Exported " + model.getRowCount() + " rows to\n" + path);
        } catch (IOException ex) {
            Theme.error(this, "Could not write file: " + ex.getMessage());
        }
    }
}
