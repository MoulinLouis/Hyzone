package io.hyvexa.ascend.data;

public class AscendPlayerProgress {

    private final EconomyState economy = new EconomyState();
    private final GameplayState gameplay = new GameplayState();
    private final AutomationConfig automation = new AutomationConfig();
    private final SessionState session = new SessionState();

    public EconomyState economy() { return economy; }
    public GameplayState gameplay() { return gameplay; }
    public AutomationConfig automation() { return automation; }
    public SessionState session() { return session; }
}
