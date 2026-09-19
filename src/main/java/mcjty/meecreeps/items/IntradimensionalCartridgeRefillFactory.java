package mcjty.meecreeps.items;

import com.google.gson.JsonObject;
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
import mcjty.meecreeps.config.ConfigSetup;

import javax.annotation.Nonnull;

/**
 * Shapeless recipe that refills an empty Intradimensional Transport Solution cartridge from a
 * water bucket or any vanilla potion container (regular, splash, or lingering).
 */
public class IntradimensionalCartridgeRefillFactory implements IRecipeFactory {
    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        ShapelessOreRecipe recipe = ShapelessOreRecipe.factory(context, json);
        NonNullList<Ingredient> ingredients = recipe.getIngredients();

        // Replace the recipe's placeholder water-bucket ingredient with an Ingredient that accepts
        // a water bucket or any regular, splash, or lingering potion.  Ingredient matching in
        // Forge 1.12 is item/damage based, so an untagged potion stack acts as a wildcard for the
        // potion's NBT-defined effect.
        ingredients.set(1, Ingredient.fromStacks(
                new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.POTIONITEM),
                new ItemStack(Items.SPLASH_POTION),
                new ItemStack(Items.LINGERING_POTION)));

        ItemStack output = recipe.getRecipeOutput();
        CartridgeItem.setTransportSolutionType(output, TransportSolutionType.INTRADIMENSIONAL);
        CartridgeItem.setCharge(output, Math.min(ConfigSetup.intradimensionalRefillAmount.get(), ConfigSetup.intradimensionalMaxCharge.get()));

        return new RefillRecipe(
                new ResourceLocation("meecreeps", "intradimensional_cartridge_refill"),
                output,
                ingredients);
    }

    private static class RefillRecipe extends ShapelessRecipes {
        private final ItemStack result;

        RefillRecipe(ResourceLocation group, ItemStack result,
                     NonNullList<Ingredient> ingredients) {
            super(group.toString(), result, ingredients);
            this.result = result.copy();
        }

        private ItemStack findEmptyIntradimensionalCartridge(InventoryCrafting inventory) {
            for (int i = 0; i < inventory.getSizeInventory(); ++i) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()
                        && stack.getItem() instanceof CartridgeItem
                        && CartridgeItem.getTransportSolutionType(stack) == TransportSolutionType.INTRADIMENSIONAL
                        && CartridgeItem.getCharge(stack) <= 0) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        private ItemStack findRefillContainer(InventoryCrafting inventory) {
            for (int i = 0; i < inventory.getSizeInventory(); ++i) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (CartridgeItem.isIntradimensionalRefillContainer(stack)) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean matches(InventoryCrafting inventory, World world) {
            return !findEmptyIntradimensionalCartridge(inventory).isEmpty()
                    && !findRefillContainer(inventory).isEmpty()
                    && super.matches(inventory, world);
        }

        @Override
        @Nonnull
        public ItemStack getCraftingResult(@Nonnull InventoryCrafting inventory) {
            if (findEmptyIntradimensionalCartridge(inventory).isEmpty() || findRefillContainer(inventory).isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack output = this.result.copy();
            CartridgeItem.setTransportSolutionType(output, TransportSolutionType.INTRADIMENSIONAL);
            CartridgeItem.setCharge(output, Math.min(ConfigSetup.intradimensionalRefillAmount.get(), ConfigSetup.intradimensionalMaxCharge.get()));
            return output;
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inventory) {
            NonNullList<ItemStack> remaining =
                    NonNullList.withSize(inventory.getSizeInventory(), ItemStack.EMPTY);

            for (int i = 0; i < inventory.getSizeInventory(); ++i) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (CartridgeItem.isIntradimensionalRefillContainer(stack)) {
                    remaining.set(i, CartridgeItem.getIntradimensionalRefillRemainder(stack));
                    break;
                }
            }
            return remaining;
        }
    }
}
