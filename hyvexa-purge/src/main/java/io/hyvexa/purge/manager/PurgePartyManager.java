package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.hyvexa.purge.data.PurgeParty;
import io.hyvexa.purge.util.PurgePlayerNameResolver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PurgePartyManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConcurrentHashMap<String, PurgeParty> partiesById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> partyIdByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> pendingInvitePartyByTarget = new ConcurrentHashMap<>();

    private volatile PurgeSessionManager sessionManager;

    public void setSessionManager(PurgeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public PurgeParty createParty(UUID creator) {
        if (creator == null) {
            return null;
        }
        if (partyIdByPlayer.containsKey(creator)) {
            return null;
        }
        String partyId = UUID.randomUUID().toString();
        PurgeParty party = new PurgeParty(partyId, creator);
        partiesById.put(partyId, party);
        partyIdByPlayer.put(creator, partyId);
        return party;
    }

    public PurgeParty getPartyByPlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        String partyId = partyIdByPlayer.get(playerId);
        return partyId != null ? partiesById.get(partyId) : null;
    }

    /**
     * @return true if invite was sent successfully
     */
    public boolean invite(UUID inviterId, UUID targetId) {
        if (inviterId == null || targetId == null) {
            return false;
        }

        // Auto-create party if inviter is not in one
        PurgeParty party = getPartyByPlayer(inviterId);
        if (party == null) {
            party = createParty(inviterId);
            if (party == null) {
                return false;
            }
        }

        // Check target not in a session
        PurgeSessionManager sm = sessionManager;
        if (sm != null && sm.hasActiveSession(targetId)) {
            sendMessageToPlayer(inviterId, "That player is already in a session.");
            return false;
        }

        // Check target not already in a party
        if (partyIdByPlayer.containsKey(targetId)) {
            sendMessageToPlayer(inviterId, "That player is already in a party.");
            return false;
        }

        // Check party not full
        if (party.size() >= PurgeParty.MAX_SIZE) {
            sendMessageToPlayer(inviterId, "Party is full (max " + PurgeParty.MAX_SIZE + ").");
            return false;
        }

        // Store invite (replaces any previous invite for this target)
        pendingInvitePartyByTarget.put(targetId, party.getPartyId());

        // Notify target
        String inviterName = getPlayerName(inviterId);
        sendMessageToPlayer(targetId,
                inviterName + " invited you to a Purge party. /purge party accept");
        return true;
    }

    /**
     * @return true if the target successfully joined the party
     */
    public boolean accept(UUID targetId) {
        if (targetId == null) {
            return false;
        }

        String partyId = pendingInvitePartyByTarget.remove(targetId);
        if (partyId == null) {
            sendMessageToPlayer(targetId, "No pending invite.");
            return false;
        }

        PurgeParty party = partiesById.get(partyId);
        if (party == null) {
            sendMessageToPlayer(targetId, "That party no longer exists.");
            return false;
        }

        if (party.size() >= PurgeParty.MAX_SIZE) {
            sendMessageToPlayer(targetId, "Party is full (max " + PurgeParty.MAX_SIZE + ").");
            return false;
        }

        // If the player is already in a different party, leave it first
        if (partyIdByPlayer.containsKey(targetId)) {
            leaveParty(targetId);
        }

        party.addMember(targetId);
        partyIdByPlayer.put(targetId, partyId);

        String targetName = getPlayerName(targetId);
        broadcastToParty(party, targetName + " joined the party.");
        return true;
    }

    public boolean leaveParty(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        String partyId = partyIdByPlayer.get(playerId);
        if (partyId == null) {
            return false;
        }

        PurgeParty party = partiesById.get(partyId);
        if (party == null) {
            partyIdByPlayer.remove(playerId);
            return false;
        }

        // If leader leaves, dissolve the entire party
        if (party.isLeader(playerId)) {
            broadcastToParty(party, "Party leader left. Party dissolved.");
            dissolveParty(partyId);
            return true;
        }

        partyIdByPlayer.remove(playerId);
        party.removeMember(playerId);

        String playerName = getPlayerName(playerId);
        broadcastToParty(party, playerName + " left the party.");

        // If party is empty, delete it
        if (party.size() == 0) {
            partiesById.remove(partyId);
            cleanupInvitesForParty(partyId);
        }

        return true;
    }

    public boolean kickPlayer(UUID kickerId, UUID targetId) {
        if (kickerId == null || targetId == null) {
            return false;
        }
        PurgeParty party = getPartyByPlayer(kickerId);
        if (party == null || !party.isLeader(kickerId)) {
            return false;
        }
        if (!party.contains(targetId)) {
            return false;
        }
        partyIdByPlayer.remove(targetId);
        party.removeMember(targetId);

        String targetName = getPlayerName(targetId);
        broadcastToParty(party, targetName + " was kicked from the party.");
        sendMessageToPlayer(targetId, "You were kicked from the party.");
        return true;
    }

    public Set<UUID> listMembers(UUID playerId) {
        PurgeParty party = getPartyByPlayer(playerId);
        return party != null ? party.getMembersSnapshot() : Set.of();
    }

    public void cleanupPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        // Remove any pending invite for this player
        pendingInvitePartyByTarget.remove(playerId);
        // Leave party if in one
        if (partyIdByPlayer.containsKey(playerId)) {
            leaveParty(playerId);
        }
    }

    public void dissolveParty(String partyId) {
        if (partyId == null) {
            return;
        }
        PurgeParty party = partiesById.remove(partyId);
        if (party == null) {
            return;
        }
        // Remove all member indexes
        for (UUID memberId : party.getMembersSnapshot()) {
            partyIdByPlayer.remove(memberId);
        }
        // Clean up pending invites referencing this party
        cleanupInvitesForParty(partyId);
    }

    // --- Query methods for UI ---

    public boolean hasPendingInvite(UUID targetId) {
        return targetId != null && pendingInvitePartyByTarget.containsKey(targetId);
    }

    public UUID getInviterForTarget(UUID targetId) {
        if (targetId == null) {
            return null;
        }
        String partyId = pendingInvitePartyByTarget.get(targetId);
        if (partyId == null) {
            return null;
        }
        PurgeParty party = partiesById.get(partyId);
        return party != null ? party.getLeaderId() : null;
    }

    public boolean hasInviteFromParty(UUID targetId, String partyId) {
        if (targetId == null || partyId == null) {
            return false;
        }
        String invitePartyId = pendingInvitePartyByTarget.get(targetId);
        return partyId.equals(invitePartyId);
    }

    public boolean declineInvite(UUID targetId) {
        if (targetId == null) {
            return false;
        }
        return pendingInvitePartyByTarget.remove(targetId) != null;
    }

    // --- Private helpers ---

    private void cleanupInvitesForParty(String partyId) {
        pendingInvitePartyByTarget.entrySet().removeIf(e -> partyId.equals(e.getValue()));
    }

    private void broadcastToParty(PurgeParty party, String message) {
        if (party == null) {
            return;
        }
        for (UUID memberId : party.getMembersSnapshot()) {
            sendMessageToPlayer(memberId, message);
        }
    }

    private void sendMessageToPlayer(UUID playerId, String text) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null) {
                return;
            }
            playerRef.sendMessage(Message.raw(text));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to send party message");
        }
    }

    private String getPlayerName(UUID playerId) {
        return PurgePlayerNameResolver.resolve(playerId, PurgePlayerNameResolver.FallbackStyle.FULL_UUID);
    }
}
