package com.spendwise.ui;

import com.spendwise.Services;
import com.spendwise.db.DataException;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.User;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.ValidationException;
import com.spendwise.util.Money;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Add / edit expense dialog. Use {@link #show} which returns true when something was saved. */
public final class ExpenseDialog extends JDialog {
    private final Services services;
    private final User user;
    private final Expense existing;
    private boolean saved;

    private final JTextField amount = Theme.field(new JTextField(14));
    private final JComboBox<Category> category;
    private final JSpinner date = Theme.dateSpinner(LocalDate.now());
    private final JTextField description = Theme.field(new JTextField(22));
    private final JComboBox<String> method = new JComboBox<>(ExpenseService.PAYMENT_METHODS.toArray(new String[0]));

    private ExpenseDialog(Window owner, Services services, User user, Expense existing) {
        super(owner, existing == null ? "Add Expense" : "Edit Expense", ModalityType.APPLICATION_MODAL);
        this.services = services;
        this.user = user;
        this.existing = existing;
        List<Category> cats = services.expenses().categories(user.id());
        category = new JComboBox<>(cats.toArray(new Category[0]));

        if (existing != null) {
            amount.setText(Money.plain(existing.amountPaise()));
            for (Category c : cats) if (c.id() == existing.categoryId()) category.setSelectedItem(c);
            date.setValue(com.spendwise.util.Dates.toDate(existing.date()));
            description.setText(existing.description());
            method.setSelectedItem(existing.paymentMethod());
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(Theme.pad(20, 24, 8, 24));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 0, 6, 12);
        gc.anchor = GridBagConstraints.WEST;
        addRow(form, gc, 0, "Amount (" + Money.SYMBOL + ")", amount);
        addRow(form, gc, 1, "Category", category);
        addRow(form, gc, 2, "Date", date);
        addRow(form, gc, 3, "Description", description);
        addRow(form, gc, 4, "Payment method", method);

        JButton save = Theme.primary("Save");
        JButton cancel = Theme.neutral("Cancel");
        save.addActionListener(e -> onSave());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.setBackground(Color.WHITE);
        buttons.setBorder(Theme.pad(0, 16, 8, 16));
        buttons.add(cancel);
        buttons.add(save);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(amount::requestFocusInWindow);
    }

    private static void addRow(JPanel p, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridy = row;
        gc.gridx = 0;
        gc.fill = GridBagConstraints.NONE;
        p.add(Theme.label(label, Font.BOLD, 12, Theme.MUTED), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        p.add(field, gc);
        gc.weightx = 0;
    }

    private void onSave() {
        try {
            Category c = (Category) category.getSelectedItem();
            if (c == null) throw new ValidationException("Choose a category.");
            Expense e = new Expense(existing == null ? 0 : existing.id(), user.id(), c.id(), c.name(),
                    Money.parse(amount.getText()), description.getText(), Theme.dateOf(date),
                    (String) method.getSelectedItem());
            services.expenses().save(e);
            saved = true;
            List<String> alerts = services.expenses().budgetAlerts(user.id(), YearMonth.from(e.date()));
            dispose();
            if (!alerts.isEmpty()) {
                Theme.info(getOwner(), "Budget alert:\n\u2022 " + String.join("\n\u2022 ", alerts));
            }
        } catch (ValidationException | IllegalArgumentException ex) {
            Theme.error(this, ex.getMessage());
        } catch (DataException ex) {
            Theme.error(this, "Database error: " + ex.getMessage());
        }
    }

    /** @return true if an expense was saved. */
    public static boolean show(Component parent, Services services, User user, Expense existing) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        ExpenseDialog d = new ExpenseDialog(owner, services, user, existing);
        d.setVisible(true);
        return d.saved;
    }
}
