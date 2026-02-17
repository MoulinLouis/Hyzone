# Player Patch Notes

What's new in Hyvexa? All player-facing changes, newest first.

---

## v0.1.4 - 2026-02-17

### New
- **Challenge Leaderboard** - See how you stack up against other players on each challenge with a new leaderboard tab.

### Improved
- **Challenge 7 Rebalanced** - Adjusted difficulty for a fairer experience with locked maps.

### Fixed
- **Elevation Achievements** - Achievements now trigger based on your actual multiplier instead of an internal value, so milestones unlock when they should.
- **Momentum Mastery Skill** - Now displays the correct x3.0 multiplier instead of a wrong value.
- **Buy All Button** - No longer crashes when you have locked maps.
- **Auto-Elevation and Auto-Summit** - No longer fire during active runs, and target sequences no longer get stuck.
- **Leaderboard Holograms** - Fixed duplicate entries appearing after updates.

---

## v0.1.3 - 2026-02-17

### New
- **6 New Ascendancy Tree Nodes** - Runner Speed IV & V, Evolution Power III, Momentum Mastery (x3.0 for 120s), Multiplier Boost II (+0.25), and Elevation Boost (-30% cost). Deep late-game skills costing 15-75 AP.
- **3 New Challenges** - Challenge 5 disables half your Runner Speed and Multiplier Gain, Challenge 6 cuts all Summit bonuses by 50%, and Challenge 7 locks maps 4 and 5. Total challenges now: 7.
- **AP Multiplier** - Each challenge you complete permanently increases the AP you earn per Ascension. Base x1, scaling up to x8 with all 7 challenges done.

### Improved
- **Summit Levels Uncapped** - Summit categories no longer cap at level 1000. Beyond 1000, XP costs grow steeply (cubic scaling), so progress slows but never stops.

### Fixed
- **Auto-Elevation and Auto-Summit** - Fixed target comparison and stale UI issues that caused these automations to malfunction. Both are now re-enabled for all players.

---

## v0.1.2 - 2026-02-16

### New
- **Gems** - A new global currency displayed on all HUDs. Earn gems through Discord linking and spend them in the Cosmetic Shop.
- **Cosmetic Shop** - Use `/shop` to browse and buy 6 glow effects (100 gems each). Equip, preview, and unequip cosmetics — your equipped glow persists across reconnects.
- **Discord Linking** - Use `/link` in-game to get a code, then enter it on Discord with `/link <code>` to connect your accounts. You'll receive 100 gems as a reward on your next login. Use `/unlink` to disconnect.
- **Discord Rank Roles** - Your parkour rank (Bronze through VexaGod) automatically syncs as a Discord role when your accounts are linked.
- **`/skill`** - Unlock Ascendancy Tree nodes directly via chat without opening the UI.
- **`/elevate` and `/summit`** - Quick commands to elevate or summit without opening the menu.

### Improved
- **Summit XP recalibrated** - Scaling adjusted so reaching level 1000 requires roughly 1 Decillion accumulated Vexa, making the endgame grind more consistent.

### Fixed
- **Skill tree points** - Fixed an issue where the Ascendancy Tree rebalance could leave players with negative AP.
- **Auto-Elevation** - Fixed target comparison and stale UI after auto-elevate or auto-summit triggers.

---

## v0.1.1 - 2026-02-15

### New
- **Auto-Summit** - Unlock the Auto-Summit skill node to automatically cycle through Summit categories with configurable thresholds and timer delay. Managed from the Automation page.
- **/hello** - Say hello and get a greeting back.
- **/ping** - Check your connection to the server.

### Improved
- **Ascendancy Tree rebalanced** - Summit categories reordered, skill costs adjusted, Momentum Surge reduced from x3 to x2.5, and tree layout streamlined.

### Fixed
- **Multiplier Boost skill** - Now correctly adds +0.10 to your base multiplier gain per run instead of applying a flat +10% bonus.

---

## v0.1.0 - 2026-02-15

### New
- **Ascend Mode** - A brand new idle-parkour hybrid mode. Runners follow your personal best ghost recordings and earn Vexa automatically while you progress through 5 maps.
- **Three-Tier Prestige System** - Elevation (multiplier boosts), Summit (permanent category upgrades), and Ascension (full reset for skill points). Each layer adds depth to your progression.
- **Skill Tree** - Unlock up to 11 nodes across multiple paths using Ascension Points. Includes Auto Runners, Auto-Evolution, Momentum, Runner Speed, Offline Boost, Summit Memory, Evolution Power+, Multiplier Boost, Auto-Elevation, and more.
- **Star Evolution** - Max out a runner at Lv.20 to evolve it (up to 5 stars). Each star doubles your multiplier gain per run and changes the runner's appearance.
- **Ascension Challenges** - Timed challenge runs where your progress is snapped, reset, and malus effects apply. Beat an ascension during a challenge for bonus Summit XP.
- **30 Achievements** - 6 categories (Milestones, Runners, Prestige, Skills, Challenges, Secret) with hidden achievements and a Completionist meta-achievement.
- **Passive Earnings** - Runners generate Vexa at 25% rate while you're offline or in another mode. Accumulates up to 24 hours with a detailed welcome-back breakdown.
- **Auto-Elevation** - Unlock the Auto-Elevation skill node to set multiplier targets and let elevation happen automatically.
- **Leaderboards** - Global rankings for Vexa, Ascensions, and Manual Runs with search and pagination. Plus per-map leaderboards for best times.
- **My Profile** - Access your Stats, Achievements, and Settings from one hub page.
- **Toast Notifications** - On-screen notifications for upgrades, evolutions, purchases, and economy events with color-coded accent bars.
- **Buy All / Evolve All** - One-click buttons to purchase all affordable upgrades or evolve all eligible runners.
- **Advanced HUD** - Toggle a compact panel showing real-time orientation, velocity, and speed above your HUD.
- **Walk-on-Start** - Step onto a map's start position to begin a run (if unlocked).
- **Practice Mode** - Set personal checkpoints to practice tricky sections without affecting your records.
- **Onboarding Tutorial** - 3-screen tutorial for new players covering maps, runners, and practice mode. Smart map recommendations after repeated failures.

### Improved
- **Currency renamed to Vexa** - "Coins" is now "Vexa" everywhere.
- **Summit caps** - Each Summit category maxes out at level 1000 with heavy diminishing returns after 500.
- **Elevation multiplier buff** - Higher elevation levels now give progressively better multipliers (slightly super-linear scaling).
- **Elevation costs reduced** - Mid and high-level elevation is significantly cheaper (level 100 is 63% cheaper, level 200 is 85% cheaper).
- **Elevation uses accumulated coins** - Spending on upgrades no longer reduces your elevation potential.
- **Map selector info** - Shows your runner completion time and multiplier gain per run.
- **Personal bests preserved** - Your map PBs now survive Ascension resets.
- **Runner speed rebalanced** - Base run times reduced and speed upgrades feel more impactful across all maps.
- **Multiplier precision** - Gain values now display with 2 decimal places.
- **Evolution Power works on runners** - Each star exponentially multiplies your gains.
- **Progress bars** - Momentum bars replaced with runner-synced progress bars.

### Fixed
- Fixed a crash when re-entering Ascend after being teleported out.
- Fixed a client crash when unlocking the Ascension Challenges skill node.
- Fixed HUD crash when showing HUD after a map run.
- Fixed star display showing "?" instead of actual star icons.
- Fixed ghost runners making erratic rotation snaps.
- Fixed a map unlock exploit that bypassed requirements.
- Fixed UI crash when interacting with NPCs after opening menus.
