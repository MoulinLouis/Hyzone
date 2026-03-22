# Public Release Hardening Plan

**Date**: 2026-03-22
**Scope**: Repository content, Git history, publication metadata, third-party notices, and final validation before changing GitHub visibility from private to public
**Goal**: Make the repository publicly visible while keeping it fully proprietary under `All Rights Reserved`, with a clean working tree, a cleaned Git history, and coherent public-facing documentation

---

## 1. Final Decisions

These decisions are already agreed and should be treated as implementation constraints:

- **License model**: Fully proprietary, `All Rights Reserved`
- **Repository visibility**: Public GitHub repository, but no permission to copy, modify, redistribute, or reuse without prior written permission
- **Commit metadata**: Rewrite personal commit emails to a GitHub `users.noreply.github.com` address if easy during the history rewrite
- **Bundled Hytale classes**: Remove tracked `com/hypixel/hytale/**/*.class` files unless a concrete build/runtime need is discovered during implementation
- **Ascend NPC command token**: Explicitly accepted as-is for this public release; do not block publication on changing the current hardcoded behavior
- **Nitrado WebServer jar**: Keep `libs/nitrado-webserver-1.1.1.jar`, preserve MIT attribution, and add a third-party notice file
- **Wardrobe assets**: Assume acceptable for publication unless the final audit reveals a concrete licensing problem
- **Mirrored remotes**: Assume none exist unless discovered during implementation
- **Build policy**: Do not run project builds during this cleanup unless explicitly requested by the repository owner

---

## 2. Success Criteria

The repository is ready to be made public only when all of the following are true:

1. No versioned production logs remain in the working tree
2. No versioned production logs remain anywhere in Git history
3. No bundled `com/hypixel/hytale/**/*.class` files remain in the working tree
4. No bundled `com/hypixel/hytale/**/*.class` files remain anywhere in Git history
5. Personal commit emails have been rewritten to a `noreply` address or explicitly accepted as public
6. The README and LICENSE are logically consistent for a public proprietary repository
7. The MIT-licensed Nitrado dependency has a clear third-party notice
8. A fresh clone of the cleaned repository shows no obvious secrets, private operational data, or history leakage
9. The rewritten history has been force-pushed and validated before repository visibility is changed

---

## 3. Out of Scope

The following are not part of this publication-hardening plan:

- Gameplay balance changes
- Feature development
- Large code refactors unrelated to publication safety
- Ascend NPC command token hardening for this release
- Replacing the proprietary license with an open-source license
- Replacing the Nitrado WebServer dependency
- Deep asset provenance work beyond issues directly found during the final audit

---

## 4. Implementation Phases

## Phase 1. Freeze and Back Up

**Objective**: Create a safe rollback point before touching history.

### Actions

- Announce a temporary push freeze for the repository
- Create a backup branch from the current default branch
- Create a full mirror backup of the repository
- Record current branches and tags before rewriting history

### Deliverables

- Backup branch created
- Mirror backup stored outside the working repository
- Snapshot of branch/tag state documented in the implementation notes

### Notes

- History rewriting is destructive for collaborators until everyone resets to the rewritten history
- Do not start content cleanup before the backup exists

---

## Phase 2. Clean the Working Tree

**Objective**: Remove obviously non-public content from the current tree before the history rewrite.

### Actions

- Delete `docs/prod_logs/**`
- Add `.gitignore` coverage for future runtime logs or similar local operational artifacts
- Review nearby diagnostic/generated paths for anything else that should never be tracked
- Remove tracked `com/hypixel/hytale/**/*.class` files from the repository tree

### Deliverables

- Clean working tree without versioned server logs
- Clean working tree without bundled Hytale class files
- `.gitignore` updated to reduce future accidental commits

### Validation

- `git status --short`
- `git ls-files docs/prod_logs`
- `git ls-files 'com/hypixel/hytale/**/*.class'`

Expected result:

- `docs/prod_logs` should no longer be tracked
- Hytale `.class` files should no longer be tracked

---

## Phase 3. Accept Current Ascend Token Behavior

**Objective**: Keep the publication plan internally consistent with the explicit decision to leave the current Ascend NPC command token behavior unchanged for this release.

### Actions

- Do not modify `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendRuntimeConfig.java` as part of this publication pass
- Do not modify `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/AscendCommand.java` as part of this publication pass
- Treat the current token behavior as an accepted non-blocker for making the repository public
- Keep the decision documented in this plan so implementation work does not accidentally reintroduce token hardening into the publication scope

### Deliverables

- Publication scope clearly excludes Ascend token hardening
- No implementation work is scheduled for the Ascend token logic in this release pass

### Validation

- Confirm the publication checklist and success criteria no longer require token changes

Expected result:

- The repository can be prepared for public release without changing the current Ascend token code path

---

## Phase 4. Update Public-Facing Documentation and Legal Messaging

**Objective**: Make the repository logically coherent for a public proprietary audience.

### Target Files

- `README.md`
- `LICENSE`
- New file: `THIRD_PARTY_NOTICES.md` or equivalent

### Actions

- Keep `LICENSE` as `All Rights Reserved`
- Rewrite the README license section so it no longer says the project is internal
- State clearly that:
  - the repository is public for visibility/reference
  - all rights are reserved
  - copying, modification, redistribution, or reuse require prior written permission
- Add a third-party notice file for `libs/nitrado-webserver-1.1.1.jar`
- Include:
  - component name
  - version
  - upstream source URL if available
  - copyright notice
  - full MIT license text

### Deliverables

- Public README that matches the actual legal state of the repo
- Third-party notice file covering the Nitrado jar

### Validation

- Manual README review from the perspective of an external visitor
- Manual consistency check between `README.md`, `LICENSE`, and the third-party notice file

---

## Phase 5. Rewrite Git History

**Objective**: Permanently remove already-committed sensitive or redundant content from all reachable history.

### Content to Purge

- `docs/prod_logs/**`
- `com/hypixel/hytale/**/*.class`

### Metadata to Rewrite

- Rewrite personal commit emails to a GitHub `users.noreply.github.com` identity
- If the exact preferred address is unknown, reuse the existing `noreply` identity already present in repo history

### Actions

- Run `git filter-repo` once with all path removals and metadata rewrites combined
- Rewrite all branches and tags
- Remove stale refs left by the rewrite process if necessary

### Deliverables

- Cleaned Git history
- Rewritten commit emails

### Validation Commands

```bash
git log --all -- docs/prod_logs
git log --all -- 'com/hypixel/hytale'
git rev-list --objects --all | rg 'prod_logs|server\.log|com/hypixel/hytale/.+\.class'
git log --all --format='%an <%ae>' | sort -u
```

Expected result:

- No history results for purged paths
- No object references for purged files
- Final author email list contains only approved public identities

### Important Note

This is the point of no return for collaborators using old clones. Everyone will need to reset or reclone after the force-push.

---

## Phase 6. Run the Final Repository Audit

**Objective**: Confirm the cleaned repo is not leaking secrets, personal data, or operational details.

### Audit Targets

- Tracked files in the working tree
- Full rewritten Git history
- Docs and scripts
- Example environment files
- Public-facing metadata and notices

### Scan For

- Tokens
- Webhooks
- Private keys
- Database credentials
- Personal emails
- IP addresses
- Player identifiers
- UUID-heavy operational logs
- Runtime auth traces
- Server paths
- Private hostnames or internal-only endpoints

### Commands

Use a combination of:

- `git grep`
- `rg`
- `git log -p`
- `git rev-list --objects --all`

Do not treat a single keyword hit as a blocker by default. Review context and distinguish:

- acceptable examples/placeholders
- third-party notices
- actual leaked operational or private data

### Deliverables

- Final audit pass completed
- Any residual blockers either fixed or explicitly accepted

---

## Phase 7. Validate from a Fresh Clone

**Objective**: Verify the cleaned repository behaves like an external public repository, not like an internal working copy.

### Actions

- Clone the rewritten repository into a separate directory
- Confirm removed files do not exist in the new clone
- Confirm purged files are unreachable in history
- Confirm `.env` and runtime-only files remain untracked
- Read the README as an external visitor would
- Confirm third-party notice and proprietary license are visible and coherent

### Validation Checklist

- `docs/prod_logs/**` absent
- `com/hypixel/hytale/**/*.class` absent
- No contradictory README language
- No personal emails unexpectedly visible in `git log`
- No accidental runtime credentials tracked

### Deliverables

- Fresh-clone validation complete

---

## Phase 8. Publish the Rewritten Repository

**Objective**: Safely make the repository public after all cleanup has been validated.

### Actions

- Force-push rewritten branches and tags
- Notify collaborators that old clones are no longer valid
- Confirm GitHub shows the rewritten commit metadata
- Switch repository visibility from private to public
- Run a final UI-level check on the public repository landing page

### Deliverables

- Public repository with clean tree, clean history, and coherent proprietary messaging

---

## 5. Concrete Files Expected to Change

These are the most likely files to be touched during implementation:

- `.gitignore`
- `README.md`
- `LICENSE` (only if wording alignment is needed, not license model change)
- `docs/plans/2026-03-22-public-release-hardening.md`
- `THIRD_PARTY_NOTICES.md` or similar new notice file

These paths are expected to be removed from the tree and history:

- `docs/prod_logs/**`
- `com/hypixel/hytale/**/*.class`

---

## 6. Risks and Mitigations

### Risk 1. Old history gets reintroduced

**Cause**: A collaborator force-pushes an old branch or merges from a stale clone.

**Mitigation**:

- Freeze pushes before the rewrite
- Notify collaborators after the force-push
- Require fresh clones or hard resets to the rewritten history

### Risk 2. Public readers question the accepted Ascend token behavior

**Cause**: The repository is made public while intentionally leaving the current Ascend NPC token implementation unchanged

**Mitigation**:

- Treat this as an explicit accepted decision for the current release
- Do not present token hardening as completed work in the README or publication notes
- Revisit later only if it becomes an actual operational or abuse issue

### Risk 3. README remains legally ambiguous

**Cause**: README says one thing while LICENSE says another

**Mitigation**:

- Review both side by side
- Use explicit proprietary wording in the README
- Mention third-party notices separately so they do not imply repo-wide open licensing

### Risk 4. Required files are removed accidentally during history rewrite

**Cause**: Over-broad purge patterns

**Mitigation**:

- Back up first
- Dry-run path matching mentally before rewrite
- Validate with a fresh clone before changing visibility

### Risk 5. A third-party asset issue appears late

**Cause**: A bundled asset turns out not to be safe to publish

**Mitigation**:

- Treat wardrobe assets as acceptable by default
- If the final audit finds a concrete issue, remove only the affected asset subset and document the reason

---

## 7. Rollback Plan

If the cleanup or history rewrite goes wrong:

1. Stop all publication work immediately
2. Restore from the mirror backup or backup branch
3. Recreate the working repository from the backup
4. Re-evaluate the purge patterns and metadata rewrite rules
5. Retry only after the issue is understood

Do not switch repository visibility to public until the fresh-clone validation is complete.

---

## 8. Ready-to-Implement Checklist

[ ] Freeze pushes and create backups
[x] Remove `docs/prod_logs/**` from the working tree
[x] Remove bundled `com/hypixel/hytale/**/*.class` from the working tree
[x] Update `.gitignore` to block future accidental operational artifacts
[x] Rewrite README for a public proprietary repository
[x] Add MIT third-party notice for `libs/nitrado-webserver-1.1.1.jar`
[x] Rewrite Git history to purge logs and Hytale classes
[x] Rewrite personal commit emails to `noreply`
[x] Audit the cleaned repository and history
[x] Validate from a fresh clone
[x] Force-push the rewritten history
[x] Make the GitHub repository public

---

## 9. Implementation Notes

### Pre-Rewrite Rollback Artifacts

- Backup branch created: `backup/public-release-hardening-2026-03-22-pre-rewrite`
- Mirror backup created outside the working repository: `/tmp/hyvexa-public-release-hardening-20260322-180118.git`
- No tags existed before the rewrite

### Pre-Rewrite Branch Snapshot

- `main` -> `9ef3bffd3470af5090a2cd893275bb78379a1caf`
- `backup/accidental-mixed-commit-5f9921b` -> `108b8eb554e10f2e94662b1b0591f182092403bc`
- `backup/public-release-hardening-2026-03-22-pre-rewrite` -> `9ef3bffd3470af5090a2cd893275bb78379a1caf`
- `feature/ascend-verticality-v1` -> `3aefbd98774402dc9a9080008ee6e6d231f833f0`
- `feature/mine-phase2` -> `5c2b94484e5bf5c3e218cf4f18a497d025e9bc68`
- `refactor/ascend-audit-fixes` -> `d6f89b6f3598127a9f2f4aa47d678cbed9a19a22`
- Multiple `worktree-agent-*` branches also existed and were included in the rewrite scope

### Rewrite Inputs

- Purged paths:
  - `docs/prod_logs/**`
  - `com/hypixel/hytale/math/vector/Vector3f.class`
  - `com/hypixel/hytale/server/core/universe/PlayerRef.class`
- Email rewrites:
  - `MoulinLouis <moulin.louis93@gmail.com>` -> `MoulinLouis <MoulinLouis@users.noreply.github.com>`
  - `Tyler Hancock <darklime@live.ca>` -> `Tyler Hancock <darklime@users.noreply.github.com>`

### Post-Rewrite Validation Completed

- `git log --all -- docs/prod_logs` -> no results
- `git log --all -- com/hypixel/hytale` -> no results
- `git rev-list --objects --all | rg 'prod_logs|server\\.log|com/hypixel/hytale/.+\\.class'` -> no results
- `git log --all --format='%an <%ae>' | sort -u` -> only `users.noreply.github.com` identities remain
- Fresh clone validation passed from `/tmp/hyvexa-public-release-fresh-clone-20260322-180118-r2`

### Remote Publication Completed

- Rewritten `main` force-pushed to `origin` at `19e1ba33afdf60137e2e5013df24ecef7ed21cf8`
- GitHub repository visibility changed from `PRIVATE` to `PUBLIC`
- Public repository URL: `https://github.com/MoulinLouis/Hyzone`

### Final Audit Adjustment

- `docs/BLACK_ASSET_RENDER_INVESTIGATION.md` was sanitized during the audit to remove player-specific production identifiers and broken references to the purged production log files
- Push-freeze communication still requires an explicit collaborator-facing operator step outside the repository
