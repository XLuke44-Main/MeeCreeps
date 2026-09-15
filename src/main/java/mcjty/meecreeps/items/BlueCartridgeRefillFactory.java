package mcjty.meecreeps.items;

import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import javax.annotation.Nonnull;

/** Shapeless recipes that refill an empty Blue Portal Fluid cartridge. */
public class BlueCartridgeRefillFactory implements IRecipeFactory {
    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        ShapelessOreRecipe recipe = ShapelessOreRecipe.factory(context, json);
        return new RefillRecipe(new ResourceLocation("meecreeps", "blue_cartridge_refill"),
                recipe.getRecipeOutput(), recipe.getIngredients());
    }

    private static class RefillRecipe extends ShapelessRecipes {
        private final ItemStack result;

        RefillRecipe(ResourceLocation group, ItemStack result, NonNullList<net.minecraft.item.crafting.Ingredient> ingredients) {
            super(group.toString(), result, ingredients);
            this.result = result.copy();
        }

        private ItemStack findEmptyBlueCartridge(InventoryCrafting inventory) {
            for (int i = 0; i < inventory.getSizeInventory(); ++i) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof CartridgeItem
                        && CartridgeItem.isBlueFluid(stack)
                        && CartridgeItem.getCharge(stack) <= 0) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean matches(InventoryCrafting inventory, World world) {
            return !findEmptyBlueCartridge(inventory).isEmpty() && super.matches(inventory, world);
        }

        @Override
        @Nonnull
        public ItemStack getCraftingResult(@Nonnull InventoryCrafting inventory) {
            if (findEmptyBlueCartridge(inventory).isEmpty()) return ItemStack.EMPTY;
            ItemStack output = this.result.copy();
            CartridgeItem.setBlueFluid(output, true);
            CartridgeItem.setCharge(output, mcjty.meecreeps.config.ConfigSetup.maxCharge.get());
            return output;
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inventory) {
            NonNullList<ItemStack> remaining = NonNullList.withSize(inventory.getSizeInventory(), ItemStack.EMPTY);
            for (int i = 0; i < inventory.getSizeInventory(); ++i) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack.isEmpty() || stack.getItem() instanceof CartridgeItem) continue;
                if (stack.getItem() == Items.WATER_BUCKET) {
                    remaining.set(i, new ItemStack(Items.BUCKET));
                } else if (stack.getItem() == Items.POTIONITEM
                        || stack.getItem() == Items.SPLASH_POTION
                        || stack.getItem() == Items.LINGERING_POTION) {
                    remaining.set(i, new ItemStack(Items.GLASS_BOTTLE));
                }
            }
            return remaining;
        }
    }
}
