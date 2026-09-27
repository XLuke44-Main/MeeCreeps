package mcjty.meecreeps.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public interface IDesiredBlock {

    default int getPass() { return 0; }

    default boolean isOptional() { return false; }

    int getAmount();

    String getName();

    Predicate<ItemStack> getMatcher();

    Predicate<IBlockState> getStateMatcher();
}
