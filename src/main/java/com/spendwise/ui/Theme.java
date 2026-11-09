package com.spendwise.ui;

import com.spendwise.util.Dates;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Calendar;
import java.util.Date;

/** Colours, fonts and small widget factories so every screen looks consistent. */
public final class Theme {
    public static final Color PRIMARY = new Color(0x4F46E5);
    public static final Color BG = new Color(0xF3F4F8);
    public static final Color CARD = Color.WHITE;
    public static final Color TEXT = new Color(0x111827);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color BORDER = new Color(0xE5E7EB);
    public static final Color DANGER = new Color(0xDC2626);
    public static final Color WARN = new Color(0xD97706);
    public static final Color OK = new Color(0x16A34A);
    public static final Color SIDEBAR = new Color(0x1E1B4B);
    public static final Color NEUTRAL = new Color(0x4B5563);

    private Theme() {}

    public static Font font(int style, float size) {
        return new Font(Font.SANS_SERIF, style, 13).deriveFont(style, size);
    }

    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        for (String key : new String[]{"Label.font", "TextField.font", "PasswordField.font", "ComboBox.font",
                "Button.font", "Table.font", "TableHeader.font", "Spinner.font", "List.font", "TextArea.font",
                "OptionPane.messageFont", "OptionPane.buttonFont"}) {
            UIManager.put(key, font(Font.PLAIN, 13));
        }
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", Color.WHITE);
        UIManager.put("Label.foreground", TEXT);
    }

    // ---------- widgets ----------

    public static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    public static JPanel card() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        return p;
    }

    public static JButton primary(String text) { return new RoundedButton(text, PRIMARY); }
    public static JButton neutral(String text) { return new RoundedButton(text, NEUTRAL); }
    public static JButton danger(String text) { return new RoundedButton(text, DANGER); }

    public static <T extends JComponent> T field(T c) {
        c.setFont(font(Font.PLAIN, 14));
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        return c;
    }

    public static JSpinner dateSpinner(LocalDate initial) {
        JSpinner sp = new JSpinner(new SpinnerDateModel(Dates.toDate(initial), null, null, Calendar.DAY_OF_MONTH));
        sp.setEditor(new JSpinner.DateEditor(sp, "dd MMM yyyy"));
        sp.setFont(font(Font.PLAIN, 13));
        return sp;
    }

    public static LocalDate dateOf(JSpinner sp) { return Dates.toLocalDate((Date) sp.getValue()); }

    public static JSpinner monthSpinner(YearMonth initial) {
        JSpinner sp = new JSpinner(new SpinnerDateModel(Dates.toDate(initial.atDay(1)), null, null, Calendar.MONTH));
        sp.setEditor(new JSpinner.DateEditor(sp, "MMMM yyyy"));
        sp.setFont(font(Font.PLAIN, 13));
        return sp;
    }

    public static YearMonth monthOf(JSpinner sp) { return YearMonth.from(Dates.toLocalDate((Date) sp.getValue())); }

    public static void styleTable(JTable t) {
        t.setRowHeight(30);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setSelectionBackground(new Color(0xE0E7FF));
        t.setSelectionForeground(TEXT);
        t.setFillsViewportHeight(true);
        t.setFont(font(Font.PLAIN, 13));
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(0, 34));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, false, false, r, c);
                l.setBackground(new Color(0xF9FAFB));
                l.setForeground(MUTED);
                l.setFont(font(Font.BOLD, 12));
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                        BorderFactory.createEmptyBorder(0, 10, 0, 10)));
                l.setHorizontalAlignment(tb.getColumnClass(c) == Long.class ? SwingConstants.RIGHT : SwingConstants.LEFT);
                return l;
            }
        });
    }

    public static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        if (c instanceof JTable t) sp.setColumnHeaderView(t.getTableHeader());
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        return sp;
    }

    public static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "SpendWise", JOptionPane.WARNING_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "SpendWise", JOptionPane.INFORMATION_MESSAGE);
    }

    public static Color hex(String hex) {
        try { return Color.decode(hex); } catch (NumberFormatException e) { return MUTED; }
    }

    /** Flat, rounded button with hover colour. */
    public static class RoundedButton extends JButton {
        private final Color base;
        private boolean over;

        public RoundedButton(String text, Color base) {
            super(text);
            this.base = base;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(font(Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = !isEnabled() ? new Color(0xC7C7CC) : (over || getModel().isArmed() ? base.darker() : base);
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
