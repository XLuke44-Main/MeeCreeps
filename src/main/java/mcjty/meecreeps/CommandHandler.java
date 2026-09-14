package mcjty.meecreeps;

import mcjty.lib.McJtyLib;
import mcjty.lib.typed.Key;
import mcjty.lib.typed.Type;
import mcjty.meecreeps.actions.ServerActionManager;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.teleport.DestinationParser;
import mcjty.meecreeps.teleport.TeleportDestination;
import mcjty.meecreeps.teleport.TeleportationTools;
import net.minecraftforge.common.DimensionManager;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class CommandHandler {

    public static final String CMD_CANCEL_PORTAL = "cancel_portal";
    public static final String CMD_DELETE_DESTINATION = "delete_dest";
    public static final String CMD_SET_CURRENT = "set_current";
    public static final String CMD_SET_CUSTOM_DESTINATION = "set_custom_destination";
    public static final String CMD_FAST_RETURN = "fast_return";
    public static final String CMD_TOGGLE_AUTOCLOSE = "toggle_autoclose";
    public static final String CMD_CLOSE_ALL = "close_all";
    public static final String CMD_LIST_DIMENSIONS = "list_dimensions";
    public static final String CMD_RESUME_ACTION = "resume_action";
    public static final String CMD_CANCEL_ACTION = "cancel_action";
    public static final Key<BlockPos> PARAM_POS = new Key<>("pos", Type.BLOCKPOS);
    public static final Key<Integer> PARAM_ID = new Key<>("id", Type.INTEGER);
    public static final Key<String> PARAM_INPUT = new Key<>("input", Type.STRING);

    public static void registerCommands() {
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_CANCEL_PORTAL, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false; // Something went wrong
            TeleportationTools.cancelPortalPair(player, arguments.get(PARAM_POS));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_DELETE_DESTINATION, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false; // Something went wrong
            PortalGunItem.addDestinationEncoded(heldItem, null, arguments.get(PARAM_ID));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_SET_CURRENT, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false; // Something went wrong
            int encoded = arguments.get(PARAM_ID);
            if (encoded < 0 || encoded >= 24) return true;
            int ring = encoded / PortalGunItem.SLOTS_PER_RING;
            int slot = encoded % PortalGunItem.SLOTS_PER_RING;
            TeleportDestination destination = PortalGunItem.getDestinations(heldItem, ring).get(slot);
            try {
                if (destination == null || !DimensionManager.isDimensionRegistered(destination.getDimension())
                        || TeleportationTools.resolveDestinationWorld(destination.getDimension()) == null) {
                    player.sendMessage(new TextComponentString(DestinationParser.INVALID_DIMENSION).setStyle(new Style().setColor(TextFormatting.RED)));
                    return true;
                }
            } catch (Throwable t) {
                player.sendMessage(new TextComponentString(DestinationParser.INVALID_DIMENSION).setStyle(new Style().setColor(TextFormatting.RED)));
                return true;
            }
            PortalGunItem.setCurrentDestination(heldItem, encoded);
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_SET_CUSTOM_DESTINATION, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            String input = arguments.get(PARAM_INPUT);
            TeleportDestination destination = TeleportationTools.resolvePlayerLocator((EntityPlayerMP) player, input);
            if (destination == null && input != null && input.indexOf(';') < 0) {
                String message = TeleportationTools.isOnlinePlayerLocator((EntityPlayerMP) player, input)
                        ? "NO SAFE DESTINATION LOCATION AVAILABLE!"
                        : DestinationParser.PLAYER_NOT_FOUND;
                player.sendMessage(new TextComponentString(message).setStyle(new Style().setColor(TextFormatting.RED)));
                return true;
            }
            DestinationParser.ParseResult result = destination != null
                    ? DestinationParser.ParseResult.valid(destination)
                    : DestinationParser.parse(input, player.getEntityWorld());
            if (!result.isValid()) {
                player.sendMessage(new TextComponentString(result.getError()).setStyle(new Style().setColor(TextFormatting.RED)));
                return true;
            }
            destination = result.getDestination();
            try {
                if (!DimensionManager.isDimensionRegistered(destination.getDimension())
                        || TeleportationTools.resolveDestinationWorld(destination.getDimension()) == null) {
                    player.sendMessage(new TextComponentString(DestinationParser.INVALID_DIMENSION).setStyle(new Style().setColor(TextFormatting.RED)));
                    return true;
                }
            } catch (Throwable t) {
                player.sendMessage(new TextComponentString(DestinationParser.INVALID_DIMENSION).setStyle(new Style().setColor(TextFormatting.RED)));
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
                player.sendMessage(new TextComponentString("NO LOCATION AVAILABLE FOR FAST RETURN!").setStyle(new Style().setColor(TextFormatting.RED)));
                return true;
            }
            PortalGunItem.setFastReturnOverride(heldItem, destination);
            player.sendMessage(new TextComponentString("Fast Return destination overridden.").setStyle(new Style().setColor(TextFormatting.GREEN)));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_TOGGLE_AUTOCLOSE, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            boolean enabled = PortalGunItem.toggleAutoClose(heldItem);
            player.sendMessage(new TextComponentString("Portal Auto-Close: " + (enabled ? "ENABLED" : "DISABLED")));
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_CLOSE_ALL, (player, arguments) -> {
            ItemStack heldItem = PortalGunItem.getGun(player);
            if (heldItem.isEmpty()) return false;
            PortalGunItem.closeAllPortals(heldItem);
            return true;
        });
        McJtyLib.registerCommand(MeeCreeps.MODID, CMD_LIST_DIMENSIONS, (player, arguments) -> {
            Integer[] ids = DimensionManager.getStaticDimensionIDs();
            player.sendMessage(new TextComponentString("Available dimensions:")
                    .setStyle(new Style().setColor(TextFormatting.GOLD)));
            for (Integer id : ids) {
                String name = null;
                try {
                    net.minecraft.world.WorldServer world = DimensionManager.getWorld(id);
                    if (world != null && world.provider != null && world.provider.getDimensionType() != null) {
                        name = world.provider.getDimensionType().getName();
                    }
                } catch (Throwable ignored) {
                }
                if (name == null || name.isEmpty()) {
                    try {
                        name = DimensionManager.getProviderType(id).getName();
                    } catch (Throwable ignored) {
                    }
                }
                player.sendMessage(new TextComponentString("DIMID " + id + (name == null || name.isEmpty() ? "" : " - " + name))
                        .setStyle(new Style().setColor(id == player.getEntityWorld().provider.getDimension() ? TextFormatting.GREEN : TextFormatting.AQUA)));
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
    }
}
