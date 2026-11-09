package com.spendwise.dao;

import com.spendwise.db.DataException;
import com.spendwise.db.Database;
import com.spendwise.model.CategoryTotal;
import com.spendwise.model.Expense;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExpenseDao {
    private static final String SELECT =
        "SELECT e.id, e.user_id, e.category_id, c.name, e.amount, e.description, e.expense_date, e.payment_method "
        + "FROM expenses e JOIN categories c ON c.id = e.category_id ";

    private final Database db;

    public ExpenseDao(Database db) { this.db = db; }

    public long insert(Expense e) {
        String sql = "INSERT INTO expenses(user_id, category_id, amount, description, expense_date, payment_method) "
                   + "VALUES (?,?,?,?,?,?)";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, e.userId());
            ps.setLong(2, e.categoryId());
            ps.setLong(3, e.amountPaise());
            ps.setString(4, e.description());
            ps.setString(5, e.date().toString());
            ps.setString(6, e.paymentMethod());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getLong(1) : -1; }
        } catch (SQLException ex) {
            throw new DataException("Could not save expense: " + ex.getMessage(), ex);
        }
    }

    public boolean update(Expense e) {
        String sql = "UPDATE expenses SET category_id=?, amount=?, description=?, expense_date=?, payment_method=? "
                   + "WHERE id=? AND user_id=?";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, e.categoryId());
            ps.setLong(2, e.amountPaise());
            ps.setString(3, e.description());
            ps.setString(4, e.date().toString());
            ps.setString(5, e.paymentMethod());
            ps.setLong(6, e.id());
            ps.setLong(7, e.userId());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataException("Could not update expense: " + ex.getMessage(), ex);
        }
    }

    public boolean delete(long userId, long id) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM expenses WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataException("Could not delete expense: " + ex.getMessage(), ex);
        }
    }

    /** categoryId and text are optional filters (null / blank = no filter). */
    public List<Expense> search(long userId, LocalDate from, LocalDate to, Long categoryId, String text) {
        StringBuilder sql = new StringBuilder(SELECT)
                .append("WHERE e.user_id = ? AND e.expense_date BETWEEN ? AND ? ");
        List<Object> params = new ArrayList<>(List.of(userId, from.toString(), to.toString()));
        if (categoryId != null) {
            sql.append("AND e.category_id = ? ");
            params.add(categoryId);
        }
        if (text != null && !text.isBlank()) {
            String like = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
            sql.append("AND (LOWER(e.description) LIKE ? ESCAPE '\\' OR LOWER(c.name) LIKE ? ESCAPE '\\') ");
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY e.expense_date DESC, e.id DESC");
        return query(sql.toString(), params);
    }

    public List<Expense> recent(long userId, int limit) {
        return query(SELECT + "WHERE e.user_id = ? ORDER BY e.expense_date DESC, e.id DESC LIMIT ?",
                     List.of(userId, limit));
    }

    public long total(long userId, LocalDate from, LocalDate to, Long categoryId) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE user_id = ? AND expense_date BETWEEN ? AND ?"
                   + (categoryId != null ? " AND category_id = ?" : "");
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            if (categoryId != null) ps.setLong(4, categoryId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        } catch (SQLException ex) {
            throw new DataException("Could not total expenses: " + ex.getMessage(), ex);
        }
    }

    public List<CategoryTotal> totalsByCategory(long userId, LocalDate from, LocalDate to) {
        String sql = "SELECT c.name, c.color, SUM(e.amount), COUNT(*) FROM expenses e "
                   + "JOIN categories c ON c.id = e.category_id "
                   + "WHERE e.user_id = ? AND e.expense_date BETWEEN ? AND ? "
                   + "GROUP BY c.id ORDER BY SUM(e.amount) DESC";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            List<CategoryTotal> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new CategoryTotal(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getInt(4)));
            }
            return out;
        } catch (SQLException ex) {
            throw new DataException("Could not summarise expenses: " + ex.getMessage(), ex);
        }
    }

    /** Key = "yyyy-MM". Months without expenses are absent. */
    public Map<String, Long> totalsByMonth(long userId, LocalDate from, LocalDate to) {
        String sql = "SELECT substr(expense_date, 1, 7), SUM(amount) FROM expenses "
                   + "WHERE user_id = ? AND expense_date BETWEEN ? AND ? GROUP BY 1 ORDER BY 1";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            Map<String, Long> out = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString(1), rs.getLong(2));
            }
            return out;
        } catch (SQLException ex) {
            throw new DataException("Could not load monthly totals: " + ex.getMessage(), ex);
        }
    }

    private List<Expense> query(String sql, List<Object> params) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            List<Expense> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Expense(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                            rs.getLong(5), rs.getString(6), LocalDate.parse(rs.getString(7)), rs.getString(8)));
                }
            }
            return out;
        } catch (SQLException ex) {
            throw new DataException("Could not load expenses: " + ex.getMessage(), ex);
        }
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
