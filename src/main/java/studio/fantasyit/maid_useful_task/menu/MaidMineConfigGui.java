package studio.fantasyit.maid_useful_task.menu;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.MaidTaskConfigGui;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.NotNull;
import studio.fantasyit.maid_useful_task.data.MaidMineConfig;
import studio.fantasyit.maid_useful_task.network.MaidConfigurePacket;
import studio.fantasyit.maid_useful_task.registry.GuiRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MaidMineConfigGui extends MaidTaskConfigGui<MaidMineConfigGui.Container> {
    private static final int LIST_HEIGHT = 70;
    private static final int LIST_WIDTH = 154;
    private static final int TEXT_BOX_WIDTH = 120;
    private static final int TEXT_BOX_HEIGHT = 16;
    private static final int COUNT_BOX_WIDTH = 30;
    private static final int START_LEFT_OFFSET = 87;
    private static final int START_TOP_OFFSET = 36;
    private MaidMineConfig.Data currentData;
    private EditBox searchBox;
    private EditBox countBox;
    private OreList oreList;
    private int lastSentCount;

    public MaidMineConfigGui(Container screenContainer, Inventory inv, Component titleIn) {
        super(screenContainer, inv, titleIn);
    }

    public static class Container extends TaskConfigContainer {
        public Container(int id, Inventory inventory, int entityId) {
            super(GuiRegistry.MAID_MINE_CONFIG_GUI.get(), id, inventory, entityId);
        }
    }

    @Override
    protected void initAdditionData() {
        this.currentData = this.maid.getOrCreateData(MaidMineConfig.KEY, MaidMineConfig.Data.getDefault());
    }

    @Override
    protected void initAdditionWidgets() {
        super.initAdditionWidgets();
        int left = leftPos + START_LEFT_OFFSET;
        int top = topPos + START_TOP_OFFSET;

        this.searchBox = new EditBox(this.font, left, top, TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT, Component.translatable("gui.maid_useful_task.mine.search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setValue("");
        this.searchBox.setResponder(this::updateFilter);
        this.addRenderableWidget(this.searchBox);

        this.countBox = new EditBox(this.font, left + TEXT_BOX_WIDTH + 8, top, COUNT_BOX_WIDTH, TEXT_BOX_HEIGHT, Component.translatable("gui.maid_useful_task.mine.count"));
        this.countBox.setMaxLength(4);
        this.countBox.setValue(String.valueOf(this.currentData.targetCount()));
        this.lastSentCount = this.currentData.targetCount();
        this.countBox.setResponder(this::updateCount);
        this.addRenderableWidget(this.countBox);

        // 修改：构造函数只传 5 个参数，去掉了用不到的 bottom (top + 24 + LIST_HEIGHT)
        this.oreList = new OreList(this.minecraft, LIST_WIDTH, LIST_HEIGHT, top + 24, 18);
        this.oreList.setLeftPos(left);
        this.oreList.refreshList("");
        this.addRenderableWidget(this.oreList);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.maid_useful_task.mine.search"), START_LEFT_OFFSET + TEXT_BOX_WIDTH / 2, START_TOP_OFFSET - 16, 0x404040);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.maid_useful_task.mine.count"), START_LEFT_OFFSET + TEXT_BOX_WIDTH + 8 + COUNT_BOX_WIDTH / 2, START_TOP_OFFSET - 16, 0x404040);
    }

    private void updateFilter(String value) {
        if (oreList != null) {
            oreList.refreshList(value);
        }
    }

    private void updateCount(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                return;
            }
            if (parsed != lastSentCount) {
                lastSentCount = parsed;
                currentData.targetCount(parsed);
                currentData.resetRemaining();
                MaidConfigurePacket.send(this.maid, MaidMineConfig.LOCATION, "targetCount", String.valueOf(parsed));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void selectOre(ResourceLocation id) {
        currentData.oreId(id.toString());
        currentData.resetRemaining();
        MaidConfigurePacket.send(this.maid, MaidMineConfig.LOCATION, "oreId", id.toString());
    }

    // ==========================================
    // OreList 类
    // ==========================================
    private class OreList extends ObjectSelectionList<OreList.OreEntry> {
        // 修改：移除了 bottom 参数
        public OreList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
        }


        public void setLeftPos(int left) {
            this.setX(left); // 使用 setX
        }

        public void refreshList(String filter) {
            this.clearEntries();
            String lower = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
            List<OreEntry> entries = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block == Blocks.AIR) {
                    continue;
                }
                // 修复：使用 NeoForge 的 Tags.Blocks.ORES
                if (!block.defaultBlockState().is(Tags.Blocks.ORES)) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                Component name = Component.translatable(block.getDescriptionId());
                String nameLower = name.getString().toLowerCase(Locale.ROOT);
                if (!lower.isEmpty() && !id.toString().toLowerCase(Locale.ROOT).contains(lower) && !nameLower.contains(lower)) {
                    continue;
                }
                entries.add(new OreEntry(id, name));
            }
            entries.sort(Comparator.comparing(o -> o.name.getString()));
            for (OreEntry entry : entries) {
                this.addEntry(entry);
                if (entry.id.toString().equals(currentData.oreId())) {
                    this.setSelected(entry);
                    this.centerScrollOn(entry); // 自动滚动到选中项
                }
            }
        }

        // ==========================================
        // OreEntry 类 (移到 OreList 内部)
        // ==========================================
        public class OreEntry extends ObjectSelectionList.Entry<OreEntry> {
            private final ResourceLocation id;
            private final Component name;

            public OreEntry(ResourceLocation id, Component name) {
                this.id = id;
                this.name = name;
            }

            // [新增] 必须实现这个方法，否则报错
            // 返回该条目的名字，供旁白系统朗读
            @Override
            public Component getNarration() {
                return this.name;
            }

            @Override
            public void render(@NotNull GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
                guiGraphics.drawString(MaidMineConfigGui.this.font, name, x + 2, y + 2, 0xFFFFFF, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                OreList.this.setSelected(this);
                MaidMineConfigGui.this.selectOre(id);
                return true;
            }
        }
    }
}
