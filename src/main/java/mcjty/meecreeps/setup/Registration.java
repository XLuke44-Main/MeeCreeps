package mcjty.meecreeps.setup;

import mcjty.lib.datafix.fixes.TileEntityNamespace;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.blocks.HeldCubeBlock;
import mcjty.meecreeps.blocks.ModBlocks;
import mcjty.meecreeps.blocks.PortalBlock;
import mcjty.meecreeps.blocks.PortalTileEntity;
import mcjty.meecreeps.blocks.PortalSpawnerBlock;
import mcjty.meecreeps.blocks.PortalSpawnerTileEntity;
import mcjty.meecreeps.items.TransportSolutionType;
import mcjty.meecreeps.items.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.datafix.FixTypes;
import net.minecraftforge.common.util.ModFixs;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber
public class Registration {

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        ModFixs modFixs = FMLCommonHandler.instance().getDataFixer().init(MeeCreeps.MODID, 1);
        Map<String, String> oldToNewIdMap = new HashMap<>();

        event.getRegistry().register(new HeldCubeBlock());
        event.getRegistry().register(new PortalBlock());

        event.getRegistry().register(new PortalSpawnerBlock(TransportSolutionType.INTRADIMENSIONAL));
        event.getRegistry().register(new PortalSpawnerBlock(TransportSolutionType.INTERDIMENSIONAL));
        event.getRegistry().register(new PortalSpawnerBlock(TransportSolutionType.EXTRADIMENSIONAL));
        event.getRegistry().register(new PortalSpawnerBlock(TransportSolutionType.HYPERDIMENSIONAL));
        event.getRegistry().register(new PortalSpawnerBlock(TransportSolutionType.INTRADIMENSIONAL, true));
        event.getRegistry().register(new PortalSpawnerBlock(TransportSolutionType.INTERDIMENSIONAL, true));
        event.getRegistry().register(new PortalSpawnerBlock(TransportSolutionType.EXTRADIMENSIONAL, true));
        event.getRegistry().register(new PortalSpawnerBlock(TransportSolutionType.HYPERDIMENSIONAL, true));

        GameRegistry.registerTileEntity(PortalTileEntity.class, new ResourceLocation(MeeCreeps.MODID, "portalblock"));
        GameRegistry.registerTileEntity(PortalSpawnerTileEntity.class, new ResourceLocation(MeeCreeps.MODID, "portal_spawner"));

        oldToNewIdMap.put(MeeCreeps.MODID + "_portalblock", MeeCreeps.MODID + ":portalblock");
        oldToNewIdMap.put("minecraft:" + MeeCreeps.MODID + "_portalblock", MeeCreeps.MODID + ":portalblock");
        modFixs.registerFix(FixTypes.BLOCK_ENTITY, new TileEntityNamespace(oldToNewIdMap, 1));
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new CreepCubeItem());
        event.getRegistry().register(new PortalGunItem());
        event.getRegistry().register(new EmptyPortalGunItem());
        event.getRegistry().register(new ProjectileItem());
        event.getRegistry().register(new CartridgeItem());
        event.getRegistry().register(new ItemBlock(ModBlocks.portalBlock).setRegistryName(ModBlocks.portalBlock.getRegistryName()));
        event.getRegistry().register(new PortalSpawnerItemBlock(ModBlocks.portalSpawnerIntradimensional).setRegistryName(ModBlocks.portalSpawnerIntradimensional.getRegistryName()));
        event.getRegistry().register(new PortalSpawnerItemBlock(ModBlocks.portalSpawnerInterdimensional).setRegistryName(ModBlocks.portalSpawnerInterdimensional.getRegistryName()));
        event.getRegistry().register(new PortalSpawnerItemBlock(ModBlocks.portalSpawnerExtradimensional).setRegistryName(ModBlocks.portalSpawnerExtradimensional.getRegistryName()));
        event.getRegistry().register(new PortalSpawnerItemBlock(ModBlocks.portalSpawnerHyperdimensional).setRegistryName(ModBlocks.portalSpawnerHyperdimensional.getRegistryName()));
        event.getRegistry().register(new PortalSpawnerItemBlock(ModBlocks.portalSpawnerLinkedIntradimensional).setRegistryName(ModBlocks.portalSpawnerLinkedIntradimensional.getRegistryName()));
        event.getRegistry().register(new PortalSpawnerItemBlock(ModBlocks.portalSpawnerLinkedInterdimensional).setRegistryName(ModBlocks.portalSpawnerLinkedInterdimensional.getRegistryName()));
        event.getRegistry().register(new PortalSpawnerItemBlock(ModBlocks.portalSpawnerLinkedExtradimensional).setRegistryName(ModBlocks.portalSpawnerLinkedExtradimensional.getRegistryName()));
        event.getRegistry().register(new PortalSpawnerItemBlock(ModBlocks.portalSpawnerLinkedHyperdimensional).setRegistryName(ModBlocks.portalSpawnerLinkedHyperdimensional.getRegistryName()));
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> registry) {
        registry.getRegistry().register(new SoundEvent(new ResourceLocation(MeeCreeps.MODID, "teleport")).setRegistryName(new ResourceLocation(MeeCreeps.MODID, "teleport")));
        registry.getRegistry().register(new SoundEvent(new ResourceLocation(MeeCreeps.MODID, "portal")).setRegistryName(new ResourceLocation(MeeCreeps.MODID, "portal")));
        registry.getRegistry().register(new SoundEvent(new ResourceLocation(MeeCreeps.MODID, "intro1")).setRegistryName(new ResourceLocation(MeeCreeps.MODID, "intro1")));
        registry.getRegistry().register(new SoundEvent(new ResourceLocation(MeeCreeps.MODID, "intro2")).setRegistryName(new ResourceLocation(MeeCreeps.MODID, "intro2")));
        registry.getRegistry().register(new SoundEvent(new ResourceLocation(MeeCreeps.MODID, "intro3")).setRegistryName(new ResourceLocation(MeeCreeps.MODID, "intro3")));
        registry.getRegistry().register(new SoundEvent(new ResourceLocation(MeeCreeps.MODID, "intro4")).setRegistryName(new ResourceLocation(MeeCreeps.MODID, "intro4")));
        registry.getRegistry().register(new SoundEvent(new ResourceLocation(MeeCreeps.MODID, "ok")).setRegistryName(new ResourceLocation(MeeCreeps.MODID, "ok")));
        registry.getRegistry().register(new SoundEvent(new ResourceLocation(MeeCreeps.MODID, "ok2")).setRegistryName(new ResourceLocation(MeeCreeps.MODID, "ok2")));
    }
}
