package io.hyvexa.parkour.command;

import io.hyvexa.core.economy.VexaStore;

import java.util.UUID;

public class VexaCommand extends AbstractCurrencyCommand {

    public VexaCommand() {
        super("vexa", "Manage player vexa");
    }

    @Override
    protected String currencyName() {
        return "vexa";
    }

    @Override
    protected long getCurrency(UUID playerId) {
        return VexaStore.getInstance().getVexa(playerId);
    }

    @Override
    protected void setCurrency(UUID playerId, long amount) {
        VexaStore.getInstance().setVexa(playerId, amount);
    }

    @Override
    protected long addCurrency(UUID playerId, long amount) {
        return VexaStore.getInstance().addVexa(playerId, amount);
    }

    @Override
    protected long removeCurrency(UUID playerId, long amount) {
        return VexaStore.getInstance().removeVexa(playerId, amount);
    }
}
