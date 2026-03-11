# hyvexa-core Refactoring Plan

Verified issues only. False positives removed after source verification.

---

## 1. DiscordLinkStore: Remove redundant ALTER TABLE migration

**File:** `hyvexa-core/src/main/java/io/hyvexa/core/discord/DiscordLinkStore.java`

**Problem:** `current_rank` and `last_synced_rank` are already in the CREATE TABLE statement (lines 67-68), but lines 87-113 also run ALTER TABLE ADD COLUMN on every startup. The ALTER is caught as "duplicate column" and logged — pure wasted work.

**Plan:**
1. Open `DiscordLinkStore.java`
2. Delete lines 87-113 (both ALTER TABLE blocks and their try-catch wrappers)
3. Delete the `isDuplicateColumn(SQLException)` helper if it becomes unused
4. Verify the CREATE TABLE at lines 62-69 already includes both columns — it does:
   ```java
   + "current_rank VARCHAR(20) DEFAULT 'Unranked', "
   + "last_synced_rank VARCHAR(20) DEFAULT NULL"
   ```

---

## 2. VoteStore: Remove unused parameter

**File:** `hyvexa-core/src/main/java/io/hyvexa/core/vote/VoteStore.java`

**Problem:** `queryTopVoters(String sql, int limit, long unused)` at line 196 — third parameter literally named `unused`. One caller at line 134 passes `0`.

**Plan:**
1. Change signature at line 196: remove `long unused`
2. Update the single caller at line 134-135: remove the `, 0` argument

---

## 3. FormatUtils: Deduplicate time extraction

**File:** `hyvexa-core/src/main/java/io/hyvexa/common/util/FormatUtils.java`

**Problem:** 4 methods (lines 16-63) all compute `totalSeconds = ms / 1000`, `minutes = totalSeconds / 60`, `seconds = totalSeconds % 60` independently.

**Plan:**
1. Add a private record at the top of the class:
   ```java
   private record TimeComponents(long hours, long minutes, long seconds, long centis, long millis) {}
   ```
2. Add extraction helper:
   ```java
   private static TimeComponents extract(long durationMs) {
       long totalMs = Math.max(0L, durationMs);
       long totalSeconds = totalMs / 1000L;
       return new TimeComponents(
           totalSeconds / 3600L,
           (totalSeconds % 3600L) / 60L,
           totalSeconds % 60L,
           (totalMs % 1000L) / 10L,
           totalMs % 1000L
       );
   }
   ```
3. Rewrite each format method to call `extract()` then format its output string. Keep each method's return format identical to current behavior.

---

## 4. DatabaseManager: Centralize applyQueryTimeout (large)

**Files:** 15+ store classes across core module

**Problem:** Every prepared statement manually calls `DatabaseManager.applyQueryTimeout(stmt)`. If timeout policy changes, 15+ files need updating.

**Plan:**
1. Add to `DatabaseManager`:
   ```java
   public static PreparedStatement prepare(Connection conn, String sql) throws SQLException {
       PreparedStatement stmt = conn.prepareStatement(sql);
       applyQueryTimeout(stmt);
       return stmt;
   }
   ```
2. Find all call sites: `grep -rn "applyQueryTimeout" hyvexa-core/src/`
3. Replace each pattern:
   ```java
   // Before:
   try (PreparedStatement stmt = conn.prepareStatement(sql)) {
       DatabaseManager.applyQueryTimeout(stmt);
   // After:
   try (PreparedStatement stmt = DatabaseManager.prepare(conn, sql)) {
   ```
4. Keep `applyQueryTimeout()` public for edge cases where a statement is created differently.

**Note:** This is mechanical but touches many files. Do one file at a time, verify compilation.
