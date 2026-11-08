package mcjty.meecreeps.actions;

import mcjty.lib.varia.SoundTools;
import mcjty.lib.varia.TeleportationTools;
import mcjty.lib.worlddata.AbstractWorldData;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.MeeCreepsApi;
import mcjty.meecreeps.actions.workers.WorkerHelper;
import mcjty.meecreeps.api.IActionWorker;
import mcjty.meecreeps.config.ConfigSetup;
import mcjty.meecreeps.entities.EntityMeeCreeps;
import mcjty.meecreeps.items.CreepCubeItem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class ServerActionManager extends AbstractWorldData<ServerActionManager> {

    private static final String NAME = "MeeCreepsData";

    private List<ActionOptions> options = new ArrayList<>();
    private Map<Integer, ActionOptions> optionMap = new HashMap<>();
    private int lastId = 0;

    private Map<Integer, EntityMeeCreeps> entityCache = new HashMap<>();

    public ServerActionManager(String name) {
        super(name);
    }

    @Override
    public void clear() {
        options.clear();
        optionMap.clear();
        lastId = 0;
        entityCache.clear();
    }

    public void updateEntityCache(int actionId, @Nullable EntityMeeCreeps entity) {
        if (entity == null) {
            entityCache.remove(actionId);
        } else {
            entityCache.put(actionId, entity);
        }
    }

    public EntityMeeCreeps getCachedEntity(int actionId) {
        return entityCache.get(actionId);
    }

    public int newId() {
        lastId++;
        save();
        return lastId;
    }

    public ActionOptions getOptions(int id) {
        return optionMap.get(id);
    }

    public int countMeeCreeps(EntityPlayer player) {
        int cnt = 0;
        for (ActionOptions option : options) {
            if (Objects.equals(option.getPlayerId(), player.getGameProfile().getId())) {
                cnt++;
            }
        }
        return cnt;
    }

    @Nonnull
    public static ServerActionManager getManager() {
        return getData(ServerActionManager.class, NAME);
    }

    public int createActionOptions(World world, BlockPos pos, EnumFacing side, @Nullable EntityPlayer player) {
        return createActionOptions(world, pos, side, player, false);
    }

    public int createActionOptions(World world, BlockPos pos, EnumFacing side, @Nullable EntityPlayer player, boolean miniCube) {
        return createActionOptions(world, pos, side, player, miniCube, null);
    }

    public int createActionOptions(World world, BlockPos pos, EnumFacing side, @Nullable EntityPlayer player,
                                   boolean miniCube, @Nullable UUID cubeId) {
        List<MeeCreepActionType> types = new ArrayList<>();
        List<MeeCreepActionType> maybeTypes = new ArrayList<>();
        for (MeeCreepsApi.Factory type : MeeCreeps.api.getFactories()) {
            if (ConfigSetup.allowedActions.contains(type.getId())) {
                if (type.getFactory().isPossible(world, pos, side)) {
                    types.add(new MeeCreepActionType(type.getId()));
                } else if (type.getFactory().isPossibleSecondary(world, pos, side)) {
                    maybeTypes.add(new MeeCreepActionType(type.getId()));
                }
            }
        }
        int actionId = newId();
        ActionOptions opt = new ActionOptions(types, maybeTypes, pos, side, world.provider.getDimension(),
                player == null ? null : player.getUniqueID(), actionId, miniCube, cubeId);
        options.add(opt);
        optionMap.put(actionId, opt);
        save();
        return actionId;
    }

    private static Random random = new Random();

    public void performAction(@Nullable EntityPlayerMP player, int id, MeeCreepActionType type, @Nullable String furtherQuestionId) {
        if (type == null || MeeCreeps.api.getFactory(type) == null) return;
        ActionOptions option = getOptions(id);
        if (option != null) {
            option.setStage(Stage.WORKING);
            option.setTask(type, furtherQuestionId);
            save();

            if (player != null) {

                ItemStack cube = option.getCubeId() == null
                        ? CreepCubeItem.getCube(player, option.isMiniCube())
                        : CreepCubeItem.getCube(player, option.getCubeId());
                if (!cube.isEmpty()) {
                    CreepCubeItem.setLastAction(cube, type, furtherQuestionId);
                }

                if (ConfigSetup.meeCreepVolume.get() > 0.01f) {
                    String snd = "ok";
                    switch (random.nextInt(2)) {
                        case 0:
                            snd = "ok";
                            break;
                        case 1:
                            snd = "ok2";
                            break;
                    }
                    SoundEvent sound = SoundEvent.REGISTRY.getObject(new ResourceLocation(MeeCreeps.MODID, snd));
                    SoundTools.playSound(player.getEntityWorld(), sound, player.posX, player.posY, player.posZ, ConfigSetup.meeCreepVolume.get(), 1);
                }
            }
        }
    }

    public boolean performPlayerAction(EntityPlayerMP player, int id, MeeCreepActionType type, @Nullable String furtherQuestionId) {
        if (player == null || type == null || MeeCreeps.api.getFactory(type) == null) return false;
        ActionOptions option = getOptions(id);
        if (option == null || option.getPlayerId() == null
                || !option.getPlayerId().equals(player.getUniqueID())
                || option.getStage() != Stage.WAITING_FOR_PLAYER_INPUT
                || option.isPaused()) {
            return false;
        }
        if (!option.getActionOptions().contains(type) && !option.getMaybeActionOptions().contains(type)) {
            return false;
        }
        performAction(player, id, type, furtherQuestionId);
        return true;
    }

    public void cancelAction(@Nullable EntityPlayerMP player, int id) {
        ActionOptions option = getOptions(id);
        if (option != null && (player == null || isOwnedBy(player, option))) {
            option.setStage(Stage.DONE);
            option.setPaused(false);
        }
    }

    public void resumeAction(EntityPlayerMP player, int id) {
        ActionOptions option = getOptions(id);
        if (option != null && isOwnedBy(player, option)) {
            option.setPaused(false);
        }
    }

    private boolean isOwnedBy(EntityPlayer player, ActionOptions option) {
        return player != null && option.getPlayerId() != null && option.getPlayerId().equals(player.getUniqueID());
    }

    public int terminateCube(EntityPlayerMP player, UUID cubeId) {
        if (player == null || cubeId == null) {
            return 0;
        }
        List<ActionOptions> owned = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (ActionOptions option : options) {
            if (cubeId.equals(option.getCubeId()) && isOwnedBy(player, option)) {
                owned.add(option);
                ids.add(option.getActionId());
            }
        }
        terminateActions(owned, ids);
        return owned.size();
    }

    public boolean terminateMeeCreep(EntityPlayerMP player, EntityMeeCreeps entity, UUID cubeId) {
        if (player == null || entity == null || cubeId == null) {
            return false;
        }
        ActionOptions option = getOptions(entity.getActionId());
        if (option == null || !isOwnedBy(player, option) || !cubeId.equals(option.getCubeId())) {
            return false;
        }
        List<ActionOptions> single = Collections.singletonList(option);
        Set<Integer> ids = new HashSet<>();
        ids.add(option.getActionId());
        terminateActions(single, ids);
        return true;
    }

    private void terminateActions(List<ActionOptions> terminated, Set<Integer> actionIds) {
        if (terminated.isEmpty()) {
            return;
        }
        for (WorldServer world : DimensionManager.getWorlds()) {
            List<EntityMeeCreeps> entities = world.getEntities(EntityMeeCreeps.class,
                    entity -> entity != null && !entity.isDead && actionIds.contains(entity.getActionId()));
            for (EntityMeeCreeps entity : entities) {
                entity.terminate();
            }
        }
        for (ActionOptions option : terminated) {
            options.remove(option);
            optionMap.remove(option.getActionId());
            entityCache.remove(option.getActionId());
        }
        save();
    }

    private EntityMeeCreeps findMeeCreep(World world, int actionId, int dimension) {
        EntityMeeCreeps cachedEntity = getCachedEntity(actionId);
        if (cachedEntity != null && !cachedEntity.isDead) {
            return cachedEntity;
        }
        List<EntityMeeCreeps> entities = world.getEntities(EntityMeeCreeps.class, input -> input != null && input.getActionId() == actionId && !input.isDead);
        if (!entities.isEmpty()) {
            updateEntityCache(actionId, entities.get(0));
            return entities.get(0);
        }

        for (WorldServer w : DimensionManager.getWorlds()) {
            entities = w.getEntities(EntityMeeCreeps.class, input -> input != null && input.getActionId() == actionId && !input.isDead);
            if (!entities.isEmpty()) {
                updateEntityCache(actionId, entities.get(0));
                return entities.get(0);
            }
        }

        World w = TeleportationTools.getWorldForDimension(dimension);
        if (w == null) return null;
        entities = w.getEntities(EntityMeeCreeps.class, input -> input != null && input.getActionId() == actionId && !input.isDead);
        if (!entities.isEmpty()) {
            updateEntityCache(actionId, entities.get(0));
            return entities.get(0);
        }

        return null;
    }

    public void tick() {
        save();
        List<ActionOptions> newlist = new ArrayList<>();
        Map<Integer, ActionOptions> newmap = new HashMap<>();
        for (ActionOptions option : options) {
            EntityMeeCreeps meeCreep = findMeeCreep(DimensionManager.getWorld(0), option.getActionId(), option.getDimension());
            boolean keep = true;

            World world = meeCreep == null ? DimensionManager.getWorld(option.getDimension()) : meeCreep.getEntityWorld();
            BlockPos meeCreepPos = meeCreep == null ? option.getTargetPos() : meeCreep.getPosition();
            if (world != null && world.isBlockLoaded(meeCreepPos)) {
                if (!option.tick(world)) {
                    keep = false;
                }
            } else {
                if (option.getStage() != Stage.OPENING_GUI && option.getStage() != Stage.WAITING_FOR_PLAYER_INPUT && option.getStage() != Stage.WAITING_FOR_SPAWN) {
                    keep = false;
                }
            }
            if (meeCreep != null) {
                stayWithPlayer(option, meeCreep);
            } else if (option.getStage() != Stage.OPENING_GUI && option.getStage() != Stage.WAITING_FOR_PLAYER_INPUT && option.getStage() != Stage.WAITING_FOR_SPAWN) {
                keep = false;
            }

            if (meeCreep == null) {
                int failureCount = option.getFailureCount();
                failureCount--;
                option.setFailureCount(failureCount);
                if (failureCount <= 0) {
                    System.out.println("ServerActionManager.tick: FAILURE");
                    keep = false;
                }
            }

            if (keep) {
                newlist.add(option);
                newmap.put(option.getActionId(), option);
            } else if (world != null) {
                dropRemainingDrops(option, world);
            }
        }
        options = newlist;
        optionMap = newmap;
    }

    private void dropRemainingDrops(ActionOptions option, World world) {
        List<Pair<BlockPos, ItemStack>> drops = option.getDrops();
        if (!drops.isEmpty()) {
            for (Pair<BlockPos, ItemStack> pair : drops) {
                EntityItem entityItem = new EntityItem(world);
                entityItem.setItem(pair.getValue());
                BlockPos pos = pair.getKey();
                entityItem.setLocationAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
                world.spawnEntity(entityItem);
            }
        }
    }

    private void stayWithPlayer(ActionOptions option, EntityMeeCreeps meeCreep) {

        EntityPlayer player = option.getPlayer();
        if (player != null) {
            if (meeCreep.getHelper() != null) {
                IActionWorker worker = meeCreep.getHelper().getWorker();
                if (worker.needsToFollowPlayer()) {
                    if (isDifferentDimension(player, meeCreep) || isTooFar(player, meeCreep)) {

                        meeCreep.cancelJob();
                        BlockPos p = WorkerHelper.findSuitablePositionNearPlayer(meeCreep, player, 4.0);
                        meeCreep = (EntityMeeCreeps) TeleportationTools.teleportEntity(meeCreep, player.getEntityWorld(), p.getX() + .5, p.getY(), p.getZ() + .5, EnumFacing.NORTH);
                        updateEntityCache(option.getActionId(), meeCreep);
                        option.setDimension(player.getEntityWorld().provider.getDimension());
                    }
                }
            }
        }
    }

    private boolean isDifferentDimension(EntityPlayer player, EntityMeeCreeps meeCreep) {
        return player.getEntityWorld().provider.getDimension() != meeCreep.getEntityWorld().provider.getDimension();
    }

    private boolean isTooFar(EntityPlayer player, EntityMeeCreeps meeCreep) {
        return player.getPositionVector().squareDistanceTo(meeCreep.getPositionVector()) > 60*60;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("actions", Constants.NBT.TAG_COMPOUND);
        options = new ArrayList<>();
        optionMap = new HashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            ActionOptions opt = new ActionOptions(list.getCompoundTagAt(i));
            options.add(opt);
            optionMap.put(opt.getActionId(), opt);
        }
        lastId = nbt.getInteger("lastId");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (ActionOptions option : options) {
            NBTTagCompound tc = new NBTTagCompound();
            option.writeToNBT(tc);
            list.appendTag(tc);
        }
        compound.setTag("actions", list);
        compound.setInteger("lastId", lastId);
        return compound;
    }
}
