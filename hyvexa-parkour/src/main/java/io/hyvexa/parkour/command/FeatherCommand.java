package io.hyvexa.parkour.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.core.economy.CurrencyStore;

import java.util.UUID;

public class FeatherCommand extends AbstractCurrencyCommand {

    private final CurrencyStore store;

    public FeatherCommand(CurrencyStore store) {
        super("feather", "Manage player feathers");
        this.store = store;
    }

    @Override
    protected String currencyName() {
        return "feathers";
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

    @Override
    protected void onCurrencyAdded(PlayerRef targetRef, long amount, long newTotal) {
        targetRef.sendMessage(Message.raw("You received " + amount + " feathers! New total: " + newTotal));
    }
}
