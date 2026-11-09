package com.spendwise.ui;

import com.spendwise.util.Dates;
import com.spendwise.util.Money;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.LocalDate;

/** Cell renderers shared by tables that show expenses. */
public final class TableRenderers {
    private TableRenderers() {}

    public static void install(JTable table) {
        table.setDefaultRenderer(LocalDate.class, new Padded(false) {
            @Override protected String text(Object v) { return Dates.DISPLAY.format((LocalDate) v); }
        });
        table.setDefaultRenderer(Long.class, new Padded(true) {
            @Override protected String text(Object v) { return Money.format((Long) v); }
        });
        table.setDefaultRenderer(String.class, new Padded(false) {
            @Override protected String text(Object v) { return v == null ? "" : v.toString(); }
        });
    }

    private abstract static class Padded extends DefaultTableCellRenderer {
        private final boolean right;
        Padded(boolean right) { this.right = right; }
        protected abstract String text(Object v);

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, text(v), sel, false, r, c);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (!sel) setBackground(r % 2 == 0 ? Color.WHITE : new Color(0xFAFAFC));
            if (right) setFont(Theme.font(Font.BOLD, 13));
            return this;
        }
    }
}
