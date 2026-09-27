package mcjty.meecreeps.items;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

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

    public static TransportSolutionType fromNBT(NBTTagCompound tag) {
        if (tag == null) return INTERDIMENSIONAL;
        if (tag.hasKey(NBT_KEY)) {
            return fromName(tag.getString(NBT_KEY));
        }

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

        tag.removeTag(LEGACY_BLUE_NBT_KEY);
    }
}
