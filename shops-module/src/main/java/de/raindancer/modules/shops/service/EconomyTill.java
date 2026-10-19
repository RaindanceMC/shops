package de.raindancer.modules.shops.service;

import de.raindancer.core.social.economy.Economies;
import de.raindancer.core.social.economy.Economy;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Money;

import java.util.Optional;
import java.util.UUID;

/** The till over Core's current economy — asked anew each time, so an economy plugin loading late is still found. */
public final class EconomyTill implements Till {

    @Override
    public boolean available() {
        return Economies.current().isPresent();
    }

    @Override
    public Money balance(UUID who) {
        return Economies.current().map(bank -> bank.balance(who)).orElse(Money.ZERO);
    }

    @Override
    public EconomyResult take(UUID who, Money amount, String reason, String source) {
        Optional<Economy> bank = Economies.current();
        if (bank.isEmpty()) {
            return EconomyResult.failed(EconomyResult.Outcome.UNAVAILABLE, amount, Money.ZERO);
        }
        bank.get().createAccount(who);
        return bank.get().withdraw(who, amount, reason, source);
    }

    @Override
    public EconomyResult give(UUID who, Money amount, String reason, String source) {
        Optional<Economy> bank = Economies.current();
        if (bank.isEmpty()) {
            return EconomyResult.failed(EconomyResult.Outcome.UNAVAILABLE, amount, Money.ZERO);
        }
        bank.get().createAccount(who);
        return bank.get().deposit(who, amount, reason, source);
    }

    @Override
    public EconomyResult refund(UUID who, Money amount, String reason, String source) {
        Optional<Economy> bank = Economies.current();
        if (bank.isEmpty()) {
            return EconomyResult.failed(EconomyResult.Outcome.UNAVAILABLE, amount, Money.ZERO);
        }
        return bank.get().refund(who, amount, reason, source);
    }
}
