package com.spendwise.ui;

import com.spendwise.Services;
import com.spendwise.model.User;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main window: dark sidebar + card-switched content area. */
public class MainFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, Refreshable> screens = new LinkedHashMap<>();

    public MainFrame(Services services, User user) {
        super("SpendWise \u2013 " + user.fullName());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 780);
        setMinimumSize(new Dimension(1050, 680));
        setLocationRelativeTo(null);

        screens.put("Dashboard", new DashboardPanel(services, user));
        screens.put("Expenses", new ExpensePanel(services, user));
        screens.put("Budgets", new BudgetPanel(services, user));
        screens.put("Reports", new ReportsPanel(services, user));
        screens.forEach((name, panel) -> content.add((Component) panel, name));

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(Theme.SIDEBAR);
        side.setPreferredSize(new Dimension(210, 0));
        side.setBorder(Theme.pad(24, 14, 20, 14));

        JLabel brand = Theme.label("SpendWise", Font.BOLD, 24, Color.WHITE);
        JLabel tag = Theme.label("Smart student expense manager", Font.PLAIN, 11, new Color(0xA5B4FC));
        brand.setAlignmentX(LEFT_ALIGNMENT);
        tag.setAlignmentX(LEFT_ALIGNMENT);
        side.add(brand);
        side.add(tag);
        side.add(Box.createVerticalStrut(28));

        ButtonGroup group = new ButtonGroup();
        for (String name : screens.keySet()) {
            NavButton b = new NavButton(name);
            b.addActionListener(e -> show(name));
            group.add(b);
            side.add(b);
            side.add(Box.createVerticalStrut(6));
            if (name.equals("Dashboard")) b.setSelected(true);
        }
        side.add(Box.createVerticalGlue());

        JLabel who = Theme.label(user.fullName(), Font.BOLD, 13, Color.WHITE);
        JLabel handle = Theme.label("@" + user.username(), Font.PLAIN, 11, new Color(0xA5B4FC));
        who.setAlignmentX(LEFT_ALIGNMENT);
        handle.setAlignmentX(LEFT_ALIGNMENT);
        JButton logout = Theme.neutral("Log out");
        logout.setAlignmentX(LEFT_ALIGNMENT);
        logout.addActionListener(e -> {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame(services).setVisible(true));
        });
        side.add(who);
        side.add(handle);
        side.add(Box.createVerticalStrut(10));
        side.add(logout);

        setLayout(new BorderLayout());
        add(side, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        show("Dashboard");
    }

    private void show(String name) {
        screens.get(name).refresh();
        cards.show(content, name);
    }

    private static final class NavButton extends JToggleButton {
        NavButton(String text) {
            super(text);
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setHorizontalAlignment(SwingConstants.LEFT);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(Theme.font(Font.BOLD, 14));
            setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 8));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics g) {
            if (isSelected() || getModel().isRollover()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() ? Theme.PRIMARY : new Color(0x312E81));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
