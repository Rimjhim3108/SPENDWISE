package com.spendwise.dao;

import com.spendwise.db.DataException;
import com.spendwise.db.Database;
import com.spendwise.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public final class UserDao {
    public record Stored(User user, String hash, String salt) {}

    private final Database db;

    public UserDao(Database db) { this.db = db; }

    /** Creates the user and their default categories in one transaction. */
    public User create(String username, String fullName, String hash, String salt) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO users(username, full_name, password_hash, salt) VALUES (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, username);
                    ps.setString(2, fullName);
                    ps.setString(3, hash);
                    ps.setString(4, salt);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO categories(user_id, name, color) VALUES (?,?,?)")) {
                    for (String[] d : CategoryDao.DEFAULTS) {
                        ps.setLong(1, id);
                        ps.setString(2, d[0]);
                        ps.setString(3, d[1]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
                return new User(id, username, fullName);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataException("Could not create account: " + e.getMessage(), e);
        }
    }

    public Optional<Stored> find(String username) {
        String sql = "SELECT id, username, full_name, password_hash, salt FROM users WHERE username = ?";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                User u = new User(rs.getLong(1), rs.getString(2), rs.getString(3));
                return Optional.of(new Stored(u, rs.getString(4), rs.getString(5)));
            }
        } catch (SQLException e) {
            throw new DataException("Could not read user: " + e.getMessage(), e);
        }
    }
}
