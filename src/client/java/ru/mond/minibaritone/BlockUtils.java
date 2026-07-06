package ru.mond.minibaritone;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

public final class BlockUtils {
    private BlockUtils() {}

    public static boolean isLog(ClientWorld world, BlockPos pos) {
        return world.getBlockState(pos).isIn(BlockTags.LOGS);
    }

    public static boolean isSafeAirLike(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) return false;
        return state.isAir() || state.getCollisionShape(world, pos, ShapeContext.absent()).isEmpty();
    }

    public static boolean isSolidFloor(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        return !state.getCollisionShape(world, pos, ShapeContext.absent()).isEmpty();
    }

    public static boolean canStandAt(ClientWorld world, BlockPos feet) {
        return isSafeAirLike(world, feet)
            && isSafeAirLike(world, feet.up())
            && isSolidFloor(world, feet.down());
    }

    public static boolean canStandOrMakeAt(ClientWorld world, BlockPos feet, boolean allowBreak, boolean allowPlace) {
        return canClearOrAlreadyClear(world, feet, allowBreak)
            && canClearOrAlreadyClear(world, feet.up(), allowBreak)
            && (isSolidFloor(world, feet.down()) || (allowPlace && canPlaceAt(world, feet.down())));
    }

    public static double standCost(ClientWorld world, BlockPos feet, boolean allowBreak, boolean allowPlace) {
        double cost = 1.0;
        cost += clearCost(world, feet, allowBreak);
        cost += clearCost(world, feet.up(), allowBreak);
        if (!isSolidFloor(world, feet.down())) {
            cost += allowPlace && canPlaceAt(world, feet.down()) ? 4.0 : 1000.0;
        }
        return cost;
    }

    public static boolean canClearOrAlreadyClear(ClientWorld world, BlockPos pos, boolean allowBreak) {
        if (isSafeAirLike(world, pos)) return true;
        return allowBreak && isBreakable(world, pos);
    }

    public static double clearCost(ClientWorld world, BlockPos pos, boolean allowBreak) {
        if (isSafeAirLike(world, pos)) return 0.0;
        if (!allowBreak || !isBreakable(world, pos)) return 1000.0;
        BlockState state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        return 3.0 + Math.max(0.0F, hardness);
    }

    public static boolean isBreakable(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        Block block = state.getBlock();
        if (block == Blocks.BEDROCK || block == Blocks.BARRIER || block == Blocks.END_PORTAL_FRAME || block == Blocks.END_PORTAL) {
            return false;
        }
        return state.getHardness(world, pos) >= 0.0F;
    }

    public static boolean canPlaceAt(ClientWorld world, BlockPos target) {
        if (!isSafeAirLike(world, target)) return false;
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue;
            BlockPos neighbor = target.offset(direction);
            if (isSolidFloor(world, neighbor)) return true;
        }
        return false;
    }

    public static boolean findAndSelectPlaceBlock(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        ItemStack selectedStack = player.getMainHandStack();
        if (isUsablePlaceStack(selectedStack)) return true;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (isUsablePlaceStack(stack)) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }
        }
        return false;
    }

    private static boolean isUsablePlaceStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof FallingBlock) return false;
        return block != Blocks.TNT && block != Blocks.CHEST && block != Blocks.ENDER_CHEST;
    }

    public static boolean placeBlock(MinecraftClient client, BlockPos target) {
        if (client.player == null || client.world == null || client.interactionManager == null) return false;
        if (!findAndSelectPlaceBlock(client)) return false;

        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue;
            BlockPos neighbor = target.offset(direction);
            if (!isSolidFloor(client.world, neighbor)) continue;

            Direction side = direction.getOpposite();
            Vec3i normal = side.getVector();
            Vec3d hit = Vec3d.ofCenter(neighbor).add(new Vec3d(normal.getX(), normal.getY(), normal.getZ()).multiply(0.5));
            lookAt(client.player, hit);
            BlockHitResult hitResult = new BlockHitResult(hit, side, neighbor, false);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
            client.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    public static void lookAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d eye = player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(MathHelper.atan2(dz, dx) * 57.2957763671875D) - 90.0F;
        float pitch = (float)(-(MathHelper.atan2(dy, horizontal) * 57.2957763671875D));
        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
    }

    public static void lookAtCenter(ClientPlayerEntity player, BlockPos pos) {
        lookAt(player, Vec3d.ofCenter(pos));
    }

    public static boolean breakBlock(MinecraftClient client, BlockPos pos) {
        if (client.player == null || client.world == null || client.interactionManager == null) return false;
        if (!isBreakable(client.world, pos)) return false;
        lookAtCenter(client.player, pos);
        Direction side = bestBreakSide(client.player, pos);
        client.interactionManager.updateBlockBreakingProgress(pos, side);
        client.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private static Direction bestBreakSide(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eye = player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;
        Direction side = Direction.getFacing(dx, dy, dz);
        return side == null ? Direction.UP : side;
    }

    public static boolean isInsideWorld(ClientWorld world, int y) {
        return y >= world.getBottomY() && y < world.getTopYInclusive();
    }
}
