# Player Patch Notes

What's new in Hyvexa? All player-facing changes, newest first.

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
