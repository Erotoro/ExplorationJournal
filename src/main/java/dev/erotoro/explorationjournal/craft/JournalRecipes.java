package dev.erotoro.explorationjournal.craft;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.utils.JournalItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for creating and registering Explorer's Journal crafting recipes.
 * Contains four ShapedRecipe definitions for journal tiers I-IV.
 * 
 * <p>Recipe patterns:</p>
 * <ul>
 *   <li>Tier I: Feather, Ink Sac, Paper, Book, Leather</li>
 *   <li>Tier II: Compass, Gold Ingot, Paper, Journal I</li>
 *   <li>Tier III: Emerald, Night Vision Potion, Totem, Ender Pearl, Journal II</li>
 *   <li>Tier IV: Netherite Ingot, End Crystal, Beacon, Journal III</li>
 * </ul>
 */
public final class JournalRecipes {

    /**
     * The plugin instance for creating NamespacedKeys.
     */
    private final ExplorationJournal plugin;

    /**
     * List of all registered recipes for potential removal on disable.
     */
    private final List<NamespacedKey> registeredRecipes = new ArrayList<>();

    /**
     * NamespacedKey for Tier I journal recipe.
     */
    private final NamespacedKey tier1Key;

    /**
     * NamespacedKey for Tier II journal recipe.
     */
    private final NamespacedKey tier2Key;

    /**
     * NamespacedKey for Tier III journal recipe.
     */
    private final NamespacedKey tier3Key;

    /**
     * NamespacedKey for Tier IV journal recipe.
     */
    private final NamespacedKey tier4Key;

    /**
     * Creates a new JournalRecipes instance with the given plugin.
     *
     * @param plugin the plugin instance (must not be null)
     * @throws IllegalArgumentException if plugin is null
     */
    public JournalRecipes(@NotNull ExplorationJournal plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");

        this.tier1Key = new NamespacedKey(plugin, "journal_tier_1");
        this.tier2Key = new NamespacedKey(plugin, "journal_tier_2");
        this.tier3Key = new NamespacedKey(plugin, "journal_tier_3");
        this.tier4Key = new NamespacedKey(plugin, "journal_tier_4");
    }

    /**
     * Registers all journal crafting recipes with the server.
     * Should be called during plugin onEnable().
     */
    public void registerAllRecipes() {
        registerTier1Recipe();
        registerTier2Recipe();
        registerTier3Recipe();
        registerTier4Recipe();

        plugin.getLogger().info("Registered 4 Explorer's Journal crafting recipes.");
    }

    /**
     * Unregisters all journal crafting recipes from the server.
     * Should be called during plugin onDisable().
     */
    public void unregisterAllRecipes() {
        int count = registeredRecipes.size();
        for (NamespacedKey key : registeredRecipes) {
            Recipe recipe = Bukkit.getRecipe(key);
            if (recipe != null) {
                Bukkit.removeRecipe(key);
            }
        }
        registeredRecipes.clear();
        plugin.getLogger().info("Unregistered " + count + " Explorer's Journal recipes.");
    }

    /**
     * Registers the Tier I (Походный) journal recipe.
     * 
     * <p>Pattern:</p>
     * <pre>
     * F I F
     * P B P
     * P L P
     * </pre>
     * <p>F = Feather, I = Ink Sac, P = Paper, B = Book, L = Leather</p>
     */
    private void registerTier1Recipe() {
        ItemStack result = JournalItemBuilder.createTier1Journal(java.util.UUID.randomUUID());
        ShapedRecipe recipe = new ShapedRecipe(tier1Key, result);

        recipe.shape("FIF", "PBP", "PLP");

        recipe.setIngredient('F', Material.FEATHER);
        recipe.setIngredient('I', Material.INK_SAC);
        recipe.setIngredient('P', Material.PAPER);
        recipe.setIngredient('B', Material.BOOK);
        recipe.setIngredient('L', Material.LEATHER);

        Bukkit.addRecipe(recipe);
        registeredRecipes.add(tier1Key);
    }

    /**
     * Registers the Tier II (Картографа) journal recipe.
     * 
     * <p>Pattern:</p>
     * <pre>
     * C G C
     * G J G
     * P G P
     * </pre>
     * <p>C = Compass, G = Gold Ingot, P = Paper, J = Journal Tier I</p>
     */
    private void registerTier2Recipe() {
        ItemStack result = JournalItemBuilder.createJournal(2, java.util.UUID.randomUUID());
        ShapedRecipe recipe = new ShapedRecipe(tier2Key, result);

        recipe.shape("CGC", "GJG", "PGP");

        recipe.setIngredient('C', Material.COMPASS);
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('P', Material.PAPER);
        // J is set via ChoiceIngredient in onCraft or handled by CraftListener
        recipe.setIngredient('J', Material.WRITTEN_BOOK);

        Bukkit.addRecipe(recipe);
        registeredRecipes.add(tier2Key);
    }

    /**
     * Registers the Tier III (Исследователя) journal recipe.
     * 
     * <p>Pattern:</p>
     * <pre>
     * E P E
     * J T J
     * E E E
     * </pre>
     * <p>E = Emerald, P = Night Vision Potion, T = Totem of Undying, 
     * J = Journal Tier II, E (bottom center) = Ender Pearl</p>
     * 
     * <p>Note: The actual pattern uses Ender Pearl at bottom center.</p>
     * <pre>
     * E P E
     * J T J
     * E X E
     * </pre>
     * <p>X = Ender Pearl</p>
     */
    private void registerTier3Recipe() {
        ItemStack result = JournalItemBuilder.createJournal(3, java.util.UUID.randomUUID());
        ShapedRecipe recipe = new ShapedRecipe(tier3Key, result);

        recipe.shape("EPE", "JTJ", "EXE");

        recipe.setIngredient('E', Material.EMERALD);
        recipe.setIngredient('P', Material.POTION);
        recipe.setIngredient('J', Material.WRITTEN_BOOK);
        recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
        recipe.setIngredient('X', Material.ENDER_PEARL);

        Bukkit.addRecipe(recipe);
        registeredRecipes.add(tier3Key);
    }

    /**
     * Registers the Tier IV (Легенды) journal recipe.
     * 
     * <p>Pattern:</p>
     * <pre>
     * N C N
     * N J N
     * N B N
     * </pre>
     * <p>N = Netherite Ingot, C = End Crystal, J = Journal Tier III, B = Beacon</p>
     */
    private void registerTier4Recipe() {
        ItemStack result = JournalItemBuilder.createJournal(4, java.util.UUID.randomUUID());
        ShapedRecipe recipe = new ShapedRecipe(tier4Key, result);

        recipe.shape("NCN", "NJN", "NBN");

        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('C', Material.END_CRYSTAL);
        recipe.setIngredient('J', Material.WRITTEN_BOOK);
        recipe.setIngredient('B', Material.BEACON);

        Bukkit.addRecipe(recipe);
        registeredRecipes.add(tier4Key);
    }

    /**
     * Gets the NamespacedKey for the Tier I journal recipe.
     *
     * @return the Tier I recipe key
     */
    @NotNull
    public NamespacedKey getTier1Key() {
        return tier1Key;
    }

    /**
     * Gets the NamespacedKey for the Tier II journal recipe.
     *
     * @return the Tier II recipe key
     */
    @NotNull
    public NamespacedKey getTier2Key() {
        return tier2Key;
    }

    /**
     * Gets the NamespacedKey for the Tier III journal recipe.
     *
     * @return the Tier III recipe key
     */
    @NotNull
    public NamespacedKey getTier3Key() {
        return tier3Key;
    }

    /**
     * Gets the NamespacedKey for the Tier IV journal recipe.
     *
     * @return the Tier IV recipe key
     */
    @NotNull
    public NamespacedKey getTier4Key() {
        return tier4Key;
    }

    /**
     * Gets all registered recipe keys.
     *
     * @return list of all recipe NamespacedKeys
     */
    @NotNull
    public List<NamespacedKey> getAllRecipeKeys() {
        return new ArrayList<>(registeredRecipes);
    }

    /**
     * Creates a Tier I journal result item for the recipe.
     * This is a helper for recipe result preview without registering.
     *
     * @return a Tier I journal ItemStack (without owner data)
     */
    @NotNull
    public static ItemStack createTier1Result() {
        // Create with a placeholder UUID for recipe display
        return JournalItemBuilder.createTier1Journal(java.util.UUID.randomUUID());
    }

    /**
     * Checks if a given NamespacedKey belongs to a journal recipe.
     *
     * @param key the NamespacedKey to check
     * @return true if this is a journal recipe key
     */
    public boolean isJournalRecipe(@NotNull NamespacedKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        return key.equals(tier1Key) || key.equals(tier2Key) || 
               key.equals(tier3Key) || key.equals(tier4Key);
    }

    /**
     * Gets the journal tier from a recipe result ItemStack.
     *
     * @param result the recipe result ItemStack
     * @return the tier level (1-4), or 0 if not a journal
     */
    public static int getTierFromResult(@NotNull ItemStack result) {
        Objects.requireNonNull(result, "result cannot be null");
        return JournalItemBuilder.getJournalLevel(result);
    }
}
