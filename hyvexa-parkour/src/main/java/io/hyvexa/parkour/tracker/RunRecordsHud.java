package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;

public class RunRecordsHud extends RunHud {

    private String lastRecordsKey;

    public RunRecordsHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/Parkour_RunHud.ui");
        commandBuilder.append("Pages/Parkour_RunRecordsHud.ui");
    }

    public void updateRunDetails(String timeText, List<RecordLine> lines) {
        updateText(timeText);
        updateTopTimes(lines);
    }

    public void updateTopTimes(List<RecordLine> lines) {
        List<RecordLine> safeLines = lines != null ? lines : List.of();
        RecordLine[] resolved = new RecordLine[6];
        StringBuilder keyBuilder = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            RecordLine line = i < safeLines.size() ? safeLines.get(i) : RecordLine.empty(i + 1);
            resolved[i] = line;
            appendLineKey(keyBuilder, line);
        }
        RecordLine self = safeLines.size() > 5 ? safeLines.get(5) : RecordLine.empty(0);
        resolved[5] = self;
        appendLineKey(keyBuilder, self);
        String key = keyBuilder.toString();
        if (key.equals(lastRecordsKey)) {
            return;
        }
        lastRecordsKey = key;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        for (int i = 0; i < 5; i++) {
            RecordLine line = resolved[i];
            String index = String.valueOf(i + 1);
            commandBuilder.set("#RecordRank" + index + ".Text", line.rank);
            commandBuilder.set("#RecordName" + index + ".Text", line.name);
            commandBuilder.set("#RecordTime" + index + ".Text", line.time);
        }
        commandBuilder.set("#RecordRankSelf.Text", self.rank);
        commandBuilder.set("#RecordNameSelf.Text", self.name);
        commandBuilder.set("#RecordTimeSelf.Text", self.time);
        update(false, commandBuilder);
    }

    private static void appendLineKey(StringBuilder builder, RecordLine line) {
        builder.append(line.rank).append('|').append(line.name).append('|').append(line.time).append('\n');
    }

    @Override
    public void resetCache() {
        super.resetCache();
        lastRecordsKey = null;
    }

    public static final class RecordLine {
        public final String rank;
        public final String name;
        public final String time;

        public RecordLine(String rank, String name, String time) {
            this.rank = rank;
            this.name = name;
            this.time = time;
        }

        public static RecordLine empty(int rank) {
            return new RecordLine(rank > 0 ? String.valueOf(rank) : "", "", "");
        }
    }
}
