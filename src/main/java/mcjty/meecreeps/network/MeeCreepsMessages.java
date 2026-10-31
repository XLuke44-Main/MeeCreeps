package mcjty.meecreeps.network;

import mcjty.lib.network.PacketHandler;
import mcjty.lib.thirteen.ChannelBuilder;
import mcjty.lib.thirteen.SimpleChannel;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.actions.PacketActionOptionToClient;
import mcjty.meecreeps.actions.PacketPerformAction;
import mcjty.meecreeps.actions.PacketShowBalloonToClient;
import mcjty.meecreeps.actions.PacketShowPortalSpawnerConfirmationToClient;
import mcjty.meecreeps.items.PacketConfirmPortalSpawnerOperation;
import mcjty.meecreeps.items.PacketTogglePortalGunMode;
import mcjty.meecreeps.teleport.PacketSetDestination;
import mcjty.meecreeps.teleport.PacketToggleFavouriteDestination;
import mcjty.meecreeps.teleport.PacketFireFavourite;
import mcjty.meecreeps.teleport.PacketBlenderDeath;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

public class MeeCreepsMessages {

    public static SimpleNetworkWrapper INSTANCE = null;

    public MeeCreepsMessages() {
    }

    public static void registerMessages(String name) {
        SimpleChannel net = ChannelBuilder
                .named(new ResourceLocation(MeeCreeps.MODID, name))
                .networkProtocolVersion(() -> MeeCreeps.VERSION)
                .clientAcceptedVersions(s -> MeeCreeps.VERSION.equals(s))
                .serverAcceptedVersions(s -> MeeCreeps.VERSION.equals(s))
                .simpleChannel();

        INSTANCE = net.getNetwork();

        net.registerMessageServer(id(), PacketPerformAction.class, PacketPerformAction::toBytes, PacketPerformAction::new, PacketPerformAction::handle);
        net.registerMessageServer(id(), PacketSetDestination.class, PacketSetDestination::toBytes, PacketSetDestination::new, PacketSetDestination::handle);
        net.registerMessageServer(id(), PacketToggleFavouriteDestination.class, PacketToggleFavouriteDestination::toBytes, PacketToggleFavouriteDestination::new, PacketToggleFavouriteDestination::handle);
        net.registerMessageServer(id(), PacketFireFavourite.class, PacketFireFavourite::toBytes, PacketFireFavourite::new, PacketFireFavourite::handle);
        net.registerMessageServer(id(), PacketConfirmPortalSpawnerOperation.class, PacketConfirmPortalSpawnerOperation::toBytes, PacketConfirmPortalSpawnerOperation::new, PacketConfirmPortalSpawnerOperation::handle);
        net.registerMessageServer(id(), PacketTogglePortalGunMode.class, PacketTogglePortalGunMode::toBytes, PacketTogglePortalGunMode::new, PacketTogglePortalGunMode::handle);

        net.registerMessageClient(id(), PacketActionOptionToClient.class, PacketActionOptionToClient::toBytes, PacketActionOptionToClient::new, PacketActionOptionToClient::handle);
        net.registerMessageClient(id(), PacketShowBalloonToClient.class, PacketShowBalloonToClient::toBytes, PacketShowBalloonToClient::new, PacketShowBalloonToClient::handle);
        net.registerMessageClient(id(), PacketShowPortalSpawnerConfirmationToClient.class, PacketShowPortalSpawnerConfirmationToClient::toBytes, PacketShowPortalSpawnerConfirmationToClient::new, PacketShowPortalSpawnerConfirmationToClient::handle);
        net.registerMessageClient(id(), PacketBlenderDeath.class, PacketBlenderDeath::toBytes, PacketBlenderDeath::new, PacketBlenderDeath::handle);
    }

    private static int id() {
        return PacketHandler.nextPacketID();
    }
}
