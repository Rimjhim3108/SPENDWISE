# SpendWise – Smart Student Expense Management System

A desktop expense tracker for students, built with **Java 17+ / Swing** and an embedded **SQLite** database.
No server, no setup – your data lives in `~/.spendwise/spendwise.db`.

## Features
- **Accounts** – register / sign in; passwords salted + hashed (PBKDF2-HMAC-SHA256); 5 failed logins lock the account for 30 s. Each user only sees their own data.
- **Expenses** – add, edit, delete; filter by date range, category and text; sortable table; export to CSV (Excel-safe, formula-injection guarded).
- **Categories** – 10 student-friendly defaults (Food, Transport, Education, Rent & Hostel…), add/delete your own.
- **Budgets** – monthly overall budget + per-category budgets, colour-coded usage bars, alert pop-up when you hit 80% / exceed a budget.
- **Dashboard** – spent this month / today, budget left, daily average, category donut chart, 6-month trend, recent expenses.
- **Reports** – any date range (presets: this month, last month, last 3 months, this year): totals, average per day, top category, largest expense, category breakdown.
- **Sample data** button on the dashboard to try everything instantly.

## Run it
Requires JDK 17 or newer and Maven.

    mvn package
    java -jar target/spendwise.jar

(or open the folder in IntelliJ / Eclipse / VS Code as a Maven project and run `com.spendwise.Main`).

## Tests
Dependency-free test runner (18 checks: money maths, auth, validation, ownership, budgets, reports, CSV):

    mvn compile
    javac -d target/test-classes -cp target/classes src/test/java/com/spendwise/TestRunner.java
    java -cp target/classes:target/test-classes:<path-to-sqlite-jdbc.jar>:<path-to-slf4j-api.jar> com.spendwise.TestRunner

(On Windows use `;` instead of `:` in the classpath. Both jars are in `~/.m2/repository` after `mvn package`.)

## Architecture
    com.spendwise
    ├── Main, Services          entry point + service container
    ├── db/        Database (schema, connections), DataException
    ├── model/     immutable records: User, Category, Expense, Budget, BudgetStatus, ReportSummary…
    ├── dao/       JDBC data access (prepared statements only – no SQL injection)
    ├── service/   AuthService, ExpenseService (validation + business rules), ExportService
    ├── ui/        Swing screens: LoginFrame, MainFrame, Dashboard/Expense/Budget/Reports panels, charts, Theme
    └── util/      Money (paise-based, no floating point), Dates

### Database schema
    users(id, username UNIQUE NOCASE, full_name, password_hash, salt, created_at)
    categories(id, user_id→users, name, color, UNIQUE(user_id, name))
    expenses(id, user_id→users, category_id→categories, amount INTEGER paise CHECK>0,
             description, expense_date ISO text, payment_method, created_at)
    budgets(id, user_id→users, category_id (0 = overall), month 'YYYY-MM', limit_amount,
            UNIQUE(user_id, category_id, month))
