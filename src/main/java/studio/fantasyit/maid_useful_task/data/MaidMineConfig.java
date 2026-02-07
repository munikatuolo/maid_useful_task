package studio.fantasyit.maid_useful_task.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import studio.fantasyit.maid_useful_task.MaidUsefulTask;

public class MaidMineConfig implements TaskDataKey<MaidMineConfig.Data> {
    public static TaskDataKey<Data> KEY = null;
    public static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(MaidUsefulTask.MODID, "mine");

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, Data.getDefault());
    }

    public static final class Data implements IConfigSetter {
        private String oreId;
        private int targetCount;
        private int remainingCount;
        private long lastNoOreWarnTick;
        private long lastToolWarnTick;
        private boolean toolInsufficient;

        public Data(String oreId, int targetCount, int remainingCount) {
            this.oreId = oreId;
            this.targetCount = targetCount;
            this.remainingCount = remainingCount;
        }

        public static Data getDefault() {
            return new Data("minecraft:iron_ore", 16, 16);
        }

        public String oreId() {
            return oreId;
        }

        public void oreId(String oreId) {
            this.oreId = oreId;
        }

        public int targetCount() {
            return targetCount;
        }

        public void targetCount(int targetCount) {
            this.targetCount = targetCount;
        }

        public int remainingCount() {
            return remainingCount;
        }

        public void remainingCount(int remainingCount) {
            this.remainingCount = remainingCount;
        }

        public boolean consumeOne() {
            if (remainingCount > 0) {
                remainingCount--;
                return true;
            }
            return false;
        }

        public void resetRemaining() {
            this.remainingCount = this.targetCount;
        }

        public boolean shouldWarnNoOre(long gameTime) {
            if (gameTime - lastNoOreWarnTick > 100) {
                lastNoOreWarnTick = gameTime;
                return true;
            }
            return false;
        }

        public boolean shouldWarnTool(long gameTime) {
            if (gameTime - lastToolWarnTick > 100) {
                lastToolWarnTick = gameTime;
                return true;
            }
            return false;
        }

        public boolean toolInsufficient() {
            return toolInsufficient;
        }

        public void toolInsufficient(boolean toolInsufficient) {
            this.toolInsufficient = toolInsufficient;
        }

        @Override
        public void setConfigValue(String name, String value) {
            switch (name) {
                case "oreId":
                    oreId = value;
                    resetRemaining();
                    break;
                case "targetCount":
                    targetCount = Math.max(1, Integer.parseInt(value));
                    resetRemaining();
                    break;
                case "remainingCount":
                    remainingCount = Math.max(0, Integer.parseInt(value));
                    break;
            }
        }
    }

    @Override
    public ResourceLocation getKey() {
        return LOCATION;
    }

    @Override
    public CompoundTag writeSaveData(Data data) {
        CompoundTag tag = new CompoundTag();
        tag.putString("oreId", data.oreId);
        tag.putInt("targetCount", data.targetCount);
        tag.putInt("remainingCount", data.remainingCount);
        return tag;
    }

    @Override
    public Data readSaveData(CompoundTag compound) {
        String oreId = compound.getString("oreId");
        int targetCount = compound.getInt("targetCount");
        int remainingCount = compound.contains("remainingCount") ? compound.getInt("remainingCount") : targetCount;
        if (oreId.isEmpty()) {
            oreId = "minecraft:iron_ore";
        }
        if (targetCount <= 0) {
            targetCount = 16;
        }
        if (remainingCount < 0) {
            remainingCount = 0;
        }
        return new Data(oreId, targetCount, remainingCount);
    }
}
