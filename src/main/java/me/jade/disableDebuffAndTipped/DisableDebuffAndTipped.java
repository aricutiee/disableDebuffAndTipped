package me.jade.disableDebuffAndTipped;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class DisableDebuffAndTipped extends JavaPlugin implements Listener {
    private static final Set<Material> POTION_ITEMS = EnumSet.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION);
    private NamespacedKey actionKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        actionKey = new NamespacedKey(this, "menu_action");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::cleanPlayerInventories, 0L, 1L);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
        } else if (!player.hasPermission("potionlimiter.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        } else openFormMenu(player);
        return true;
    }

    private void cleanPlayerInventories() {
        for (Player player : getServer().getOnlinePlayers()) cleanInventory(player.getInventory());
    }

    private void cleanInventory(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) continue;
            if (item.getType() == Material.TIPPED_ARROW || item.getType() == Material.SPECTRAL_ARROW) {
                inventory.setItem(slot, new ItemStack(Material.ARROW, item.getAmount()));
            } else if (isDebuffPotion(item)) inventory.setItem(slot, createWaterPotion(item));
            else {
                ItemStack limited = applyLimits(item);
                if (limited != item) inventory.setItem(slot, limited);
            }
        }
    }

    private ItemStack applyLimits(ItemStack original) {
        if (!POTION_ITEMS.contains(original.getType()) || !(original.getItemMeta() instanceof PotionMeta meta)) return original;
        PotionType type = meta.getBasePotionType();
        if (type == null || variantOf(type) == Variant.NORMAL) return original;
        String family = familyOf(type);
        if (isAllowed(formOf(original.getType()), family, variantOf(type))) return original;
        PotionType normal = findPotionType(family);
        if (normal == null) return original;
        ItemStack result = original.clone();
        PotionMeta resultMeta = (PotionMeta) result.getItemMeta();
        resultMeta.setBasePotionType(normal);
        result.setItemMeta(resultMeta);
        return result;
    }

    private boolean isAllowed(String form, String family, Variant variant) {
        String option = variant == Variant.LONG ? "extended" : "strong";
        return getConfig().getBoolean("limits." + form + "." + family + "." + option, true);
    }

    private boolean menuAllowed(String form, String family, Variant variant) {
        if (!form.equals("all")) return isAllowed(form, family, variant);
        return isAllowed("drinkable", family, variant) && isAllowed("splash", family, variant)
                && isAllowed("lingering", family, variant);
    }

    private void setAllowed(String form, String family, Variant variant, boolean value) {
        String option = variant == Variant.LONG ? "extended" : "strong";
        List<String> forms = form.equals("all") ? List.of("drinkable", "splash", "lingering") : List.of(form);
        for (String actualForm : forms) getConfig().set("limits." + actualForm + "." + family + "." + option, value);
        saveConfig();
    }

    @EventHandler(ignoreCancelled = true) public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (POTION_ITEMS.contains(item.getType()) && isNearTrialSpawner(event.getEntity()))
            event.getEntity().setItemStack(makeTrialPotionLong(item));
    }

    @EventHandler(ignoreCancelled = true) public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof ThrownPotion potion && isNearTrialSpawner(potion)) potion.setItem(makeTrialPotionLong(potion.getItem()));
    }

    private boolean isNearTrialSpawner(Entity entity) {
        Block center = entity.getLocation().getBlock();
        for (int x = -8; x <= 8; x++) for (int y = -8; y <= 8; y++) for (int z = -8; z <= 8; z++)
            if (center.getRelative(x, y, z).getType() == Material.TRIAL_SPAWNER) return true;
        return false;
    }

    private ItemStack makeTrialPotionLong(ItemStack original) {
        if (!POTION_ITEMS.contains(original.getType()) || !(original.getItemMeta() instanceof PotionMeta meta)) return original;
        PotionType type = meta.getBasePotionType();
        if (type == null || variantOf(type) != Variant.NORMAL) return original;
        String family = familyOf(type);
        if (!family.equals("swiftness") && !family.equals("strength")) return original;
        PotionType longer = findPotionType("long_" + family);
        if (longer == null) return original;
        ItemStack result = original.clone();
        PotionMeta resultMeta = (PotionMeta) result.getItemMeta();
        resultMeta.setBasePotionType(longer);
        result.setItemMeta(resultMeta);
        return result;
    }

    private void openFormMenu(Player player) {
        Inventory menu = Bukkit.createInventory(new MenuHolder(Page.FORMS, null, null), 27, ChatColor.DARK_GREEN + "Potion Limiter - Form");
        fill(menu);
        menu.setItem(10, button(Material.POTION, ChatColor.GREEN + "Drinkable Potions", "form:drinkable"));
        menu.setItem(12, button(Material.SPLASH_POTION, ChatColor.AQUA + "Splash Potions", "form:splash"));
        menu.setItem(14, button(Material.LINGERING_POTION, ChatColor.LIGHT_PURPLE + "Lingering Potions", "form:lingering"));
        menu.setItem(16, button(Material.BREWING_STAND, ChatColor.GOLD + "All Potion Forms", "form:all"));
        player.openInventory(menu);
    }

    private void openPotionMenu(Player player, String form) {
        List<String> families = potionFamilies();
        int size = Math.min(54, Math.max(18, ((families.size() + 8) / 9 + 1) * 9));
        Inventory menu = Bukkit.createInventory(new MenuHolder(Page.POTIONS, form, null), size, ChatColor.DARK_GREEN + "Potion Limiter - Potions");
        for (int i = 0; i < families.size() && i < size - 9; i++) {
            String family = families.get(i);
            ItemStack icon = potionIcon(form, family);
            ItemMeta meta = icon.getItemMeta();
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "potion:" + family);
            icon.setItemMeta(meta);
            menu.setItem(i, icon);
        }
        menu.setItem(size - 5, button(Material.ARROW, ChatColor.YELLOW + "Back", "back:forms"));
        player.openInventory(menu);
    }

    private void openOptionsMenu(Player player, String form, String family) {
        Inventory menu = Bukkit.createInventory(new MenuHolder(Page.OPTIONS, form, family), 27, ChatColor.DARK_GREEN + "Potion Limiter - Options");
        fill(menu);
        menu.setItem(4, potionIcon(form, family));
        menu.setItem(10, named(Material.REDSTONE, ChatColor.RED + "Extended (Redstone)", "Longer duration"));
        menu.setItem(12, statusGlass("toggle:long", menuAllowed(form, family, Variant.LONG)));
        menu.setItem(14, named(Material.GLOWSTONE_DUST, ChatColor.YELLOW + "Strong (Glowstone)", "Stronger but shorter"));
        menu.setItem(16, statusGlass("toggle:strong", menuAllowed(form, family, Variant.STRONG)));
        menu.setItem(22, button(Material.ARROW, ChatColor.YELLOW + "Back", "back:potions"));
        player.openInventory(menu);
    }

    @EventHandler(ignoreCancelled = true) public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        if (action.startsWith("form:")) openPotionMenu(player, action.substring(5));
        else if (action.startsWith("potion:")) openOptionsMenu(player, holder.form, action.substring(7));
        else if (action.equals("back:forms")) openFormMenu(player);
        else if (action.equals("back:potions")) openPotionMenu(player, holder.form);
        else if (action.startsWith("toggle:")) {
            Variant variant = action.endsWith("long") ? Variant.LONG : Variant.STRONG;
            setAllowed(holder.form, holder.family, variant, !menuAllowed(holder.form, holder.family, variant));
            openOptionsMenu(player, holder.form, holder.family);
        }
    }

    @EventHandler(ignoreCancelled = true) public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private ItemStack statusGlass(String action, boolean enabled) {
        return button(enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                enabled ? ChatColor.GREEN + "Enabled - click to disable" : ChatColor.RED + "Disabled - click to enable", action);
    }

    private ItemStack potionIcon(String form, String family) {
        ItemStack item = new ItemStack(materialForForm(form));
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        PotionType type = findPotionType(family);
        if (type != null) meta.setBasePotionType(type);
        meta.setDisplayName(ChatColor.GOLD + pretty(family) + ChatColor.GRAY + " (" + pretty(form) + ")");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(ChatColor.GRAY + lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, String action) {
        ItemStack item = named(material, name, "Click to select");
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setHideTooltip(true);
        filler.setItemMeta(meta);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    private List<String> potionFamilies() {
        List<String> result = new ArrayList<>();
        for (PotionType type : PotionType.values()) {
            String family = familyOf(type);
            if (variantOf(type) == Variant.NORMAL && (findPotionType("long_" + family) != null || findPotionType("strong_" + family) != null)
                    && !result.contains(family)) result.add(family);
        }
        result.sort(Comparator.comparing(this::pretty));
        return result;
    }

    private PotionType findPotionType(String name) {
        return Arrays.stream(PotionType.values()).filter(type -> type.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    private String familyOf(PotionType type) { return type.name().toLowerCase(Locale.ROOT).replaceFirst("^(long|strong)_", ""); }
    private Variant variantOf(PotionType type) {
        if (type.name().startsWith("LONG_")) return Variant.LONG;
        if (type.name().startsWith("STRONG_")) return Variant.STRONG;
        return Variant.NORMAL;
    }
    private Material materialForForm(String form) { return switch (form) { case "splash" -> Material.SPLASH_POTION; case "lingering" -> Material.LINGERING_POTION; default -> Material.POTION; }; }
    private String formOf(Material material) { return switch (material) { case SPLASH_POTION -> "splash"; case LINGERING_POTION -> "lingering"; default -> "drinkable"; }; }
    private String pretty(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    private boolean isDebuffPotion(ItemStack item) {
        if (!POTION_ITEMS.contains(item.getType()) || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
        PotionType base = meta.getBasePotionType();
        if (base == PotionType.WEAVING || (base != null && familyOf(base).equals("turtle_master"))) return false;
        return (base != null && base.getPotionEffects().stream().anyMatch(e -> e.getType().getCategory() == PotionEffectTypeCategory.HARMFUL))
                || meta.getCustomEffects().stream().anyMatch(e -> e.getType().getCategory() == PotionEffectTypeCategory.HARMFUL);
    }

    private ItemStack createWaterPotion(ItemStack original) {
        ItemStack water = new ItemStack(original.getType(), original.getAmount());
        PotionMeta meta = (PotionMeta) water.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        water.setItemMeta(meta);
        return water;
    }

    private enum Variant { NORMAL, LONG, STRONG }
    private enum Page { FORMS, POTIONS, OPTIONS }
    private record MenuHolder(Page page, String form, String family) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
