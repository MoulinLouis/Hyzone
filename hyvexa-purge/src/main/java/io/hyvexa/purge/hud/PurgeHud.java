package io.hyvexa.purge.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

public class PurgeHud extends CustomUIHud {

    private int lastPlayerCount = -1;
    private long lastVexa = -1;
    private int lastWave = -1;
    private int lastAlive = -1;
    private int lastTotal = -1;
    private long lastScrap = -1;
    private String lastIntermissionText = null;
    private int lastHpCurrent = -1;
    private int lastHpMax = -1;
    private int lastCombo = -1;
    private int lastComboBarQuantized = -1;
    private boolean comboVisible = false;
    private int lastUpgHp = -1;
    private int lastUpgAmmo = -1;
    private int lastUpgSpeed = -1;
    private int lastUpgLuck = -1;
    private String lastWxpName = null;
    private String lastWxpXpText = null;
    private int lastWxpBarQuantized = -1;
    private boolean wxpVisible = false;
    private boolean killMeterVisible = false;
    private int lastKmPlayerCount = -1;
    private final String[] lastKmNames = new String[5];
    private final int[] lastKmKills = new int[]{-1, -1, -1, -1, -1};

    public PurgeHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/Purge_RunHud.ui");
    }

    public void updateVexa(long vexa) {
        if (vexa == lastVexa) {
            return;
        }
        lastVexa = vexa;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PlayerVexaValue.Text", String.valueOf(vexa));
        update(false, cmd);
    }

    public void updatePlayerCount() {
        updatePlayerCount(Universe.get().getPlayers().size());
    }

    public void updatePlayerCount(int count) {
        if (count == lastPlayerCount) {
            return;
        }
        lastPlayerCount = count;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PlayerCountText.Text", String.format("%,d", count));
        update(false, cmd);
    }

    public void updateWaveStatus(int wave, int alive, int total) {
        if (wave == lastWave && alive == lastAlive && total == lastTotal) {
            return;
        }
        lastWave = wave;
        lastAlive = alive;
        lastTotal = total;
        lastIntermissionText = null;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#WaveLabel.Text", "WAVE " + wave);
        cmd.set("#ZombieCountLabel.Text", "");
        update(false, cmd);
    }

    public void updateIntermission(int seconds) {
        String text = "Next wave in " + seconds + "...";
        if (text.equals(lastIntermissionText)) {
            return;
        }
        lastIntermissionText = text;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#ZombieCountLabel.Text", text);
        update(false, cmd);
    }

    public void updateScrap(long scrap) {
        if (scrap == lastScrap) {
            return;
        }
        lastScrap = scrap;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#ScrapHudValue.Text", String.format("%,d", scrap));
        update(false, cmd);
    }

    public void updatePlayerHealth(int current, int max) {
        if (current == lastHpCurrent && max == lastHpMax) {
            return;
        }
        lastHpCurrent = current;
        lastHpMax = max;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PlayerHealthLabel.Text", "HP: " + current + " / " + max);
        update(false, cmd);
    }

    public void setWaveStatusVisible(boolean visible) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#WaveStatusRow.Visible", visible);
        update(false, cmd);
    }

    public void setPlayerHealthVisible(boolean visible) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PlayerHealthRow.Visible", visible);
        update(false, cmd);
    }

    public void updateCombo(int combo, float barProgress) {
        UICommandBuilder cmd = new UICommandBuilder();
        boolean shouldShow = combo >= 2;
        if (shouldShow != comboVisible) {
            comboVisible = shouldShow;
            cmd.set("#ComboRoot.Visible", shouldShow);
        }
        if (!shouldShow) {
            if (lastCombo != -1 || lastComboBarQuantized != -1) {
                lastCombo = -1;
                lastComboBarQuantized = -1;
                update(false, cmd);
            }
            return;
        }
        if (combo != lastCombo) {
            lastCombo = combo;
            cmd.set("#ComboLabel.Text", "COMBO x" + combo);
        }
        int quantized = (int) (barProgress * 1000);
        if (quantized != lastComboBarQuantized) {
            lastComboBarQuantized = quantized;
            cmd.set("#ComboBar.Value", barProgress);
        }
        update(false, cmd);
    }

    public void updateUpgradeLevels(int hp, int ammo, int speed, int luck) {
        UICommandBuilder cmd = new UICommandBuilder();
        boolean changed = false;
        if (hp != lastUpgHp) {
            lastUpgHp = hp;
            cmd.set("#UpgHp.Visible", hp > 0);
            cmd.set("#UpgHpLv.Text", "Lv " + hp);
            changed = true;
        }
        if (ammo != lastUpgAmmo) {
            lastUpgAmmo = ammo;
            cmd.set("#UpgAmmo.Visible", ammo > 0);
            cmd.set("#UpgAmmoLv.Text", "Lv " + ammo);
            changed = true;
        }
        if (speed != lastUpgSpeed) {
            lastUpgSpeed = speed;
            cmd.set("#UpgSpeed.Visible", speed > 0);
            cmd.set("#UpgSpeedLv.Text", "Lv " + speed);
            changed = true;
        }
        if (luck != lastUpgLuck) {
            lastUpgLuck = luck;
            cmd.set("#UpgLuck.Visible", luck > 0);
            cmd.set("#UpgLuckLv.Text", "Lv " + luck);
            changed = true;
        }
        if (changed) {
            update(false, cmd);
        }
    }

    public void updateWeaponXp(String nameText, String xpText, float barProgress) {
        UICommandBuilder cmd = new UICommandBuilder();
        boolean changed = false;
        if (!wxpVisible) {
            wxpVisible = true;
            cmd.set("#WeaponXpHud.Visible", true);
            changed = true;
        }
        if (!nameText.equals(lastWxpName)) {
            lastWxpName = nameText;
            cmd.set("#WxpWeaponName.Text", nameText);
            changed = true;
        }
        if (!xpText.equals(lastWxpXpText)) {
            lastWxpXpText = xpText;
            cmd.set("#WxpXpText.Text", xpText);
            changed = true;
        }
        int quantized = (int) (barProgress * 1000);
        if (quantized != lastWxpBarQuantized) {
            lastWxpBarQuantized = quantized;
            cmd.set("#WxpBar.Value", barProgress);
            changed = true;
        }
        if (changed) {
            update(false, cmd);
        }
    }

    public void hideWeaponXp() {
        if (wxpVisible) {
            wxpVisible = false;
            lastWxpName = null;
            lastWxpXpText = null;
            lastWxpBarQuantized = -1;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#WeaponXpHud.Visible", false);
            update(false, cmd);
        }
    }

    public void updateKillMeter(String[] names, int[] kills, int count) {
        UICommandBuilder cmd = new UICommandBuilder();
        boolean changed = false;
        if (!killMeterVisible) {
            killMeterVisible = true;
            cmd.set("#KillMeter.Visible", true);
            changed = true;
        }
        if (count != lastKmPlayerCount) {
            for (int i = 0; i < 5; i++) {
                boolean visible = i < count;
                boolean wasVisible = i < lastKmPlayerCount || lastKmPlayerCount == -1;
                if (visible != wasVisible || lastKmPlayerCount == -1) {
                    cmd.set("#KmRow" + i + ".Visible", visible);
                    changed = true;
                }
            }
            lastKmPlayerCount = count;
        }
        for (int i = 0; i < count && i < 5; i++) {
            if (!names[i].equals(lastKmNames[i])) {
                lastKmNames[i] = names[i];
                cmd.set("#KmName" + i + ".Text", names[i]);
                changed = true;
            }
            if (kills[i] != lastKmKills[i]) {
                lastKmKills[i] = kills[i];
                cmd.set("#KmKills" + i + ".Text", String.valueOf(kills[i]));
                changed = true;
            }
        }
        if (changed) {
            update(false, cmd);
        }
    }

    public void hideKillMeter() {
        if (killMeterVisible) {
            killMeterVisible = false;
            lastKmPlayerCount = -1;
            for (int i = 0; i < 5; i++) {
                lastKmNames[i] = null;
                lastKmKills[i] = -1;
            }
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#KillMeter.Visible", false);
            update(false, cmd);
        }
    }

    public void resetCache() {
        lastWave = -1;
        lastAlive = -1;
        lastTotal = -1;
        lastScrap = -1;
        lastPlayerCount = -1;
        lastVexa = -1;
        lastIntermissionText = null;
        lastHpCurrent = -1;
        lastHpMax = -1;
        lastCombo = -1;
        lastComboBarQuantized = -1;
        comboVisible = false;
        lastUpgHp = -1;
        lastUpgAmmo = -1;
        lastUpgSpeed = -1;
        lastUpgLuck = -1;
        lastWxpName = null;
        lastWxpXpText = null;
        lastWxpBarQuantized = -1;
        wxpVisible = false;
        killMeterVisible = false;
        lastKmPlayerCount = -1;
        for (int i = 0; i < 5; i++) {
            lastKmNames[i] = null;
            lastKmKills[i] = -1;
        }
    }
}
