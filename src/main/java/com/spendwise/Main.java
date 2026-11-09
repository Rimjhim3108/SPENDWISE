package com.spendwise;

import com.spendwise.db.DataException;
import com.spendwise.db.Database;
import com.spendwise.ui.LoginFrame;
import com.spendwise.ui.Theme;

import javax.swing.*;

/** Entry point. Data is stored in ~/.spendwise/spendwise.db */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            try {
                Services services = Services.create(Database.defaultDatabase());
                new LoginFrame(services).setVisible(true);
            } catch (DataException e) {
                JOptionPane.showMessageDialog(null, "SpendWise could not open its database:\n" + e.getMessage(),
                        "SpendWise", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
