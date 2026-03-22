# Plan: Workflow Reminders Cleanup

**Goal:** Remove code-pattern items from Workflow Reminders that are already documented in reference files. Shorten verbose items.

## Current State (15 items)

| # | Item | Type | Already in docs? |
|---|------|------|-------------------|
| 1 | Update CHANGELOG.md | Workflow | No — unique |
| 2 | Update ECONOMY_BALANCE.md | Workflow | No — unique |
| 3 | Follow existing patterns | Workflow | No — unique |
| 4 | Reuse existing Managers | Workflow | No — unique |
| 5 | Run tests selectively | Workflow | No — unique (but verbose) |
| 6 | UI paths | Code pattern | YES — CODE_PATTERNS.md L136-142, HYTALE_API.md L147 |
| 7 | Check ref validity | Code pattern | YES — CODE_PATTERNS.md L424-428, HYTALE_API.md L145 |
| 8 | World thread for entity ops | Code pattern | YES — CODE_PATTERNS.md L392-398, HYTALE_API.md L49-62 |
| 9 | Entity removal | Code pattern | YES — CODE_PATTERNS.md L515-516, HYTALE_API.md L42-47 |
| 10 | Singleton components | Code pattern | YES — CODE_PATTERNS.md L518-520, HYTALE_API.md L28-39 |
| 11 | Track online players | Code pattern | YES — CODE_PATTERNS.md L535-546 |
| 12 | Disable NPC AI | Code pattern | YES — CODE_PATTERNS.md L520, HYTALE_API.md L504-506 |
| 13 | Avoid Unicode in chat | Gotcha | No — unique, not in any docs file |
| 14 | No auto-memory | Workflow | No — unique |
| 15 | Plans in docs/plans/ | Workflow | No — unique |

## Changes

### Step 1: Remove items 6-12 from Workflow Reminders

These are code patterns, not workflow rules. All 7 are already documented (with more detail and examples) in `docs/CODE_PATTERNS.md` and `docs/HYTALE_API.md`. Agents consult those files when doing entity/UI work — that's their purpose.

No changes to target files needed — the content is already there.

### Step 2: Shorten item 5 (Run tests selectively)

**Before (1 long line):**
> Run tests selectively - only run tests when explicitly asked OR after large/significant changes (new features, multi-file refactors). Do NOT run tests after every small task. Only pure-logic classes with zero Hytale imports are testable — classes importing Hytale types (e.g. `Message`, `HytaleLogger`, `EntityRef`) cannot be tested without `HytaleServer.jar` on the test classpath (not yet configured).

**After:**
> Run tests selectively - only when explicitly asked or after large changes. Only pure-logic classes (zero Hytale imports) are testable.

The `HytaleServer.jar` classpath detail and the specific type examples are implementation details that don't change agent behavior — if a test fails with import errors, the agent will figure out why.

### Step 3: Move Unicode gotcha to HYTALE_API.md

Item 13 (Avoid Unicode) is a Hytale engine gotcha, not a workflow rule. It belongs in `docs/HYTALE_API.md` under "Common Gotchas Summary" where similar gotchas live. Add it there, remove from Workflow Reminders.

### Step 4: Renumber remaining items

**After cleanup (8 items):**
1. Update CHANGELOG.md *(unchanged)*
2. Update ECONOMY_BALANCE.md *(unchanged)*
3. Follow existing patterns *(unchanged)*
4. Reuse existing Managers *(unchanged)*
5. Run tests selectively *(shortened)*
6. No auto-memory for project knowledge *(was #14)*
7. Plans go in docs/plans/ *(was #15)*
8. Avoid Unicode in chat messages *(was #13)* **-- OR removed if moved to HYTALE_API.md**

## Result

- Workflow Reminders: 15 items -> 7-8 items
- No information lost (code patterns already in docs, Unicode gotcha moved to HYTALE_API.md)
- Section becomes purely workflow rules, no code snippets mixed in

## Risk

Low. All removed items have full coverage in reference files. If an agent needs entity patterns, they read `docs/HYTALE_API.md` — that's what the Reference Files table points them to.

## Decision: Unicode gotcha

Move entirely to HYTALE_API.md, remove from Workflow Reminders. Workflow Reminders should be process rules, not engine gotchas. When writing chat messages, the agent is already coding and should be consulting HYTALE_API.md. Keeping it in both places perpetuates the duplication pattern we're cleaning up.

**Final item count: 7 items** (down from 15).
