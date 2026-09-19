package mcjty.meecreeps.gui;

import io.netty.buffer.ByteBuf;
import mcjty.lib.network.PacketSendServerCommand;
import mcjty.lib.typed.TypedMap;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.network.MeeCreepsMessages;
import mcjty.meecreeps.items.PortalGunItem;
import mcjty.meecreeps.teleport.DestinationParser;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiAskCoords extends GuiScreen {
    private GuiButton doneButton;
    private GuiTextField textfield;
    private int updateCounter;
    private String validationError;
    public static String player_input;

    @Override
    public void initGui() {
        textfield = new GuiTextField(1, fontRenderer, width / 2 - 150, height / 2 - 35, 300, 20);
        textfield.setMaxStringLength(128);
        textfield.setText("");
        textfield.setFocused(true);
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        doneButton = addButton(new GuiButton(0, width / 2 - 100, height / 2 + 20, 200, 20, I18n.format("gui.done")));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        textfield.textboxKeyTyped(typedChar, keyCode);
    }

    @Override public void updateScreen() {
        updateCounter++;
        textfield.updateCursorCounter();
        player_input = textfield.getText();
    }

    @Override public void onGuiClosed() {
        if (textfield != null) textfield.setFocused(false);
        Keyboard.enableRepeatEvents(false);
    }

    @Override protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id != 0) return;
        String input = textfield.getText().trim();
        if (input.isEmpty()) {
            validationError = DestinationParser.INVALID_DESTINATION;
            return;
        }
        // Validate the syntax locally before sending. Dimension registration remains an
        // authoritative server-side check because the client may not know every registered world.
        String syntaxError = validateSyntax(input);
        if (syntaxError != null) {
            validationError = syntaxError;
            return;
        }
        validationError = null;
        int index = GuiAskName.destinationIndex;
        MeeCreepsMessages.INSTANCE.sendToServer(new PacketSendServerCommand(
                MeeCreeps.MODID, CommandHandler.CMD_SET_CUSTOM_DESTINATION,
                TypedMap.builder().put(CommandHandler.PARAM_ID, index).put(CommandHandler.PARAM_INPUT, input).build()));
        close();
    }

    private void close() {
        mc.displayGuiScreen(null);
        if (mc.currentScreen == null) mc.setIngameFocus();
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        boolean intradimensional = PortalGunItem.isIntradimensionalActive(PortalGunItem.getGun(mc.player));
        drawCenteredString(fontRenderer, intradimensional ? "Custom Destination: X;Y;Z" : "Custom Destination: X;Y;Z [;DIMID|;DIRECTION|;DIRECTION;DIMID]", width / 2, height / 2 - 65, 0xFFFFFF);
        drawCenteredString(fontRenderer, "Or enter an online player username", width / 2, height / 2 - 105, 0xCCCCCC);
        drawCenteredString(fontRenderer, "U/D/W/E/N/S = portal direction, decimals are rounded", width / 2, height / 2 - 50, 0xCCCCCC);
        if (intradimensional) {
            drawCenteredString(fontRenderer, PortalGunItem.INTRADIMENSIONAL_DIMENSION_LIST_ERROR, width / 2, height / 2 - 90, 0xFF55AAFF);
        } else {
            drawCenteredString(fontRenderer, "Press ? to list available dimensions", width / 2, height / 2 - 90, 0xCCCCCC);
        }
        textfield.drawTextBox();
        if (validationError != null) {
            drawCenteredString(fontRenderer, validationError, width / 2, height / 2 + 46, 0xFF5555);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override protected void mouseClicked(int x, int y, int btn) throws IOException {
        super.mouseClicked(x, y, btn);
        textfield.mouseClicked(x, y, btn);
    }


    private String validateSyntax(String input) {
        // A single token is a player locator. The server will resolve it against players
        // currently online; local validation only rejects obviously unusable input.
        if (input.indexOf(';') < 0) {
            return input.matches("[A-Za-z0-9_]{1,16}") ? null : DestinationParser.INVALID_DESTINATION;
        }
        String[] parts = input.split(";", -1);
        if (parts.length < 3 || parts.length > 5) return DestinationParser.INVALID_DESTINATION;
        try {
            for (int i = 0; i < 3; i++) {
                double value = Double.parseDouble(parts[i].trim());
                if (!Double.isFinite(value)) return DestinationParser.INVALID_DESTINATION;
            }
            if (parts.length == 3) return null;
            if (parts[3].trim().isEmpty() || (parts.length == 5 && parts[4].trim().isEmpty())) return DestinationParser.INVALID_DESTINATION;
            boolean intradimensional = PortalGunItem.isIntradimensionalActive(PortalGunItem.getGun(mc.player));
            if (parts.length == 4) {
                String fourth = parts[3].trim();
                if (fourth.length() != 1 || "UDWENSudwens".indexOf(fourth.charAt(0)) < 0) {
                    if (intradimensional) return PortalGunItem.INTRADIMENSIONAL_DIMENSION_LIST_ERROR;
                    Integer.parseInt(fourth);
                }
            } else if (parts.length == 5) {
                String direction = parts[3].trim();
                if (direction.length() != 1 || "UDWENSudwens".indexOf(direction.charAt(0)) < 0) return DestinationParser.INVALID_DESTINATION;
                if (intradimensional) return PortalGunItem.INTRADIMENSIONAL_DIMENSION_LIST_ERROR;
                Integer.parseInt(parts[4].trim());
            }
            return null;
        } catch (NumberFormatException ex) {
            return DestinationParser.INVALID_DESTINATION;
        }
    }

    public static String getPlayerInput() { return player_input; }
}
