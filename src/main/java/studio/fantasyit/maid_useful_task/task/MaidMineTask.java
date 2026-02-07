package studio.fantasyit.maid_useful_task.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import studio.fantasyit.maid_useful_task.Config;
import studio.fantasyit.maid_useful_task.MaidUsefulTask;
import studio.fantasyit.maid_useful_task.behavior.common.DestoryBlockBehavior;
import studio.fantasyit.maid_useful_task.behavior.common.MaidMineMoveBehavior;
import studio.fantasyit.maid_useful_task.behavior.common.MaidSelfRescueBehavior;
import studio.fantasyit.maid_useful_task.data.MaidMineConfig;
import studio.fantasyit.maid_useful_task.menu.MaidMineConfigGui;
import studio.fantasyit.maid_useful_task.util.MaidUtils;
import studio.fantasyit.maid_useful_task.util.WrappedMaidFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MaidMineTask implements IMaidTask, IMaidBlockDestroyTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(MaidUsefulTask.MODID, "maid_mine");
    public static int ownerRange(EntityMaid maid) {
        MaidMineConfig.Data data = MaidMineConfig.get(maid);
        if (data.mineRange() > 0) {
            return data.mineRange();
        }
        return Config.mineRange;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.IRON_PICKAXE.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid entityMaid) {
        return null;
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return true;
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return Config.enableMineTask;
    }

    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("");
            }

            @Override
            public AbstractContainerMenu createMenu(int index, Inventory playerInventory, Player player) {
                return new MaidMineConfigGui.Container(index, playerInventory, maid.getId());
            }
        };
    }

    @Override
    public boolean shouldDestroyBlock(EntityMaid maid, BlockPos pos) {
        MaidMineConfig.Data data = MaidMineConfig.get(maid);
        if (data.remainingCount() <= 0) {
            return false;
        }
        BlockState blockState = maid.level().getBlockState(pos);
        if (!isTargetOre(blockState, data)) {
            return false;
        }
        if (!isWithinOwnerRange(maid, pos)) {
            return false;
        }
        if (!hasCorrectTool(maid, blockState, data)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean mayDestroy(EntityMaid maid, BlockPos pos) {
        MaidMineConfig.Data data = MaidMineConfig.get(maid);
        if (data.remainingCount() <= 0) {
            return false;
        }
        BlockState blockState = maid.level().getBlockState(pos);
        if (!isTargetOre(blockState, data)) {
            return false;
        }
        if (!isWithinOwnerRange(maid, pos)) {
            return false;
        }
        return hasCorrectTool(maid, blockState, data);
    }

    @Override
    public void tryTakeOutTool(EntityMaid maid) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            if (inv.getStackInSlot(i).is(ItemTags.PICKAXES)) {
                @NotNull ItemStack tmp = inv.getStackInSlot(i);
                inv.setStackInSlot(i, maid.getMainHandItem());
                maid.setItemInHand(InteractionHand.MAIN_HAND, tmp);
                return;
            }
        }
    }

    @Override
    public boolean tryDestroyBlock(EntityMaid maid, BlockPos blockPos) {
        BlockState blockState = maid.level().getBlockState(blockPos);
        if (IMaidBlockDestroyTask.super.tryDestroyBlock(maid, blockPos)) {
        if (isTargetOre(blockState, MaidMineConfig.get(maid))) {
            MaidMineConfig.Data data = MaidMineConfig.get(maid);
            data.consumeOne();
            if (data.remainingCount() <= 0) {
                MaidUtils.switchToIdleTask(maid);
            }
        }
        return true;
        }
        return false;
    }

    @Override
    public boolean availableToGetDrop(EntityMaid maid, WrappedMaidFakePlayer fakePlayer, BlockPos pos, BlockState targetBlockState) {
        MaidMineConfig.Data data = MaidMineConfig.get(maid);
        if (!fakePlayer.hasCorrectToolForDrops(targetBlockState)) {
            warnToolInsufficient(maid, data);
            return false;
        }
        return IMaidBlockDestroyTask.super.availableToGetDrop(maid, fakePlayer, pos, targetBlockState);
    }

    @Override
    public @NotNull List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid entityMaid) {
        List<Pair<Integer, BehaviorControl<? super EntityMaid>>> list = new ArrayList<>();
        list.add(Pair.of(0, new MaidSelfRescueBehavior()));
        list.add(Pair.of(1, new DestoryBlockBehavior()));
        list.add(Pair.of(1, new MaidMineMoveBehavior()));
        return list;
    }

    private boolean isTargetOre(BlockState blockState, MaidMineConfig.Data data) {
        Optional<ResourceLocation> id = Optional.ofNullable(ResourceLocation.tryParse(data.oreId()));
        if (id.isEmpty()) {
            return false;
        }
        Block block = BuiltInRegistries.BLOCK.get(id.get());
        return block != Blocks.AIR && blockState.is(block);
    }

    private boolean isWithinOwnerRange(EntityMaid maid, BlockPos pos) {
        LivingEntity owner = maid.getOwner();
        if (owner == null) {
            return false;
        }
        int range = ownerRange(maid);
        return pos.getCenter().distanceToSqr(owner.position()) <= range * range;
    }

    private boolean hasCorrectTool(EntityMaid maid, BlockState blockState, MaidMineConfig.Data data) {
        WrappedMaidFakePlayer fakePlayer = WrappedMaidFakePlayer.get(maid);
        if (!fakePlayer.hasCorrectToolForDrops(blockState)) {
            warnToolInsufficient(maid, data);
            return false;
        }
        return true;
    }

    private void warnToolInsufficient(EntityMaid maid, MaidMineConfig.Data data) {
        data.toolInsufficient(true);
        if (!data.shouldWarnTool(maid.level().getGameTime())) {
            return;
        }
        MaidUtils.notifyOwnerWithBubble(maid, toolInsufficientMessage());
    }

    public static Component toolInsufficientMessage() {
        return Component.translatable("message.maid_useful_task.mine.tool_insufficient");
    }

    public static Component noOreMessage() {
        return Component.translatable("message.maid_useful_task.mine.no_ore");
    }

    public static Component pathBlockedMessage() {
        return Component.translatable("message.maid_useful_task.mine.path_blocked");
    }

    public static Component pathTooFarMessage() {
        return Component.translatable("message.maid_useful_task.mine.path_too_far");
    }

    public boolean hasTargetOreInRange(ServerLevel level, BlockPos center, int radius, MaidMineConfig.Data data) {
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState state = level.getBlockState(mutable);
                    if (isTargetOre(state, data)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean hasSamePlaneAdjacentOre(EntityMaid maid, BlockPos pos) {
        MaidMineConfig.Data data = MaidMineConfig.get(maid);
        BlockPos north = pos.north();
        BlockPos south = pos.south();
        BlockPos east = pos.east();
        BlockPos west = pos.west();
        return isTargetOre(maid.level().getBlockState(north), data)
                || isTargetOre(maid.level().getBlockState(south), data)
                || isTargetOre(maid.level().getBlockState(east), data)
                || isTargetOre(maid.level().getBlockState(west), data);
    }

    @Override
    public @Nullable List<BlockPos> toDestroyFromStanding(EntityMaid maid, BlockPos targetPos, BlockPos standPos) {
        List<BlockPos> list = new ArrayList<>();
        Vec3 eyePos = standPos.getCenter().add(0, maid.getEyeHeight() - 0.5, 0);
        BlockPos standBelow = standPos.below();
        Boolean available = net.minecraft.world.level.BlockGetter.traverseBlocks(eyePos, targetPos.getCenter(), maid.level(), (level, pos) -> {
            if (pos.distSqr(standPos) > reachDistance() * reachDistance()) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return null;
            }
            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty()) {
                return false;
            }
            if (pos.equals(standBelow)) {
                return false;
            }
            if (pos.equals(targetPos)) {
                if (!shouldDestroyBlock(maid, pos)) {
                    return false;
                }
                list.add(pos.immutable());
                return null;
            }
            if (canBreakObstacle(maid, pos, state)) {
                list.add(pos.immutable());
                return null;
            }
            return false;
        }, (a) -> true);
        if (available) {
            return list;
        }
        return null;
    }

    private boolean canBreakObstacle(EntityMaid maid, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.getDestroySpeed(maid.level(), pos) < 0) {
            return false;
        }
        WrappedMaidFakePlayer fakePlayer = WrappedMaidFakePlayer.get(maid);
        if (fakePlayer.hasCorrectToolForDrops(state)) {
            return true;
        }
        return !state.requiresCorrectToolForDrops();
    }
}
