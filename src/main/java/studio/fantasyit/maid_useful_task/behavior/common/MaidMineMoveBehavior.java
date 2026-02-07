package studio.fantasyit.maid_useful_task.behavior.common;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import studio.fantasyit.maid_useful_task.data.MaidMineConfig;
import studio.fantasyit.maid_useful_task.task.MaidMineTask;
import studio.fantasyit.maid_useful_task.util.MaidUtils;
import studio.fantasyit.maid_useful_task.util.MemoryUtil;

public class MaidMineMoveBehavior extends DestoryBlockMoveBehavior {
    @Override
    protected double getOwnerSearchRadius(EntityMaid maid) {
        return MaidMineTask.ownerRange(maid);
    }

    @Override
    protected void start(@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
        MaidMineConfig.Data data = MaidMineConfig.get(maid);
        if (data.remainingCount() <= 0) {
            data.resetRemaining();
        }
        data.toolInsufficient(false);
        this.setSearchRange(MaidMineTask.ownerRange(maid));
        super.start(level, maid, gameTime);
        if (MemoryUtil.getTargetPos(maid) == null && data.remainingCount() > 0 && !data.toolInsufficient() && data.shouldWarnNoOre(level.getGameTime())) {
            LivingEntity owner = maid.getOwner();
            if (owner instanceof Player player) {
                MaidMineTask task = (MaidMineTask) maid.getTask();
                if (task.hasTargetOreInRange(level, player.blockPosition(), MaidMineTask.ownerRange(maid), data)) {
                    MaidUtils.notifyOwnerWithBubble(maid, MaidMineTask.pathBlockedMessage());
                } else if (task.hasTargetOreInRange(level, player.blockPosition(), MaidMineTask.ownerRange(maid) * 2, data)) {
                    MaidUtils.notifyOwnerWithBubble(maid, MaidMineTask.pathTooFarMessage());
                } else {
                    MaidUtils.notifyOwnerWithBubble(maid, MaidMineTask.noOreMessage());
                }
            }
        }
    }
}
