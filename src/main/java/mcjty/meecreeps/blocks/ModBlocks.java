package mcjty.meecreeps.blocks;

import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ModBlocks {

    @GameRegistry.ObjectHolder("meecreeps:portalblock")
    public static PortalBlock portalBlock;

    @GameRegistry.ObjectHolder("meecreeps:creepcube")
    public static HeldCubeBlock heldCubeBlock;

    @GameRegistry.ObjectHolder("meecreeps:portal_spawner_intradimensional")
    public static PortalSpawnerBlock portalSpawnerIntradimensional;

    @GameRegistry.ObjectHolder("meecreeps:portal_spawner_extradimensional")
    public static PortalSpawnerBlock portalSpawnerExtradimensional;

    @GameRegistry.ObjectHolder("meecreeps:portal_spawner_interdimensional")
    public static PortalSpawnerBlock portalSpawnerInterdimensional;

    @GameRegistry.ObjectHolder("meecreeps:portal_spawner_hyperdimensional")
    public static PortalSpawnerBlock portalSpawnerHyperdimensional;

    @GameRegistry.ObjectHolder("meecreeps:portal_spawner_linked_intradimensional")
    public static PortalSpawnerBlock portalSpawnerLinkedIntradimensional;

    @GameRegistry.ObjectHolder("meecreeps:portal_spawner_linked_extradimensional")
    public static PortalSpawnerBlock portalSpawnerLinkedExtradimensional;

    @GameRegistry.ObjectHolder("meecreeps:portal_spawner_linked_interdimensional")
    public static PortalSpawnerBlock portalSpawnerLinkedInterdimensional;

    @GameRegistry.ObjectHolder("meecreeps:portal_spawner_linked_hyperdimensional")
    public static PortalSpawnerBlock portalSpawnerLinkedHyperdimensional;

    @SideOnly(Side.CLIENT)
    public static void initModels() {
        portalBlock.initModel();
        heldCubeBlock.initModel();
        portalSpawnerIntradimensional.initModel();
        portalSpawnerExtradimensional.initModel();
        portalSpawnerInterdimensional.initModel();
        portalSpawnerHyperdimensional.initModel();
        portalSpawnerLinkedIntradimensional.initModel();
        portalSpawnerLinkedExtradimensional.initModel();
        portalSpawnerLinkedInterdimensional.initModel();
        portalSpawnerLinkedHyperdimensional.initModel();

        initPortalSpawnerItemModel(portalSpawnerIntradimensional);
        initPortalSpawnerItemModel(portalSpawnerInterdimensional);
        initPortalSpawnerItemModel(portalSpawnerExtradimensional);
        initPortalSpawnerItemModel(portalSpawnerHyperdimensional);
        initPortalSpawnerItemModel(portalSpawnerLinkedIntradimensional);
        initPortalSpawnerItemModel(portalSpawnerLinkedInterdimensional);
        initPortalSpawnerItemModel(portalSpawnerLinkedExtradimensional);
        initPortalSpawnerItemModel(portalSpawnerLinkedHyperdimensional);
    }

    @SideOnly(Side.CLIENT)
    private static void initPortalSpawnerItemModel(PortalSpawnerBlock block) {
        Item item = Item.getItemFromBlock(block);
        if (item == null || block.getRegistryName() == null) return;
        ModelLoader.setCustomModelResourceLocation(
                item, 0, new ModelResourceLocation(block.getRegistryName(), "inventory"));
    }

}
