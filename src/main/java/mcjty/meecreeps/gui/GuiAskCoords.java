package mcjty.meecreeps.gui;

import mcjty.lib.network.PacketSendServerCommand;
import mcjty.lib.typed.TypedMap;
import mcjty.meecreeps.CommandHandler;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.network.MeeCreepsMessages;
import mcjty.meecreeps.teleport.DestinationParser;
import mcjty.meecreeps.teleport.DestinationBounds;
import mcjty.meecreeps.teleport.TeleportDestination;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GuiAskCoords extends GuiScreen {
    private GuiButton doneButton;
    private GuiTextField textfield;
    private int updateCounter;
    private String validationError;
    private String completionPrefix;
    private String lastCompletion;
    private List<String> playerCompletions = Collections.emptyList();
    private int completionIndex = -1;
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
        if (keyCode == Keyboard.KEY_TAB) {
            completePlayerName(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            return;
        }
        textfield.textboxKeyTyped(typedChar, keyCode);
        completionPrefix = null;
        lastCompletion = null;
        playerCompletions = Collections.emptyList();
        completionIndex = -1;
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
    private void completePlayerName(boolean backwards) {
        if (mc == null || mc.getConnection() == null || textfield == null) return;

        String current = textfield.getText().trim();
        boolean cycling = completionPrefix != null && current.equals(lastCompletion);
        if (!cycling) {
            completionPrefix = current;
            playerCompletions = new ArrayList<>();
            String prefix = current.toLowerCase(Locale.ROOT);
            for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
                if (info == null || info.getGameProfile() == null) continue;
                String name = info.getGameProfile().getName();
                if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    playerCompletions.add(name);
                }
            }
            Collections.sort(playerCompletions, String.CASE_INSENSITIVE_ORDER);
            completionIndex = -1;
        }

        if (playerCompletions.isEmpty()) return;
        int size = playerCompletions.size();
        completionIndex = backwards
                ? (completionIndex <= 0 ? size - 1 : completionIndex - 1)
                : (completionIndex + 1) % size;
        lastCompletion = playerCompletions.get(completionIndex);
        textfield.setText(lastCompletion);
        textfield.setFocused(true);
    }

    private void close() {
        mc.displayGuiScreen(null);
        if (mc.currentScreen == null) mc.setIngameFocus();
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        drawCenteredString(fontRenderer,
            "Custom Destination: X;Y;Z [;DIMID|;DIRECTION|;DIRECTION;DIMID]",
            width / 2, height / 2 - 98, 0xFFFFFF);

        drawCenteredString(fontRenderer,
            "Or enter an online player username (TAB completes names)",
            width / 2, height / 2 - 83, 0xCCCCCC);

        drawCenteredString(fontRenderer,
            "U/D/W/E/N/S = portal direction, decimals are rounded",
            width / 2, height / 2 - 68, 0xCCCCCC);

        textfield.drawTextBox();

        if (validationError != null) {
            drawCenteredString(fontRenderer, validationError,
                width / 2, height / 2 + 46, 0xFF5555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override protected void mouseClicked(int x, int y, int btn) throws IOException {
        super.mouseClicked(x, y, btn);
        textfield.mouseClicked(x, y, btn);
    }

    private String validateSyntax(String input) {

        if (input.indexOf(';') < 0) {
            return TeleportDestination.BLENDER_DIMENSION.equals(input)
                    || input.matches("[A-Za-z0-9_]{1,16}") ? null : DestinationParser.INVALID_DESTINATION;
        }
        String[] parts = input.split(";", -1);
        if (parts.length < 3 || parts.length > 5) return DestinationParser.INVALID_DESTINATION;
        try {
            for (int i = 0; i < 3; i++) {
                if (!DestinationBounds.isNumericCoordinate(parts[i])) return DestinationParser.INVALID_DESTINATION;
            }
            if (parts.length == 3) return null;
            if (parts[3].trim().isEmpty() || (parts.length == 5 && parts[4].trim().isEmpty())) return DestinationParser.INVALID_DESTINATION;
            if (parts.length == 4) {
                String fourth = parts[3].trim();
                if (fourth.length() != 1 || "UDWENSudwens".indexOf(fourth.charAt(0)) < 0) {
                    Integer.parseInt(fourth);
                }
            } else if (parts.length == 5) {
                String direction = parts[3].trim();
                if (direction.length() != 1 || "UDWENSudwens".indexOf(direction.charAt(0)) < 0) return DestinationParser.INVALID_DESTINATION;
                Integer.parseInt(parts[4].trim());
            }
            return null;
        } catch (NumberFormatException ex) {
            return DestinationParser.INVALID_DESTINATION;
        }
    }

    public static String getPlayerInput() { return player_input; }
}
