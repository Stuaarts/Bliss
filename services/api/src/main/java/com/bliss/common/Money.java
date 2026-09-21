package com.bliss.common;

import jakarta.ws.rs.BadRequestException;
import java.math.BigDecimal;
import java.util.Currency;

/** Exact two-minor-unit money supported by the portfolio schema. */
public final class Money {
    private Money() {}

    public static void requireCurrency(String currency) {
        try {
            if (currency == null
                    || !currency.matches("[A-Z]{3}")
                    || Currency.getInstance(currency).getDefaultFractionDigits() != 2)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported currency");
        }
    }

    public static BigDecimal amount(String value, String currency) {
        requireCurrency(currency);
        if (value == null || !value.matches("(0|[1-9][0-9]{0,9})(\\.[0-9]{1,2})?"))
            throw new BadRequestException("Invalid amount");
        return new BigDecimal(value).setScale(2);
    }
}
