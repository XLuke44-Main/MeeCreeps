package mcjty.meecreeps.setup;

import mcjty.lib.setup.DefaultModSetup;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.blocks.ModBlocks;
import mcjty.meecreeps.blocks.PortalChunkLoadingCallback;
import mcjty.meecreeps.ForgeEventHandlers;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.config.ConfigSetup;
import mcjty.meecreeps.entities.ModEntities;
import mcjty.meecreeps.items.CartridgeItem;
import mcjty.meecreeps.items.ModItems;
import mcjty.meecreeps.items.TransportSolutionType;
import mcjty.meecreeps.network.MeeCreepsMessages;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.NonNullList;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

public class ModSetup extends DefaultModSetup {

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        super.preInit(e);

        ForgeChunkManager.setForcedChunkLoadingCallback(MeeCreeps.instance, PortalChunkLoadingCallback.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new ForgeEventHandlers());
        NetworkRegistry.INSTANCE.registerGuiHandler(MeeCreeps.instance, new GuiProxy());

        CommandHandler.registerCommands();

        MeeCreepsMessages.registerMessages("meecreeps");

        ModEntities.init();
    }

    @Override
    protected void setupModCompat() {

    }

    @Override
    protected void setupConfig() {
        MeeCreeps.api.registerFactories();
        ConfigSetup.init();
    }

    @Override
    public void createTabs() {

        creativeTab = new CreativeTabs("meecreeps") {
            @Override
            public ItemStack getTabIconItem() {
                ItemStack icon = new ItemStack(ModItems.portalGunItem);
                CartridgeItem.setTransportSolutionType(icon, TransportSolutionType.EXTRADIMENSIONAL);
                CartridgeItem.setCharge(icon, CartridgeItem.getMaxCharge(TransportSolutionType.EXTRADIMENSIONAL));
                icon.getTagCompound().setBoolean("meecreepsCreativeTabIcon", true);
                return icon;
            }

            @Override
            public void displayAllRelevantItems(NonNullList<ItemStack> items) {
                super.displayAllRelevantItems(items);

                Item intra = Item.getItemFromBlock(ModBlocks.portalSpawnerIntradimensional);
                Item inter = Item.getItemFromBlock(ModBlocks.portalSpawnerInterdimensional);
                Item extra = Item.getItemFromBlock(ModBlocks.portalSpawnerExtradimensional);
                Item hyper = Item.getItemFromBlock(ModBlocks.portalSpawnerHyperdimensional);
                Item linkedIntra = Item.getItemFromBlock(ModBlocks.portalSpawnerLinkedIntradimensional);
                Item linkedInter = Item.getItemFromBlock(ModBlocks.portalSpawnerLinkedInterdimensional);
                Item linkedExtra = Item.getItemFromBlock(ModBlocks.portalSpawnerLinkedExtradimensional);
                Item linkedHyper = Item.getItemFromBlock(ModBlocks.portalSpawnerLinkedHyperdimensional);

                Item[] order = { intra, inter, extra, hyper, linkedIntra, linkedInter, linkedExtra, linkedHyper };
                int insertAt = -1;
                for (int i = 0; i < items.size(); i++) {
                    Item item = items.get(i).getItem();
                    if (item == intra || item == inter || item == extra || item == hyper
                            || item == linkedIntra || item == linkedInter || item == linkedExtra || item == linkedHyper) {
                        if (insertAt < 0) insertAt = i;
                    }
                }
                if (insertAt < 0) return;

                java.util.List<ItemStack> orderedSpawners = new java.util.ArrayList<>();
                for (Item item : order) {
                    for (ItemStack stack : items) {
                        if (stack.getItem() == item) {
                            ItemStack copy = stack.copy();
                            if (item == linkedIntra || item == linkedInter || item == linkedExtra || item == linkedHyper) {
                                copy.setCount(2);
                                if (copy.getTagCompound() == null) copy.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
                                if (!copy.getTagCompound().hasUniqueId(mcjty.meecreeps.blocks.PortalSpawnerBlock.LINKED_PAIR_ID)) {
                                    copy.getTagCompound().setUniqueId(mcjty.meecreeps.blocks.PortalSpawnerBlock.LINKED_PAIR_ID, java.util.UUID.randomUUID());
                                }
                            }
                            orderedSpawners.add(copy);
                            break;
                        }
                    }
                }

                for (int i = items.size() - 1; i >= 0; i--) {
                    Item item = items.get(i).getItem();
                    if (item == intra || item == inter || item == extra || item == hyper
                            || item == linkedIntra || item == linkedInter || item == linkedExtra || item == linkedHyper) {
                        items.remove(i);
                    }
                }
                items.addAll(insertAt, orderedSpawners);
            }
        };
    }

    @Override
    public void postInit(FMLPostInitializationEvent e) {
        super.postInit(e);
        ConfigSetup.postInit();
    }
}
