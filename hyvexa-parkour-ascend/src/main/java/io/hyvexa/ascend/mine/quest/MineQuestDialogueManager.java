package io.hyvexa.ascend.mine.quest;

import io.hyvexa.ascend.mine.hud.MineHudManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages transient dialogue sessions for quest NPC interactions.
 * All state is in-memory only — no persistence needed.
 */
public class MineQuestDialogueManager {

    private static final String NPC_NAME = "Old Miner";
    private static final long DIALOGUE_TIMEOUT_MS = 5000;

    private final MineHudManager mineHudManager;
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, DialogueType> lastDialogueType = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dialogueShowTime = new ConcurrentHashMap<>();

    public enum DialogueType { GIVE, COMPLETE, REMINDER }

    public MineQuestDialogueManager(MineHudManager mineHudManager) {
        this.mineHudManager = mineHudManager;
    }

    public void startGiveDialogue(UUID playerId, MineQuest quest) {
        String[] lines = quest.getGiveDialogue();
        DialogueSession session = new DialogueSession(lines, DialogueType.GIVE);
        sessions.put(playerId, session);
        dialogueShowTime.put(playerId, System.currentTimeMillis());
        showLine(playerId, session);
    }

    public void startCompleteDialogue(UUID playerId, MineQuest quest) {
        String[] lines = quest.getCompleteDialogue();
        DialogueSession session = new DialogueSession(lines, DialogueType.COMPLETE);
        sessions.put(playerId, session);
        dialogueShowTime.put(playerId, System.currentTimeMillis());
        showLine(playerId, session);
    }

    /**
     * Shows the last give dialogue line as a reminder (objective).
     * Auto-fades after 5s, no session to advance.
     */
    public void showObjectiveReminder(UUID playerId, MineQuest quest, long current, long target) {
        String[] lines = quest.getGiveDialogue();
        String lastLine = lines[lines.length - 1];
        String reminderText = lastLine + " (" + current + "/" + target + ")";
        dialogueShowTime.put(playerId, System.currentTimeMillis());
        // No session — just show the line, it'll auto-fade
        if (mineHudManager != null) {
            mineHudManager.showDialogue(playerId, NPC_NAME, reminderText, true);
        }
    }

    /**
     * Advances to the next line. Returns true if dialogue has ended.
     */
    public boolean advanceLine(UUID playerId) {
        DialogueSession session = sessions.get(playerId);
        if (session == null) return true;

        session.lineIndex++;
        dialogueShowTime.put(playerId, System.currentTimeMillis());

        if (session.lineIndex >= session.lines.length) {
            lastDialogueType.put(playerId, session.type);
            sessions.remove(playerId);
            if (mineHudManager != null) {
                mineHudManager.hideDialogue(playerId);
            }
            return true;
        }

        showLine(playerId, session);
        return false;
    }

    /**
     * Called each tick — auto-hides dialogue after 5s of no interaction.
     */
    public void tick(UUID playerId) {
        Long showTime = dialogueShowTime.get(playerId);
        if (showTime == null) return;

        if (System.currentTimeMillis() - showTime > DIALOGUE_TIMEOUT_MS) {
            dialogueShowTime.remove(playerId);
            // End active session if any
            if (sessions.remove(playerId) != null) {
                lastDialogueType.remove(playerId);
            }
            if (mineHudManager != null) {
                mineHudManager.hideDialogue(playerId);
            }
        }
    }

    public boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void endDialogue(UUID playerId) {
        sessions.remove(playerId);
        lastDialogueType.remove(playerId);
        dialogueShowTime.remove(playerId);
        if (mineHudManager != null) {
            mineHudManager.hideDialogue(playerId);
        }
    }

    public DialogueType getLastDialogueType(UUID playerId) {
        return lastDialogueType.get(playerId);
    }

    public void clearLastType(UUID playerId) {
        lastDialogueType.remove(playerId);
    }

    private void showLine(UUID playerId, DialogueSession session) {
        if (mineHudManager == null) return;
        String line = session.lines[session.lineIndex];
        boolean isLast = session.lineIndex >= session.lines.length - 1;
        mineHudManager.showDialogue(playerId, NPC_NAME, line, isLast);
    }

    private static class DialogueSession {
        final String[] lines;
        final DialogueType type;
        int lineIndex;

        DialogueSession(String[] lines, DialogueType type) {
            this.lines = lines;
            this.type = type;
            this.lineIndex = 0;
        }
    }
}
