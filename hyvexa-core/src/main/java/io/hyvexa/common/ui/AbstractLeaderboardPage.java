package io.hyvexa.common.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Base class for leaderboard pages that share: load → sort → filter → paginate → render.
 * <p>
 * Subclasses implement {@link #loadRows()} to provide sorted, ranked, formatted rows.
 * The base class handles search filtering, pagination, row rendering, and empty states.
 * <p>
 * For multi-column layouts (e.g. Parkour medal columns), override {@link #renderRow} and
 * store extra data in a side map keyed by {@link LeaderboardRow#playerId()}.
 */
public abstract class AbstractLeaderboardPage extends AbstractSearchablePaginatedPage {

    protected AbstractLeaderboardPage(@Nonnull PlayerRef playerRef, int pageSize) {
        super(playerRef, pageSize);
    }

    // === Abstract methods ===

    /**
     * Load, sort, rank, and format entries for the current tab/category.
     * Return {@code null} if the underlying data source has no entries at all,
     * or an empty list if entries exist but none match the current category filter.
     */
    protected abstract List<LeaderboardRow> loadRows();

    /** UI template path appended for each card row (e.g. "Pages/Ascend_LeaderboardEntry.ui"). */
    protected abstract String getCardTemplatePath();

    // === Optional overrides ===

    /** Container element ID for leaderboard card rows. Default: "#LeaderboardCards". */
    protected String getCardContainerId() {
        return "#LeaderboardCards";
    }

    /** Accent color for the given rank, or {@code null} for no accent. */
    protected String getRankAccentColor(int rank) {
        return null;
    }

    /** Render a single row into the card at the given prefix. Override for multi-column layouts. */
    protected void renderRow(UICommandBuilder cmd, String cardPrefix, LeaderboardRow row) {
        cmd.set(cardPrefix + " #Rank.Text", "#" + row.rank());
        cmd.set(cardPrefix + " #PlayerName.Text", row.name());
        cmd.set(cardPrefix + " #Value.Text", row.formattedValue());
    }

    /** Called at start of {@link #buildLeaderboard} after clearing cards and restoring search text.
     *  Use for tab styling, dynamic setup, etc. */
    protected void onBuildLeaderboard(UICommandBuilder cmd) {
    }

    protected String getNoDataMessage() {
        return "No players yet.";
    }

    protected String getNoEntriesMessage() {
        return "No data available.";
    }

    protected String getNoMatchesMessage() {
        return "No matches.";
    }

    /**
     * Whether to reassign ranks based on position in the filtered (search) results.
     * Default: false — ranks from {@link #loadRows()} are preserved (global ranks).
     * Override to return true for leaderboards that show rank-within-search-results.
     */
    protected boolean useFilteredRanks() {
        return false;
    }

    // === Built-in lifecycle ===

    @Override
    protected final void buildContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        buildLeaderboard(commandBuilder);
    }

    protected final void buildLeaderboard(UICommandBuilder cmd) {
        String containerId = getCardContainerId();
        cmd.clear(containerId);
        cmd.set(getSearchFieldId() + ".Value", getSearchText());

        onBuildLeaderboard(cmd);

        List<LeaderboardRow> rows = loadRows();
        if (rows == null) {
            showEmpty(cmd, getNoDataMessage());
            return;
        }
        if (rows.isEmpty()) {
            showEmpty(cmd, getNoEntriesMessage());
            return;
        }

        List<LeaderboardRow> filtered = filterBySearch(rows, LeaderboardRow::name);
        if (filtered.isEmpty()) {
            showEmpty(cmd, getNoMatchesMessage());
            return;
        }

        if (useFilteredRanks()) {
            List<LeaderboardRow> reranked = new ArrayList<>(filtered.size());
            for (int i = 0; i < filtered.size(); i++) {
                LeaderboardRow r = filtered.get(i);
                reranked.add(new LeaderboardRow(i + 1, r.playerId(), r.name(), r.formattedValue()));
            }
            filtered = reranked;
        }

        cmd.set("#EmptyText.Text", "");
        PaginationState.PageSlice slice = getPagination().slice(filtered.size());
        int index = 0;
        for (int i = slice.startIndex; i < slice.endIndex; i++) {
            LeaderboardRow row = filtered.get(i);
            cmd.append(containerId, getCardTemplatePath());
            String cardPrefix = containerId + "[" + index + "]";
            String accentColor = getRankAccentColor(row.rank());
            if (accentColor != null) {
                AccentOverlayUtils.applyAccent(cmd, cardPrefix + " #AccentBar",
                        accentColor, AccentOverlayUtils.RANK_ACCENTS);
            }
            renderRow(cmd, cardPrefix, row);
            index++;
        }
        cmd.set("#PageLabel.Text", slice.getLabel());
    }

    // === Tab styling utility ===

    /** Common tab active/inactive styling used across tabbed leaderboards. */
    protected void setTabActive(UICommandBuilder cmd, String tabId, boolean active) {
        String wrapPath = "#" + tabId + "Wrap";
        String accentPath = "#" + tabId + "Accent";
        cmd.set(wrapPath + " #" + tabId + "ActiveBg.Visible", active);
        cmd.set(wrapPath + " #" + tabId + "InactiveBg.Visible", !active);
        cmd.set(wrapPath + " " + accentPath + " #" + tabId + "AccentActive.Visible", active);
        cmd.set(wrapPath + " " + accentPath + " #" + tabId + "AccentInactive.Visible", !active);
    }

    // === Standard row type ===

    /**
     * A single ranked leaderboard row. For multi-column pages that need extra data
     * beyond formattedValue, store a side map keyed by {@link #playerId()} and
     * look it up in {@link #renderRow}.
     */
    public record LeaderboardRow(int rank, UUID playerId, String name, String formattedValue) {
    }
}
