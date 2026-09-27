package mcjty.meecreeps.commands;

import mcjty.meecreeps.blocks.PortalTileEntity;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandCloseAllPortals implements ICommand {

    @Override
    public String getName() {
        return "close_all_portals";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/close_all_portals";
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        int closedPortals = 0;

        for (WorldServer world : server.worlds) {
            if (world == null) {
                continue;
            }

            List<PortalTileEntity> portals = new ArrayList<>();
            for (TileEntity tileEntity : world.loadedTileEntityList) {
                if (tileEntity instanceof PortalTileEntity) {
                    portals.add((PortalTileEntity) tileEntity);
                }
            }

            for (PortalTileEntity portal : portals) {
                portal.killPortal();
                closedPortals++;
            }
        }

        sender.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Closed " + closedPortals + " MeeCreeps portal endpoint(s)."
        ));
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender.canUseCommand(2, getName());
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(ICommand o) {
        return getName().compareTo(o.getName());
    }
}
