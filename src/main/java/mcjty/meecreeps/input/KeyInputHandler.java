package mcjty.meecreeps.input;

import mcjty.lib.network.PacketSendServerCommand;
import mcjty.lib.typed.TypedMap;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.gui.GuiAskName;
import mcjty.meecreeps.setup.GuiProxy;
import mcjty.meecreeps.network.MeeCreepsMessages;
import mcjty.meecreeps.render.BalloonRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class KeyInputHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (KeyBindings.repeatLastMessage.isPressed()) BalloonRenderer.repeatLast();

        ItemStack gun = getHeldGun(mc);
        if (gun.isEmpty()) return;

        if (Keyboard.getEventKey() == Keyboard.KEY_INSERT && Keyboard.getEventKeyState() && mc.currentScreen == null) {
            int current = PortalGunItem.getCurrentDestination(gun);
            int index = current >= 0 && current < 24 ? current : PortalGunItem.RING_CENTER * PortalGunItem.SLOTS_PER_RING;
            GuiAskName.destinationIndex = index;
            BlockPos p = mc.player.getPosition().down();
            mc.player.openGui(MeeCreeps.instance, GuiProxy.GUI_ASKCOORDS, mc.world, p.getX(), p.getY(), p.getZ());
            return;
        }

        // Keyboard-only question-mark shortcut. Handle it here so it works both in-game and
        // while the Custom Destination GUI is open; the GUI itself does not expose a button.
        if (Keyboard.getEventKey() == Keyboard.KEY_SLASH && Keyboard.getEventKeyState()
                && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
            if (mc.currentScreen != null) {
                mc.displayGuiScreen(null);
                if (mc.currentScreen == null) mc.setIngameFocus();
            }
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID,
                    CommandHandler.CMD_LIST_DIMENSIONS, TypedMap.builder().build()));
            return;
        }

        if (KeyBindings.fastReturn.isPressed()) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID, CommandHandler.CMD_FAST_RETURN, TypedMap.builder().build()));
        }
        if (KeyBindings.autoClose.isPressed() && mc.currentScreen == null) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID, CommandHandler.CMD_TOGGLE_AUTOCLOSE, TypedMap.builder().build()));
        }
        if (KeyBindings.closeAllPortals.isPressed()) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID, CommandHandler.CMD_CLOSE_ALL, TypedMap.builder().build()));
        }
    }

    private static ItemStack getHeldGun(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getHeldItem(EnumHand.MAIN_HAND);
        if (main.getItem() == mcjty.meecreeps.items.ModItems.portalGunItem) return main;
        ItemStack off = mc.player.getHeldItem(EnumHand.OFF_HAND);
        return off.getItem() == mcjty.meecreeps.items.ModItems.portalGunItem ? off : ItemStack.EMPTY;
    }
}
