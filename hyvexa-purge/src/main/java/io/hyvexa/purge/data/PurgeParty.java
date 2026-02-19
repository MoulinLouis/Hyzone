package io.hyvexa.purge.data;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeParty {

    public static final int MAX_SIZE = 5;

    private final String partyId;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();

    public PurgeParty(String partyId, UUID creator) {
        this.partyId = partyId;
        if (creator != null) {
            members.add(creator);
        }
    }

    public String getPartyId() {
        return partyId;
    }

    public Set<UUID> getMembersSnapshot() {
        return Set.copyOf(members);
    }

    public boolean addMember(UUID playerId) {
        if (playerId == null || members.size() >= MAX_SIZE) {
            return false;
        }
        return members.add(playerId);
    }

    public boolean removeMember(UUID playerId) {
        return playerId != null && members.remove(playerId);
    }

    public boolean contains(UUID playerId) {
        return playerId != null && members.contains(playerId);
    }

    public int size() {
        return members.size();
    }
}
