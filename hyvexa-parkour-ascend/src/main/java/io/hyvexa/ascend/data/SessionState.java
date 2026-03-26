package io.hyvexa.ascend.data;

public class SessionState {

    private volatile boolean sessionFirstRunClaimed;
    private volatile boolean hudHidden;
    private volatile boolean playersHidden;

    // ── Session first run ─────────────────────────────────────────────

    public boolean isSessionFirstRunClaimed() { return sessionFirstRunClaimed; }

    public void setSessionFirstRunClaimed(boolean sessionFirstRunClaimed) { this.sessionFirstRunClaimed = sessionFirstRunClaimed; }

    // ── UI settings ───────────────────────────────────────────────────

    public boolean isHudHidden() { return hudHidden; }

    public void setHudHidden(boolean hudHidden) { this.hudHidden = hudHidden; }

    public boolean isPlayersHidden() { return playersHidden; }

    public void setPlayersHidden(boolean playersHidden) { this.playersHidden = playersHidden; }
}
