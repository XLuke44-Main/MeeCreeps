package mcjty.meecreeps.config;

import mcjty.meecreeps.MeeCreeps;
import mcjty.meecreeps.items.TransportSolutionType;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class DimensionGroups {

    public enum DestinationStatus {
        ALLOWED,
        UNAVAILABLE,
        BLACKLISTED,
        GROUP_RESTRICTED
    }

    private static final String CATEGORY_GROUPS = "dimension_groups";

    private static final int[] GALAXYSPACE_DIMENSIONS = {
            -1022, -1007, -1013, -1017, -1015, -1016, -1023, -1014, -1026, -1009,
            -1005, -1024, -1012, -1008, -1018, -1021, -1006, -1127, -1126, -1128, -1129
    };
    private static final int[] GALACTICRAFT_DIMENSIONS = {-29, -30, -31, -28};

    private static final Set<String> REPORTED_WARNINGS = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());

    private DimensionGroups() {
    }

    public static boolean isDimensionAvailable(int dimensionId) {
        if (!DimensionManager.isDimensionRegistered(dimensionId)) {
            return false;
        }
        try {
            return DimensionManager.getProviderType(dimensionId) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isBlacklisted(int dimensionId) {
        if (ConfigSetup.dimensionBlacklist == null) {
            return false;
        }
        for (int blacklisted : ConfigSetup.dimensionBlacklist) {
            if (blacklisted == dimensionId) {
                return true;
            }
        }
        return false;
    }

    public static DestinationStatus checkDestination(TransportSolutionType type, int currentDimension, int targetDimension) {
        if (!isDimensionAvailable(targetDimension)) {
            return DestinationStatus.UNAVAILABLE;
        }
        if (isBlacklisted(targetDimension)) {
            return DestinationStatus.BLACKLISTED;
        }

        TransportSolutionType effectiveType = type == null ? TransportSolutionType.INTERDIMENSIONAL : type;
        if (!effectiveType.isIntradimensional() || currentDimension == targetDimension) {
            return DestinationStatus.ALLOWED;
        }

        Map<Integer, Integer> memberships = getEffectiveMemberships();
        Integer currentGroup = memberships.get(currentDimension);
        Integer targetGroup = memberships.get(targetDimension);
        if (currentGroup == null || !currentGroup.equals(targetGroup)) {
            return DestinationStatus.GROUP_RESTRICTED;
        }
        return DestinationStatus.ALLOWED;
    }

    public static boolean isDestinationAllowed(TransportSolutionType type, int currentDimension, int targetDimension) {
        return checkDestination(type, currentDimension, targetDimension) == DestinationStatus.ALLOWED;
    }

    public static List<Integer> getVisibleDimensions(TransportSolutionType type, int currentDimension) {
        List<Integer> visible = new ArrayList<>();
        TransportSolutionType effectiveType = type == null ? TransportSolutionType.INTERDIMENSIONAL : type;
        Map<Integer, Integer> memberships = effectiveType.isIntradimensional()
                ? getEffectiveMemberships() : Collections.<Integer, Integer>emptyMap();
        Integer currentGroup = memberships.get(currentDimension);

        Integer[] registered = DimensionManager.getStaticDimensionIDs();
        if (registered != null) {
            for (Integer dimensionId : registered) {
                if (dimensionId == null || !isDimensionAvailable(dimensionId) || isBlacklisted(dimensionId)) {
                    continue;
                }
                if (effectiveType.isIntradimensional() && dimensionId != currentDimension) {
                    Integer targetGroup = memberships.get(dimensionId);
                    if (currentGroup == null || !currentGroup.equals(targetGroup)) {
                        continue;
                    }
                }
                visible.add(dimensionId);
            }
        }

        if (isDimensionAvailable(currentDimension) && !isBlacklisted(currentDimension)
                && !visible.contains(currentDimension)) {
            visible.add(currentDimension);
        }

        Collections.sort(visible, Collections.reverseOrder());
        return visible;
    }

    public static String getDimensionName(int dimensionId) {
        try {
            World world = DimensionManager.getWorld(dimensionId);
            if (world != null && world.provider != null && world.provider.getDimensionType() != null) {
                String name = world.provider.getDimensionType().getName();
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            net.minecraft.world.DimensionType providerType = DimensionManager.getProviderType(dimensionId);
            if (providerType != null) {
                String name = providerType.getName();
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
        }
        return "Unknown";
    }

    public static String getDisplayName(int dimensionId, TransportSolutionType type) {
        return formatDimensionDisplayName(getDimensionName(dimensionId));
    }

    public static String formatDimensionDisplayName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) return "Unknown";

        String name = rawName.trim();
        String lower = name.toLowerCase(Locale.ROOT);

        if (lower.startsWith("planet.")) {
            String planet = name.substring(name.indexOf('.') + 1);
            planet = titleCaseDimensionWords(planet);
            return "Planet".equalsIgnoreCase(planet) ? "Planet" : planet + ":";
        }
        if (lower.startsWith("moon.")) {
            String moon = titleCaseDimensionWords(name.substring(name.indexOf('.') + 1));
            return "Moon".equalsIgnoreCase(moon) ? "The Moon" : moon;
        }

        int separator = Math.max(name.lastIndexOf('.'), name.lastIndexOf(':'));
        if (separator >= 0 && separator + 1 < name.length()) {
            name = name.substring(separator + 1);
        }
        return titleCaseDimensionWords(name);
    }

    private static String titleCaseDimensionWords(String value) {
        if (value == null) return "Unknown";
        String normalized = value.trim().replace('_', ' ').replace('-', ' ');
        if (normalized.isEmpty()) return "Unknown";

        String[] words = normalized.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            String lower = word.toLowerCase(Locale.ROOT);
            result.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) result.append(lower.substring(1));
        }
        return result.length() == 0 ? "Unknown" : result.toString();
    }

    private static Map<Integer, Integer> getEffectiveMemberships() {
        Map<Integer, Set<Integer>> configuredGroups = readConfiguredGroups();
        Map<Integer, List<Integer>> memberGroups = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> group : configuredGroups.entrySet()) {
            for (Integer dimensionId : group.getValue()) {
                List<Integer> groups = memberGroups.get(dimensionId);
                if (groups == null) {
                    groups = new ArrayList<>();
                    memberGroups.put(dimensionId, groups);
                }
                groups.add(group.getKey());
            }
        }

        Map<Integer, Integer> memberships = new HashMap<>();
        Set<Integer> ambiguousDimensions = new HashSet<>();
        for (Map.Entry<Integer, List<Integer>> entry : memberGroups.entrySet()) {
            if (entry.getValue().size() == 1) {
                memberships.put(entry.getKey(), entry.getValue().get(0));
            } else {
                ambiguousDimensions.add(entry.getKey());
                warnOnce("duplicate-dimension-" + entry.getKey(),
                        "Dimension " + entry.getKey() + " belongs to multiple MeeCreeps dimension groups "
                                + entry.getValue() + "; rejecting its group membership.");
            }
        }

        boolean galaxySpace = ConfigSetup.enableGalaxySpaceSupport;
        boolean galactiCraft = ConfigSetup.enableGalactiCraftSupport;
        boolean spaceStations = ConfigSetup.enableGalatiCraftSpaceStationSupport;
        if (galaxySpace || galactiCraft || spaceStations) {
            int targetGroup = ConfigSetup.defaultSpaceGroupId;

            addRuntimeMember(memberships, ambiguousDimensions, targetGroup, targetGroup);

            if (galaxySpace) {
                for (int dimensionId : GALAXYSPACE_DIMENSIONS) {
                    addRuntimeMember(memberships, ambiguousDimensions, targetGroup, dimensionId);
                }
            }
            if (galactiCraft) {
                for (int dimensionId : GALACTICRAFT_DIMENSIONS) {
                    addRuntimeMember(memberships, ambiguousDimensions, targetGroup, dimensionId);
                }
            }
            if (spaceStations) {
                Integer[] registered = DimensionManager.getStaticDimensionIDs();
                if (registered != null) {
                    for (Integer dimensionId : registered) {
                        if (dimensionId != null && isDimensionAvailable(dimensionId)
                                && !isBlacklisted(dimensionId)
                                && "Space Station".equals(getDimensionName(dimensionId))) {
                            addRuntimeMember(memberships, ambiguousDimensions, targetGroup, dimensionId);
                        }
                    }
                }
            }
        }
        return memberships;
    }

    private static void addRuntimeMember(Map<Integer, Integer> memberships, Set<Integer> ambiguousDimensions,
                                         int groupId, int dimensionId) {
        if (ambiguousDimensions.contains(dimensionId) || !isDimensionAvailable(dimensionId)
                || isBlacklisted(dimensionId)) {
            return;
        }
        Integer existingGroup = memberships.get(dimensionId);
        if (existingGroup != null) {
            return;
        }
        memberships.put(dimensionId, groupId);
    }

    private static Map<Integer, Set<Integer>> readConfiguredGroups() {
        Map<Integer, Set<Integer>> groups = new TreeMap<>();
        if (ConfigSetup.mainConfig == null) {
            return groups;
        }

        try {
            ConfigCategory category = ConfigSetup.mainConfig.getCategory(CATEGORY_GROUPS);
            for (Map.Entry<String, Property> entry : category.getValues().entrySet()) {
                final int groupId;
                try {
                    groupId = Integer.parseInt(entry.getKey());
                    if (!Integer.toString(groupId).equals(entry.getKey())) {
                        warnOnce("noncanonical-group-id-" + entry.getKey(),
                                "Ignoring non-canonical MeeCreeps dimension group ID '" + entry.getKey() + "'.");
                        continue;
                    }
                } catch (NumberFormatException e) {
                    warnOnce("invalid-group-id-" + entry.getKey(),
                            "Ignoring invalid MeeCreeps dimension group ID '" + entry.getKey() + "'.");
                    continue;
                }

                Property property = entry.getValue();
                if (property == null || !property.isIntList()) {
                    warnOnce("invalid-group-property-" + entry.getKey(),
                            "Ignoring MeeCreeps dimension group '" + entry.getKey()
                                    + "' because its value is not an integer list.");
                    continue;
                }

                try {
                    Set<Integer> dimensions = new HashSet<>();
                    for (int dimensionId : property.getIntList()) {
                        dimensions.add(dimensionId);
                    }
                    if (groups.containsKey(groupId)) {
                        warnOnce("duplicate-group-id-" + groupId,
                                "Ignoring duplicate MeeCreeps dimension group ID " + groupId + ".");
                        continue;
                    }
                    groups.put(groupId, dimensions);
                } catch (RuntimeException e) {
                    warnOnce("malformed-group-property-" + entry.getKey(),
                            "Ignoring malformed MeeCreeps dimension group '" + entry.getKey() + "'.");
                }
            }
        } catch (RuntimeException e) {
            warnOnce("dimension-groups-read-failure",
                    "Unable to read MeeCreeps dimension groups safely: " + e.getMessage());
        }
        return groups;
    }

    private static void warnOnce(String key, String message) {
        if (!REPORTED_WARNINGS.add(key)) {
            return;
        }
        try {
            if (MeeCreeps.setup != null) {
                MeeCreeps.setup.getLogger().warn(message);
            }
        } catch (Throwable ignored) {
        }
    }
}
