package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.purge.data.PurgeParty;
import io.hyvexa.purge.manager.PurgePartyManager;
import io.hyvexa.purge.manager.PurgeSessionManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PurgePartyMenuPage extends InteractiveCustomUIPage<PurgePartyMenuPage.PartyMenuData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_PLAY_SOLO = "PlaySolo";
    private static final String BUTTON_CREATE_PARTY = "CreateParty";
    private static final String BUTTON_ACCEPT_INVITE = "AcceptInvite";
    private static final String BUTTON_DECLINE_INVITE = "DeclineInvite";
    private static final String BUTTON_START_PARTY = "StartParty";
    private static final String BUTTON_SHOW_INVITE = "ShowInvite";
    private static final String BUTTON_HIDE_INVITE = "HideInvite";
    private static final String BUTTON_LEAVE_PARTY = "LeaveParty";
    private static final String BUTTON_INVITE_PREFIX = "Invite:";
    private static final String BUTTON_KICK_PREFIX = "Kick:";

    private final UUID playerId;
    private final PurgePartyManager partyManager;
    private final PurgeSessionManager sessionManager;
    private boolean showingInvitePanel;
    private String searchText = "";

    public PurgePartyMenuPage(@Nonnull PlayerRef playerRef, UUID playerId,
                              PurgePartyManager partyManager, PurgeSessionManager sessionManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PartyMenuData.CODEC);
        this.playerId = playerId;
        this.partyManager = partyManager;
        this.sessionManager = sessionManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_PartyMenu.ui");
        buildContent(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PartyMenuData data) {
        super.handleDataEvent(ref, store, data);

        String previousSearch = searchText;
        if (data.search != null) {
            searchText = data.search.trim();
        }

        String button = data.getButton();
        if (button == null) {
            if (!previousSearch.equals(searchText)) {
                sendRefresh();
            }
            return;
        }

        switch (button) {
            case BUTTON_CLOSE -> close();
            case BUTTON_PLAY_SOLO -> handlePlaySolo(ref, store);
            case BUTTON_CREATE_PARTY -> handleCreateParty();
            case BUTTON_ACCEPT_INVITE -> handleAcceptInvite();
            case BUTTON_DECLINE_INVITE -> handleDeclineInvite();
            case BUTTON_START_PARTY -> handleStartParty(ref, store);
            case BUTTON_SHOW_INVITE -> {
                showingInvitePanel = true;
                sendRefresh();
            }
            case BUTTON_HIDE_INVITE -> {
                showingInvitePanel = false;
                sendRefresh();
            }
            case BUTTON_LEAVE_PARTY -> handleLeaveParty();
            default -> {
                if (button.startsWith(BUTTON_INVITE_PREFIX)) {
                    handleInvitePlayer(button.substring(BUTTON_INVITE_PREFIX.length()));
                } else if (button.startsWith(BUTTON_KICK_PREFIX)) {
                    handleKickPlayer(button.substring(BUTTON_KICK_PREFIX.length()));
                }
            }
        }
    }

    private void handlePlaySolo(Ref<EntityStore> ref, Store<EntityStore> store) {
        close();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> sessionManager.startSession(playerId, ref), world);
    }

    private void handleStartParty(Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeParty party = partyManager.getPartyByPlayer(playerId);
        if (party == null || !party.isLeader(playerId)) {
            return;
        }
        close();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> sessionManager.startSession(playerId, ref), world);
    }

    private void handleCreateParty() {
        if (partyManager.getPartyByPlayer(playerId) != null) {
            return;
        }
        partyManager.createParty(playerId);
        sendRefresh();
    }

    private void handleAcceptInvite() {
        partyManager.accept(playerId);
        sendRefresh();
    }

    private void handleDeclineInvite() {
        partyManager.declineInvite(playerId);
        sendRefresh();
    }

    private void handleLeaveParty() {
        partyManager.leaveParty(playerId);
        showingInvitePanel = false;
        sendRefresh();
    }

    private void handleInvitePlayer(String targetIdStr) {
        try {
            UUID targetId = UUID.fromString(targetIdStr);
            partyManager.invite(playerId, targetId);
            sendRefresh();
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void handleKickPlayer(String targetIdStr) {
        try {
            UUID targetId = UUID.fromString(targetIdStr);
            partyManager.kickPlayer(playerId, targetId);
            sendRefresh();
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        buildContent(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void buildContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        // Always bind close
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        PurgeParty party = partyManager.getPartyByPlayer(playerId);
        boolean hasPendingInvite = partyManager.hasPendingInvite(playerId);

        if (party != null) {
            // In a party
            commandBuilder.set("#SoloSection.Visible", false);
            commandBuilder.set("#InviteSection.Visible", false);
            commandBuilder.set("#PartySection.Visible", true);
            commandBuilder.set("#InvitePanel.Visible", showingInvitePanel && party.isLeader(playerId));

            buildPartySection(commandBuilder, eventBuilder, party);

            if (showingInvitePanel && party.isLeader(playerId)) {
                buildInvitePanel(commandBuilder, eventBuilder, party);
            }
        } else if (hasPendingInvite) {
            // Has pending invite, not in party
            commandBuilder.set("#SoloSection.Visible", true);
            commandBuilder.set("#InviteSection.Visible", true);
            commandBuilder.set("#PartySection.Visible", false);
            commandBuilder.set("#InvitePanel.Visible", false);

            buildSoloSection(eventBuilder);
            buildInviteSection(commandBuilder, eventBuilder);
        } else {
            // Solo, no invite
            commandBuilder.set("#SoloSection.Visible", true);
            commandBuilder.set("#InviteSection.Visible", false);
            commandBuilder.set("#PartySection.Visible", false);
            commandBuilder.set("#InvitePanel.Visible", false);

            buildSoloSection(eventBuilder);
        }
    }

    private void buildSoloSection(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlaySoloButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PLAY_SOLO), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CreatePartyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CREATE_PARTY), false);
    }

    private void buildInviteSection(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        UUID inviterId = partyManager.getInviterForTarget(playerId);
        if (inviterId != null) {
            String inviterName = getPlayerName(inviterId);
            commandBuilder.set("#InviterName.Text", inviterName);
        }
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AcceptInviteButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ACCEPT_INVITE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DeclineInviteButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DECLINE_INVITE), false);
    }

    private void buildPartySection(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, PurgeParty party) {
        boolean isLeader = party.isLeader(playerId);
        Set<UUID> members = party.getMembersSnapshot();

        commandBuilder.set("#PartyTitle.Text", "Party (" + members.size() + "/" + PurgeParty.MAX_SIZE + ")");

        // Build member list
        commandBuilder.clear("#MemberList");
        int index = 0;
        for (UUID memberId : members) {
            String root = "#MemberList[" + index + "]";
            commandBuilder.append("#MemberList", "Pages/Purge_PartyMemberEntry.ui");

            String name = getPlayerName(memberId);
            commandBuilder.set(root + " #MemberName.Text", name);

            if (party.isLeader(memberId)) {
                commandBuilder.set(root + " #LeaderBadge.Visible", true);
            }

            // Show kick button for leader on non-leader members
            if (isLeader && !party.isLeader(memberId)) {
                commandBuilder.set(root + " #KickGroup.Visible", true);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        root + " #KickButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_KICK_PREFIX + memberId), false);
            }

            index++;
        }

        // Leader controls
        if (isLeader) {
            commandBuilder.set("#LeaderControls.Visible", true);
            commandBuilder.set("#WaitingLabel.Visible", false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StartPartyButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_START_PARTY), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ShowInviteButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SHOW_INVITE), false);
        } else {
            commandBuilder.set("#LeaderControls.Visible", false);
            commandBuilder.set("#WaitingLabel.Visible", true);
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeavePartyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_LEAVE_PARTY), false);
    }

    private void buildInvitePanel(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, PurgeParty party) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#HideInviteButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_HIDE_INVITE), false);

        // Search field
        commandBuilder.set("#SearchField.Value", searchText);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchField",
                EventData.of(PartyMenuData.KEY_SEARCH, "#SearchField.Value"), false);

        // Build online player list
        commandBuilder.clear("#PlayerList");
        List<PlayerRef> candidates = getInvitablePlayers(party);
        String filter = searchText.toLowerCase();

        int index = 0;
        for (PlayerRef candidate : candidates) {
            String name = candidate.getUsername();
            if (name == null) {
                continue;
            }
            if (!filter.isEmpty() && !name.toLowerCase().startsWith(filter)) {
                continue;
            }

            UUID candidateId = candidate.getUuid();
            String root = "#PlayerList[" + index + "]";
            commandBuilder.append("#PlayerList", "Pages/Purge_PartyPlayerEntry.ui");
            commandBuilder.set(root + " #PlayerName.Text", name);

            if (partyManager.hasInviteFromParty(candidateId, party.getPartyId())) {
                // Already invited
                commandBuilder.set(root + " #InviteButton.Visible", false);
                commandBuilder.set(root + " #InvitedLabel.Visible", true);
            } else {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        root + " #InviteButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_INVITE_PREFIX + candidateId), false);
            }

            index++;
        }

        commandBuilder.set("#NoPlayersLabel.Visible", index == 0);
    }

    private List<PlayerRef> getInvitablePlayers(PurgeParty party) {
        List<PlayerRef> result = new ArrayList<>();
        Set<UUID> partyMembers = party.getMembersSnapshot();

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID candidateId = playerRef.getUuid();
            if (candidateId == null || candidateId.equals(playerId)) {
                continue;
            }
            // Skip players already in a party
            if (partyManager.getPartyByPlayer(candidateId) != null) {
                continue;
            }
            // Skip players in active session
            if (sessionManager.hasActiveSession(candidateId)) {
                continue;
            }
            // Skip party members (shouldn't happen but defensive)
            if (partyMembers.contains(candidateId)) {
                continue;
            }
            // Only include players in Purge world
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            World world = ref.getStore().getExternalData().getWorld();
            if (!ModeGate.isPurgeWorld(world)) {
                continue;
            }
            result.add(playerRef);
        }
        return result;
    }

    private String getPlayerName(UUID targetId) {
        try {
            PlayerRef ref = Universe.get().getPlayer(targetId);
            if (ref != null) {
                return ref.getUsername();
            }
        } catch (Exception ignored) {
        }
        return targetId.toString();
    }

    public static class PartyMenuData extends ButtonEventData {
        static final String KEY_SEARCH = "@Search";

        public static final BuilderCodec<PartyMenuData> CODEC =
                BuilderCodec.<PartyMenuData>builder(PartyMenuData.class, PartyMenuData::new)
                        .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .addField(new KeyedCodec<>(KEY_SEARCH, Codec.STRING),
                                (data, value) -> data.search = value, data -> data.search)
                        .build();

        private String button;
        private String search;

        @Override
        public String getButton() {
            return button;
        }
    }
}
