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

public class InterdimensionalCartridgeFactory implements IRecipeFactory {
    @Override
    public net.minecraft.item.crafting.IRecipe parse(JsonContext context, JsonObject json) {
        ShapedOreRecipe recipe = ShapedOreRecipe.factory(context, json);
        ShapedPrimer primer = new ShapedPrimer();
        primer.width = recipe.getWidth();
        primer.height = recipe.getHeight();
        primer.mirrored = JsonUtils.getBoolean(json, "mirrored", true);
        primer.input = recipe.getIngredients();

        ItemStack output = recipe.getRecipeOutput();
        CartridgeItem.setTransportSolutionType(output, TransportSolutionType.INTERDIMENSIONAL);
        CartridgeItem.setCharge(output, 0);

        return new InterdimensionalRecipe(new ResourceLocation(MeeCreeps.MODID, "interdimensional_cartridge"), recipe.getRecipeOutput(), primer);
    }

    private static class InterdimensionalRecipe extends ShapedOreRecipe {
        InterdimensionalRecipe(ResourceLocation group, ItemStack result, ShapedPrimer primer) {
            super(group, result, primer);
        }

        @Override
        @Nonnull
        public ItemStack getCraftingResult(@Nonnull InventoryCrafting inventory) {
            ItemStack result = super.getCraftingResult(inventory);
            if (result.isEmpty()) return result;
            CartridgeItem.setTransportSolutionType(result, TransportSolutionType.INTERDIMENSIONAL);
            CartridgeItem.setCharge(result, 0);
            return result;
        }
    }
}
