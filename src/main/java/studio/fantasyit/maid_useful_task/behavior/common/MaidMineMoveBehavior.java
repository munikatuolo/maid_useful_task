package studio.fantasyit.maid_useful_task.behavior.common;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import studio.fantasyit.maid_useful_task.data.MaidMineConfig;
import studio.fantasyit.maid_useful_task.task.MaidMineTask;
import studio.fantasyit.maid_useful_task.util.MemoryUtil;

public class MaidMineMoveBehavior extends DestoryBlockMoveBehavior {
    @Override
    protected void start(@NotNull ServerLevel level, @NotNull EntityMaid maid, long gameTime) {
        MaidMineConfig.Data data = MaidMineConfig.get(maid);
        data.toolInsufficient(false);
        super.start(level, maid, gameTime);
        if (MemoryUtil.getTargetPos(maid) == null && data.remainingCount() > 0 && !data.toolInsufficient() && data.shouldWarnNoOre(level.getGameTime())) {
            LivingEntity owner = maid.getOwner();
            if (owner instanceof Player player) {
                player.displayClientMessage(MaidMineTask.noOreMessage(), false);
            }
        }
    }
}
