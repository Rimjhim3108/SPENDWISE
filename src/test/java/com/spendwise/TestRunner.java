package com.spendwise;

import com.spendwise.db.Database;
import com.spendwise.model.*;
import com.spendwise.service.*;
import com.spendwise.util.Money;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Dependency-free test runner:  java -cp out:test-out:sqlite-jdbc.jar com.spendwise.TestRunner */
public class TestRunner {
    static int passed = 0, failed = 0;

    interface Body { void run() throws Exception; }

    static void test(String name, Body b) {
        try { b.run(); passed++; System.out.println("  PASS  " + name); }
        catch (Throwable t) { failed++; System.out.println("  FAIL  " + name + " -> " + t); }
    }
    static void check(boolean c, String msg) { if (!c) throw new AssertionError(msg); }
    static void eq(Object a, Object b) { if (!a.equals(b)) throw new AssertionError("expected " + b + " but was " + a); }
    static void rejects(String why, Runnable r) {
        try { r.run(); } catch (ValidationException | IllegalArgumentException e) { return; }
        throw new AssertionError("expected rejection: " + why);
    }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempFile("spendwise-test", ".db");
        Files.deleteIfExists(tmp);
        Database db = new Database(tmp);
        AuthService auth = new AuthService(db);
        ExpenseService svc = new ExpenseService(db);
        ExportService export = new ExportService();

        User u = auth.register("rohit", "Rohit", "secret1".toCharArray());
        User other = auth.register("amit", "Amit", "secret2".toCharArray());
        List<Category> cats = svc.categories(u.id());
        long food = cats.stream().filter(c -> c.name().equals("Food & Dining")).findFirst().get().id();
        LocalDate today = LocalDate.now();

        System.out.println("Money");
        test("parse plain/comma/symbol", () -> {
            eq(Money.parse("250"), 25000L); eq(Money.parse("1,200.50"), 120050L); eq(Money.parse("\u20B999.5"), 9950L);
        });
        test("parse rejects junk", () -> {
            rejects("abc", () -> Money.parse("abc")); rejects("3 decimals", () -> Money.parse("1.234"));
            rejects("blank", () -> Money.parse(" "));
        });
        test("format", () -> { eq(Money.format(123456), "\u20B91,234.56"); eq(Money.format(5), "\u20B90.05"); });

        System.out.println("Auth");
        test("register seeds default categories", () -> eq(cats.size(), 10));
        test("login ok", () -> eq(auth.login("RoHiT", "secret1".toCharArray()).id(), u.id()));
        test("wrong password rejected", () -> rejects("bad pw", () -> auth.login("rohit", "nope".toCharArray())));
        test("duplicate username rejected", () -> rejects("dup", () -> auth.register("ROHIT", "X", "abcdef".toCharArray())));
        test("weak password / bad username rejected", () -> {
            rejects("short pw", () -> auth.register("newuser", "N", "123".toCharArray()));
            rejects("bad name", () -> auth.register("a b", "N", "abcdef".toCharArray()));
        });
        test("lockout after 5 failures", () -> {
            for (int i = 0; i < 5; i++) { try { auth.login("amit", "x".toCharArray()); } catch (ValidationException ignored) {} }
            try { auth.login("amit", "secret2".toCharArray()); throw new AssertionError("should be locked"); }
            catch (ValidationException e) { check(e.getMessage().contains("Too many"), e.getMessage()); }
        });

        System.out.println("Expenses");
        test("add + search + total", () -> {
            svc.save(new Expense(0, u.id(), food, "", 25000, "Lunch", today, "UPI"));
            svc.save(new Expense(0, u.id(), food, "", 10000, "Chai", today.minusDays(1), "Cash"));
            eq(svc.search(u.id(), today.minusDays(5), today, null, null).size(), 2);
            eq(svc.search(u.id(), today.minusDays(5), today, food, "lun").size(), 1);
            eq(svc.total(u.id(), today.minusDays(5), today), 35000L);
        });
        test("search treats % and _ literally", () ->
            eq(svc.search(u.id(), today.minusDays(5), today, null, "%").size(), 0));
        test("validation", () -> {
            rejects("zero", () -> svc.save(new Expense(0, u.id(), food, "", 0, "", today, "Cash")));
            rejects("future", () -> svc.save(new Expense(0, u.id(), food, "", 100, "", today.plusDays(1), "Cash")));
            rejects("bad method", () -> svc.save(new Expense(0, u.id(), food, "", 100, "", today, "Bitcoin")));
            rejects("foreign category", () -> svc.save(new Expense(0, other.id(), food, "", 100, "", today, "Cash")));
        });
        test("update and delete are scoped to the owner", () -> {
            long id = svc.save(new Expense(0, u.id(), food, "", 5000, "Temp", today, "Cash"));
            rejects("other user update", () -> svc.save(new Expense(id, other.id(), svc.categories(other.id()).get(0).id(), "", 100, "", today, "Cash")));
            svc.delete(other.id(), id);
            eq(svc.search(u.id(), today, today, null, "Temp").size(), 1);
            svc.save(new Expense(id, u.id(), food, "", 7000, "Temp2", today, "Cash"));
            svc.delete(u.id(), id);
            eq(svc.search(u.id(), today, today, null, "Temp").size(), 0);
        });

        System.out.println("Categories");
        test("add/duplicate/delete-in-use", () -> {
            Category c = svc.addCategory(u.id(), "Gym");
            rejects("dup", () -> svc.addCategory(u.id(), "gym"));
            rejects("in use", () -> svc.deleteCategory(u.id(), cats.stream().filter(x -> x.id() == food).findFirst().get()));
            svc.deleteCategory(u.id(), c);
        });

        System.out.println("Budgets & reports");
        test("budget status levels and alerts", () -> {
            YearMonth ym = YearMonth.from(today);
            svc.setBudget(u.id(), 0, ym, 100_000);
            svc.setBudget(u.id(), food, ym, 30_000);
            List<BudgetStatus> st = svc.budgetStatuses(u.id(), ym);
            eq(st.size(), 2);
            BudgetStatus foodStatus = st.stream().filter(s -> !s.budget().isOverall()).findFirst().get();
            check(foodStatus.spentPaise() >= 25000, "food spend counted");
            check(foodStatus.level() != BudgetStatus.Level.OK, "food budget should warn");
            check(!svc.budgetAlerts(u.id(), ym).isEmpty(), "alerts present");
            svc.setBudget(u.id(), food, ym, 20_000); // upsert replaces
            eq(svc.budgetStatuses(u.id(), ym).size(), 2);
            rejects("zero budget", () -> svc.setBudget(u.id(), 0, ym, 0));
        });
        test("summary + breakdown + trend", () -> {
            ReportSummary s = svc.summary(u.id(), today.minusDays(5), today);
            eq(s.totalPaise(), 35000L); eq(s.count(), 2); eq(s.largest().amountPaise(), 25000L);
            eq(svc.breakdown(u.id(), today.minusDays(5), today).get(0).name(), "Food & Dining");
            eq(svc.monthlyTrend(u.id(), YearMonth.from(today), 6).size(), 6);
        });
        test("sample data loads", () -> {
            User demo = auth.register("demo", "Demo", "demo123".toCharArray());
            svc.loadSampleData(demo.id());
            check(svc.search(demo.id(), today.minusDays(100), today, null, null).size() == 30, "30 rows");
        });

        System.out.println("Export");
        test("CSV quoting and formula guard", () -> {
            Path f = Files.createTempFile("sw", ".csv");
            svc.save(new Expense(0, u.id(), food, "", 100, "=HYPERLINK(\"x\")", today, "Cash"));
            export.exportCsv(svc.search(u.id(), today, today, null, "hyper"), f);
            String csv = Files.readString(f);
            check(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""), csv);
            check(csv.contains(",1.00"), "plain amount");
        });

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        Files.deleteIfExists(tmp);
        System.exit(failed == 0 ? 0 : 1);
    }
}
