package io.hyvexa.ascend.mine.quest;

import java.util.Arrays;

public enum MineQuest {

    // "miner" chain — introductory quest line
    MINER_1("miner", 0, MineQuestObjectiveType.MINE_BLOCKS, 50, null, 500,
        "First Swing",
        "Mine 50 blocks",
        new String[]{
            "Hey there, new miner!",
            "Welcome to the mines. I've got a task for you.",
            "Mine 50 blocks and come back to me."
        },
        new String[]{
            "Well done! You're a natural.",
            "Here are your crystals. Ready for the next task?"
        }),

    MINER_2("miner", 1, MineQuestObjectiveType.SELL_BLOCKS, 30, null, 750,
        "Turning Profit",
        "Sell 30 blocks",
        new String[]{
            "Now that you've got blocks, let's turn them into crystals.",
            "Sell 30 blocks and come back."
        },
        new String[]{
            "Nice! Those crystals will come in handy.",
            "Keep it up, miner."
        }),

    MINER_3("miner", 2, MineQuestObjectiveType.REACH_UPGRADE_LEVEL, 3, "BAG_CAPACITY", 1500,
        "Room to Grow",
        "Upgrade Bag Capacity to Lv 3",
        new String[]{
            "Your bag is looking a bit small.",
            "Upgrade your Bag Capacity to level 3."
        },
        new String[]{
            "Much better! More room means more profit.",
            "Here's your reward."
        }),

    MINER_4("miner", 3, MineQuestObjectiveType.MINE_BLOCKS, 200, null, 2000,
        "Deep Dig",
        "Mine 200 blocks",
        new String[]{
            "Time to dig deeper.",
            "Mine 200 blocks for me."
        },
        new String[]{
            "Impressive work!",
            "You're becoming a real miner."
        }),

    MINER_5("miner", 4, MineQuestObjectiveType.REACH_UPGRADE_LEVEL, 2, "HASTE", 2500,
        "Need for Speed",
        "Upgrade Haste to Lv 2",
        new String[]{
            "You're mining too slowly.",
            "Upgrade your Haste to level 2 and feel the difference."
        },
        new String[]{
            "Feel that speed? Much better.",
            "Take these crystals."
        }),

    MINER_6("miner", 5, MineQuestObjectiveType.EARN_CRYSTALS, 5000, null, 3000,
        "Crystal Collector",
        "Earn 5,000 crystals total",
        new String[]{
            "Let's see how much you can earn.",
            "Accumulate 5,000 crystals from mining and selling."
        },
        new String[]{
            "A healthy stash! Well earned.",
            "Here's a bonus on top."
        }),

    MINER_7("miner", 6, MineQuestObjectiveType.REACH_UPGRADE_LEVEL, 3, "FORTUNE", 4000,
        "Lucky Strike",
        "Upgrade Fortune to Lv 3",
        new String[]{
            "Ever heard of Fortune? More drops per swing.",
            "Upgrade it to level 3."
        },
        new String[]{
            "Fortune favors the prepared!",
            "Enjoy the extra drops."
        }),

    MINER_8("miner", 7, MineQuestObjectiveType.OPEN_EGGS, 3, null, 5000,
        "Egg Hunter",
        "Open 3 eggs",
        new String[]{
            "Found any eggs while mining?",
            "Open 3 of them and see what's inside."
        },
        new String[]{
            "Exciting finds! Miners make great helpers.",
            "Here's your reward."
        }),

    MINER_9("miner", 8, MineQuestObjectiveType.UPGRADE_PICKAXE_TIER, 1, null, 8000,
        "Better Tools",
        "Upgrade pickaxe to Stone tier",
        new String[]{
            "That wooden pickaxe has served you well.",
            "Time for an upgrade. Get a Stone pickaxe."
        },
        new String[]{
            "Now THAT is a proper pickaxe!",
            "Take this generous reward."
        }),

    MINER_10("miner", 9, MineQuestObjectiveType.MINE_BLOCKS, 1000, null, 10000,
        "Thousand Swings",
        "Mine 1,000 blocks",
        new String[]{
            "You've come a long way.",
            "One last challenge: mine 1,000 blocks."
        },
        new String[]{
            "A thousand blocks! You've proven yourself.",
            "Take this. You've earned every crystal.",
            "Come back anytime, miner."
        });

    private final String chain;
    private final int orderInChain;
    private final MineQuestObjectiveType objectiveType;
    private final long target;
    private final String param;
    private final long crystalReward;
    private final String title;
    private final String objectiveText;
    private final String[] giveDialogue;
    private final String[] completeDialogue;

    MineQuest(String chain, int orderInChain, MineQuestObjectiveType objectiveType,
              long target, String param, long crystalReward,
              String title, String objectiveText,
              String[] giveDialogue, String[] completeDialogue) {
        this.chain = chain;
        this.orderInChain = orderInChain;
        this.objectiveType = objectiveType;
        this.target = target;
        this.param = param;
        this.crystalReward = crystalReward;
        this.title = title;
        this.objectiveText = objectiveText;
        this.giveDialogue = giveDialogue;
        this.completeDialogue = completeDialogue;
    }

    public String getChain() { return chain; }
    public int getOrderInChain() { return orderInChain; }
    public MineQuestObjectiveType getObjectiveType() { return objectiveType; }
    public long getTarget() { return target; }
    public String getParam() { return param; }
    public long getCrystalReward() { return crystalReward; }
    public String getTitle() { return title; }
    public String getObjectiveText() { return objectiveText; }
    public String[] getGiveDialogue() { return giveDialogue; }
    public String[] getCompleteDialogue() { return completeDialogue; }

    public static MineQuest getByChainAndIndex(String chain, int index) {
        for (MineQuest q : values()) {
            if (q.chain.equals(chain) && q.orderInChain == index) return q;
        }
        return null;
    }

    public static int getChainLength(String chain) {
        return (int) Arrays.stream(values()).filter(q -> q.chain.equals(chain)).count();
    }
}
