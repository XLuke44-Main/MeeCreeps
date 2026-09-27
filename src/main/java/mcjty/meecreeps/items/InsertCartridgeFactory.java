package mcjty.meecreeps.items;

import com.google.gson.JsonObject;
import mcjty.meecreeps.MeeCreeps;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import javax.annotation.Nonnull;

public class InsertCartridgeFactory implements IRecipeFactory {
    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        ShapelessOreRecipe recipe = ShapelessOreRecipe.factory(context, json);
        return new InsertCartridgeRecipe(new ResourceLocation(MeeCreeps.MODID, "insert_cartridge_factory"),
                recipe.getRecipeOutput(), recipe.getIngredients());
    }

    public static class InsertCartridgeRecipe extends ShapelessRecipes {
        private final ItemStack result;

        public InsertCartridgeRecipe(ResourceLocation group, ItemStack result, net.minecraft.util.NonNullList<net.minecraft.item.crafting.Ingredient> ingredients) {
            super(group.toString(), result, ingredients);
            this.result = result.copy();
        }

        @Override
        public boolean matches(InventoryCrafting inventory, net.minecraft.world.World worldIn) {
            boolean foundCartridge = false;
            boolean foundPortalGun = false;

            for (int i = 0; i < inventory.getSizeInventory(); ++i) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }

                if (stack.getItem() instanceof CartridgeItem) {
                    if (foundCartridge) return false;
                    foundCartridge = true;
                } else if (stack.getItem() instanceof EmptyPortalGunItem) {
                    if (foundPortalGun) return false;
                    foundPortalGun = true;
                } else {
                    return false;
                }
            }

            return foundCartridge && foundPortalGun;
        }

        @Override
        @Nonnull
        public ItemStack getCraftingResult(@Nonnull InventoryCrafting var1) {
            ItemStack newOutput = this.result.copy();

            ItemStack cartridge = ItemStack.EMPTY;
            ItemStack portalgun = ItemStack.EMPTY;

            for (int i = 0; i < var1.getSizeInventory(); ++i) {
                ItemStack stack = var1.getStackInSlot(i);

                if (!stack.isEmpty()) {
                    if (stack.getItem() instanceof CartridgeItem) {
                        cartridge = stack;
                    } else if (stack.getItem() instanceof EmptyPortalGunItem) {
                        portalgun = stack;
                    }
                }
            }

            if (portalgun.hasTagCompound()) {
                newOutput.setTagCompound(portalgun.getTagCompound().copy());
            }
            if (!cartridge.isEmpty()) {
                int charge = CartridgeItem.getCharge(cartridge);
                PortalGunItem.setCharge(newOutput, charge);
                PortalGunItem.setTransportSolutionType(newOutput, CartridgeItem.getTransportSolutionType(cartridge));
            }

            return newOutput;
        }
    }
}