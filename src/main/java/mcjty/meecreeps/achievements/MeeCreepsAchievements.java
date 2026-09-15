package mcjty.meecreeps.achievements;

import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.config.ConfigSetup;
import mcjty.meecreeps.items.CartridgeItem;
import mcjty.meecreeps.items.ModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = MeeCreeps.MODID)
public class MeeCreepsAchievements {

    private static final ResourceLocation ROOT = new ResourceLocation(MeeCreeps.MODID, "root");
    private static final ResourceLocation PORTAL_GUN = new ResourceLocation(MeeCreeps.MODID, "the_portal_gun");
    private static final ResourceLocation LOCAL_TRAVEL = new ResourceLocation(MeeCreeps.MODID, "local_travel");
    private static final ResourceLocation DIMENSIONAL_TRAVEL = new ResourceLocation(MeeCreeps.MODID, "dimensional_travel");
    private static final ResourceLocation LOOK_AT_ME = new ResourceLocation(MeeCreeps.MODID, "look_at_me");

    private static final Map<EntityPlayerMP, RefillState[]> refillStates = new IdentityHashMap<>();

    private static class RefillState {
        private ItemStack stack;
        private int charge;

        private RefillState(ItemStack stack, int charge) {
            this.stack = stack;
            this.charge = charge;
        }
    }

    public static void awardRoot(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        grant((EntityPlayerMP) player, ROOT, "installed");
    }

    public static void awardCrafted(EntityPlayer player, ItemStack stack) {
        if (!(player instanceof EntityPlayerMP) || stack.isEmpty()) {
            return;
        }
        if (stack.getItem() == ModItems.emptyPortalGunItem) {
            grant((EntityPlayerMP) player, PORTAL_GUN, "crafted");
        } else if (stack.getItem() == ModItems.creepCubeItem) {
            grant((EntityPlayerMP) player, LOOK_AT_ME, "crafted");
        }
    }

    private static void grant(EntityPlayerMP player, ResourceLocation advancementId, String criterion) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Advancement advancement = server.getAdvancementManager().getAdvancement(advancementId);
        if (advancement != null) {
            player.getAdvancements().grantCriterion(advancement, criterion);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        awardRoot(event.player);
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        awardCrafted(event.player, event.crafting);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        RefillState[] states = refillStates.get(player);
        if (states == null) {
            states = new RefillState[2];
            refillStates.put(player, states);
        }
        states[0] = checkRefill(player, player.getHeldItemMainhand(), states[0]);
        states[1] = checkRefill(player, player.getHeldItemOffhand(), states[1]);
    }

    private static RefillState checkRefill(EntityPlayerMP player, ItemStack stack, RefillState state) {
        if (stack.isEmpty() || stack.getItem() != ModItems.cartridgeItem) {
            return null;
        }

        int maxCharge = ConfigSetup.maxCharge.get();
        int charge = CartridgeItem.getCharge(stack);
        if (state == null || state.stack != stack) {
            return new RefillState(stack, charge);
        }

        if (charge >= maxCharge && state.charge < maxCharge) {
            if (CartridgeItem.isBlueFluid(stack)) {
                grant(player, LOCAL_TRAVEL, "refilled");
            } else {
                grant(player, DIMENSIONAL_TRAVEL, "refilled");
            }
        }
        state.charge = charge;
        return state;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            refillStates.remove((EntityPlayerMP) event.player);
        }
    }
}
