package com.spendwise.model;

import java.time.LocalDate;

/** amountPaise is always positive. id == 0 means "not saved yet". */
public record Expense(long id, long userId, long categoryId, String categoryName,
                      long amountPaise, String description, LocalDate date, String paymentMethod) {}
