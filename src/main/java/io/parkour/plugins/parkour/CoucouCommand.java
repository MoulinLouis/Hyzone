package io.parkour.plugins.parkour;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * This is an example command that will simply print the name of the plugin in chat when used.
 */
public class CoucouCommand extends CommandBase {

    private final String pluginName;
    private final String pluginVersion;

    public CoucouCommand(String pluginName, String pluginVersion) {
        super("coucou", "omg guigui, ça semble bien marché :o");

        this.setPermissionGroup(GameMode.Adventure); // Allows the command to be used by anyone, not just OP
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
    }


    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("omg guigui, ça semble bien marché :o"));
    }
}