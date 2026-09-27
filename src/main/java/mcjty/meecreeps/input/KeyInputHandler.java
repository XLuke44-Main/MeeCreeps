package mcjty.meecreeps.input;

import mcjty.lib.network.PacketSendServerCommand;
import mcjty.lib.typed.TypedMap;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.gui.GuiAskName;
import mcjty.meecreeps.gui.GuiWheel;
import mcjty.meecreeps.setup.GuiProxy;
import mcjty.meecreeps.network.MeeCreepsMessages;
import mcjty.meecreeps.render.BalloonRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class KeyInputHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack gun = getHeldGun(mc);

        if (isKeyDown(KeyBindings.repeatLastMessage)) BalloonRenderer.repeatLast();

        if (gun.isEmpty()) return;

        if (isKeyDown(KeyBindings.airPlacement) && mc.currentScreen == null) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID,
                    CommandHandler.CMD_TOGGLE_AIR_PLACEMENT, TypedMap.builder().build()));
        }

        if (isKeyDown(KeyBindings.openSavedDestinations) && mc.currentScreen == null) {
            openSavedDestinations(mc);
            return;
        }

        if (Keyboard.getEventKey() == Keyboard.KEY_INSERT && Keyboard.getEventKeyState() && mc.currentScreen == null) {
            int current = PortalGunItem.getCurrentDestination(gun);
            int index = current >= 0 && current < 24 ? current : PortalGunItem.RING_CENTER * PortalGunItem.SLOTS_PER_RING;
            GuiAskName.destinationIndex = index;
            BlockPos p = mc.player.getPosition().down();
            mc.player.openGui(MeeCreeps.instance, GuiProxy.GUI_ASKCOORDS, mc.world, p.getX(), p.getY(), p.getZ());
            return;
        }

        if (Keyboard.getEventKey() == Keyboard.KEY_SLASH && Keyboard.getEventKeyState()
                && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
            if (mc.currentScreen != null) {
                mc.displayGuiScreen(null);
                if (mc.currentScreen == null) mc.setIngameFocus();
            }
            if (PortalGunItem.isIntradimensionalActive(gun)) {
                mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(PortalGunItem.INTRADIMENSIONAL_DIMENSION_LIST_ERROR)
                        .setStyle(new net.minecraft.util.text.Style().setColor(net.minecraft.util.text.TextFormatting.BLUE)));
            } else {
                MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID,
                        CommandHandler.CMD_LIST_DIMENSIONS, TypedMap.builder().build()));
            }
            return;
        }

        if (isKeyDown(KeyBindings.fastReturn)) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID, CommandHandler.CMD_FAST_RETURN, TypedMap.builder().build()));
        }
        if (isKeyDown(KeyBindings.autoClose) && mc.currentScreen == null) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID, CommandHandler.CMD_TOGGLE_AUTOCLOSE, TypedMap.builder().build()));
        }

        if (isKeyDown(KeyBindings.closeAllPortals)) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID, CommandHandler.CMD_CLOSE_ALL, TypedMap.builder().build()));
        }
    }

    private static boolean isKeyDown(KeyBinding binding) {
        return Keyboard.getEventKeyState() && Keyboard.getEventKey() == binding.getKeyCode();
    }

    private static void openSavedDestinations(Minecraft mc) {
        BlockPos pos = mc.player.getPosition().down();
        EnumFacing side = EnumFacing.UP;
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            pos = mc.objectMouseOver.getBlockPos();
            side = mc.objectMouseOver.sideHit;
        }
        GuiWheel.selectedBlock = pos;
        GuiWheel.selectedSide = side;
        mc.player.openGui(MeeCreeps.instance, GuiProxy.GUI_WHEEL, mc.world, pos.getX(), pos.getY(), pos.getZ());
    }

    private static ItemStack getHeldGun(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getHeldItem(EnumHand.MAIN_HAND);
        if (main.getItem() == mcjty.meecreeps.items.ModItems.portalGunItem) return main;
        ItemStack off = mc.player.getHeldItem(EnumHand.OFF_HAND);
        return off.getItem() == mcjty.meecreeps.items.ModItems.portalGunItem ? off : ItemStack.EMPTY;
    }
}
