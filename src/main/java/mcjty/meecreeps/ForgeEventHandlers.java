package mcjty.meecreeps;

import mcjty.meecreeps.actions.ActionOptions;
import mcjty.meecreeps.entities.EntityMeeCreeps;
import mcjty.meecreeps.actions.ServerActionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.WorldEvent;
import mcjty.meecreeps.blocks.PortalSpawnerRegistry;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.items.CreepCubeItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ForgeEventHandlers {

    @SubscribeEvent
    public void onCubeLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntityPlayer().getHeldItem(event.getHand()).getItem() instanceof CreepCubeItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMeeCreepAttacked(AttackEntityEvent event) {
        if (event.getEntityPlayer().world.isRemote || !(event.getTarget() instanceof EntityMeeCreeps)
                || !(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        ItemStack cube = CreepCubeItem.getHeldCube(player);
        if (cube.isEmpty()) {
            return;
        }

        UUID cubeId = CreepCubeItem.getCubeId(cube, player);
        if (cubeId != null && ServerActionManager.getManager().terminateMeeCreep(
                player, (EntityMeeCreeps) event.getTarget(), cubeId)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        PortalSpawnerRegistry.clearWorld(event.getWorld());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP) {
            PortalGunItem.tickPendingSpawnerOperation((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PortalGunItem.clearPendingSpawnerOperation(event.player);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerActionManager.getManager().tick();

        harvestableBlocksToCollect.entrySet().removeIf(entry -> ServerActionManager.getManager().getOptions(entry.getValue()) == null);
    }

    private static final Map<HarvestKey, Integer> harvestableBlocksToCollect = new HashMap<>();

    public static void registerHarvestableBlock(World world, BlockPos pos, int actionId) {
        if (world == null || pos == null) return;
        harvestableBlocksToCollect.put(new HarvestKey(world.provider.getDimension(), pos), actionId);
    }

    @SubscribeEvent
    public void onHarvestDropsEvent(BlockEvent.HarvestDropsEvent event) {
        BlockPos pos = event.getPos();
        World world = event.getWorld();
        if (world == null) return;

        HarvestKey key = new HarvestKey(world.provider.getDimension(), pos);
        Integer actionId = harvestableBlocksToCollect.remove(key);
        if (actionId == null) return;

        ActionOptions options = ServerActionManager.getManager().getOptions(actionId);
        if (options != null) {
            options.registerDrops(pos, event.getDrops());
            event.getDrops().clear();
            ServerActionManager.getManager().save();
        }
    }

    private static final class HarvestKey {
        private final int dimension;
        private final BlockPos pos;

        private HarvestKey(int dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos.toImmutable();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HarvestKey)) return false;
            HarvestKey that = (HarvestKey) o;
            return dimension == that.dimension && pos.equals(that.pos);
        }

        @Override
        public int hashCode() {
            return 31 * dimension + Objects.hashCode(pos);
        }
    }
}
