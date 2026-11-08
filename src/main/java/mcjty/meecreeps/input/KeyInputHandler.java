package mcjty.meecreeps.input;

import mcjty.lib.network.PacketSendServerCommand;
import mcjty.lib.typed.TypedMap;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.items.CreepCubeItem;
import mcjty.meecreeps.items.PacketConfirmPortalSpawnerOperation;
import mcjty.meecreeps.items.PacketTogglePortalGunMode;
import mcjty.meecreeps.gui.GuiAskName;
import mcjty.meecreeps.gui.GuiWheel;
import mcjty.meecreeps.setup.GuiProxy;
import mcjty.meecreeps.teleport.PacketFireFavourite;
import mcjty.meecreeps.network.MeeCreepsMessages;
import mcjty.meecreeps.render.BalloonRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class KeyInputHandler {

    private boolean cubeLeftClickArmed = true;

    @SubscribeEvent(receiveCanceled = true)
    public void onCubeLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getSide() != Side.CLIENT) {
            return;
        }
        ItemStack stack = event.getEntityPlayer().getHeldItem(event.getHand());
        if (stack.isEmpty() || !(stack.getItem() instanceof CreepCubeItem)) {
            return;
        }

        event.setCanceled(true);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player != event.getEntityPlayer() || !cubeLeftClickArmed) {
            return;
        }
        cubeLeftClickArmed = false;

        IBlockState state = event.getWorld().getBlockState(event.getPos());
        if (!state.getMaterial().isSolid()) {
            return;
        }

        java.util.UUID cubeId = CreepCubeItem.getCubeId(stack, event.getEntityPlayer());
        if (cubeId == null) {
            return;
        }
        EnumFacing face = event.getFace() == null ? EnumFacing.UP : event.getFace();
        MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID,
                CommandHandler.CMD_CUBE_LEFT_CLICK, TypedMap.builder()
                .put(CommandHandler.PARAM_POS, event.getPos())
                .put(CommandHandler.PARAM_SIDE, face.ordinal())
                .put(CommandHandler.PARAM_CUBE_UUID, cubeId.toString()).build()));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.currentScreen != null || !mc.gameSettings.keyBindAttack.isKeyDown()) {
                cubeLeftClickArmed = true;
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && Keyboard.getEventKeyState()) {
            java.util.UUID confirmation = BalloonRenderer.takeConfirmationToken();
            if (confirmation != null) {
                boolean accepted = Keyboard.getEventKey() == Keyboard.KEY_Y
                        || Character.toLowerCase(Keyboard.getEventCharacter()) == 'y';
                MeeCreepsMessages.INSTANCE.sendToServer(new PacketConfirmPortalSpawnerOperation(confirmation, accepted));
                return;
            }
        }
        ItemStack cube = mc.player == null ? ItemStack.EMPTY : CreepCubeItem.getHeldCube(mc.player);
        if (isKeyDown(KeyBindings.terminateCube) && mc.currentScreen == null && !cube.isEmpty()) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID,
                    CommandHandler.CMD_TERMINATE_CUBE, TypedMap.builder().build()));
            return;
        }

        ItemStack gun = getHeldGun(mc);

        if (isKeyDown(KeyBindings.repeatLastMessage)) BalloonRenderer.repeatLast();

        if (isKeyDown(KeyBindings.favouriteDestination)
                && mc.currentScreen == null && !gun.isEmpty()) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketFireFavourite());
            return;
        }

        if (gun.isEmpty()) return;

        if (isKeyDown(KeyBindings.togglePortalGunMode) && mc.currentScreen == null) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketTogglePortalGunMode());
            return;
        }

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
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(MeeCreeps.MODID,
                    CommandHandler.CMD_LIST_DIMENSIONS, TypedMap.builder().build()));
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
