package io.hyvexa.ascend;

import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SkillTreeNodeTest {

    @Test
    void rootNodeHasNoPrerequisites() {
        assertEquals(0, SkillTreeNode.AUTO_RUNNERS.getPrerequisites().length);
    }

    @Test
    void allNonRootNodesHaveAtLeastOnePrerequisite() {
        for (SkillTreeNode node : SkillTreeNode.values()) {
            if (node == SkillTreeNode.AUTO_RUNNERS) continue;
            assertTrue(node.getPrerequisites().length > 0,
                node.name() + " should have at least one prerequisite");
        }
    }

    @Test
    void hasPrerequisitesSatisfiedUsesOrLogic() {
        // RUNNER_SPEED_2 requires RUNNER_SPEED or EVOLUTION_POWER
        SkillTreeNode node = SkillTreeNode.RUNNER_SPEED_2;
        SkillTreeNode[] prereqs = node.getPrerequisites();
        assertTrue(prereqs.length >= 2, "RUNNER_SPEED_2 should have 2+ prerequisites");

        // Satisfying just the first prerequisite should work
        assertTrue(node.hasPrerequisitesSatisfied(EnumSet.of(prereqs[0])));
        // Satisfying just the second prerequisite should work
        assertTrue(node.hasPrerequisitesSatisfied(EnumSet.of(prereqs[1])));
        // Neither should fail
        assertFalse(node.hasPrerequisitesSatisfied(EnumSet.noneOf(SkillTreeNode.class)));
    }

    @Test
    void hasPrerequisitesSatisfiedReturnsTrueForRootWithEmptySet() {
        assertTrue(SkillTreeNode.AUTO_RUNNERS.hasPrerequisitesSatisfied(Set.of()));
    }

    @Test
    void allNodesHavePositiveCosts() {
        for (SkillTreeNode node : SkillTreeNode.values()) {
            assertTrue(node.getCost() > 0, node.name() + " cost should be positive");
        }
    }

    @Test
    void costsAreReasonablyOrdered() {
        // Root node has lowest cost
        assertEquals(1, SkillTreeNode.AUTO_RUNNERS.getCost());
        // Terminal node has highest cost
        assertEquals(1000, SkillTreeNode.RUNNER_SPEED_5.getCost());
    }

    @Test
    void prerequisiteGraphHasNoCycles() {
        for (SkillTreeNode start : SkillTreeNode.values()) {
            Set<SkillTreeNode> visited = EnumSet.noneOf(SkillTreeNode.class);
            Queue<SkillTreeNode> queue = new ArrayDeque<>();

            for (SkillTreeNode prereq : start.getPrerequisites()) {
                queue.add(prereq);
            }

            while (!queue.isEmpty()) {
                SkillTreeNode current = queue.poll();
                assertNotEquals(start, current,
                    "Cycle detected: " + start.name() + " -> ... -> " + current.name());
                if (visited.add(current)) {
                    for (SkillTreeNode prereq : current.getPrerequisites()) {
                        queue.add(prereq);
                    }
                }
            }
        }
    }

    @Test
    void allNodesAreReachableFromRoot() {
        // Build reverse graph: for each node, which nodes can it unlock?
        Set<SkillTreeNode> reachable = EnumSet.of(SkillTreeNode.AUTO_RUNNERS);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillTreeNode node : SkillTreeNode.values()) {
                if (reachable.contains(node)) continue;
                // A node is reachable if any of its prerequisites is reachable (OR logic)
                for (SkillTreeNode prereq : node.getPrerequisites()) {
                    if (reachable.contains(prereq)) {
                        reachable.add(node);
                        changed = true;
                        break;
                    }
                }
            }
        }
        assertEquals(SkillTreeNode.values().length, reachable.size(),
            "Not all nodes reachable from root. Missing: " + findMissing(reachable));
    }

    private String findMissing(Set<SkillTreeNode> reachable) {
        List<String> missing = new ArrayList<>();
        for (SkillTreeNode node : SkillTreeNode.values()) {
            if (!reachable.contains(node)) missing.add(node.name());
        }
        return String.join(", ", missing);
    }

    @Test
    void allNodesHaveNonBlankNameAndDescription() {
        for (SkillTreeNode node : SkillTreeNode.values()) {
            assertNotNull(node.getName(), node.name() + " name is null");
            assertFalse(node.getName().isBlank(), node.name() + " name is blank");
            assertNotNull(node.getDescription(), node.name() + " description is null");
            assertFalse(node.getDescription().isBlank(), node.name() + " description is blank");
        }
    }
}
