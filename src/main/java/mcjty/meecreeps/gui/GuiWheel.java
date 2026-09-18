package mcjty.meecreeps.gui;

import mcjty.lib.client.RenderHelper;
import mcjty.lib.network.PacketSendServerCommand;
import mcjty.lib.typed.TypedMap;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.actions.ClientActionManager;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.network.MeeCreepsMessages;
import mcjty.meecreeps.setup.GuiProxy;
import mcjty.meecreeps.teleport.TeleportDestination;
import mcjty.meecreeps.teleport.TeleportationTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiWheel extends GuiScreen {
    private static final int RING_WIDTH = 160;
    private static final int HEIGHT = 160;
    private static final int WIDTH = RING_WIDTH * 3;

    private int guiLeft;
    private int guiTop;
    private int lastSelected = -1;
    private int lastRing = PortalGunItem.RING_CENTER;

    private static final ResourceLocation background =
            new ResourceLocation(MeeCreeps.MODID, "textures/gui/wheel.png");
    private static final ResourceLocation hilight =
            new ResourceLocation(MeeCreeps.MODID, "textures/gui/wheel_hilight.png");

    private static final ResourceLocation backgroundBlue =
            new ResourceLocation(MeeCreeps.MODID, "textures/gui/wheel_blue.png");
    private static final ResourceLocation hilightBlue =
            new ResourceLocation(MeeCreeps.MODID, "textures/gui/wheel_hilight_blue.png");

    public static BlockPos selectedBlock;
    public static EnumFacing selectedSide;

    @Override
    public void initGui() {
        super.initGui();
        guiLeft = (width - WIDTH) / 2;
        guiTop = (height - HEIGHT) / 2;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == org.lwjgl.input.Keyboard.KEY_DELETE || keyCode == org.lwjgl.input.Keyboard.KEY_BACK) {
            if (lastSelected >= 0 && lastRing >= 0) {
                ItemStack gun = PortalGunItem.getGun(mc.player);
                List<TeleportDestination> destinations = PortalGunItem.getDestinations(gun, lastRing);
                if (destinations.get(lastSelected) != null) {
                    MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(
                            MeeCreeps.MODID,
                            CommandHandler.CMD_DELETE_DESTINATION,
                            TypedMap.builder()
                                    .put(CommandHandler.PARAM_ID, encode(lastRing, lastSelected))
                                    .build()));
                }
            }
            return;
        }

        if (keyCode == org.lwjgl.input.Keyboard.KEY_INSERT) {
            int ring = lastRing;
            int slot = lastSelected;

            if (ring < 0 || slot < 0) {
                int current = PortalGunItem.getCurrentDestination(PortalGunItem.getGun(mc.player));
                if (current >= 0 && current < 24) {
                    ring = current / PortalGunItem.SLOTS_PER_RING;
                    slot = current % PortalGunItem.SLOTS_PER_RING;
                } else {
                    ring = PortalGunItem.RING_CENTER;
                    slot = 0;
                }
            }

            GuiAskName.destinationIndex = encode(ring, slot);
            BlockPos p = mc.player.getPosition().down();
            mc.player.openGui(
                    MeeCreeps.instance,
                    GuiProxy.GUI_ASKCOORDS,
                    mc.world,
                    p.getX(),
                    p.getY(),
                    p.getZ()
            );
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int ring = getRingAt(mouseX);
        if (ring < 0) {
            closeThis();
            return;
        }

        int baseX = guiLeft + ring * RING_WIDTH;
        int cx = mouseX - baseX - RING_WIDTH / 2;
        int cy = mouseY - guiTop - HEIGHT / 2;
        int q = getSelectedSection(cx, cy);

        if (q < 0) {
            closeThis();
            return;
        }

        lastRing = ring;
        lastSelected = q;

        ItemStack gun = PortalGunItem.getGun(mc.player);
        if (gun.isEmpty()) {
            closeThis();
            return;
        }

        List<TeleportDestination> destinations = PortalGunItem.getDestinations(gun, ring);
        TeleportDestination existing = destinations.get(q);

        if (existing != null && mouseButton == 0) {
            MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(
                    MeeCreeps.MODID,
                    CommandHandler.CMD_SET_CURRENT,
                    TypedMap.builder()
                            .put(CommandHandler.PARAM_ID, encode(ring, q))
                            .build()));
            closeThis();
            return;
        }

        // Creating a saved destination still uses the player's current location.
        // Right-click explicitly forces a floor/up-oriented destination before calculating its placement.
        EnumFacing destinationSide = mouseButton == 1 ? EnumFacing.UP : selectedSide;
        BlockPos bestPosition = TeleportationTools.findBestPosition(
                mc.world,
                selectedBlock,
                destinationSide
        );

        if (bestPosition == null) {
            closeThis();
            ClientActionManager.showProblem("message.meecreeps.cant_find_portal_spot");
            return;
        }

        GuiAskName.destinationIndex = encode(ring, q);
        GuiAskName.destination = new TeleportDestination(
                "",
                mc.world.provider.getDimension(),
                bestPosition,
                destinationSide
        );

        closeThis();

        mc.player.openGui(
                MeeCreeps.instance,
                GuiProxy.GUI_ASKNAME,
                mc.world,
                selectedBlock.getX(),
                selectedBlock.getY(),
                selectedBlock.getZ()
        );
    }

    private int getRingAt(int mouseX) {
        int rel = mouseX - guiLeft;
        if (rel < 0 || rel >= WIDTH) {
            return -1;
        }
        return rel / RING_WIDTH;
    }

    private static int encode(int ring, int slot) {
        return ring * PortalGunItem.SLOTS_PER_RING + slot;
    }

    private void closeThis() {
        mc.displayGuiScreen(null);
        if (mc.currentScreen == null) {
            mc.setIngameFocus();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.enableBlend();

        lastSelected = -1;
        lastRing = -1;

        ItemStack gun = PortalGunItem.getGun(mc.player);
        int currentDestination = PortalGunItem.getCurrentDestination(gun);

        boolean blueFluid = PortalGunItem.isBlueFluidActive(gun);

        ResourceLocation wheelBackground = blueFluid ? backgroundBlue : background;
        ResourceLocation wheelHighlight = blueFluid ? hilightBlue : hilight;

        for (int ring = 0; ring < 3; ring++) {
            int x = guiLeft + ring * RING_WIDTH;

            mc.getTextureManager().bindTexture(wheelBackground);
            drawTexturedModalRect(
                    x,
                    guiTop,
                    0,
                    0,
                    RING_WIDTH,
                    HEIGHT
            );

            int cx = mouseX - x - RING_WIDTH / 2;
            int cy = mouseY - guiTop - HEIGHT / 2;
            int q = getSelectedSection(cx, cy);

            if (currentDestination >= ring * PortalGunItem.SLOTS_PER_RING
                    && currentDestination < (ring + 1) * PortalGunItem.SLOTS_PER_RING) {

                int selected = currentDestination - ring * PortalGunItem.SLOTS_PER_RING;
                drawSelectedSection(x, selected, 128, wheelHighlight);
            }

            if (q >= 0) {
                lastSelected = q;
                lastRing = ring;

                // Hover highlight is drawn last so it is never hidden by the current-destination highlight.
                drawSelectedSection(x, q, 0, wheelHighlight);
            }

            drawIcons(x, ring);
        }

        if (lastSelected >= 0 && lastRing >= 0) {
            drawTooltip(lastRing, lastSelected, mouseX, mouseY);
            drawDestinationInfo(lastRing, lastSelected);
        }
    }

    private void drawIcons(int ringX, int ring) {
        List<TeleportDestination> destinations =
                PortalGunItem.getDestinations(
                        PortalGunItem.getGun(Minecraft.getMinecraft().player),
                        ring
                );

        int offset = 4;

        for (int i = 0; i < 8; i++) {
            String id = destinations.get(i) == null
                    ? ""
                    : destinations.get(i).getName();

            int offs = (i - offset + 8) % 8;
            double angle = Math.PI * 2.0 * offs / 8
                    - Math.PI / 2.0
                    + Math.PI / 8.0;

            int tx = (int) (ringX + 80 + 60 * Math.cos(angle));
            int ty = (int) (guiTop + 80 + 60 * Math.sin(angle));

            RenderHelper.renderText(
                    mc,
                    tx - mc.fontRenderer.getStringWidth(id) / 2,
                    ty - mc.fontRenderer.FONT_HEIGHT / 2,
                    id
            );
        }
    }

    private void drawDestinationInfo(int ring, int slot) {
        TeleportDestination destination =
                PortalGunItem.getDestinations(
                        PortalGunItem.getGun(mc.player),
                        ring
                ).get(slot);

        if (destination == null || mc.player == null || mc.world == null) {
            return;
        }

        double distance = calculateDestinationDistance(mc.player, destination);

        String line = destination.getPos().getX()
                + ","
                + destination.getPos().getY()
                + ","
                + destination.getPos().getZ()
                + " ("
                + formatDistance(distance)
                + " meters)";

        int playerDimension = mc.world.provider.getDimension();

        if (playerDimension != destination.getDimension()) {
            String dimensionName = getDimensionName(destination.getDimension());

            if (!dimensionName.isEmpty()) {
                line += " " + dimensionName;
            } else {
                line += " DIM" + destination.getDimension();
            }
        }

        int centerX = guiLeft + RING_WIDTH + RING_WIDTH / 2;
        drawCenteredString(
                fontRenderer,
                line,
                centerX,
                guiTop + HEIGHT + 4,
                0xFFFFFFFF
        );

        ItemStack gun = PortalGunItem.getGun(mc.player);

        if (PortalGunItem.isBlueFluidActive(gun)
                && playerDimension != destination.getDimension()) {

            drawCenteredString(
                    fontRenderer,
                    PortalGunItem.BLUE_DIMENSION_ERROR,
                    centerX,
                    guiTop + HEIGHT + 18,
                    0xFF55AAFF
            );
        }
    }

    private double calculateDestinationDistance(
            net.minecraft.entity.player.EntityPlayer player,
            TeleportDestination destination) {

        BlockPos pos = destination.getPos();

        double playerX = player.posX;
        double playerY = player.posY;
        double playerZ = player.posZ;

        double destinationX = pos.getX();
        double destinationY = pos.getY();
        double destinationZ = pos.getZ();

        int playerDimension = player.getEntityWorld().provider.getDimension();
        int destinationDimension = destination.getDimension();

        // Nether portal coordinate scaling applies to X/Z, not Y.
        // Convert the player's coordinates into the destination dimension's
        // coordinate space before calculating the Euclidean distance.
        if (playerDimension == -1 && destinationDimension == 0) {
            playerX *= 8.0D;
            playerZ *= 8.0D;
        } else if (playerDimension == 0 && destinationDimension == -1) {
            playerX /= 8.0D;
            playerZ /= 8.0D;
        }

        double dx = destinationX - playerX;
        double dy = destinationY - playerY;
        double dz = destinationZ - playerZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private String formatDistance(double distance) {
        return Long.toString(Math.round(distance));
    }

    private String getDimensionName(int dimension) {
        try {
            net.minecraft.world.World world =
                    net.minecraftforge.common.DimensionManager.getWorld(dimension);

            if (world != null
                    && world.provider != null
                    && world.provider.getDimensionType() != null) {

                String name = world.provider.getDimensionType().getName();

                if (name != null && !name.isEmpty()) {
                    return name.toLowerCase(java.util.Locale.ROOT);
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            String name =
                    net.minecraftforge.common.DimensionManager
                            .getProviderType(dimension)
                            .getName();

            return name == null
                    ? ""
                    : name.toLowerCase(java.util.Locale.ROOT);

        } catch (Throwable ignored) {
            return "";
        }
    }

    private void drawTooltip(
            int ring,
            int q,
            int mouseX,
            int mouseY) {

        List<TeleportDestination> destinations =
                PortalGunItem.getDestinations(
                        PortalGunItem.getGun(Minecraft.getMinecraft().player),
                        ring
                );

        TeleportDestination destination = destinations.get(q);
        List<String> lines = new ArrayList<>();

        if (destination == null) {
            lines.add(
                    TextFormatting.BLUE
                            + "Left-Click: "
                            + TextFormatting.WHITE
                            + "save current location"
            );
        } else {
            lines.add(
                    TextFormatting.BLUE
                            + "Left-Click: "
                            + TextFormatting.WHITE
                            + "select destination"
            );

            lines.add(
                    TextFormatting.RED
                            + "Del: "
                            + TextFormatting.WHITE
                            + "remove destination"
            );
        }

        lines.add(
                TextFormatting.AQUA
                        + "Right-Click: "
                        + TextFormatting.WHITE
                        + "save current location (vertical)"
        );

        lines.add(
                TextFormatting.GREEN
                        + "Insert: "
                        + TextFormatting.WHITE
                        + "custom coordinates (temporary)"
        );

        drawHoveringText(lines, mouseX, mouseY);
    }

    private void drawSelectedSection(
            int x,
            int q,
            int voffset,
            ResourceLocation highlightTexture) {

        mc.getTextureManager().bindTexture(highlightTexture);

        int top = guiTop;

        // The logical destination slot order is rotated by four positions
        // relative to the wheel texture. This is the same mapping used
        // by the original wheel.
        switch ((q - 4 + 8) % 8) {
            case 0:
                drawTexturedModalRect(
                        x + 78,
                        top,
                        0,
                        voffset,
                        63,
                        63
                );
                break;

            case 1:
                drawTexturedModalRect(
                        x + 107,
                        top + 22,
                        64,
                        voffset,
                        63,
                        63
                );
                break;

            case 2:
                drawTexturedModalRect(
                        x + 107,
                        top + 78,
                        128,
                        voffset,
                        63,
                        63
                );
                break;

            case 3:
                drawTexturedModalRect(
                        x + 78,
                        top + 108,
                        192,
                        voffset,
                        63,
                        63
                );
                break;

            case 4:
                drawTexturedModalRect(
                        x + 23,
                        top + 107,
                        0,
                        voffset + 64,
                        63,
                        63
                );
                break;

            case 5:
                drawTexturedModalRect(
                        x,
                        top + 78,
                        64,
                        voffset + 64,
                        63,
                        63
                );
                break;

            case 6:
                drawTexturedModalRect(
                        x,
                        top + 22,
                        128,
                        voffset + 64,
                        63,
                        63
                );
                break;

            case 7:
                drawTexturedModalRect(
                        x + 22,
                        top,
                        192,
                        voffset + 64,
                        63,
                        63
                );
                break;
        }
    }

    private int getSelectedSection(int cx, int cy) {
        double dist = Math.sqrt(cx * cx + cy * cy);

        if (dist < 37 || dist > 80) {
            return -1;
        }

        int q;

        if (cx >= 0 && cy < 0 && Math.abs(cx) < Math.abs(cy)) {
            q = 0;
        } else if (cx >= 0 && cy < 0) {
            q = 1;
        } else if (cx >= 0 && cy >= 0 && Math.abs(cx) >= Math.abs(cy)) {
            q = 2;
        } else if (cx >= 0 && cy >= 0) {
            q = 3;
        } else if (cx < 0 && cy >= 0 && Math.abs(cx) < Math.abs(cy)) {
            q = 4;
        } else if (cx < 0 && cy >= 0) {
            q = 5;
        } else if (cx < 0 && cy < 0 && Math.abs(cx) >= Math.abs(cy)) {
            q = 6;
        } else {
            q = 7;
        }

        return (q + 4) % 8;
    }
}
