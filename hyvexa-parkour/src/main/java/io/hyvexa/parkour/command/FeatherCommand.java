package io.hyvexa.parkour.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.core.economy.FeatherStore;

import java.util.UUID;

public class FeatherCommand extends AbstractCurrencyCommand {

    public FeatherCommand() {
        super("feather", "Manage player feathers");
    }

    @Override
    protected String currencyName() {
        return "feathers";
    }

    @Override
    protected long getCurrency(UUID playerId) {
        return FeatherStore.getInstance().getFeathers(playerId);
    }

    @Override
    protected void setCurrency(UUID playerId, long amount) {
        FeatherStore.getInstance().setFeathers(playerId, amount);
    }

    @Override
    protected long addCurrency(UUID playerId, long amount) {
        return FeatherStore.getInstance().addFeathers(playerId, amount);
    }

    @Override
    protected long removeCurrency(UUID playerId, long amount) {
        return FeatherStore.getInstance().removeFeathers(playerId, amount);
    }

    @Override
    protected void onCurrencyAdded(PlayerRef targetRef, long amount, long newTotal) {
        targetRef.sendMessage(Message.raw("You received " + amount + " feathers! New total: " + newTotal));
    }
}
