package mcjty.meecreeps.items;

import com.google.gson.JsonObject;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.blocks.PortalSpawnerBlock;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.CraftingHelper.ShapedPrimer;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.oredict.ShapedOreRecipe;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PortalSpawnerRecipeFactory implements IRecipeFactory {
    @Override
    public net.minecraft.item.crafting.IRecipe parse(JsonContext context, JsonObject json) {
        ShapedOreRecipe recipe = ShapedOreRecipe.factory(context, json);
        ShapedPrimer primer = new ShapedPrimer();
        primer.width = recipe.getWidth();
        primer.height = recipe.getHeight();
        primer.mirrored = JsonUtils.getBoolean(json, "mirrored", false);
        primer.input = recipe.getIngredients();

        TransportSolutionType expectedType = TransportSolutionType.fromName(JsonUtils.getString(json, "solution"));
        boolean linked = JsonUtils.getBoolean(json, "linked", false);
        ItemStack output = recipe.getRecipeOutput();
        ResourceLocation outputName = new ResourceLocation(
                MeeCreeps.MODID,
                (linked ? "portal_spawner_linked_" : "portal_spawner_") + expectedType.getName());

        return new PortalSpawnerRecipe(outputName, output, primer, expectedType, linked);
    }

    private static class PortalSpawnerRecipe extends ShapedOreRecipe {
        private final TransportSolutionType expectedType;
        private final boolean linked;

        private PortalSpawnerRecipe(ResourceLocation group, ItemStack result, ShapedPrimer primer,
                                    TransportSolutionType expectedType, boolean linked) {
            super(group, result, primer);
            this.expectedType = expectedType;
            this.linked = linked;
        }

        private ItemStack findCartridge(InventoryCrafting inventory) {
            for (int i = 0; i < inventory.getSizeInventory(); ++i) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof CartridgeItem) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean matches(InventoryCrafting inventory, World world) {
            if (!super.matches(inventory, world)) return false;
            ItemStack cartridge = findCartridge(inventory);
            if (cartridge.isEmpty()) return false;
            return CartridgeItem.getTransportSolutionType(cartridge) == expectedType
                    && CartridgeItem.getCharge(cartridge) == CartridgeItem.getMaxCharge(expectedType);
        }

        @Override
        @Nonnull
        public ItemStack getCraftingResult(@Nonnull InventoryCrafting inventory) {
            ItemStack cartridge = findCartridge(inventory);
            if (cartridge.isEmpty()
                    || CartridgeItem.getTransportSolutionType(cartridge) != expectedType
                    || CartridgeItem.getCharge(cartridge) != CartridgeItem.getMaxCharge(expectedType)) {
                return ItemStack.EMPTY;
            }

            ItemStack result = super.getCraftingResult(inventory);
            if (linked) {
                if (result.getCount() < 2) result.setCount(2);
                NBTTagCompound tag = result.getTagCompound();
                if (tag == null) {
                    tag = new NBTTagCompound();
                    result.setTagCompound(tag);
                }

                tag.setUniqueId(PortalSpawnerBlock.LINKED_PAIR_ID, UUID.randomUUID());
                tag.removeTag(PortalSpawnerBlock.LINKED_FIRST_DIM);
                tag.removeTag(PortalSpawnerBlock.LINKED_FIRST_POS);
                tag.removeTag(PortalSpawnerBlock.LINKED_TWIN_DIM);
                tag.removeTag(PortalSpawnerBlock.LINKED_TWIN_POS);
            }
            return result;
        }
    }
}
