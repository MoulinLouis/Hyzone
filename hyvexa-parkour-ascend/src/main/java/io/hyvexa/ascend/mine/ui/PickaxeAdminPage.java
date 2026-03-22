package io.hyvexa.ascend.mine.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.mine.MineBlockRegistry;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.PickaxeTier;

import java.util.List;
import java.util.Map;

public class PickaxeAdminPage extends InteractiveCustomUIPage<PickaxeAdminPage.PageData> {

    private static final String SELECT_BLOCK_PREFIX = "SelectBlock:";

    private final PlayerRef playerRef;
    private final MineConfigStore configStore;
    private int selectedTier = 0;
    private String selectedBlockId = "";
    private String amountInput = "";
    private String costInput = "";

    public PickaxeAdminPage(@Nonnull PlayerRef playerRef, MineConfigStore configStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.configStore = configStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Ascend_PickaxeAdmin.ui");
        bindEvents(evt);
        populate(cmd);
        bindDynamicButtons(evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);
        if (data.amount != null) amountInput = data.amount.trim();
        if (data.cost != null) costInput = data.cost.trim();
        if (data.button == null) return;

        switch (data.button) {
            case PageData.BUTTON_CLOSE -> { this.close(); return; }
            case PageData.BUTTON_TIER_PREV -> { selectedTier = Math.max(0, selectedTier - 1); selectedBlockId = ""; }
            case PageData.BUTTON_TIER_NEXT -> { selectedTier = Math.min(PickaxeTier.values().length - 1, selectedTier + 1); selectedBlockId = ""; }
            case PageData.BUTTON_ADD_RECIPE -> handleAddRecipe(ref, store);
            case PageData.BUTTON_SET_COST_1 -> handleSetCost(ref, store, 1);
            case PageData.BUTTON_SET_COST_2 -> handleSetCost(ref, store, 2);
            case PageData.BUTTON_SET_COST_3 -> handleSetCost(ref, store, 3);
            case PageData.BUTTON_SET_COST_4 -> handleSetCost(ref, store, 4);
            case PageData.BUTTON_SET_COST_5 -> handleSetCost(ref, store, 5);
            default -> {
                if (data.button.startsWith(PageData.BUTTON_REMOVE_RECIPE_PREFIX)) {
                    handleRemoveRecipe(ref, store, data.button.substring(PageData.BUTTON_REMOVE_RECIPE_PREFIX.length()));
                } else if (data.button.startsWith(SELECT_BLOCK_PREFIX)) {
                    selectedBlockId = data.button.substring(SELECT_BLOCK_PREFIX.length());
                }
            }
        }

        sendRefresh(ref, store);
    }

    private void handleAddRecipe(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (selectedTier < 1) {
            player.sendMessage(Message.raw("WOOD (tier 0) has no recipe - it's the starter tier."));
            return;
        }
        if (selectedBlockId.isEmpty()) {
            player.sendMessage(Message.raw("Select a block from the list first."));
            return;
        }
        int amount;
        try {
            amount = amountInput.isEmpty() ? 1 : Integer.parseInt(amountInput);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Invalid amount: " + amountInput));
            return;
        }
        if (amount <= 0) {
            player.sendMessage(Message.raw("Amount must be > 0."));
            return;
        }

        configStore.saveTierRecipe(selectedTier, selectedBlockId, amount);
        String name = MineBlockRegistry.getDisplayName(selectedBlockId);
        player.sendMessage(Message.raw("Recipe added: " + amount + "x " + name + " for tier " + selectedTier));
    }

    private void handleRemoveRecipe(Ref<EntityStore> ref, Store<EntityStore> store, String blockTypeId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        configStore.removeTierRecipe(selectedTier, blockTypeId);
        player.sendMessage(Message.raw("Removed " + MineBlockRegistry.getDisplayName(blockTypeId) + " from tier " + selectedTier + " recipe."));
    }

    private void handleSetCost(Ref<EntityStore> ref, Store<EntityStore> store, int level) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        long cost;
        try {
            cost = costInput.isEmpty() ? 0 : Long.parseLong(costInput);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Invalid cost: " + costInput));
            return;
        }
        if (cost < 0) {
            player.sendMessage(Message.raw("Cost cannot be negative."));
            return;
        }

        if (cost == 0) {
            configStore.removeEnhanceCost(selectedTier, level);
        } else {
            configStore.saveEnhanceCost(selectedTier, level, cost);
        }
        player.sendMessage(Message.raw("Tier " + selectedTier + " level " + level + " cost -> " + (cost == 0 ? "Free" : cost + " cryst")));
    }

    private void bindEvents(UIEventBuilder evt) {
        // Text fields
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AmountField",
            EventData.of(PageData.KEY_AMOUNT, "#AmountField.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CostField",
            EventData.of(PageData.KEY_COST, "#CostField.Value"), false);

        // Tier navigation
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TierPrev",
            EventData.of(PageData.KEY_BUTTON, PageData.BUTTON_TIER_PREV), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TierNext",
            EventData.of(PageData.KEY_BUTTON, PageData.BUTTON_TIER_NEXT), false);

        // Add recipe
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#AddRecipeBtn",
            EventData.of(PageData.KEY_BUTTON, PageData.BUTTON_ADD_RECIPE), false);

        // Set cost buttons (levels 1-5)
        for (int lvl = 1; lvl <= PickaxeTier.MAX_ENHANCEMENT; lvl++) {
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetCost" + lvl,
                EventData.of(PageData.KEY_BUTTON, "SetCost" + lvl), false);
        }

        // Close
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(PageData.KEY_BUTTON, PageData.BUTTON_CLOSE), false);
    }

    private void populate(UICommandBuilder cmd) {
        PickaxeTier tier = PickaxeTier.fromTier(selectedTier);

        // Header info
        cmd.set("#TierTitle.Text", tier.getDisplayName() + " (Tier " + selectedTier + ")");
        cmd.set("#TierDamage.Text", "Base Damage: " + tier.getBaseDamage());

        // Selected block display
        if (!selectedBlockId.isEmpty()) {
            cmd.set("#SelectedBlockText.Text", MineBlockRegistry.getDisplayName(selectedBlockId));
        } else {
            cmd.set("#SelectedBlockText.Text", "No block selected");
        }

        // Recipe list
        cmd.clear("#RecipeList");
        if (selectedTier >= 1) {
            Map<String, Integer> recipe = configStore.getTierRecipe(selectedTier);
            if (recipe.isEmpty()) {
                cmd.set("#RecipeEmpty.Visible", true);
            } else {
                cmd.set("#RecipeEmpty.Visible", false);
                int i = 0;
                for (var entry : recipe.entrySet()) {
                    cmd.append("#RecipeList", "Pages/Ascend_PickaxeAdminRecipeEntry.ui");
                    String sel = "#RecipeList[" + i + "]";
                    String name = MineBlockRegistry.getDisplayName(entry.getKey());
                    cmd.set(sel + " #EntryText.Text", entry.getValue() + "x " + name);
                    i++;
                }
            }
        } else {
            cmd.set("#RecipeEmpty.Visible", true);
            cmd.set("#RecipeEmpty.Text", "Starter tier - no recipe needed");
        }

        // Block picker list
        buildBlockList(cmd);

        // Enhancement costs (levels 1-5)
        for (int lvl = 1; lvl <= PickaxeTier.MAX_ENHANCEMENT; lvl++) {
            long cost = configStore.getEnhanceCost(selectedTier, lvl);
            cmd.set("#CostLabel" + lvl + ".Text", "Lv " + lvl + ": " + (cost > 0 ? cost + " cryst" : "Free"));
        }
    }

    private void buildBlockList(UICommandBuilder cmd) {
        cmd.clear("#BlockEntries");
        int index = 0;
        for (Map.Entry<String, List<MineBlockRegistry.BlockDef>> category : MineBlockRegistry.getByCategory().entrySet()) {
            cmd.append("#BlockEntries", "Pages/Ascend_MineBlockPickerHeader.ui");
            cmd.set("#BlockEntries[" + index + "] #CategoryName.Text", category.getKey());
            index++;

            for (MineBlockRegistry.BlockDef block : category.getValue()) {
                cmd.append("#BlockEntries", "Pages/Ascend_MineBlockPickerEntry.ui");
                String sel = "#BlockEntries[" + index + "]";
                cmd.set(sel + " #BlockIcon.ItemId", block.blockTypeId);
                cmd.set(sel + " #BlockName.Text", block.displayName);

                if (block.blockTypeId.equals(selectedBlockId)) {
                    cmd.set(sel + " #SelectedOverlay.Visible", true);
                }
                index++;
            }
        }
    }

    private void bindDynamicButtons(UIEventBuilder evt) {
        // Recipe remove buttons
        if (selectedTier >= 1) {
            Map<String, Integer> recipe = configStore.getTierRecipe(selectedTier);
            int i = 0;
            for (var entry : recipe.entrySet()) {
                String sel = "#RecipeList[" + i + "]";
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                    sel + " #RemoveBtn",
                    EventData.of(PageData.KEY_BUTTON, PageData.BUTTON_REMOVE_RECIPE_PREFIX + entry.getKey()), false);
                i++;
            }
        }

        // Block picker entries
        int index = 0;
        for (Map.Entry<String, List<MineBlockRegistry.BlockDef>> category : MineBlockRegistry.getByCategory().entrySet()) {
            index++; // skip header
            for (MineBlockRegistry.BlockDef block : category.getValue()) {
                String sel = "#BlockEntries[" + index + "]";
                evt.addEventBinding(CustomUIEventBindingType.Activating, sel,
                    EventData.of(PageData.KEY_BUTTON, SELECT_BLOCK_PREFIX + block.blockTypeId), false);
                index++;
            }
        }
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        populate(cmd);
        bindDynamicButtons(evt);
        bindEvents(evt);
        this.sendUpdate(cmd, evt, false);
    }

    public static class PageData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_AMOUNT = "@Amount";
        static final String KEY_COST = "@Cost";

        static final String BUTTON_CLOSE = "Close";
        static final String BUTTON_TIER_PREV = "TierPrev";
        static final String BUTTON_TIER_NEXT = "TierNext";
        static final String BUTTON_ADD_RECIPE = "AddRecipe";
        static final String BUTTON_SET_COST_1 = "SetCost1";
        static final String BUTTON_SET_COST_2 = "SetCost2";
        static final String BUTTON_SET_COST_3 = "SetCost3";
        static final String BUTTON_SET_COST_4 = "SetCost4";
        static final String BUTTON_SET_COST_5 = "SetCost5";
        static final String BUTTON_REMOVE_RECIPE_PREFIX = "RemoveRecipe:";

        public static final BuilderCodec<PageData> CODEC = BuilderCodec.<PageData>builder(PageData.class, PageData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (d, v) -> d.button = v, d -> d.button)
            .addField(new KeyedCodec<>(KEY_AMOUNT, Codec.STRING), (d, v) -> d.amount = v, d -> d.amount)
            .addField(new KeyedCodec<>(KEY_COST, Codec.STRING), (d, v) -> d.cost = v, d -> d.cost)
            .build();

        private String button;
        private String amount;
        private String cost;
    }
}
