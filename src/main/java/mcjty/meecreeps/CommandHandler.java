package mcjty.meecreeps;

import mcjty.lib.McJtyLib;
import mcjty.lib.typed.Key;
import mcjty.lib.typed.Type;
import mcjty.meecreeps.actions.ServerActionManager;
import mcjty.meecreeps.config.DimensionGroups;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.items.CreepCubeItem;
import mcjty.meecreeps.entities.EntityMeeCreeps;
import mcjty.meecreeps.items.TransportSolutionType;
import mcjty.meecreeps.teleport.DestinationParser;
import mcjty.meecreeps.teleport.TeleportDestination;
import mcjty.meecreeps.teleport.TeleportationTools;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;

public class CommandHandler {

    public static final String CMD_CANCEL_PORTAL = "cancel_portal";
    public static final String CMD_DELETE_DESTINATION = "delete_dest";
    public static final String CMD_SET_CURRENT = "set_current";
    public static final String CMD_SET_CUSTOM_DESTINATION = "set_custom_destination";
    public static final String CMD_FAST_RETURN = "fast_return";
    public static final String CMD_TOGGLE_AUTOCLOSE = "toggle_autoclose";
    public static final String CMD_CLOSE_ALL = "close_all";
    public static final String CMD_LIST_DIMENSIONS = "list_dimensions";
    public static final String CMD_TOGGLE_AIR_PLACEMENT = "toggle_air_placement";
    public static final String CMD_RESUME_ACTION = "resume_action";
    public static final String CMD_CANCEL_ACTION = "cancel_action";
    public static final String CMD_CUBE_LEFT_CLICK = "cube_left_click";
    public static final String CMD_CUBE_RIGHT_CLICK = "cube_right_click";
    public static final String CMD_TERMINATE_CUBE = "terminate_cube";
    public static final String CMD_TERMINATE_ENTITY = "terminate_entity";
    public static final Key<BlockPos> PARAM_POS = new Key<>("pos", Type.BLOCKPOS);
    public static final Key<Integer> PARAM_ID = new Key<>("id", Type.INTEGER);
    public static final Key<Integer> PARAM_SIDE = new Key<>("side", Type.INTEGER);
    public static final Key<Integer> PARAM_ENTITY_ID = new Key<>("entity_id", Type.INTEGER);
    public static final Key<String> PARAM_CUBE_UUID = new Key<>("cube_uuid", Type.STRING);
    public static final Key<String> PARAM_INPUT = new Key<>("input", Type.STRING);
    public static void registerCommands() {
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_CANCEL_PORTAL, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            TeleportationTools.cancelPortalPair(player, arguments.get(PARAM_POS));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_DELETE_DESTINATION, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            PortalGunItem.addDestinationEncoded(heldItem, null, arguments.get(PARAM_ID));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_SET_CURRENT, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            int encoded = arguments.get(PARAM_ID);
            if (encoded < 0 || encoded >= 24) return true;
            int ring = encoded / PortalGunItem.SLOTS_PER_RING;
            int slot = encoded % PortalGunItem.SLOTS_PER_RING;
            TeleportDestination destination = PortalGunItem.getDestinations(heldItem, ring).get(slot);
            if (destination == null) {
                return true;
            }
            PortalGunItem.setCurrentDestination(heldItem, encoded);
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_SET_CUSTOM_DESTINATION, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            String input = arguments.get(PARAM_INPUT);
            TeleportDestination destination = null;
            if (!TeleportDestination.BLENDER_DIMENSION.equals(input == null ? null : input.trim())) {
                destination = TeleportationTools.resolvePlayerLocator((EntityPlayerMP) player, input);
                if (destination == null && input != null && input.indexOf(';') < 0) {
                    String message = TeleportationTools.isOnlinePlayerLocator((EntityPlayerMP) player, input)
                            ? "NO SAFE DESTINATION LOCATION AVAILABLE!"
                            : DestinationParser.PLAYER_NOT_FOUND;
                    PortalGunItem.sendPortalGunMessage(player, PortalGunItem.getTransportSolutionType(heldItem), message);
                    return true;
                }
            }
            DestinationParser.ParseResult result = destination != null
                    ? DestinationParser.ParseResult.valid(destination)
                    : DestinationParser.parse(input, player.getEntityWorld());
            if (!result.isValid()) {
                PortalGunItem.sendPortalGunMessage(player, PortalGunItem.getTransportSolutionType(heldItem), result.getError());
                return true;
            }
            destination = result.getDestination();
            if (!PortalGunItem.validateDestinationForPlayer(heldItem, player.getEntityWorld(), destination, player)) {
                return true;
            }
            PortalGunItem.setCustomDestination(heldItem, arguments.get(PARAM_ID), destination);
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_FAST_RETURN, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            TeleportDestination destination = TeleportationTools.getFastReturnDestination((EntityPlayerMP) player);
            if (destination == null) {
                PortalGunItem.sendPortalGunMessage(player, PortalGunItem.getTransportSolutionType(heldItem), "NO LOCATION AVAILABLE FOR FAST RETURN!");
                return true;
            }
            if (!PortalGunItem.validateDestinationForPlayer(heldItem, player.getEntityWorld(), destination, player)) {
                return true;
            }
            PortalGunItem.setFastReturnOverride(heldItem, destination);
            PortalGunItem.sendInformationalMessage(player, heldItem, "Fast Return destination overridden.");
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_TOGGLE_AUTOCLOSE, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            boolean enabled = PortalGunItem.toggleAutoClose(heldItem);
            PortalGunItem.sendInformationalMessage(player, heldItem,
                    "Portal Auto-Close: " + (enabled ? "ENABLED" : "DISABLED"));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_CLOSE_ALL, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            PortalGunItem.closeAllPortals(heldItem);
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_TOGGLE_AIR_PLACEMENT, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            boolean enabled = PortalGunItem.toggleAirPlacement(heldItem);
            PortalGunItem.sendInformationalMessage(player, heldItem,
                    "Air Placement: " + (enabled ? "ENABLED" : "DISABLED"));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_LIST_DIMENSIONS, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            TransportSolutionType type = heldItem.isEmpty()
                    ? TransportSolutionType.INTERDIMENSIONAL : PortalGunItem.getTransportSolutionType(heldItem);
            int currentDimension = player.getEntityWorld().provider.getDimension();
            for (Integer id : DimensionGroups.getVisibleDimensions(type, currentDimension)) {
                String name = DimensionGroups.getDisplayName(id, type);
                TextFormatting color = id == currentDimension
                        ? type.getCurrentDimensionListFormatting() : type.getOtherDimensionListFormatting();
                player.sendMessage(new TextComponentString(id + " - " + name)
                        .setStyle(new Style().setColor(color)));
            }
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_RESUME_ACTION, (player, arguments) -> {
            ServerActionManager.getManager().resumeAction((EntityPlayerMP) player, arguments.get(PARAM_ID));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_CANCEL_ACTION, (player, arguments) -> {
            ServerActionManager.getManager().cancelAction((EntityPlayerMP) player, arguments.get(PARAM_ID));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_CUBE_LEFT_CLICK, (player, arguments) -> {
            if (!(player instanceof EntityPlayerMP)) return false;
            int side = arguments.get(PARAM_SIDE);
            EnumFacing facing = side >= 0 && side < EnumFacing.VALUES.length ? EnumFacing.VALUES[side] : EnumFacing.UP;
            java.util.UUID cubeId;
            String encodedCubeId = arguments.get(PARAM_CUBE_UUID);
            if (encodedCubeId == null) return false;
            try {
                cubeId = java.util.UUID.fromString(encodedCubeId);
            } catch (IllegalArgumentException e) {
                return false;
            }
            CreepCubeItem.handleLeftClick((EntityPlayerMP) player, arguments.get(PARAM_POS), facing, cubeId);
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_CUBE_RIGHT_CLICK, (player, arguments) -> {
            if (!(player instanceof EntityPlayerMP)) return false;
            int side = arguments.get(PARAM_SIDE);
            EnumFacing facing = side >= 0 && side < EnumFacing.VALUES.length ? EnumFacing.VALUES[side] : EnumFacing.UP;
            String encodedCubeId = arguments.get(PARAM_CUBE_UUID);
            if (encodedCubeId == null) return false;
            java.util.UUID cubeId;
            try {
                cubeId = java.util.UUID.fromString(encodedCubeId);
            } catch (IllegalArgumentException e) {
                return false;
            }
            CreepCubeItem.handleRightClick((EntityPlayerMP) player, arguments.get(PARAM_POS), facing, cubeId);
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_TERMINATE_CUBE, (player, arguments) -> {
            if (!(player instanceof EntityPlayerMP)) return false;
            ItemStack cube = CreepCubeItem.getHeldCube(player);
            if (cube.isEmpty()) return false;
            ServerActionManager.getManager().terminateCube((EntityPlayerMP) player, CreepCubeItem.getCubeId(cube, player));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_TERMINATE_ENTITY, (player, arguments) -> {
            if (!(player instanceof EntityPlayerMP)) return false;
            java.util.UUID cubeId;
            String encodedCubeId = arguments.get(PARAM_CUBE_UUID);
            if (encodedCubeId == null) return false;
            try {
                cubeId = java.util.UUID.fromString(encodedCubeId);
            } catch (IllegalArgumentException e) {
                return false;
            }
            ItemStack cube = CreepCubeItem.getHeldCube(player, cubeId);
            if (cube.isEmpty()) return false;
            net.minecraft.entity.Entity target = player.getEntityWorld().getEntityByID(arguments.get(PARAM_ENTITY_ID));
            if (target instanceof EntityMeeCreeps) {
                ServerActionManager.getManager().terminateMeeCreep((EntityPlayerMP) player, (EntityMeeCreeps) target, cubeId);
            }
            return true;
        });
    }
}
