Currency Rename Plan

 Context

 Rename two currencies across the codebase:
 1. Global "gems" → "vexa" — main cross-mode currency, using existing vexa.png images
 2. Ascend "vexa" → "volt" — ascend-specific currency, using volt.png from project root

 Since "vexa" is currently the ascend currency name AND the target name for the global currency,
 ascend vexa→volt must happen first to avoid conflicts.

 ---
 Phase 1: Ascend Currency — "vexa" → "volt"

 1A. Java Files (hyvexa-parkour-ascend)

 Data layer:
 - AscendPlayerProgress.java — rename fields: vexa→volt, totalVexaEarned→totalVoltEarned,
 summitAccumulatedVexa→summitAccumulatedVolt, elevationAccumulatedVexa→elevationAccumulatedVolt, and
 all getters/setters/methods (getVexa→getVolt, setVexa→setVolt, casVexa→casVolt, addVexa→addVolt,
 etc.)
 - AscendDatabaseSetup.java — update CREATE TABLE column names (vexa_mantissa→volt_mantissa,
 vexa_exp10→volt_exp10), rename migrateCoinsColumnsToVexa→migrateCoinsColumnsToVolt, update all
 column name targets in migrations, add new migration migrateVexaColumnsToVolt to rename existing DB
 columns
 - AscendPlayerPersistence.java — update all SQL column references and field mappings
 - AscendPlayerStore.java — update vexa references in method calls

 UI/HUD layer:
 - AscendHud.java — rename lastVexaText→lastVoltText, lastVexaPerRunText→lastVoltPerRunText,
 lastVexa→lastVolt, update UI element references (#TopVexaValue→#TopVoltValue,
 #TopVexaPerRunValue→#TopVoltPerRunValue)
 - AscendHudManager.java — update method calls
 - AscendAdminVexaPage.java → rename file to AscendAdminVoltPage.java, update all references
 (VexaAmountField→VoltAmountField, CurrentVexaValue→CurrentVoltValue, AddVexa→AddVolt,
 RemoveVexa→RemoveVolt, display strings)
 - AscendOnboardingCopy.java — replace all "vexa" display text with "volt"

 Game systems (rename vexa→volt in method calls and variables):
 - AscendRunTracker.java, RobotManager.java, AscendMapSelectPage.java
 - ElevationPage.java, SummitManager.java, SummitPage.java
 - AscensionManager.java, AscensionPage.java
 - TranscendenceManager.java, TranscendencePage.java
 - PassiveEarningsManager.java, PassiveEarningsPage.java
 - StatsPage.java, AscendLeaderboardPage.java
 - ChallengeSnapshot.java, TutorialTriggerService.java
 - ElevateCommand.java
 - AscendConstants.java — rename ASCENSION_VEXA_THRESHOLD → ASCENSION_VOLT_THRESHOLD (if present)

 1B. UI Files (hyvexa-parkour-ascend)

 - Ascend_RunHud.ui — rename #TopVexaHud→#TopVoltHud, #VexaBar→#VoltBar, #VexaAccent→#VoltAccent,
 #VexaContent→#VoltContent, #VexaIcon→#VoltIcon, #TopVexaValue→#TopVoltValue,
 #TopVexaPerRunValue→#TopVoltPerRunValue, change vexa.png→volt.png
 - Ascend_AdminVexa.ui → rename file to Ascend_AdminVolt.ui, update title/element IDs
 (CurrentVexaValue→CurrentVoltValue, VexaAmountField→VoltAmountField)
 - All other ascend UI files with "Vexa"/"vexa" text: Ascend_Elevation.ui, Ascend_Ascension.ui,
 Ascend_Help.ui, Ascend_Tutorial_*.ui, Ascend_MapSelect.ui, Ascend_MapSelectEntry.ui,
 Ascend_PassiveEarnings.ui, Ascend_PassiveEarningsEntry.ui, Ascend_Summit.ui,
 Ascend_Transcendence.ui, Ascend_Stats.ui, Ascend_Welcome.ui, Ascend_Leaderboard.ui,
 Ascend_HudPreview.ui

 1C. Image Files

 - Copy volt.png from project root →
 hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Textures/volt.png

 1D. Database Migration

 Add migrateVexaColumnsToVolt method in AscendDatabaseSetup.java:
 - Rename vexa_mantissa → volt_mantissa
 - Rename vexa_exp10 → volt_exp10
 - Rename total_vexa_earned_mantissa → total_volt_earned_mantissa
 - Rename total_vexa_earned_exp10 → total_volt_earned_exp10
 - Rename summit_accumulated_vexa_mantissa → summit_accumulated_volt_mantissa
 - Rename summit_accumulated_vexa_exp10 → summit_accumulated_volt_exp10
 - Rename elevation_accumulated_vexa_mantissa → elevation_accumulated_volt_mantissa
 - Rename elevation_accumulated_vexa_exp10 → elevation_accumulated_volt_exp10
 - Pattern: check if old column exists, rename if so (idempotent)

 ---
 Phase 2: Global Currency — "gems" → "vexa"

 2A. Core Java (hyvexa-core)

 - GemStore.java → create new VexaStore.java at same package, delete GemStore.java
   - Class: VexaStore, methods: getVexa/setVexa/addVexa/removeVexa/evictPlayer
   - DB table: player_vexa (column vexa)
   - Add migration: rename player_gems → player_vexa + column gems → vexa (idempotent)
   - Comments/logs updated
 - DiscordLinkStore.java — rename GEM_REWARD→VEXA_REWARD, checkAndRewardGems→checkAndRewardVexa,
 update SQL gems_rewarded→vexa_rewarded, update display strings, add column migration, update
 gemsRewarded→vexaRewarded in DiscordLink record

 2B. Parkour Module (hyvexa-parkour)

 - GemsCommand.java → create new VexaCommand.java, delete GemsCommand.java
   - Command name: /vexa, methods/messages updated
 - HyvexaPlugin.java — update imports (GemStore→VexaStore, GemsCommand→VexaCommand)
 - HudManager.java — update import
 - RunHud.java — rename lastGems→lastVexa, updateGems→updateVexa, UI references
 - CosmeticShopPage.java — update GemStore→VexaStore, #GemBalance→#VexaBalance

 2C. Hub Module (hyvexa-hub)

 - HubHud.java — rename lastGems→lastVexa, updateGems→updateVexa, UI refs
 - HyvexaHubPlugin.java — update checkAndRewardGems→checkAndRewardVexa, GemStore→VexaStore

 2D. Ascend Module (hyvexa-parkour-ascend)

 - AscendHud.java — rename lastGems→lastVexa, updateGems→updateVexa (global currency display in side
 panel)
 - AscendHudManager.java — update GemStore→VexaStore
 - ParkourAscendPlugin.java — update imports and calls

 2E. Purge Module (hyvexa-purge)

 - PurgeHud.java — rename lastGems→lastVexa, updateGems→updateVexa, UI refs
 - Purge plugin main class — update GemStore→VexaStore references

 2F. RunOrFall Module (hyvexa-runorfall)

 - RunOrFallHud.java — rename lastGems→lastVexa, updateGems→updateVexa, UI refs
 - RunOrFall plugin main class — update GemStore→VexaStore references

 2G. UI Files (all 5 module HUDs)

 In each module's RunHud .ui file:
 - #PlayerGemsRow → #PlayerVexaRow
 - #GemIcon → #VexaIcon
 - #PlayerGemsValue → #PlayerVexaValue
 - gem.png → vexa.png

 Files: Parkour_RunHud.ui, Hub_RunHud.ui, Ascend_RunHud.ui, Purge_RunHud.ui, RunOrFall_RunHud.ui

 Cosmetic shop: Parkour_CosmeticShop.ui — #GemBalance → #VexaBalance, gem.png → vexa.png

 2H. Image Files

 - Copy vexa.png from hub or ascend textures → parkour, purge, runorfall texture dirs (it already
 exists in hub + ascend)
 - Delete gem.png from all 5 module texture dirs after migration

 2I. Database Migration

 In VexaStore.initialize():
 - RENAME TABLE player_gems TO player_vexa (if player_gems exists)
 - ALTER TABLE player_vexa RENAME COLUMN gems TO vexa (if gems column exists)

 In DiscordLinkStore.initialize():
 - ALTER TABLE discord_links RENAME COLUMN gems_rewarded TO vexa_rewarded (if gems_rewarded exists)

 2J. Discord Bot

 - discord-bot/src/index.js — change "100 gems" → "100 vexa" in display string
 - discord-bot/src/db.js — update gems_rewarded → vexa_rewarded in SQL
 - discord-bot/package.json — update description

 ---
 Phase 3: Documentation & Memory

 - CHANGELOG.md — update historical references
 - docs/ECONOMY_BALANCE.md — rename Gems section → Vexa
 - docs/DATABASE.md — update table/column names
 - CLAUDE.md — update MEMORY.md references (GemStore→VexaStore, gems→vexa, etc.)
 - MEMORY.md — update the Global Gems Currency section → Global Vexa Currency

 ---

 Verification

 - grep -ri "gemstore\|GemStore\|gem\.png\|getGems\|setGems\|addGems\|removeGems\|lastGems\|updateGem
 s\|PlayerGemsValue\|GemIcon\|GemBalance\|PlayerGemsRow\|player_gems\|gems_rewarded\|GemsCommand"
 --include="*.java" --include="*.ui" --include="*.js" should return zero results
 - grep -ri "\"vexa\"" --include="*.java" hyvexa-parkour-ascend/ should return zero ascend currency
 results (all should be "volt" now)
 - Verify no broken imports (all GemStore imports → VexaStore)
 - Owner runs ./gradlew build to verify compilation