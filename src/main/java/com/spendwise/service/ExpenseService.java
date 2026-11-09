package com.spendwise.service;

import com.spendwise.dao.BudgetDao;
import com.spendwise.dao.CategoryDao;
import com.spendwise.dao.ExpenseDao;
import com.spendwise.db.Database;
import com.spendwise.model.*;
import com.spendwise.util.Money;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Business rules for expenses, categories, budgets and reports. */
public final class ExpenseService {
    public static final List<String> PAYMENT_METHODS = List.of("Cash", "UPI", "Card", "Net Banking", "Other");
    private static final long MAX_AMOUNT_PAISE = 1_000_000_000L; // 1 crore rupees
    private static final String[] PALETTE = {"#F43F5E", "#0EA5E9", "#84CC16", "#A855F7", "#F59E0B", "#10B981", "#6366F1", "#D946EF"};

    private final ExpenseDao expenses;
    private final CategoryDao categories;
    private final BudgetDao budgets;

    public ExpenseService(Database db) {
        this.expenses = new ExpenseDao(db);
        this.categories = new CategoryDao(db);
        this.budgets = new BudgetDao(db);
    }

    // ---------- expenses ----------

    /** Inserts when expense.id() == 0, otherwise updates. Returns the expense id. */
    public long save(Expense e) {
        if (e.amountPaise() <= 0) throw new ValidationException("Amount must be greater than zero.");
        if (e.amountPaise() > MAX_AMOUNT_PAISE) throw new ValidationException("Amount is too large.");
        if (e.date() == null) throw new ValidationException("Choose a date.");
        if (e.date().isAfter(LocalDate.now())) throw new ValidationException("Date cannot be in the future.");
        if (!PAYMENT_METHODS.contains(e.paymentMethod())) throw new ValidationException("Choose a payment method.");
        String desc = e.description() == null ? "" : e.description().trim();
        if (desc.length() > 120) throw new ValidationException("Description is limited to 120 characters.");
        if (categories.find(e.userId(), e.categoryId()).isEmpty()) throw new ValidationException("Choose a category.");

        Expense clean = new Expense(e.id(), e.userId(), e.categoryId(), e.categoryName(),
                e.amountPaise(), desc, e.date(), e.paymentMethod());
        if (e.id() == 0) return expenses.insert(clean);
        if (!expenses.update(clean)) throw new ValidationException("That expense no longer exists.");
        return e.id();
    }

    public void delete(long userId, long expenseId) { expenses.delete(userId, expenseId); }

    public List<Expense> search(long userId, LocalDate from, LocalDate to, Long categoryId, String text) {
        if (from.isAfter(to)) throw new ValidationException("'From' date must not be after 'To' date.");
        return expenses.search(userId, from, to, categoryId, text);
    }

    public List<Expense> recent(long userId, int limit) { return expenses.recent(userId, limit); }

    public long total(long userId, LocalDate from, LocalDate to) { return expenses.total(userId, from, to, null); }

    // ---------- categories ----------

    public List<Category> categories(long userId) { return categories.list(userId); }

    public Category addCategory(long userId, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 30) throw new ValidationException("Category name must be 1-30 characters.");
        if (categories.existsByName(userId, n)) throw new ValidationException("That category already exists.");
        String color = PALETTE[categories.list(userId).size() % PALETTE.length];
        return categories.add(userId, n, color);
    }

    public void deleteCategory(long userId, Category c) {
        int used = categories.countExpenses(userId, c.id());
        if (used > 0) throw new ValidationException("'" + c.name() + "' is used by " + used
                + " expense(s). Re-categorise or delete them first.");
        categories.delete(userId, c.id());
    }

    // ---------- budgets ----------

    /** categoryId 0 = overall monthly budget. */
    public void setBudget(long userId, long categoryId, YearMonth month, long limitPaise) {
        if (limitPaise <= 0) throw new ValidationException("Budget must be greater than zero.");
        if (limitPaise > MAX_AMOUNT_PAISE) throw new ValidationException("Budget is too large.");
        if (categoryId != 0 && categories.find(userId, categoryId).isEmpty())
            throw new ValidationException("Choose a category.");
        budgets.upsert(userId, categoryId, month, limitPaise);
    }

    public void deleteBudget(long userId, long budgetId) { budgets.delete(userId, budgetId); }

    public List<BudgetStatus> budgetStatuses(long userId, YearMonth month) {
        List<BudgetStatus> out = new ArrayList<>();
        for (Budget b : budgets.list(userId, month)) {
            Long cat = b.isOverall() ? null : b.categoryId();
            long spent = expenses.total(userId, month.atDay(1), month.atEndOfMonth(), cat);
            out.add(new BudgetStatus(b, spent));
        }
        return out;
    }

    /** Human-readable warnings for budgets at 80%+ usage. */
    public List<String> budgetAlerts(long userId, YearMonth month) {
        List<String> out = new ArrayList<>();
        for (BudgetStatus s : budgetStatuses(userId, month)) {
            String name = s.budget().isOverall() ? "Overall budget" : s.budget().categoryName();
            switch (s.level()) {
                case EXCEEDED -> out.add(name + " exceeded by " + Money.format(-s.remainingPaise()));
                case WARNING -> out.add(name + " is at " + s.percent() + "% (" + Money.format(s.remainingPaise()) + " left)");
                default -> { }
            }
        }
        return out;
    }

    // ---------- reports ----------

    public List<CategoryTotal> breakdown(long userId, LocalDate from, LocalDate to) {
        return expenses.totalsByCategory(userId, from, to);
    }

    /** Totals for the last {@code months} months ending at endMonth, zero-filled, oldest first. */
    public LinkedHashMap<YearMonth, Long> monthlyTrend(long userId, YearMonth endMonth, int months) {
        YearMonth start = endMonth.minusMonths(months - 1L);
        Map<String, Long> raw = expenses.totalsByMonth(userId, start.atDay(1), endMonth.atEndOfMonth());
        LinkedHashMap<YearMonth, Long> out = new LinkedHashMap<>();
        for (YearMonth m = start; !m.isAfter(endMonth); m = m.plusMonths(1)) {
            out.put(m, raw.getOrDefault(m.toString(), 0L));
        }
        return out;
    }

    public ReportSummary summary(long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new ValidationException("'From' date must not be after 'To' date.");
        List<Expense> list = expenses.search(userId, from, to, null, null);
        long total = list.stream().mapToLong(Expense::amountPaise).sum();
        LocalDate effectiveEnd = to.isAfter(LocalDate.now()) ? LocalDate.now() : to;
        int days = (int) Math.max(1, ChronoUnit.DAYS.between(from, effectiveEnd) + 1);
        List<CategoryTotal> cats = expenses.totalsByCategory(userId, from, to);
        Expense largest = list.stream().max(Comparator.comparingLong(Expense::amountPaise)).orElse(null);
        return new ReportSummary(total, list.size(), days, total / days,
                cats.isEmpty() ? null : cats.get(0), largest);
    }

    // ---------- demo data ----------

    /** Adds ~30 realistic student expenses over the last 3 months plus two budgets for this month. */
    public void loadSampleData(long userId) {
        Map<String, Long> ids = new HashMap<>();
        for (Category c : categories.list(userId)) ids.put(c.name(), c.id());
        Object[][] items = {
            {"Food & Dining", "Canteen lunch", 90, "UPI"}, {"Food & Dining", "Chai & snacks", 40, "Cash"},
            {"Food & Dining", "Dinner with friends", 450, "UPI"}, {"Transport", "Bus pass top-up", 300, "UPI"},
            {"Transport", "Auto to station", 120, "Cash"}, {"Education", "Online course", 799, "Card"},
            {"Books & Supplies", "Notebooks & pens", 260, "Cash"}, {"Entertainment", "Movie ticket", 220, "UPI"},
            {"Bills & Recharge", "Mobile recharge", 299, "UPI"}, {"Shopping", "T-shirt", 599, "Card"},
            {"Health", "Pharmacy", 180, "Cash"}, {"Rent & Hostel", "Hostel mess fee", 3500, "Net Banking"}
        };
        Random rnd = new Random(42);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 30; i++) {
            Object[] it = items[rnd.nextInt(items.length)];
            Long cat = ids.get((String) it[0]);
            if (cat == null) continue;
            long paise = ((Integer) it[2]) * 100L + rnd.nextInt(5) * 1000L;
            expenses.insert(new Expense(0, userId, cat, (String) it[0], paise, (String) it[1],
                    today.minusDays(rnd.nextInt(90)), (String) it[3]));
        }
        YearMonth now = YearMonth.now();
        budgets.upsert(userId, 0, now, 1_500_000);
        Long food = ids.get("Food & Dining");
        if (food != null) budgets.upsert(userId, food, now, 300_000);
    }
}
