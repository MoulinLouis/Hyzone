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
import io.hyvexa.purge.util.PurgePlayerNameResolver;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private static final String BUTTON_TOGGLE_INVITE = "ToggleInvite";
    private static final String BUTTON_APPLY_INVITE_SEARCH = "ApplyInviteSearch";
    private static final String BUTTON_LEAVE_PARTY = "LeaveParty";
    private static final String BUTTON_INVITE_PREFIX = "Invite:";
    private static final String BUTTON_KICK_PREFIX = "Kick:";

    private final UUID playerId;
    private final PurgePartyManager partyManager;
    private final PurgeSessionManager sessionManager;
    private boolean showingInvitePanel;
    private String searchInputText = "";
    private String appliedSearchText = "";
    private String inviteStatusMessage = "";

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

        if (data.search != null) {
            searchInputText = data.search.trim();
        }

        String button = data.getButton();
        if (button == null) {
            return;
        }

        switch (button) {
            case BUTTON_CLOSE -> close();
            case BUTTON_PLAY_SOLO -> handlePlaySolo(ref, store);
            case BUTTON_CREATE_PARTY -> handleCreateParty();
            case BUTTON_ACCEPT_INVITE -> handleAcceptInvite();
            case BUTTON_DECLINE_INVITE -> handleDeclineInvite();
            case BUTTON_START_PARTY -> handleStartParty(ref, store);
            case BUTTON_TOGGLE_INVITE -> {
                showingInvitePanel = !showingInvitePanel;
                inviteStatusMessage = "";
                sendRefresh();
            }
            case BUTTON_APPLY_INVITE_SEARCH -> {
                appliedSearchText = searchInputText;
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
        inviteStatusMessage = "";
        sendRefresh();
    }

    private void handleDeclineInvite() {
        partyManager.declineInvite(playerId);
        inviteStatusMessage = "";
        sendRefresh();
    }

    private void handleLeaveParty() {
        partyManager.leaveParty(playerId);
        showingInvitePanel = false;
        searchInputText = "";
        appliedSearchText = "";
        inviteStatusMessage = "";
        sendRefresh();
    }

    private void handleInvitePlayer(String targetIdStr) {
        try {
            UUID targetId = UUID.fromString(targetIdStr);
            boolean invited = partyManager.invite(playerId, targetId);
            inviteStatusMessage = invited
                    ? "Invite sent to " + getPlayerName(targetId) + "."
                    : "Could not invite that player. Check chat for details.";
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
            commandBuilder.set("#InviteDrawer.Visible", showingInvitePanel);

            buildPartySection(commandBuilder, eventBuilder, party);

            if (showingInvitePanel) {
                buildInviteDrawer(commandBuilder, eventBuilder, party);
            }
        } else if (hasPendingInvite) {
            // Has pending invite, not in party
            commandBuilder.set("#SoloSection.Visible", true);
            commandBuilder.set("#InviteSection.Visible", true);
            commandBuilder.set("#PartySection.Visible", false);
            commandBuilder.set("#InviteDrawer.Visible", false);

            buildSoloSection(eventBuilder);
            buildInviteSection(commandBuilder, eventBuilder);
        } else {
            // Solo, no invite
            commandBuilder.set("#SoloSection.Visible", true);
            commandBuilder.set("#InviteSection.Visible", false);
            commandBuilder.set("#PartySection.Visible", false);
            commandBuilder.set("#InviteDrawer.Visible", false);

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
        List<UUID> sortedMembers = new ArrayList<>(members);
        sortedMembers.sort(Comparator.comparing(
                memberId -> PurgePlayerNameResolver.resolve(memberId, PurgePlayerNameResolver.FallbackStyle.FULL_UUID),
                String.CASE_INSENSITIVE_ORDER));

        commandBuilder.set("#PartyTitle.Text", "Party (" + members.size() + "/" + PurgeParty.MAX_SIZE + ")");

        // Build member list
        commandBuilder.clear("#MemberList");
        int index = 0;
        for (UUID memberId : sortedMembers) {
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

        // Party controls
        if (isLeader) {
            commandBuilder.set("#StartPartyButton.Visible", true);
            commandBuilder.set("#WaitingLabel.Visible", false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StartPartyButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_START_PARTY), false);
        } else {
            commandBuilder.set("#StartPartyButton.Visible", false);
            commandBuilder.set("#WaitingLabel.Visible", true);
            commandBuilder.set("#WaitingLabel.Text", "Only the leader can start. Any party member can invite players.");
        }

        commandBuilder.set("#ToggleInviteButton.Text", showingInvitePanel ? "HIDE INVITE LIST" : "INVITE PLAYERS");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleInviteButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE_INVITE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeavePartyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_LEAVE_PARTY), false);
    }

    private void buildInviteDrawer(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, PurgeParty party) {
        commandBuilder.set("#InviteSearchField.Value", searchInputText);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InviteSearchField",
                EventData.of(PartyMenuData.KEY_SEARCH, "#InviteSearchField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyInviteSearchButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_APPLY_INVITE_SEARCH), false);

        commandBuilder.clear("#PlayerList");
        List<InviteCandidate> candidates = getInviteCandidates(party);
        String filter = appliedSearchText.toLowerCase(Locale.ROOT);

        List<InviteCandidate> filtered = new ArrayList<>();
        for (InviteCandidate candidate : candidates) {
            String nameLower = candidate.name.toLowerCase(Locale.ROOT);
            if (!filter.isEmpty() && !nameLower.contains(filter)) {
                continue;
            }
            filtered.add(candidate);
        }

        filtered.sort((a, b) -> {
            int priorityA = getSearchPriority(a.name.toLowerCase(Locale.ROOT), filter);
            int priorityB = getSearchPriority(b.name.toLowerCase(Locale.ROOT), filter);
            if (priorityA != priorityB) {
                return Integer.compare(priorityA, priorityB);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
        });

        int index = 0;
        for (InviteCandidate candidate : filtered) {
            String root = "#PlayerList[" + index + "]";
            commandBuilder.append("#PlayerList", "Pages/Purge_PartyPlayerEntry.ui");
            commandBuilder.set(root + " #PlayerName.Text", candidate.name);

            if (candidate.state == InviteCandidateState.AVAILABLE) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        root + " #InviteButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_INVITE_PREFIX + candidate.playerId), false);
            } else if (candidate.state == InviteCandidateState.INVITED) {
                commandBuilder.set(root + " #InviteButton.Visible", false);
                commandBuilder.set(root + " #InvitedLabel.Visible", true);
            } else {
                commandBuilder.set(root + " #InviteButton.Visible", false);
                commandBuilder.set(root + " #UnavailableLabel.Visible", true);
                commandBuilder.set(root + " #UnavailableLabel.Text", candidate.statusText);
            }

            index++;
        }

        commandBuilder.set("#NoPlayersLabel.Visible", index == 0);
        commandBuilder.set("#InviteStatusLabel.Visible", !inviteStatusMessage.isBlank());
        if (!inviteStatusMessage.isBlank()) {
            commandBuilder.set("#InviteStatusLabel.Text", inviteStatusMessage);
        }
    }

    private List<InviteCandidate> getInviteCandidates(PurgeParty party) {
        List<InviteCandidate> result = new ArrayList<>();
        Set<UUID> partyMembers = party.getMembersSnapshot();
        boolean partyFull = party.size() >= PurgeParty.MAX_SIZE;

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID candidateId = playerRef.getUuid();
            if (candidateId == null || candidateId.equals(playerId) || partyMembers.contains(candidateId)) {
                continue;
            }

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            World world = ref.getStore().getExternalData().getWorld();
            if (!ModeGate.isPurgeWorld(world)) {
                continue;
            }

            String name = playerRef.getUsername();
            if (name == null || name.isBlank()) {
                name = getPlayerName(candidateId);
            }

            if (partyManager.hasInviteFromParty(candidateId, party.getPartyId())) {
                result.add(new InviteCandidate(candidateId, name, InviteCandidateState.INVITED, "Invited"));
                continue;
            }
            if (partyManager.getPartyByPlayer(candidateId) != null) {
                result.add(new InviteCandidate(candidateId, name, InviteCandidateState.UNAVAILABLE, "In party"));
                continue;
            }
            if (sessionManager.hasActiveSession(candidateId)) {
                result.add(new InviteCandidate(candidateId, name, InviteCandidateState.UNAVAILABLE, "In match"));
                continue;
            }
            if (partyFull) {
                result.add(new InviteCandidate(candidateId, name, InviteCandidateState.UNAVAILABLE, "Party full"));
                continue;
            }
            result.add(new InviteCandidate(candidateId, name, InviteCandidateState.AVAILABLE, ""));
        }
        return result;
    }

    private int getSearchPriority(String nameLower, String filter) {
        if (filter.isEmpty()) {
            return 0;
        }
        if (nameLower.startsWith(filter)) {
            return 0;
        }
        return 1;
    }

    private String getPlayerName(UUID targetId) {
        return PurgePlayerNameResolver.resolve(targetId, PurgePlayerNameResolver.FallbackStyle.FULL_UUID);
    }

    private enum InviteCandidateState {
        AVAILABLE,
        INVITED,
        UNAVAILABLE
    }

    private static class InviteCandidate {
        private final UUID playerId;
        private final String name;
        private final InviteCandidateState state;
        private final String statusText;

        private InviteCandidate(UUID playerId, String name, InviteCandidateState state, String statusText) {
            this.playerId = playerId;
            this.name = name;
            this.state = state;
            this.statusText = statusText;
        }
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
