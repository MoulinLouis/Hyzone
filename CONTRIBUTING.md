# Contributing

Guidelines for contributing to the Hyvexa Parkour Plugin.

## Getting Started

1. Clone the repository
2. Ensure you have Java 17+ and IntelliJ IDEA
3. Run `./gradlew build` to verify the build works
4. Use the `HytaleServer` run config to test changes

## Code Style

### Java Conventions

- **Indentation**: 4 spaces (no tabs)
- **Braces**: Same-line opening braces
- **Line length**: 120 characters max
- **Imports**: No wildcard imports; organize by package

### Naming

| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `MapSelectPage` |
| Methods | camelCase | `getActiveMapId()` |
| Constants | UPPER_SNAKE | `TOUCH_RADIUS_SQ` |
| Fields | camelCase | `activeRuns` |
| Packages | lowercase | `io.hyvexa.parkour.data` |

### Nullability

- Use `@Nonnull` annotations on public method parameters and return types
- Check entity refs with `ref.isValid()` before use
- Prefer early returns over deep nesting for null checks:

```java
// Good
if (playerRef == null) return;
var ref = playerRef.getReference();
if (ref == null || !ref.isValid()) return;

// Avoid
if (playerRef != null) {
    var ref = playerRef.getReference();
    if (ref != null && ref.isValid()) {
        // deeply nested code
    }
}
```

### Threading

- World operations MUST use `CompletableFuture.runAsync(..., world)`
- Never block the main thread with file I/O in hot paths
- Use `ConcurrentHashMap` for shared state between threads
- Document any synchronization requirements in comments

### Error Handling

- Log errors with context: `LOGGER.at(Level.WARNING).log("Failed to load map " + mapId + ": " + e.getMessage())`
- Don't swallow exceptions silently
- Validate user input at command/UI boundaries

## Project Structure

### Where to Put New Code

| Type | Location |
|------|----------|
| Commands | `io.hyvexa.parkour.command/` |
| UI Pages | `io.hyvexa.parkour.ui/` |
| Data Stores | `io.hyvexa.parkour.data/` |
| Interactions | `io.hyvexa.parkour.interaction/` |
| Systems (ECS) | `io.hyvexa.parkour.system/` |
| Utilities | `io.hyvexa.common.util/` |
| Constants | `io.hyvexa.parkour.ParkourConstants` |

### Registration

All commands, interactions, and systems must be registered in `HyvexaPlugin.setup()`.

## Pull Request Process

### Before Submitting

1. **Build passes**: `./gradlew build` completes without errors
2. **Test in-game**: Run the server and verify your changes work
3. **Update CHANGELOG**: Add a line describing your change
4. **No unrelated changes**: Keep PRs focused on one feature/fix

### PR Description

Include:
- **What**: Brief description of the change
- **Why**: Motivation or issue being fixed
- **How**: High-level approach (if not obvious from code)
- **Testing**: How you verified the change works

### Review Feedback

- Address all review comments before merging
- If you disagree with feedback, explain your reasoning
- Mark conversations resolved after addressing them

## Commit Messages

Format: `<type>: <description>`

Types:
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code change that neither fixes nor adds
- `docs`: Documentation only
- `chore`: Build, config, or tooling changes

Examples:
```
feat: add per-map first-completion XP rewards
fix: prevent duplicate checkpoint messages
refactor: extract fall detection into FallTracker
docs: update README with current commands
```

## File Formats

### UI Files (`.ui`)

- Located in `src/main/resources/Common/UI/Custom/Pages/`
- Use existing UI files as templates
- Test UI changes in-game (UI errors only show at runtime)

### JSON Data Files

- Located in `Parkour/` at runtime
- Schema is defined by the corresponding `*Store.java` class
- Include version field if schema may evolve

### Interaction JSON

- Located in `src/main/resources/Server/Item/Interactions/`
- Must reference a registered interaction class

## Common Pitfalls

1. **Forgetting world thread**: Entity modifications outside `CompletableFuture.runAsync` will fail silently or crash
2. **Stale entity refs**: Always check `ref.isValid()` - players disconnect
3. **UI path separators**: Use forward slashes even on Windows: `"Common/UI/Custom/Pages/MyPage.ui"`
4. **Missing registration**: New commands/interactions won't work without `setup()` registration
5. **Blocking I/O**: `syncSave()` blocks - don't call it in tight loops

## Questions?

Open an issue or reach out to the maintainers.
