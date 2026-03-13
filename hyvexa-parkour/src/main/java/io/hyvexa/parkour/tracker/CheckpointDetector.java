package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.math.vector.Vector3d;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.TransformData;

import java.util.List;
import java.util.Set;

/**
 * Shared checkpoint/finish proximity detection logic used by both RunValidator and DuelTracker.
 * <p>
 * This class handles the core detection pattern: iterating checkpoints, checking proximity
 * via distance-squared with a vertical bonus, marking checkpoints as touched, and validating
 * that all checkpoints are reached before the finish is accepted.
 */
public final class CheckpointDetector {

    private CheckpointDetector() {
    }

    /** Mutable checkpoint state, implemented by both ActiveRun and DuelPlayerState. */
    public interface CheckpointState {
        Set<Integer> getTouchedCheckpoints();
        int getLastCheckpointIndex();
        void setLastCheckpointIndex(int index);
        boolean isFinishTouched();
        void setFinishTouched(boolean touched);
        long getLastFinishWarningMs();
        void setLastFinishWarningMs(long ms);
    }

    /**
     * Detects which checkpoints the player has newly entered, based on proximity.
     *
     * @param state          mutable checkpoint state
     * @param position       current player position
     * @param map            the map being played
     * @param touchRadiusSq  squared touch radius for proximity detection
     * @param verticalBonus  vertical bonus passed to distanceSqWithVerticalBonus
     * @param listener       callback for each newly touched checkpoint
     * @return the number of newly touched checkpoints this call
     */
    public static int detectCheckpoints(CheckpointState state, Vector3d position, Map map,
                                        double touchRadiusSq, double verticalBonus,
                                        CheckpointListener listener) {
        List<TransformData> checkpoints = map.getCheckpoints();
        if (checkpoints == null || checkpoints.isEmpty()) {
            return 0;
        }
        Set<Integer> touched = state.getTouchedCheckpoints();
        if (touched.size() >= checkpoints.size()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < checkpoints.size(); i++) {
            if (touched.contains(i)) {
                continue;
            }
            TransformData checkpoint = checkpoints.get(i);
            if (checkpoint == null) {
                continue;
            }
            if (TrackerUtils.distanceSqWithVerticalBonus(position, checkpoint, verticalBonus) <= touchRadiusSq) {
                touched.add(i);
                state.setLastCheckpointIndex(i);
                count++;
                if (listener != null) {
                    listener.onCheckpointTouched(i);
                }
            }
        }
        return count;
    }

    /**
     * Checks if the player has reached the finish, enforcing that all checkpoints must be touched first.
     *
     * @param state          mutable checkpoint state
     * @param position       current player position
     * @param map            the map being played
     * @param touchRadiusSq  squared touch radius for proximity detection
     * @param verticalBonus  vertical bonus passed to distanceSqWithVerticalBonus
     * @param warningHandler callback when the player reaches the finish without all checkpoints
     * @return true if the finish was newly touched (all checkpoints satisfied), false otherwise
     */
    public static boolean detectFinish(CheckpointState state, Vector3d position, Map map,
                                       double touchRadiusSq, double verticalBonus,
                                       MissingCheckpointWarningHandler warningHandler) {
        if (state.isFinishTouched() || map.getFinish() == null) {
            return false;
        }
        if (TrackerUtils.distanceSqWithVerticalBonus(position, map.getFinish(), verticalBonus) > touchRadiusSq) {
            return false;
        }
        List<TransformData> checkpoints = map.getCheckpoints();
        int checkpointCount = checkpoints != null ? checkpoints.size() : 0;
        if (checkpointCount > 0 && state.getTouchedCheckpoints().size() < checkpointCount) {
            long now = System.currentTimeMillis();
            if (now - state.getLastFinishWarningMs() >= 2000L) {
                state.setLastFinishWarningMs(now);
                if (warningHandler != null) {
                    warningHandler.onMissingCheckpoints();
                }
            }
            return false;
        }
        state.setFinishTouched(true);
        return true;
    }

    /** Callback for each newly touched checkpoint. */
    @FunctionalInterface
    public interface CheckpointListener {
        void onCheckpointTouched(int checkpointIndex);
    }

    /** Callback when the player reaches the finish without all checkpoints. */
    @FunctionalInterface
    public interface MissingCheckpointWarningHandler {
        void onMissingCheckpoints();
    }
}
