package mcjty.meecreeps.items;

import com.google.gson.JsonObject;
import mcjty.meecreeps.config.ConfigSetup;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import javax.annotation.Nonnull;

public class HyperdimensionalCartridgeRefillFactory implements IRecipeFactory {
    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        ShapelessOreRecipe recipe = ShapelessOreRecipe.factory(context, json);

        ItemStack output = recipe.getRecipeOutput();
        CartridgeItem.setTransportSolutionType(output, TransportSolutionType.HYPERDIMENSIONAL);
        CartridgeItem.setCharge(output, Math.min(ConfigSetup.hyperdimensionalRefillAmount.get(), ConfigSetup.hyperdimensionalMaxCharge.get()));

        return new RefillRecipe(new ResourceLocation("meecreeps", "hyperdimensional_cartridge_refill"), output, recipe.getIngredients());
    }

    private static class RefillRecipe extends ShapelessRecipes {
        private final ItemStack result;

        RefillRecipe(ResourceLocation group, ItemStack result, NonNullList<Ingredient> ingredients) {
            super(group.toString(), result, ingredients);
            this.result = result.copy();
        }

        private ItemStack findEmptyCartridge(InventoryCrafting inventory) {
            for (int i = 0; i < inventory.getSizeInventory(); ++i) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof CartridgeItem
                        && CartridgeItem.getTransportSolutionType(stack) == TransportSolutionType.HYPERDIMENSIONAL
                        && CartridgeItem.getCharge(stack) <= 0) return stack;
            }
            return ItemStack.EMPTY;
        }

        private ItemStack findRedstone(InventoryCrafting inventory) {
            for (int i = 0; i < inventory.getSizeInventory(); ++i) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() == Items.REDSTONE) return stack;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean matches(InventoryCrafting inventory, World world) {
            return !findEmptyCartridge(inventory).isEmpty()
                    && !findRedstone(inventory).isEmpty()
                    && super.matches(inventory, world);
        }

        @Override
        @Nonnull
        public ItemStack getCraftingResult(@Nonnull InventoryCrafting inventory) {
            if (findEmptyCartridge(inventory).isEmpty() || findRedstone(inventory).isEmpty()) return ItemStack.EMPTY;
            ItemStack output = result.copy();
            CartridgeItem.setTransportSolutionType(output, TransportSolutionType.HYPERDIMENSIONAL);
            CartridgeItem.setCharge(output, Math.min(ConfigSetup.hyperdimensionalRefillAmount.get(), ConfigSetup.hyperdimensionalMaxCharge.get()));
            return output;
        }
    }
}
