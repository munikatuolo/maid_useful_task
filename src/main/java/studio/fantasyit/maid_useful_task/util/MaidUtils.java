package studio.fantasyit.maid_useful_task.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import studio.fantasyit.maid_useful_task.util.MemoryUtil;

import java.lang.reflect.Method;
import java.util.function.Predicate;

public class MaidUtils {
    public static void swapToHand(EntityMaid maid, Predicate<ItemStack> isSuitable) {
        RangedWrapper availableBackpackInv = maid.getAvailableBackpackInv();
        for (int i = 0; i < availableBackpackInv.getSlots(); i++) {
            ItemStack itemStack = availableBackpackInv.getStackInSlot(i);
            if (isSuitable.test(itemStack)) {
                ItemStack tmp = availableBackpackInv.getStackInSlot(i);
                availableBackpackInv.setStackInSlot(i, maid.getMainHandItem());
                maid.setItemInHand(InteractionHand.MAIN_HAND, tmp);
                return;
            }
        }
    }

    public static float getDestroyProgressDelta(EntityMaid maid, BlockPos blockPos) {
        WrappedMaidFakePlayer fakePlayer = WrappedMaidFakePlayer.get(maid);
        BlockState blockState = maid.level().getBlockState(blockPos);
        return blockState.getDestroyProgress(fakePlayer, maid.level(), blockPos);
    }

    public static BlockPos getMaidRestrictCenter(EntityMaid maid) {
        if (MemoryUtil.getBlockUpContext(maid).hasTarget()) {
            return MemoryUtil.getBlockUpContext(maid).getTargetPos();
        }
        if (maid.hasRestriction())
            return maid.getRestrictCenter();
        return maid.blockPosition();
    }

    public static boolean destroyBlock(EntityMaid maid, BlockPos blockPos) {
        WrappedMaidFakePlayer fakePlayer = WrappedMaidFakePlayer.get(maid);
        maid.getMainHandItem().hurtAndBreak(1, fakePlayer, EquipmentSlot.MAINHAND);
        ServerLevel level = (ServerLevel) maid.level();
        BlockState blockState = level.getBlockState(blockPos);
        if (blockState.isAir()) {
            return false;
        } else {
            FluidState fluidState = level.getFluidState(blockPos);
            if (!(blockState.getBlock() instanceof BaseFireBlock)) {
                level.levelEvent(2001, blockPos, Block.getId(blockState));
            }

            BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(blockPos) : null;
            //改用MainHandItem来roll loot
            maid.dropResourcesToMaidInv(blockState, level, blockPos, blockEntity, maid, maid.getMainHandItem());

            boolean setResult = level.setBlock(blockPos, fluidState.createLegacyBlock(), 3);
            if (setResult) {
                level.gameEvent(GameEvent.BLOCK_DESTROY, blockPos, GameEvent.Context.of(maid, blockState));
            }

            return setResult;
        }
    }

    public static boolean placeBlock(EntityMaid maid, BlockPos pos) {
        WrappedMaidFakePlayer fakePlayer = WrappedMaidFakePlayer.get(maid);
        BlockHitResult result = null;
        ClipContext rayTraceContext = new ClipContext(maid.getPosition(0).add(0, maid.getEyeHeight(), 0),
                pos.getCenter(),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                fakePlayer);
        result = maid.level().clip(rayTraceContext);
        UseOnContext useContext = new UseOnContext(fakePlayer, InteractionHand.MAIN_HAND, result);
        InteractionResult actionresult = fakePlayer.getMainHandItem().onItemUseFirst(useContext);
        if (actionresult == InteractionResult.PASS) {
            InteractionResult interactionResult = fakePlayer.getMainHandItem().useOn(useContext);
            if (interactionResult.consumesAction()) {
                return true;
            }
        }
        return false;
    }

    public static void notifyOwnerWithBubble(EntityMaid maid, net.minecraft.network.chat.Component message) {
        LivingEntity owner = maid.getOwner();
        if (owner instanceof Player player) {
            player.displayClientMessage(message, false);
        }
        trySendChatBubble(maid, message);
    }

    public static void switchToIdleTask(EntityMaid maid) {
        MemoryUtil.clearDestroyTargetMemory(maid);
        MemoryUtil.clearTarget(maid);
        MemoryUtil.setCurrent(maid, studio.fantasyit.maid_useful_task.memory.CurrentWork.IDLE);
        trySetIdleTask(maid);
    }

    private static void trySendChatBubble(EntityMaid maid, net.minecraft.network.chat.Component message) {
        String[] methodNames = new String[]{"setChatBubble", "setChatBubbleMessage", "setChatBubbleText"};
        for (String methodName : methodNames) {
            if (invokeChatBubbleMethod(maid, message, methodName)) {
                return;
            }
        }
    }

    private static boolean invokeChatBubbleMethod(EntityMaid maid, net.minecraft.network.chat.Component message, String methodName) {
        try {
            Method method = maid.getClass().getMethod(methodName, net.minecraft.network.chat.Component.class);
            method.invoke(maid, message);
            return true;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = maid.getClass().getMethod(methodName, net.minecraft.network.chat.Component.class, int.class);
            method.invoke(maid, message, 100);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void trySetIdleTask(EntityMaid maid) {
        String[] methodNames = new String[]{"setTask", "setTaskUid", "setTaskByUid"};
        for (String methodName : methodNames) {
            if (invokeIdleTaskMethod(maid, methodName)) {
                return;
            }
        }
    }

    private static boolean invokeIdleTaskMethod(EntityMaid maid, String methodName) {
        try {
            Method method = maid.getClass().getMethod(methodName, net.minecraft.resources.ResourceLocation.class);
            method.invoke(maid, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "idle"));
            return true;
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }
}
