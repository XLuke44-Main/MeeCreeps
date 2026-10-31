package mcjty.meecreeps.config;

import mcjty.lib.thirteen.ConfigSpec;
import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.MeeCreepsApi;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.logging.log4j.Level;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConfigSetup {

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_PERMISSON = "permission";
    private static final String CATEGORY_DIMENSIONS = "dimensions";
    private static final String CATEGORY_DIMENSION_GROUPS = "dimension_groups";
    private static final String DEFAULT_CONFIG_RESOURCE = "/meecreeps.cfg";

    public static Configuration mainConfig;
    private static boolean configNeedsSave;

    public static ConfigSpec.IntValue portalTimeout;
    public static ConfigSpec.IntValue portalTimeoutAfterEntry;
    public static ConfigSpec.IntValue interdimensionalMaxCharge;
    public static ConfigSpec.IntValue interdimensionalRefillAmount;
    public static ConfigSpec.IntValue intradimensionalMaxCharge;
    public static ConfigSpec.IntValue intradimensionalRefillAmount;
    public static ConfigSpec.IntValue extradimensionalMaxCharge;
    public static ConfigSpec.IntValue extradimensionalRefillAmount;
    public static ConfigSpec.IntValue hyperdimensionalMaxCharge;
    public static ConfigSpec.IntValue hyperdimensionalRefillAmount;

    public static ConfigSpec.IntValue meeCreepBoxMaxUsage;
    public static ConfigSpec.IntValue maxMeecreepsPerPlayer;

    public static ConfigSpec.DoubleValue meeCreepVolume;
    public static ConfigSpec.DoubleValue teleportVolume;

    public static ConfigSpec.IntValue messageTimeout;
    public static ConfigSpec.IntValue messageX;
    public static ConfigSpec.IntValue messageY;

    public static ConfigSpec.IntValue maxSpawnCount;
    public static ConfigSpec.IntValue maxTreeBlocks;

    public static ConfigSpec.DoubleValue delayAtHardness;
    public static ConfigSpec.DoubleValue delayFactor;

    public static Set<String> allowedActions = new HashSet<>();

    public static int[] dimensionBlacklist = new int[0];
    public static boolean enableGalaxySpaceSupport;
    public static boolean enableGalactiCraftSupport;
    public static boolean enableGalatiCraftSpaceStationSupport;
    public static int defaultSpaceGroupId;

    public static void init() {
        configNeedsSave = false;
        File configFile = new File(MeeCreeps.setup.getModConfigDir(), "meecreeps.cfg");

        try {
            installBundledConfigIfMissing(configFile);
            Configuration defaults = loadBundledDefaults();
            mainConfig = new Configuration(configFile);
            mainConfig.load();

            initGeneralConfig(mainConfig, defaults);
            initConfig(mainConfig, defaults);
        } catch (Exception e) {
            MeeCreeps.setup.getLogger().log(Level.ERROR, "Problem loading config file!", e);
        } finally {
            saveConfigIfChanged();
        }
    }
    private static void installBundledConfigIfMissing(File configFile) throws IOException {
        if (configFile.exists()) {
            return;
        }

        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create MeeCreeps config directory: " + parent);
        }

        InputStream input = ConfigSetup.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE);
        if (input == null) {
            throw new IOException("Missing bundled MeeCreeps defaults: " + DEFAULT_CONFIG_RESOURCE);
        }

        try (InputStream in = input; FileOutputStream out = new FileOutputStream(configFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
    private static Configuration loadBundledDefaults() throws IOException {
        InputStream input = ConfigSetup.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE);
        if (input == null) {
            throw new IOException("Missing bundled MeeCreeps defaults: " + DEFAULT_CONFIG_RESOURCE);
        }

        File temporaryDefaults = File.createTempFile("meecreeps-defaults-", ".cfg");
        temporaryDefaults.deleteOnExit();
        try (InputStream in = input; FileOutputStream out = new FileOutputStream(temporaryDefaults)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        Configuration defaults = new Configuration(temporaryDefaults);
        defaults.load();
        return defaults;
    }
    private static void initGeneralConfig(Configuration cfg, Configuration defaults) {
        cfg.addCustomCategoryComment(CATEGORY_GENERAL, "General configuration");

        portalTimeout = readIntValue(cfg, defaults, "portalTimeout", 1, 1000000,
                "Amount of ticks until the portalpair disappears");
        portalTimeoutAfterEntry = readIntValue(cfg, defaults, "portalTimeoutAfterEntry", 1, 1000000,
                "Amount of ticks until the portalpair disappears after an entity has gone through");
        interdimensionalMaxCharge = readIntValue(cfg, defaults, "interdimensionalMaxCharge", 1, 1000000,
                "Maximum charge in an interdimensional portal gun/cartridge");
        interdimensionalRefillAmount = readIntValue(cfg, defaults, "interdimensionalRefillAmount", 1, 1000000,
                "Charges added by one ender pearl to the interdimensional transport solution");
        intradimensionalMaxCharge = readIntValue(cfg, defaults, "intradimensionalMaxCharge", 1, 1000000,
                "Maximum charge in an intradimensional portal gun/cartridge");
        intradimensionalRefillAmount = readIntValue(cfg, defaults, "intradimensionalRefillAmount", 1, 1000000,
                "Charges added by one water container to the intradimensional transport solution");
        extradimensionalMaxCharge = readIntValue(cfg, defaults, "extradimensionalMaxCharge", 1, 1000000,
                "Maximum charge in an extradimensional portal gun/cartridge");
        extradimensionalRefillAmount = readIntValue(cfg, defaults, "extradimensionalRefillAmount", 1, 1000000,
                "Charges added by one golden ingot to the extradimensional transport solution");
        hyperdimensionalMaxCharge = readIntValue(cfg, defaults, "hyperdimensionalMaxCharge", 1, 1000000,
                "Maximum charge in a hyperdimensional portal gun/cartridge");
        hyperdimensionalRefillAmount = readIntValue(cfg, defaults, "hyperdimensionalRefillAmount", 1, 1000000,
                "Charges added by one redstone to the hyperdimensional transport solution");
        meeCreepBoxMaxUsage = readIntValue(cfg, defaults, "meeCreepBoxMaxUsage", -1, 1000000,
                "Maximum number of uses for a single MeeCreep box (-1 means unlimited)");
        maxMeecreepsPerPlayer = readIntValue(cfg, defaults, "maxMeecreepsPerPlayer", -1, 1000000,
                "Maximum number of active MeeCreeps per player (-1 means unlimited)");

        meeCreepVolume = readDoubleValue(cfg, defaults, "meeCreepVolume", 0.0, 1.0,
                "Volume of the MeeCreep");
        teleportVolume = readDoubleValue(cfg, defaults, "teleportVolume", 0.0, 1.0,
                "Volume of the Portal Gun");

        messageX = readIntValue(cfg, defaults, "messageX", -100, 100,
                "Balloon horizontal postion: 0 means centered, positive means percentage offset from left side, negative means percentage offset from right side");
        messageY = readIntValue(cfg, defaults, "messageY", -100, 100,
                "Balloon vertical position: 0 means centered, positive means percentage offset from top side, negative means percentage offset from bottom side");
        messageTimeout = readIntValue(cfg, defaults, "messageTimeout", 1, 10000,
                "Number of ticks (20 ticks per second) before the balloon message disappears");

        maxSpawnCount = readIntValue(cfg, defaults, "maxSpawnCount", 1, 200,
                "Spawn cap for an angry MeeCreep (a MeeCreep with a box)");
        maxTreeBlocks = readIntValue(cfg, defaults, "maxTreeBlocks", 1, 100000,
                "Maximum number of tree blocks a single MeeCreep can chop down");

        delayAtHardness = readDoubleValue(cfg, defaults, "delayAtHardness", 0.0, 10000000.0,
                "Delay harvest of blocks if hardness is bigger then this value");
        delayFactor = readDoubleValue(cfg, defaults, "delayFactor", 0.0, 1000.0,
                "Speed modifier for harvesting (i.e. how much faster a MeeCreep is compared to a player)");
    }

    private static ConfigSpec.IntValue readIntValue(Configuration cfg, Configuration defaults, String key,
                                                     int minimum, int maximum, String comment) {
        int defaultValue = requiredDefaultInt(defaults, CATEGORY_GENERAL, key);
        ensurePropertyTypeAndShape(cfg, CATEGORY_GENERAL, key, Property.Type.INTEGER, false);
        Property property = cfg.get(CATEGORY_GENERAL, key, defaultValue, comment, minimum, maximum);
        int value = property.getInt(defaultValue);
        if (property.getType() != Property.Type.INTEGER || property.isList()
                || !property.isIntValue() || value < minimum || value > maximum) {
            property.setToDefault();
            warnInvalid(CATEGORY_GENERAL, key);
        }
        return new ConfigSpec.IntValue(property.getInt(defaultValue));
    }

    private static ConfigSpec.DoubleValue readDoubleValue(Configuration cfg, Configuration defaults, String key,
                                                           double minimum, double maximum, String comment) {
        double defaultValue = requiredDefaultDouble(defaults, CATEGORY_GENERAL, key);
        ensurePropertyTypeAndShape(cfg, CATEGORY_GENERAL, key, Property.Type.DOUBLE, false);
        Property property = cfg.get(CATEGORY_GENERAL, key, defaultValue, comment, minimum, maximum);
        double value = property.getDouble(defaultValue);
        if (property.getType() != Property.Type.DOUBLE || property.isList()
                || !property.isDoubleValue() || Double.isNaN(value) || Double.isInfinite(value)
                || value < minimum || value > maximum) {
            property.setToDefault();
            warnInvalid(CATEGORY_GENERAL, key);
        }
        return new ConfigSpec.DoubleValue(property.getDouble(defaultValue));
    }

    private static int requiredDefaultInt(Configuration defaults, String category, String key) {
        Property property = requiredDefaultProperty(defaults, category, key);
        if (property.getType() != Property.Type.INTEGER || property.isList() || !property.isIntValue()) {
            throw new IllegalStateException("Invalid bundled integer default: " + category + "." + key);
        }
        return property.getInt(0);
    }

    private static double requiredDefaultDouble(Configuration defaults, String category, String key) {
        Property property = requiredDefaultProperty(defaults, category, key);
        double value = property.getDouble(Double.NaN);
        if (property.getType() != Property.Type.DOUBLE || property.isList() || !property.isDoubleValue()
                || Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalStateException("Invalid bundled decimal default: " + category + "." + key);
        }
        return value;
    }

    private static Property requiredDefaultProperty(Configuration defaults, String category, String key) {
        ConfigCategory defaultsCategory = defaults.getCategory(category);
        Property property = defaultsCategory == null ? null : defaultsCategory.get(key);
        if (property == null) {
            throw new IllegalStateException("Missing bundled configuration default: " + category + "." + key);
        }
        return property;
    }

    private static void initConfig(Configuration cfg, Configuration defaults) {
        initPermissionConfig(cfg, defaults);
        initDimensionConfig(cfg, defaults);
        validateDimensionGroups(cfg);
    }

    private static void initPermissionConfig(Configuration cfg, Configuration defaults) {
        cfg.addCustomCategoryComment(CATEGORY_PERMISSON, "Permission configuration");
        allowedActions.clear();

        for (MeeCreepsApi.Factory factory : MeeCreeps.api.getFactories()) {
            String key = "allowed_" + factory.getId();
            boolean defaultValue = defaultBoolean(defaults, CATEGORY_PERMISSON, key, true);
            if (getValidatedBoolean(cfg, CATEGORY_PERMISSON, key, defaultValue, "")) {
                allowedActions.add(factory.getId());
            }
        }
    }

    private static void initDimensionConfig(Configuration cfg, Configuration defaults) {
        cfg.addCustomCategoryComment(CATEGORY_DIMENSIONS, "Dimension access and optional space-mod support");
        cfg.addCustomCategoryComment(CATEGORY_DIMENSION_GROUPS,
                "Dimension groups for the intradimensional portal gun. Each property name is a numerical group ID and its value is a list of dimension IDs.");

        ensurePropertyTypeAndShape(cfg, CATEGORY_DIMENSIONS, "DimensionBlacklist", Property.Type.INTEGER, true);
        Property blacklist = cfg.get(CATEGORY_DIMENSIONS, "DimensionBlacklist", defaultIntList(defaults),
                "Dimensions in this list are inaccessible to every MeeCreeps portal gun.");
        if (blacklist.getType() != Property.Type.INTEGER || !blacklist.isList() || !blacklist.isIntList()) {
            blacklist.setToDefault();
            warnInvalid(CATEGORY_DIMENSIONS, "DimensionBlacklist");
        }
        dimensionBlacklist = blacklist.getIntList();

        enableGalaxySpaceSupport = getValidatedBoolean(cfg, CATEGORY_DIMENSIONS, "EnableGalaxySpaceSupport",
                requiredDefaultBoolean(defaults, CATEGORY_DIMENSIONS, "EnableGalaxySpaceSupport"),
                "Add available GalaxySpace dimensions to DefaultSpaceGroupID at runtime.");
        enableGalactiCraftSupport = getValidatedBoolean(cfg, CATEGORY_DIMENSIONS, "EnableGalactiCraftSupport",
                requiredDefaultBoolean(defaults, CATEGORY_DIMENSIONS, "EnableGalactiCraftSupport"),
                "Add available Galacticraft dimensions to DefaultSpaceGroupID at runtime.");
        enableGalatiCraftSpaceStationSupport = getValidatedBoolean(cfg, CATEGORY_DIMENSIONS,
                "EnableGalatiCraftSpaceStationSupport",
                requiredDefaultBoolean(defaults, CATEGORY_DIMENSIONS, "EnableGalatiCraftSpaceStationSupport"),
                "Add registered dimensions named exactly Space Station to DefaultSpaceGroupID at runtime.");

        int defaultGroupId = requiredDefaultInt(defaults, CATEGORY_DIMENSIONS, "DefaultSpaceGroupID");
        ensurePropertyTypeAndShape(cfg, CATEGORY_DIMENSIONS, "DefaultSpaceGroupID", Property.Type.INTEGER, false);
        Property groupId = cfg.get(CATEGORY_DIMENSIONS, "DefaultSpaceGroupID", defaultGroupId,
                "Dimension group ID extended by enabled GalaxySpace and Galacticraft support.",
                Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (groupId.getType() != Property.Type.INTEGER || groupId.isList() || !groupId.isIntValue()) {
            groupId.setToDefault();
            warnInvalid(CATEGORY_DIMENSIONS, "DefaultSpaceGroupID");
        }
        defaultSpaceGroupId = groupId.getInt(defaultGroupId);
    }

    private static boolean defaultBoolean(Configuration defaults, String category, String key, boolean fallback) {
        ConfigCategory defaultsCategory = defaults.getCategory(category);
        Property property = defaultsCategory == null ? null : defaultsCategory.get(key);
        if (property == null) {
            return fallback;
        }
        if (property.getType() != Property.Type.BOOLEAN || property.isList() || !property.isBooleanValue()) {
            throw new IllegalStateException("Invalid bundled boolean default: " + category + "." + key);
        }
        return property.getBoolean(fallback);
    }

    private static int[] defaultIntList(Configuration defaults) {
        Property property = requiredDefaultProperty(defaults, CATEGORY_DIMENSIONS, "DimensionBlacklist");
        if (property.getType() != Property.Type.INTEGER || !property.isList() || !property.isIntList()) {
            throw new IllegalStateException("Invalid bundled integer-list default: "
                    + CATEGORY_DIMENSIONS + ".DimensionBlacklist");
        }
        return property.getIntList();
    }

    private static boolean requiredDefaultBoolean(Configuration defaults, String category, String key) {
        Property property = requiredDefaultProperty(defaults, category, key);
        if (property.getType() != Property.Type.BOOLEAN || property.isList() || !property.isBooleanValue()) {
            throw new IllegalStateException("Invalid bundled boolean default: " + category + "." + key);
        }
        return property.getBoolean(false);
    }

    private static boolean getValidatedBoolean(Configuration cfg, String category, String key,
                                               boolean defaultValue, String comment) {
        ensurePropertyTypeAndShape(cfg, category, key, Property.Type.BOOLEAN, false);
        Property property = cfg.get(category, key, defaultValue, comment);
        if (property.getType() != Property.Type.BOOLEAN || property.isList() || !property.isBooleanValue()) {
            property.setToDefault();
            warnInvalid(category, key);
        }
        return property.getBoolean(defaultValue);
    }
    private static void validateDimensionGroups(Configuration cfg) {
        ConfigCategory category = cfg.getCategory(CATEGORY_DIMENSION_GROUPS);
        Set<String> invalidKeys = new HashSet<>();

        for (Map.Entry<String, Property> entry : category.getValues().entrySet()) {
            String key = entry.getKey();
            Property property = entry.getValue();

            boolean validId;
            try {
                int id = Integer.parseInt(key);
                validId = Integer.toString(id).equals(key);
            } catch (NumberFormatException e) {
                validId = false;
            }

            if (!validId || property == null || property.getType() != Property.Type.INTEGER
                    || !property.isList() || !property.isIntList()) {
                invalidKeys.add(key);
            }
        }
        for (String key : invalidKeys) {
            category.remove(key);
            configNeedsSave = true;
            MeeCreeps.setup.getLogger().warn("Invalid MeeCreeps configuration value '"
                    + CATEGORY_DIMENSION_GROUPS + "." + key + "'; removing the invalid group entry.");
        }
    }
    private static void ensurePropertyTypeAndShape(Configuration cfg, String categoryName, String key,
                                                    Property.Type expectedType, boolean expectedList) {
        ConfigCategory category = cfg.getCategory(categoryName);
        Property existing = category.get(key);
        if (existing == null) {
            return;
        }

        boolean wrongType = existing.getType() != null && existing.getType() != expectedType;
        boolean wrongShape = existing.isList() != expectedList;
        if (wrongType || wrongShape) {
            category.remove(key);
            warnInvalid(categoryName, key);
        }
    }

    private static void warnInvalid(String category, String key) {
        configNeedsSave = true;
        MeeCreeps.setup.getLogger().warn("Invalid MeeCreeps configuration value '" + category + "." + key
                + "'; restoring its bundled default.");
    }

    public static void postInit() {
        saveConfigIfChanged();
    }

    private static void saveConfigIfChanged() {
        if (mainConfig != null && (configNeedsSave || mainConfig.hasChanged())) {
            try {
                mainConfig.save();
                configNeedsSave = false;
            } catch (Exception e) {
                MeeCreeps.setup.getLogger().log(Level.ERROR, "Unable to save MeeCreeps configuration!", e);
            }
        }
    }
}
