package org.example;

import com.hypixel.hytale.Main;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityBreakBlockEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/**
 * This class serves as the entrypoint for your plugin. Use the setup method to register into game registries or add
 * event listeners.
 */
public class PluginExample extends JavaPlugin {

    public PluginExample(@Nonnull JavaPluginInit init) {
        super(init);
        IO.println("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        IO.println("Setting up plugin " + this.getName());
        this.getCommandRegistry().registerCommand(new ExampleCommand(this.getName()));
        this.getEventRegistry().registerGlobal(LivingEntityBreakBlockEvent.class, this::onLivingBreakBlockEvent);
    }

    private void onLivingBreakBlockEvent(LivingEntityBreakBlockEvent ctx) {
        if (ctx.getEntity() instanceof Player player) {
            player.sendMessage(Message.raw("Player " + player.getDisplayName() + " broke a block at " + ctx.getTargetBlock().toString()));
        }
    }
}