package com.spendwise.ui;

import com.spendwise.util.Money;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Simple vertical bar chart with gridlines and value labels. */
public class BarChart extends JPanel {
    public record Bar(String label, long value) {}

    private List<Bar> bars = List.of();
    private Color barColor = Theme.PRIMARY;

    public BarChart() {
        setOpaque(false);
        setPreferredSize(new Dimension(300, 200));
    }

    public void setData(List<Bar> data) {
        this.bars = data;
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (bars.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int left = 44, right = 8, top = 22, bottom = 26;
        int w = getWidth() - left - right, h = getHeight() - top - bottom;
        if (w <= 0 || h <= 0) { g2.dispose(); return; }
        long max = bars.stream().mapToLong(Bar::value).max().orElse(0);
        if (max <= 0) max = 100;
        g2.setFont(Theme.font(Font.PLAIN, 11));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i <= 4; i++) {
            int y = top + h - (int) (h * i / 4.0);
            g2.setColor(Theme.BORDER);
            g2.drawLine(left, y, left + w, y);
            g2.setColor(Theme.MUTED);
            String s = Money.compact(max * i / 4);
            g2.drawString(s, left - 6 - fm.stringWidth(s), y + 4);
        }
        int slot = w / bars.size();
        int bw = Math.max(8, Math.min(48, slot - 14));
        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            int bh = (int) (h * (double) b.value() / max);
            int bx = left + i * slot + (slot - bw) / 2, by = top + h - bh;
            g2.setColor(i == bars.size() - 1 ? barColor : barColor.brighter());
            if (bh > 0) g2.fillRoundRect(bx, by, bw, bh, 6, 6);
            g2.setColor(Theme.MUTED);
            g2.drawString(b.label(), left + i * slot + (slot - fm.stringWidth(b.label())) / 2, top + h + 16);
            if (b.value() > 0) {
                String v = Money.compact(b.value());
                g2.setColor(Theme.TEXT);
                g2.drawString(v, bx + (bw - fm.stringWidth(v)) / 2, by - 4);
            }
        }
        g2.dispose();
    }
}
