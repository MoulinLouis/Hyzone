package io.hyvexa.parkour.command;

import io.hyvexa.core.economy.CurrencyStore;

import java.util.UUID;

public class VexaCommand extends AbstractCurrencyCommand {

    private final CurrencyStore store;

    public VexaCommand(CurrencyStore store) {
        super("vexa", "Manage player vexa");
        this.store = store;
    }

    @Override
    protected String currencyName() {
        return "vexa";
    }

    @Override
    protected long getCurrency(UUID playerId) {
        return store.getBalance(playerId);
    }

    @Override
    protected void setCurrency(UUID playerId, long amount) {
        store.setBalance(playerId, amount);
    }

    @Override
    protected long addCurrency(UUID playerId, long amount) {
        return store.addBalance(playerId, amount);
    }

    @Override
    protected long removeCurrency(UUID playerId, long amount) {
        return store.removeBalance(playerId, amount);
    }
}
