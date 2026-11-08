package mcjty.meecreeps.items;

import mcjty.meecreeps.MeeCreeps;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ProjectileItem extends Item {

    public ProjectileItem() {
        setRegistryName("projectile");
        setUnlocalizedName(MeeCreeps.MODID + ".projectile");
        setMaxStackSize(1);
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(this, 0,
                new net.minecraft.client.renderer.block.model.ModelResourceLocation(getRegistryName(), "inventory"));
        addPropertyOverride(new net.minecraft.util.ResourceLocation(MeeCreeps.MODID, "portal_solution"),
                new IItemPropertyGetter() {
                    @Override
                    public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                        switch (CartridgeItem.getTransportSolutionType(stack)) {
                            case INTRADIMENSIONAL: return 1.0F;
                            case EXTRADIMENSIONAL: return 2.0F;
                            case HYPERDIMENSIONAL: return 3.0F;
                            case INTERDIMENSIONAL:
                            default: return 0.0F;
                        }
                    }
                });
    }
}
