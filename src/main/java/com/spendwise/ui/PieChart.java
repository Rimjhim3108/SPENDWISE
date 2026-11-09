package com.spendwise.ui;

import com.spendwise.util.Money;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.List;

/** Donut chart with a legend; painted with Java2D (no external chart library). */
public class PieChart extends JPanel {
    public record Slice(String label, long value, Color color) {}

    private List<Slice> slices = List.of();

    public PieChart() {
        setOpaque(false);
        setPreferredSize(new Dimension(300, 200));
    }

    public void setData(List<Slice> data) {
        this.slices = data;
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        long total = slices.stream().mapToLong(Slice::value).sum();
        int w = getWidth(), h = getHeight();
        if (total <= 0) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.font(Font.PLAIN, 13));
            String msg = "No expenses in this period";
            g2.drawString(msg, (w - g2.getFontMetrics().stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }
        int d = Math.max(60, Math.min(h - 10, (int) (w * 0.5)));
        int x = 6, y = (h - d) / 2;
        double start = 90;
        for (Slice s : slices) {
            double extent = -360.0 * s.value() / total;
            g2.setColor(s.color());
            g2.fill(new Arc2D.Double(x, y, d, d, start, extent, Arc2D.PIE));
            start += extent;
        }
        int hole = (int) (d * 0.58);
        g2.setColor(Theme.CARD);
        g2.fill(new Ellipse2D.Double(x + (d - hole) / 2.0, y + (d - hole) / 2.0, hole, hole));
        g2.setColor(Theme.TEXT);
        g2.setFont(Theme.font(Font.BOLD, 13));
        String t = Money.format(total);
        g2.drawString(t, x + (d - g2.getFontMetrics().stringWidth(t)) / 2, y + d / 2 + 5);

        int lx = x + d + 20, ly = Math.max(10, (h - Math.min(slices.size(), 8) * 22) / 2) + 12;
        g2.setFont(Theme.font(Font.PLAIN, 12));
        int shown = 0;
        for (Slice s : slices) {
            if (shown++ >= 8) break;
            g2.setColor(s.color());
            g2.fillRoundRect(lx, ly - 10, 12, 12, 4, 4);
            g2.setColor(Theme.TEXT);
            String label = s.label() + "  " + Math.round(100.0 * s.value() / total) + "%";
            g2.drawString(label, lx + 18, ly);
            ly += 22;
        }
        g2.dispose();
    }
}
