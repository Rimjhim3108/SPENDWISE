package com.spendwise.dao;

import com.spendwise.db.DataException;
import com.spendwise.db.Database;
import com.spendwise.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CategoryDao {
    /** name, hex colour */
    public static final String[][] DEFAULTS = {
        {"Food & Dining", "#F97316"}, {"Transport", "#3B82F6"}, {"Education", "#8B5CF6"},
        {"Books & Supplies", "#14B8A6"}, {"Rent & Hostel", "#EF4444"}, {"Entertainment", "#EC4899"},
        {"Health", "#22C55E"}, {"Shopping", "#EAB308"}, {"Bills & Recharge", "#06B6D4"}, {"Other", "#6B7280"}
    };

    private final Database db;

    public CategoryDao(Database db) { this.db = db; }

    public List<Category> list(long userId) {
        String sql = "SELECT id, name, color FROM categories WHERE user_id = ? ORDER BY name COLLATE NOCASE";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            List<Category> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Category(rs.getLong(1), rs.getString(2), rs.getString(3)));
            }
            return out;
        } catch (SQLException e) {
            throw new DataException("Could not load categories: " + e.getMessage(), e);
        }
    }

    public Optional<Category> find(long userId, long id) {
        String sql = "SELECT id, name, color FROM categories WHERE user_id = ? AND id = ?";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Category(rs.getLong(1), rs.getString(2), rs.getString(3)))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataException("Could not load category: " + e.getMessage(), e);
        }
    }

    public boolean existsByName(long userId, String name) {
        String sql = "SELECT 1 FROM categories WHERE user_id = ? AND name = ? COLLATE NOCASE";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            throw new DataException("Could not check category: " + e.getMessage(), e);
        }
    }

    public Category add(long userId, String name, String color) {
        String sql = "INSERT INTO categories(user_id, name, color) VALUES (?,?,?)";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setString(3, color);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Category(rs.getLong(1), name, color);
            }
        } catch (SQLException e) {
            throw new DataException("Could not add category: " + e.getMessage(), e);
        }
    }

    public int countExpenses(long userId, long categoryId) {
        String sql = "SELECT COUNT(*) FROM expenses WHERE user_id = ? AND category_id = ?";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, categoryId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) {
            throw new DataException("Could not count expenses: " + e.getMessage(), e);
        }
    }

    /** Deletes the category and any budgets that point at it. */
    public void delete(long userId, long categoryId) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM budgets WHERE user_id = ? AND category_id = ?")) {
                    ps.setLong(1, userId);
                    ps.setLong(2, categoryId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM categories WHERE user_id = ? AND id = ?")) {
                    ps.setLong(1, userId);
                    ps.setLong(2, categoryId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataException("Could not delete category: " + e.getMessage(), e);
        }
    }
}
