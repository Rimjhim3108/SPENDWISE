package com.spendwise.ui;

import com.spendwise.model.Expense;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ExpenseTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Date", "Category", "Description", "Payment", "Amount"};
    private List<Expense> rows = new ArrayList<>();

    public void setRows(List<Expense> rows) {
        this.rows = new ArrayList<>(rows);
        fireTableDataChanged();
    }

    public Expense get(int modelRow) { return rows.get(modelRow); }
    public List<Expense> all() { return List.copyOf(rows); }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int c) { return COLUMNS[c]; }
    @Override public boolean isCellEditable(int r, int c) { return false; }

    @Override public Class<?> getColumnClass(int c) {
        return switch (c) { case 0 -> LocalDate.class; case 4 -> Long.class; default -> String.class; };
    }

    @Override public Object getValueAt(int r, int c) {
        Expense e = rows.get(r);
        return switch (c) {
            case 0 -> e.date();
            case 1 -> e.categoryName();
            case 2 -> e.description();
            case 3 -> e.paymentMethod();
            default -> e.amountPaise();
        };
    }
}
