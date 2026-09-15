package mcjty.meecreeps.items;

import com.google.gson.JsonObject;
import mcjty.meecreeps.MeeCreeps;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.crafting.CraftingHelper.ShapedPrimer;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.oredict.ShapedOreRecipe;

import javax.annotation.Nonnull;

/** Creates an empty Blue Portal Fluid cartridge from glass + an empty bucket. */
public class BlueEmptyCartridgeFactory implements IRecipeFactory {
    @Override
    public net.minecraft.item.crafting.IRecipe parse(JsonContext context, JsonObject json) {
        ShapedOreRecipe recipe = ShapedOreRecipe.factory(context, json);
        ShapedPrimer primer = new ShapedPrimer();
        primer.width = recipe.getWidth();
        primer.height = recipe.getHeight();
        primer.mirrored = false;
        primer.input = recipe.getIngredients();
        return new BlueEmptyCartridgeRecipe(new net.minecraft.util.ResourceLocation(MeeCreeps.MODID, "blue_empty_cartridge"), recipe.getRecipeOutput(), primer);
    }

    private static class BlueEmptyCartridgeRecipe extends ShapedOreRecipe {
        BlueEmptyCartridgeRecipe(net.minecraft.util.ResourceLocation group, ItemStack result, ShapedPrimer primer) {
            super(group, result, primer);
        }

        @Override
        @Nonnull
        public ItemStack getCraftingResult(@Nonnull InventoryCrafting inventory) {
            ItemStack result = super.getCraftingResult(inventory);
            if (result.isEmpty()) return result;
            CartridgeItem.setBlueFluid(result, true);
            CartridgeItem.setCharge(result, 0);
            return result;
        }
    }
}
