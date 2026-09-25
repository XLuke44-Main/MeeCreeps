package mcjty.meecreeps;

import mcjty.meecreeps.actions.ActionOptions;
import mcjty.meecreeps.actions.ServerActionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import mcjty.meecreeps.blocks.PortalSpawnerRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ForgeEventHandlers {

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        PortalSpawnerRegistry.clearWorld(event.getWorld());
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
