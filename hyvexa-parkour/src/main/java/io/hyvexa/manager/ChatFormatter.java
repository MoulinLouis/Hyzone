package io.hyvexa.manager;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;

import java.util.UUID;

public class ChatFormatter {

    private final ProgressStore progressStore;
    private final MapStore mapStore;
    private final PlayerPerksManager perksManager;

    public ChatFormatter(ProgressStore progressStore, MapStore mapStore, PlayerPerksManager perksManager) {
        this.progressStore = progressStore;
        this.mapStore = mapStore;
        this.perksManager = perksManager;
    }

    public Message formatChatMessage(PlayerRef sender, String content) {
        if (sender == null) {
            return Message.raw(content != null ? content : "");
        }
        String name = sender.getUsername();
        if (name == null || name.isBlank()) {
            name = "Player";
        }
        String safeContent = content != null ? content : "";

        // Check OP status directly via PermissionsModule (no store access needed)
        boolean isOp = false;
        UUID senderUuid = sender.getUuid();
        if (senderUuid != null) {
            var permissions = PermissionsModule.get();
            if (permissions != null) {
                isOp = permissions.getGroupsForUser(senderUuid).contains("OP");
            }
        }

        if (isOp) {
            Message rankPart = Message.raw("Admin").color("#ff0000");
            return Message.join(
                    Message.raw("["),
                    rankPart,
                    Message.raw("] "),
                    Message.raw(name),
                    Message.raw(": "),
                    Message.raw(safeContent)
            );
        }

        String rank = progressStore != null ? progressStore.getRankName(senderUuid, mapStore) : "Unranked";
        Message rankPart = FormatUtils.getRankMessage(rank);
        String badgeLabel = perksManager != null ? perksManager.getSpecialRankLabel(senderUuid) : null;
        String badgeColor = perksManager != null ? perksManager.getSpecialRankColor(senderUuid) : null;

        if (badgeLabel != null) {
            return Message.join(
                    Message.raw("["),
                    rankPart,
                    Message.raw("] "),
                    Message.raw("(").color("#ffffff"),
                    Message.raw(badgeLabel).color(badgeColor != null ? badgeColor : "#b2c0c7"),
                    Message.raw(")").color("#ffffff"),
                    Message.raw(" "),
                    Message.raw(name),
                    Message.raw(": "),
                    Message.raw(safeContent)
            );
        }

        return Message.join(
                Message.raw("["),
                rankPart,
                Message.raw("] "),
                Message.raw(name),
                Message.raw(": "),
                Message.raw(safeContent)
        );
    }
}
