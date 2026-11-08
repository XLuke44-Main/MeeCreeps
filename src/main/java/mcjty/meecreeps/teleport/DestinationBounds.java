package mcjty.meecreeps.teleport;

import net.minecraft.util.math.BlockPos;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

public final class DestinationBounds {

    public static final int MIN_X = -29999975;
    public static final int MAX_X = 29999975;
    public static final int MIN_Y = 1;
    public static final int MAX_Y = 255;
    public static final int MIN_Z = -29999975;
    public static final int MAX_Z = 29999975;

    private static final Pattern DECIMAL_WITH_EXPONENT = Pattern.compile(
            "^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)[eE][+-]?\\d+$");
    private static final BigInteger LARGE_EXPONENT_THRESHOLD = BigInteger.valueOf(10000L);

    private DestinationBounds() {
    }
    public static int parseAndClampCoordinate(String input, int minimum, int maximum) {
        String value = input.trim();
        BigDecimal exactValue;
        try {
            exactValue = new BigDecimal(stripFloatingSuffix(value));
        } catch (NumberFormatException e) {
            Integer oversizedExponentValue = clampUnrepresentableExponent(value, minimum, maximum);
            if (oversizedExponentValue != null) {
                return oversizedExponentValue;
            }
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException("Non-finite coordinate");
            }
            if (parsed < minimum) return minimum;
            if (parsed > maximum) return maximum;
            long rounded = Math.round(parsed);
            if (rounded < minimum) return minimum;
            if (rounded > maximum) return maximum;
            return (int) rounded;
        }

        if (exactValue.compareTo(BigDecimal.valueOf(minimum)) < 0) return minimum;
        if (exactValue.compareTo(BigDecimal.valueOf(maximum)) > 0) return maximum;
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed)) {
            throw new NumberFormatException("Non-finite coordinate");
        }
        long rounded = Math.round(parsed);
        if (rounded < minimum) return minimum;
        if (rounded > maximum) return maximum;
        return (int) rounded;
    }
    public static boolean isNumericCoordinate(String input) {
        if (input == null) return false;
        String value = input.trim();
        try {
            new BigDecimal(stripFloatingSuffix(value));
            return true;
        } catch (NumberFormatException e) {
            if (hasUnrepresentableExponent(value)) return true;
            try {
                return Double.isFinite(Double.parseDouble(value));
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
    }
    public static BlockPos clampPosition(double x, double y, double z) {
        return new BlockPos(clampDouble(x, MIN_X, MAX_X),
                clampDouble(y, MIN_Y, MAX_Y), clampDouble(z, MIN_Z, MAX_Z));
    }
    public static BlockPos clampPosition(BlockPos position) {
        return new BlockPos(clampInt(position.getX(), MIN_X, MAX_X),
                clampInt(position.getY(), MIN_Y, MAX_Y),
                clampInt(position.getZ(), MIN_Z, MAX_Z));
    }

    public static boolean isWithinBounds(BlockPos position) {
        return position != null
                && position.getX() >= MIN_X && position.getX() <= MAX_X
                && position.getY() >= MIN_Y && position.getY() <= MAX_Y
                && position.getZ() >= MIN_Z && position.getZ() <= MAX_Z;
    }
    public static boolean isWithinHorizontalBounds(int x, int z) {
        return x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z;
    }
    private static double clampDouble(double value, int minimum, int maximum) {
        if (value < minimum) return minimum;
        if (value > maximum) return maximum;
        return value;
    }
    private static int clampInt(int value, int minimum, int maximum) {
        if (value < minimum) return minimum;
        if (value > maximum) return maximum;
        return value;
    }
    private static Integer clampUnrepresentableExponent(String value, int minimum, int maximum) {
        if (!hasUnrepresentableExponent(value)) return null;

        value = stripFloatingSuffix(value);
        int exponentIndex = Math.max(value.indexOf('e'), value.indexOf('E'));
        String mantissa = value.substring(0, exponentIndex);
        String exponentText = value.substring(exponentIndex + 1);
        BigInteger exponent = new BigInteger(exponentText);
        boolean negative = mantissa.startsWith("-");
        String digits = mantissa.startsWith("-") || mantissa.startsWith("+")
                ? mantissa.substring(1) : mantissa;
        boolean nonZero = false;
        for (int i = 0; i < digits.length(); i++) {
            char ch = digits.charAt(i);
            if (ch >= '1' && ch <= '9') {
                nonZero = true;
                break;
            }
        }

        if (!nonZero) {
            return clampInt(0, minimum, maximum);
        }
        if (exponent.compareTo(LARGE_EXPONENT_THRESHOLD) > 0) {
            return negative ? minimum : maximum;
        }
        if (exponent.compareTo(LARGE_EXPONENT_THRESHOLD.negate()) < 0) {
            return minimum > 0 ? minimum : 0;
        }
        return null;
    }

    private static boolean hasUnrepresentableExponent(String value) {
        if (value == null) return false;
        value = stripFloatingSuffix(value);
        if (!DECIMAL_WITH_EXPONENT.matcher(value).matches()) return false;
        int exponentIndex = Math.max(value.indexOf('e'), value.indexOf('E'));
        BigInteger exponent = new BigInteger(value.substring(exponentIndex + 1));
        return exponent.compareTo(LARGE_EXPONENT_THRESHOLD) > 0
                || exponent.compareTo(LARGE_EXPONENT_THRESHOLD.negate()) < 0;
    }

    private static String stripFloatingSuffix(String value) {
        if (value == null || value.isEmpty()) return value;
        char last = value.charAt(value.length() - 1);
        if (last == 'f' || last == 'F' || last == 'd' || last == 'D') {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
