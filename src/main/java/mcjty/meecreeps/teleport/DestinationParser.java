package mcjty.meecreeps.teleport;

import net.minecraftforge.common.DimensionManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class DestinationParser {

    public static final String INVALID_DIMENSION = "INVALID DESTINATION DIMENSION!";
    public static final String INVALID_DESTINATION = "INVALID DESTINATION!";
    public static final String PLAYER_NOT_FOUND = "PLAYER NOT FOUND OR NOT ONLINE!";

    private DestinationParser() {
    }

    public static ParseResult parse(String input, World currentWorld) {
        if (input == null || currentWorld == null) {
            return ParseResult.invalid(INVALID_DESTINATION);
        }
        String[] parts = input.trim().split(";", -1);
        if (parts.length < 3 || parts.length > 5) {
            return ParseResult.invalid(INVALID_DESTINATION);
        }

        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return ParseResult.invalid(INVALID_DESTINATION);
            }

            int dimension = currentWorld.provider.getDimension();
            EnumFacing side = EnumFacing.NORTH;

            if (parts.length == 4) {
                String fourth = parts[3].trim();
                EnumFacing parsedSide = parseDirection(fourth);
                if (parsedSide != null) {
                    side = parsedSide;
                } else {
                    dimension = Integer.parseInt(fourth);
                }
            } else if (parts.length == 5) {
                side = parseDirection(parts[3].trim());
                if (side == null) {
                    return ParseResult.invalid(INVALID_DESTINATION);
                }
                dimension = Integer.parseInt(parts[4].trim());
            }

            if (!DimensionManager.isDimensionRegistered(dimension)) {
                return ParseResult.invalid(INVALID_DIMENSION);
            }

            BlockPos reference = new BlockPos(Math.round(x), Math.round(y), Math.round(z));
            return ParseResult.valid(new TeleportDestination("", dimension, reference, side, true));
        } catch (NumberFormatException | ArithmeticException e) {
            return ParseResult.invalid(INVALID_DESTINATION);
        }
    }

    private static EnumFacing parseDirection(String value) {
        if (value == null || value.length() != 1) {
            return null;
        }
        switch (Character.toUpperCase(value.charAt(0))) {
            case 'U': return EnumFacing.UP;
            case 'D': return EnumFacing.DOWN;
            case 'W': return EnumFacing.WEST;
            case 'E': return EnumFacing.EAST;
            case 'N': return EnumFacing.NORTH;
            case 'S': return EnumFacing.SOUTH;
            default: return null;
        }
    }

    public static final class ParseResult {
        private final TeleportDestination destination;
        private final String error;

        private ParseResult(TeleportDestination destination, String error) {
            this.destination = destination;
            this.error = error;
        }

        public static ParseResult valid(TeleportDestination destination) {
            return new ParseResult(destination, null);
        }

        public static ParseResult invalid(String error) {
            return new ParseResult(null, error);
        }

        public boolean isValid() {
            return destination != null;
        }

        public TeleportDestination getDestination() {
            return destination;
        }

        public String getError() {
            return error;
        }
    }
}
