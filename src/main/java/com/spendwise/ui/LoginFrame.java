package com.spendwise.ui;

import com.spendwise.Services;
import com.spendwise.db.DataException;
import com.spendwise.model.User;
import com.spendwise.service.ValidationException;

import javax.swing.*;
import java.awt.*;

/** Sign-in / create-account window. */
public class LoginFrame extends JFrame {
    private final Services services;
    private final JTextField username = Theme.field(new JTextField(18));
    private final JTextField fullName = Theme.field(new JTextField(18));
    private final JPasswordField password = Theme.field(new JPasswordField(18));
    private final JLabel nameLabel = Theme.label("Full name", Font.BOLD, 12, Theme.MUTED);
    private final JLabel message = Theme.label(" ", Font.PLAIN, 12, Theme.DANGER);
    private final JButton submit = Theme.primary("Sign in");
    private final JButton toggle = Theme.neutral("Create a new account");
    private boolean registering;

    public LoginFrame(Services services) {
        super("SpendWise \u2013 Sign in");
        this.services = services;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.SIDEBAR);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(Theme.pad(32, 40, 32, 40));

        JLabel title = Theme.label("SpendWise", Font.BOLD, 30, Theme.PRIMARY);
        JLabel sub = Theme.label("Smart Student Expense Management System", Font.PLAIN, 12, Theme.MUTED);
        for (JComponent c : new JComponent[]{title, sub, nameLabel, fullName,
                Theme.label("Username", Font.BOLD, 12, Theme.MUTED), username,
                Theme.label("Password", Font.BOLD, 12, Theme.MUTED), password, message, submit, toggle}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
        }
        card.add(title);
        card.add(sub);
        card.add(Box.createVerticalStrut(22));
        card.add(nameLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(fullName);
        card.add(Box.createVerticalStrut(12));
        JLabel ul = Theme.label("Username", Font.BOLD, 12, Theme.MUTED);
        ul.setAlignmentX(LEFT_ALIGNMENT);
        card.add(ul);
        card.add(Box.createVerticalStrut(4));
        card.add(username);
        card.add(Box.createVerticalStrut(12));
        JLabel pl = Theme.label("Password", Font.BOLD, 12, Theme.MUTED);
        pl.setAlignmentX(LEFT_ALIGNMENT);
        card.add(pl);
        card.add(Box.createVerticalStrut(4));
        card.add(password);
        card.add(Box.createVerticalStrut(8));
        card.add(message);
        card.add(Box.createVerticalStrut(8));
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        toggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        card.add(submit);
        card.add(Box.createVerticalStrut(8));
        card.add(toggle);
        root.add(card);
        setContentPane(root);

        submit.addActionListener(e -> submit());
        toggle.addActionListener(e -> setRegistering(!registering));
        getRootPane().setDefaultButton(submit);
        setRegistering(false);
        setSize(520, 560);
        setMinimumSize(new Dimension(480, 540));
        setLocationRelativeTo(null);
    }

    private void setRegistering(boolean on) {
        registering = on;
        nameLabel.setVisible(on);
        fullName.setVisible(on);
        submit.setText(on ? "Create account" : "Sign in");
        toggle.setText(on ? "I already have an account" : "Create a new account");
        setTitle("SpendWise \u2013 " + (on ? "Create account" : "Sign in"));
        message.setText(" ");
        (on ? fullName : username).requestFocusInWindow();
    }

    private void submit() {
        try {
            User user = registering
                    ? services.auth().register(username.getText(), fullName.getText(), password.getPassword())
                    : services.auth().login(username.getText(), password.getPassword());
            dispose();
            new MainFrame(services, user).setVisible(true);
        } catch (ValidationException ex) {
            message.setText(ex.getMessage());
            password.setText("");
        } catch (DataException ex) {
            message.setText("Database error: " + ex.getMessage());
        }
    }
}
