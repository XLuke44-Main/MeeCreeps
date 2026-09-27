package mcjty.meecreeps.api;

import net.minecraft.entity.EntityCreature;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.Random;
import java.util.function.Predicate;

public interface IMeeCreep {

    EntityCreature getEntity();

    World getWorld();

    Random getRandom();

    ItemStack addStack(ItemStack stack);

    NonNullList<ItemStack> getInventory();

    Predicate<ItemStack> getInventoryMatcher();

    boolean hasEmptyInventory();

    boolean hasStuffInInventory();

    boolean hasItem(Predicate<ItemStack> matcher);

    boolean hasItems(Predicate<ItemStack> matcher, int amount);

    boolean hasRoom(Predicate<ItemStack> matcher);

    void dropInventory();

    ItemStack consumeItem(Predicate<ItemStack> matcher, int amount);
}
