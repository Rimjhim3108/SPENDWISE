package com.spendwise.model;

public record BudgetStatus(Budget budget, long spentPaise) {
    public enum Level { OK, WARNING, EXCEEDED }

    public long remainingPaise() { return budget.limitPaise() - spentPaise; }

    public int percent() {
        return (int) Math.min(999, spentPaise * 100 / budget.limitPaise());
    }

    public Level level() {
        int p = percent();
        if (p >= 100) return Level.EXCEEDED;
        if (p >= 80) return Level.WARNING;
        return Level.OK;
    }
}
