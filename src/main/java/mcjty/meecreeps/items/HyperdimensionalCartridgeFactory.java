package mcjty.meecreeps.items;

import com.google.gson.JsonObject;
import mcjty.meecreeps.MeeCreeps;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper.ShapedPrimer;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.oredict.ShapedOreRecipe;

import javax.annotation.Nonnull;

/** Creates an empty Hyperdimensional Transport Solution cartridge. */
public class HyperdimensionalCartridgeFactory implements IRecipeFactory {
    @Override
    public net.minecraft.item.crafting.IRecipe parse(JsonContext context, JsonObject json) {
        ShapedOreRecipe recipe = ShapedOreRecipe.factory(context, json);
        ShapedPrimer primer = new ShapedPrimer();
        primer.width = recipe.getWidth();
        primer.height = recipe.getHeight();
        primer.mirrored = JsonUtils.getBoolean(json, "mirrored", true);
        primer.input = recipe.getIngredients();

        ItemStack output = recipe.getRecipeOutput();
        CartridgeItem.setTransportSolutionType(output, TransportSolutionType.HYPERDIMENSIONAL);
        CartridgeItem.setCharge(output, 0);

        return new HyperdimensionalRecipe(new ResourceLocation(MeeCreeps.MODID, "hyperdimensional_cartridge"), recipe.getRecipeOutput(), primer);
    }

    private static class HyperdimensionalRecipe extends ShapedOreRecipe {
        HyperdimensionalRecipe(ResourceLocation group, ItemStack result, ShapedPrimer primer) { super(group, result, primer); }

        @Override
        @Nonnull
        public ItemStack getCraftingResult(@Nonnull InventoryCrafting inventory) {
            ItemStack result = super.getCraftingResult(inventory);
            if (result.isEmpty()) return result;
            CartridgeItem.setTransportSolutionType(result, TransportSolutionType.HYPERDIMENSIONAL);
            CartridgeItem.setCharge(result, 0);
            return result;
        }
    }
}
