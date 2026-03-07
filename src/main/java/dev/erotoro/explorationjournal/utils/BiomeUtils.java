package dev.erotoro.explorationjournal.utils;

import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for biome-related operations.
 * Provides helpers for biome rarity, dimension classification, and categorization.
 *
 * <p>Uses string-based biome keys (via {@link Biome#getKey()}) for compatibility
 * with Paper 1.21+ where Biome is a registry type, not an enum.</p>
 */
public final class BiomeUtils {

    /**
     * Cache for biome rarity calculations (keyed by biome key string).
     */
    private static final Map<String, BiomeRarity> RARITY_CACHE = new ConcurrentHashMap<>();

    /**
     * Set of rare/unique biome keys (namespace-stripped, lowercase).
     */
    private static final Set<String> RARE_BIOME_KEYS = Set.of(
            // Mushroom biomes
            "mushroom_fields",

            // Jungle variants
            "bamboo_jungle",
            "sparse_jungle",

            // Extreme biomes
            "ice_spikes",
            "windswept_savanna",
            "windswept_hills",

            // Ocean depths
            "deep_dark",
            "deep_frozen_ocean",
            "deep_cold_ocean",
            "deep_ocean",
            "deep_lukewarm_ocean",

            // End biomes
            "the_end",
            "end_highlands",
            "end_midlands",
            "end_barrens",
            "small_end_islands",

            // Nether rare
            "basalt_deltas",
            "soul_sand_valley",
            "crimson_forest",
            "warped_forest",

            // Other rare
            "sunflower_plains",
            "savanna_plateau",
            "wooded_badlands",
            "eroded_badlands"
    );

    /**
     * Set of very rare biome keys that are exceptionally hard to find.
     */
    private static final Set<String> VERY_RARE_BIOME_KEYS = Set.of(
            "mushroom_fields",
            "ice_spikes",
            "eroded_badlands",
            "deep_dark",
            "the_end",
            "end_highlands",
            "end_midlands",
            "end_barrens",
            "small_end_islands"
    );

    /**
     * Set of common biome keys.
     */
    private static final Set<String> COMMON_BIOME_KEYS = Set.of(
            "plains",
            "forest",
            "dark_forest",
            "birch_forest",
            "taiga",
            "snowy_taiga",
            "snowy_plains",
            "desert",
            "savanna",
            "swamp",
            "river",
            "beach",
            "stony_shore"
    );

    /**
     * Biome categories cache (keyed by biome key string).
     */
    private static final Map<String, BiomeCategory> CATEGORY_CACHE = new ConcurrentHashMap<>();

    private BiomeUtils() {
        // Private constructor - utility class
    }

    /**
     * Extracts the simple key from a biome (namespace-stripped, lowercase).
     *
     * @param biome the biome
     * @return the simple key (e.g., "plains")
     */
    @NotNull
    public static String getSimpleKey(@NotNull Biome biome) {
        return biome.getKey().getKey();
    }

    /**
     * Gets the rarity classification for a biome.
     *
     * @param biome the biome to classify
     * @return the BiomeRarity (COMMON, UNCOMMON, RARE, VERY_RARE)
     */
    @NotNull
    public static BiomeRarity getBiomeRarity(@NotNull Biome biome) {
        Objects.requireNonNull(biome, "biome cannot be null");

        String key = getSimpleKey(biome);
        return RARITY_CACHE.computeIfAbsent(key, BiomeUtils::calculateRarity);
    }

    /**
     * Calculates the rarity for a biome based on its key.
     *
     * @param key the biome key (namespace-stripped)
     * @return the calculated BiomeRarity
     */
    @NotNull
    private static BiomeRarity calculateRarity(@NotNull String key) {
        // Check for very rare biomes
        if (VERY_RARE_BIOME_KEYS.contains(key)) {
            return BiomeRarity.VERY_RARE;
        }

        // Check for rare biomes
        if (RARE_BIOME_KEYS.contains(key)) {
            return BiomeRarity.RARE;
        }

        // Check for rare biomes by name pattern
        if (key.contains("plateau") ||
            key.contains("badlands")) {
            return BiomeRarity.RARE;
        }

        // Check for uncommon biomes
        if (key.contains("flower") ||
            key.contains("grove") ||
            key.contains("old_growth") ||
            key.contains("cherry")) {
            return BiomeRarity.UNCOMMON;
        }

        // Check for common biomes
        if (COMMON_BIOME_KEYS.contains(key)) {
            return BiomeRarity.COMMON;
        }

        // Default to uncommon for most biomes
        return BiomeRarity.UNCOMMON;
    }

    /**
     * Checks if a biome is considered rare.
     *
     * @param biome the biome to check
     * @return true if the biome is rare or very rare
     */
    public static boolean isRare(@NotNull Biome biome) {
        BiomeRarity rarity = getBiomeRarity(biome);
        return rarity == BiomeRarity.RARE || rarity == BiomeRarity.VERY_RARE;
    }

    /**
     * Gets the category for a biome.
     *
     * @param biome the biome
     * @return the BiomeCategory
     */
    @NotNull
    public static BiomeCategory getBiomeCategory(@NotNull Biome biome) {
        Objects.requireNonNull(biome, "biome cannot be null");

        String key = getSimpleKey(biome);
        return CATEGORY_CACHE.computeIfAbsent(key, BiomeUtils::categorizeBiome);
    }

    /**
     * Categorizes a biome based on its key.
     *
     * @param key the biome key (namespace-stripped)
     * @return the BiomeCategory
     */
    @NotNull
    private static BiomeCategory categorizeBiome(@NotNull String key) {
        if (key.contains("ocean") || key.contains("river") || key.contains("beach")) {
            return BiomeCategory.AQUATIC;
        }

        if (key.contains("forest") || key.contains("wood") || key.contains("jungle")) {
            return BiomeCategory.FOREST;
        }

        if (key.contains("plains") || key.contains("meadow")) {
            return BiomeCategory.PLAINS;
        }

        if (key.contains("desert") || key.contains("badlands")) {
            return BiomeCategory.ARID;
        }

        if (key.contains("snow") || key.contains("ice") || key.contains("frozen")) {
            return BiomeCategory.SNOWY;
        }

        if (key.contains("mountain") || key.contains("peak") || key.contains("windswept") ||
            key.contains("hill") || key.contains("highland")) {
            return BiomeCategory.MOUNTAIN;
        }

        if (key.contains("nether") || key.contains("basalt") || key.contains("crimson") ||
            key.contains("warped") || key.contains("soul")) {
            return BiomeCategory.NETHER;
        }

        if (key.contains("end") || key.contains("barren")) {
            return BiomeCategory.END;
        }

        if (key.contains("mushroom")) {
            return BiomeCategory.SPECIAL;
        }

        if (key.contains("swamp") || key.contains("marsh")) {
            return BiomeCategory.WETLAND;
        }

        if (key.contains("savanna")) {
            return BiomeCategory.SAVANNA;
        }

        if (key.contains("taiga") || key.contains("grove")) {
            return BiomeCategory.TAIGA;
        }

        if (key.contains("cave") || key.contains("deep_dark") || key.contains("lush")) {
            return BiomeCategory.CAVE;
        }

        // Default category
        return BiomeCategory.MISC;
    }

    /**
     * Gets a display name for a biome using its registry key.
     *
     * @param biome the biome
     * @return the display name
     */
    @NotNull
    public static String getDisplayName(@NotNull Biome biome) {
        Objects.requireNonNull(biome, "biome cannot be null");

        return formatBiomeName(getSimpleKey(biome));
    }

    /**
     * Formats a biome name key into a readable display name.
     *
     * @param biomeKey the biome key (e.g., "minecraft:plains" or "plains")
     * @return the formatted display name
     */
    @NotNull
    public static String formatBiomeName(@NotNull String biomeKey) {
        Objects.requireNonNull(biomeKey, "biomeKey cannot be null");

        // Remove namespace prefix
        String name = biomeKey.contains(":") ? biomeKey.substring(biomeKey.indexOf(':') + 1) : biomeKey;

        // Convert underscores to spaces and capitalize
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("_")) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? name : sb.toString();
    }

    /**
     * Gets the dimension name from a dimension key.
     *
     * @param dimensionKey the dimension key (e.g., "minecraft:overworld")
     * @return the display name
     */
    @NotNull
    public static String getDimensionName(@NotNull String dimensionKey) {
        Objects.requireNonNull(dimensionKey, "dimensionKey cannot be null");

        return switch (dimensionKey) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "The Nether";
            case "minecraft:the_end" -> "The End";
            default -> formatBiomeName(dimensionKey);
        };
    }

    /**
     * Checks if a biome is an ocean biome.
     *
     * @param biome the biome to check
     * @return true if the biome is oceanic
     */
    public static boolean isOcean(@NotNull Biome biome) {
        return getSimpleKey(biome).contains("ocean");
    }

    /**
     * Checks if a biome is a nether biome.
     *
     * @param biome the biome to check
     * @return true if the biome is in the Nether
     */
    public static boolean isNether(@NotNull Biome biome) {
        String key = getSimpleKey(biome);
        return key.contains("nether") ||
               key.contains("basalt") ||
               key.contains("crimson") ||
               key.contains("warped") ||
               key.contains("soul");
    }

    /**
     * Checks if a biome is an end biome.
     *
     * @param biome the biome to check
     * @return true if the biome is in the End
     */
    public static boolean isEnd(@NotNull Biome biome) {
        return getSimpleKey(biome).contains("end");
    }

    /**
     * Checks if a biome is snowy or ice-based.
     *
     * @param biome the biome to check
     * @return true if the biome has snow/ice
     */
    public static boolean isSnowy(@NotNull Biome biome) {
        String key = getSimpleKey(biome);
        return key.contains("snow") ||
               key.contains("ice") ||
               key.contains("frozen");
    }

    /**
     * Enum representing biome rarity tiers.
     */
    public enum BiomeRarity {
        COMMON("&f", "Common"),
        UNCOMMON("&a", "Uncommon"),
        RARE("&9", "Rare"),
        VERY_RARE("&5", "Very Rare");

        private final String colorCode;
        private final String displayName;

        BiomeRarity(@NotNull String colorCode, @NotNull String displayName) {
            this.colorCode = colorCode;
            this.displayName = displayName;
        }

        /**
         * Gets the color code for this rarity.
         *
         * @return the color code
         */
        @NotNull
        public String getColorCode() {
            return colorCode;
        }

        /**
         * Gets the display name for this rarity.
         *
         * @return the display name
         */
        @NotNull
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Enum representing biome categories.
     */
    public enum BiomeCategory {
        FOREST("Forest"),
        PLAINS("Plains"),
        MOUNTAIN("Mountain"),
        AQUATIC("Aquatic"),
        ARID("Arid"),
        SNOWY("Snowy"),
        NETHER("Nether"),
        END("End"),
        WETLAND("Wetland"),
        SAVANNA("Savanna"),
        TAIGA("Taiga"),
        CAVE("Cave"),
        SPECIAL("Special"),
        MISC("Miscellaneous");

        private final String displayName;

        BiomeCategory(@NotNull String displayName) {
            this.displayName = displayName;
        }

        /**
         * Gets the display name for this category.
         *
         * @return the display name
         */
        @NotNull
        public String getDisplayName() {
            return displayName;
        }
    }
}
