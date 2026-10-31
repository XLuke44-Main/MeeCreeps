package mcjty.meecreeps.varia;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.*;

public class GeneralTools {

    private static FakePlayer harvester = null;

    private static class SilentHarvester extends FakePlayer {
        private SilentHarvester(WorldServer world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        protected void playEquipSound(ItemStack stack) {

        }
    }

    public static FakePlayer getHarvester(World world) {
        if (harvester == null) {
            WorldServer serverWorld = (WorldServer) world;
            harvester = new SilentHarvester(serverWorld,
                    new GameProfile(UUID.nameUUIDFromBytes("meecreeps".getBytes()), "meecreeps"));
        }

        harvester.setWorld(world);
        ItemStack heldTool = harvester.getHeldItem(EnumHand.MAIN_HAND);
        if (heldTool.isEmpty() || heldTool.getItem() != Items.DIAMOND_PICKAXE) {
            harvester.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        }
        return harvester;
    }

    public static void playBlockBreakEffect(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            world.playEvent(2001, pos, Block.getStateId(state));
        }
    }

    public static void playBlockPlaceSound(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            SoundType soundType = state.getBlock().getSoundType();
            world.playSound(null, pos, soundType.getPlaceSound(), SoundCategory.BLOCKS,
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        }
    }

    public static boolean traverseBoxTest(AxisAlignedBB box, Predicate<BlockPos> matcher) {
        for (int x = (int) box.minX; x <= box.maxX; x++) {
            for (int y = (int) box.minY; y <= box.maxY; y++) {
                for (int z = (int) box.minZ; z <= box.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (matcher.test(pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    public static <T> T traverseBoxFirst(AxisAlignedBB box, Function<BlockPos, T> matcher) {
        for (int x = (int) box.minX; x <= box.maxX; x++) {
            for (int y = (int) box.minY; y <= box.maxY; y++) {
                for (int z = (int) box.minZ; z <= box.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    T result = matcher.apply(pos);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    public static void traverseBoxConsume(AxisAlignedBB box, Consumer<BlockPos> consumer) {
        for (int x = (int) box.minX; x <= box.maxX; x++) {
            for (int y = (int) box.minY; y <= box.maxY; y++) {
                for (int z = (int) box.minZ; z <= box.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    consumer.accept(pos);
                }
            }
        }
    }

    public static void traverseBox(World world, AxisAlignedBB box, BiPredicate<BlockPos, IBlockState> tester, BiConsumer<BlockPos, IBlockState> consumer) {
        for (int x = (int) box.minX; x <= box.maxX; x++) {
            for (int y = (int) box.minY; y <= box.maxY; y++) {
                for (int z = (int) box.minZ; z <= box.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = world.getBlockState(pos);
                    if (tester.test(pos, state)) {
                        consumer.accept(pos, state);
                    }
                }
            }
        }
    }
}
