package mcjty.meecreeps.blocks;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PortalSpawnerRegistry {

    private static final Map<UUID, List<WeakReference<PortalSpawnerTileEntity>>> REGISTRY = new HashMap<>();

    private PortalSpawnerRegistry() {
    }

    public static synchronized void register(@Nullable PortalSpawnerTileEntity spawner) {
        if (!isServerSpawner(spawner)) {
            return;
        }

        UUID pairId = spawner.getLinkedPairId();
        if (pairId == null) {
            return;
        }

        List<WeakReference<PortalSpawnerTileEntity>> refs = REGISTRY.get(pairId);
        if (refs == null) {
            refs = new ArrayList<>();
            REGISTRY.put(pairId, refs);
        }

        boolean alreadyRegistered = false;
        for (Iterator<WeakReference<PortalSpawnerTileEntity>> iterator = refs.iterator(); iterator.hasNext();) {
            PortalSpawnerTileEntity candidate = iterator.next().get();
            if (candidate == null || candidate.isInvalid()) {
                iterator.remove();
                continue;
            }
            if (candidate == spawner) {
                alreadyRegistered = true;
            }
        }

        if (!alreadyRegistered) {
            refs.add(new WeakReference<>(spawner));
        }

        if (refs.isEmpty()) {
            REGISTRY.remove(pairId);
        }
    }

    public static synchronized void unregister(@Nullable PortalSpawnerTileEntity spawner) {
        if (spawner == null) {
            return;
        }

        UUID pairId = spawner.getLinkedPairId();
        if (pairId == null) {
            return;
        }

        removeReference(pairId, spawner);
    }

    public static synchronized void clearWorld(@Nullable World world) {
        if (world == null) {
            return;
        }

        for (Iterator<Map.Entry<UUID, List<WeakReference<PortalSpawnerTileEntity>>>> mapIterator = REGISTRY.entrySet().iterator();
             mapIterator.hasNext();) {
            Map.Entry<UUID, List<WeakReference<PortalSpawnerTileEntity>>> entry = mapIterator.next();
            List<WeakReference<PortalSpawnerTileEntity>> refs = entry.getValue();
            for (Iterator<WeakReference<PortalSpawnerTileEntity>> iterator = refs.iterator(); iterator.hasNext();) {
                PortalSpawnerTileEntity candidate = iterator.next().get();
                if (candidate == null || candidate.isInvalid() || candidate.getWorld() == world) {
                    iterator.remove();
                }
            }
            if (refs.isEmpty()) {
                mapIterator.remove();
            }
        }
    }

    @Nullable
    public static synchronized PortalSpawnerTileEntity find(UUID pairId, @Nullable PortalSpawnerTileEntity exclude) {
        if (pairId == null) {
            return null;
        }

        List<WeakReference<PortalSpawnerTileEntity>> refs = REGISTRY.get(pairId);
        if (refs == null) {
            return null;
        }

        PortalSpawnerTileEntity result = null;
        for (Iterator<WeakReference<PortalSpawnerTileEntity>> iterator = refs.iterator(); iterator.hasNext();) {
            PortalSpawnerTileEntity candidate = iterator.next().get();
            if (!isUsable(candidate, pairId)) {
                iterator.remove();
                continue;
            }
            if (candidate != exclude && result == null) {
                result = candidate;
            }
        }

        if (refs.isEmpty()) {
            REGISTRY.remove(pairId);
        }
        return result;
    }

    private static boolean removeReference(UUID pairId, PortalSpawnerTileEntity spawner) {
        List<WeakReference<PortalSpawnerTileEntity>> refs = REGISTRY.get(pairId);
        if (refs == null) {
            return false;
        }

        boolean removed = false;
        for (Iterator<WeakReference<PortalSpawnerTileEntity>> iterator = refs.iterator(); iterator.hasNext();) {
            PortalSpawnerTileEntity candidate = iterator.next().get();
            if (candidate == null || candidate == spawner || candidate.isInvalid()) {
                iterator.remove();
                removed = true;
            }
        }
        if (refs.isEmpty()) {
            REGISTRY.remove(pairId);
        }
        return removed;
    }

    private static boolean isUsable(@Nullable PortalSpawnerTileEntity spawner, UUID pairId) {
        if (!isServerSpawner(spawner) || spawner.getLinkedPairId() == null || !pairId.equals(spawner.getLinkedPairId())) {
            return false;
        }

        World world = spawner.getWorld();
        BlockPos pos = spawner.getPos();
        if (world == null || pos == null) {
            return false;
        }

        return world.getBlockState(pos).getBlock() instanceof PortalSpawnerBlock
                && world.getBlockState(pos).getValue(PortalSpawnerBlock.HALF) == PortalSpawnerBlock.Half.UPPER
                && ((PortalSpawnerBlock) world.getBlockState(pos).getBlock()).isLinked();
    }

    private static boolean isServerSpawner(@Nullable PortalSpawnerTileEntity spawner) {
        return spawner != null && spawner.getWorld() != null && !spawner.getWorld().isRemote && !spawner.isInvalid();
    }
}
