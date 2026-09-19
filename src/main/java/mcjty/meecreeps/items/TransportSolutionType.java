package mcjty.meecreeps.items;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

/**
 * The four portal transport solutions carried by the portal gun cartridge.
 *
 * INTERDIMENSIONAL is the historical green solution, INTRADIMENSIONAL is the historical blue solution,
 * EXTRADIMENSIONAL is the yellow solution, and HYPERDIMENSIONAL is the red solution. All types are explicitly stored.
 */
public enum TransportSolutionType {
    INTERDIMENSIONAL("interdimensional", true, TextFormatting.GREEN),
    INTRADIMENSIONAL("intradimensional", false, TextFormatting.BLUE),
    EXTRADIMENSIONAL("extradimensional", true, TextFormatting.YELLOW),
    HYPERDIMENSIONAL("hyperdimensional", true, TextFormatting.RED);

    public static final String NBT_KEY = "transportSolutionType";
    private static final String LEGACY_BLUE_NBT_KEY = "blueFluid";

    private final String name;
    private final boolean allowsInterdimensionalTravel;
    private final TextFormatting formatting;

    TransportSolutionType(String name, boolean allowsInterdimensionalTravel, TextFormatting formatting) {
        this.name = name;
        this.allowsInterdimensionalTravel = allowsInterdimensionalTravel;
        this.formatting = formatting;
    }

    public String getName() {
        return name;
    }

    public boolean allowsInterdimensionalTravel() {
        return allowsInterdimensionalTravel;
    }

    public boolean isIntradimensional() {
        return this == INTRADIMENSIONAL;
    }

    public TextFormatting getFormatting() {
        return formatting;
    }

    public int getId() {
        return ordinal();
    }

    public static TransportSolutionType fromId(int id) {
        TransportSolutionType[] values = values();
        return id >= 0 && id < values.length ? values[id] : INTERDIMENSIONAL;
    }

    /** Reads the new explicit type and transparently migrates the old green/blue encoding. */
    public static TransportSolutionType fromNBT(NBTTagCompound tag) {
        if (tag == null) return INTERDIMENSIONAL;
        if (tag.hasKey(NBT_KEY)) {
            return fromName(tag.getString(NBT_KEY));
        }
        // Old worlds/items used absence = the green/interdimensional solution and blueFluid=true = the blue/intradimensional solution.
        return tag.getBoolean(LEGACY_BLUE_NBT_KEY) ? INTRADIMENSIONAL : INTERDIMENSIONAL;
    }

    public static TransportSolutionType fromName(String name) {
        for (TransportSolutionType type : values()) {
            if (type.name.equalsIgnoreCase(name)) return type;
        }
        return INTERDIMENSIONAL;
    }

    public static void writeToNBT(NBTTagCompound tag, TransportSolutionType type) {
        tag.setString(NBT_KEY, (type == null ? INTERDIMENSIONAL : type).getName());
        // Do not retain the old flag once a stack/entity has been written in the new format.
        tag.removeTag(LEGACY_BLUE_NBT_KEY);
    }
}
