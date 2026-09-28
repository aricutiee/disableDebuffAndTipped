package me.jade.disableDebuffAndTipped;

import org.bukkit.Material;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PotionRulesTest {
    ServerMock server;
    DisableDebuffAndTipped plugin;
    @BeforeEach void setup() {
        server=MockBukkit.mock(); plugin=spy(MockBukkit.load(DisableDebuffAndTipped.class));
        // MockBukkit does not implement the platform's potion category lookup.
        doAnswer(invocation -> java.util.Set.of(PotionEffectType.POISON,PotionEffectType.INSTANT_DAMAGE,
                PotionEffectType.SLOWNESS,PotionEffectType.WEAKNESS,PotionEffectType.WEAVING)
                .contains(invocation.getArgument(0))).when(plugin).harmful(any(PotionEffectType.class));
    }
    @AfterEach void close() { MockBukkit.unmock(); }
    ItemStack potion(Material form, PotionType type) {
        ItemStack item=new ItemStack(form,2);
        PotionMeta meta=(PotionMeta)item.getItemMeta();meta.setBasePotionType(type);
        meta.displayName(net.kyori.adventure.text.Component.text("Keep this name"));
        item.setItemMeta(meta);return item;
    }
    @Test void weavingIsEightMinutesInEveryFormAndIdempotent() {
        for(Material form:new Material[]{Material.POTION,Material.SPLASH_POTION,Material.LINGERING_POTION}) {
            ItemStack original=potion(form,PotionType.WEAVING),result=plugin.normalize(original);
            PotionMeta meta=(PotionMeta)result.getItemMeta();
            assertEquals(PotionType.WEAVING,meta.getBasePotionType());
            assertEquals(9600,meta.getCustomEffects().getFirst().getDuration());
            assertEquals(PotionEffectType.WEAVING,meta.getCustomEffects().getFirst().getType());
            assertEquals(2,result.getAmount());assertEquals(original.getItemMeta().displayName(),meta.displayName());
            assertSame(result,plugin.normalize(result));
        }
    }
    @Test void allTurtleMasterVariantsBypassBothDebuffAndUpgradeLimits() {
        for(String form:new String[]{"drinkable","splash","lingering"}) {
            plugin.getConfig().set("limits."+form+".turtle_master.extended",false);
            plugin.getConfig().set("limits."+form+".turtle_master.strong",false);
        }
        for(Material form:new Material[]{Material.POTION,Material.SPLASH_POTION,Material.LINGERING_POTION})
            for(PotionType type:new PotionType[]{PotionType.TURTLE_MASTER,PotionType.LONG_TURTLE_MASTER,PotionType.STRONG_TURTLE_MASTER}) {
                ItemStack original=potion(form,type);assertEquals(original,plugin.normalize(original));
            }
    }
    @Test void harmfulPotionsAndSpecialArrowsRemainRestricted() {
        for(PotionType type:new PotionType[]{PotionType.POISON,PotionType.HARMING,PotionType.SLOWNESS,PotionType.WEAKNESS})
            for(Material form:new Material[]{Material.POTION,Material.SPLASH_POTION,Material.LINGERING_POTION})
                assertEquals(PotionType.WATER,((PotionMeta)plugin.normalize(potion(form,type)).getItemMeta()).getBasePotionType());
        for(Material type:new Material[]{Material.TIPPED_ARROW,Material.SPECTRAL_ARROW}) {
            ItemStack result=plugin.normalize(new ItemStack(type,16));
            assertEquals(Material.ARROW,result.getType());assertEquals(16,result.getAmount());
        }
    }
    @Test void customWeavingWorksAndDoesNotExemptOtherCustomDebuffs() {
        ItemStack custom=potion(Material.POTION,PotionType.AWKWARD);
        PotionMeta meta=(PotionMeta)custom.getItemMeta();meta.addCustomEffect(new PotionEffect(PotionEffectType.WEAVING,200,0),true);
        custom.setItemMeta(meta);
        assertEquals(9600,((PotionMeta)plugin.normalize(custom).getItemMeta()).getCustomEffects().getFirst().getDuration());
        meta.addCustomEffect(new PotionEffect(PotionEffectType.POISON,200,0),true);custom.setItemMeta(meta);
        assertEquals(PotionType.WATER,((PotionMeta)plugin.normalize(custom).getItemMeta()).getBasePotionType());
    }
    @Test void ordinaryBuffsArePreservedAndExplicitLimitsStillWork() {
        ItemStack original=potion(Material.POTION,PotionType.LONG_SWIFTNESS);
        assertSame(original,plugin.normalize(original));
        plugin.getConfig().set("limits.drinkable.swiftness.extended",false);
        assertEquals(PotionType.SWIFTNESS,((PotionMeta)plugin.normalize(original).getItemMeta()).getBasePotionType());
    }
    @Test void existingInventoryAndImmediateConsumptionAreCoveredButCancelledConsumptionIsNotChanged() {
        var player=server.addPlayer();player.getInventory().setItem(0,potion(Material.POTION,PotionType.WEAVING));
        server.getScheduler().performTicks(5);
        assertEquals(9600,((PotionMeta)player.getInventory().getItem(0).getItemMeta()).getCustomEffects().getFirst().getDuration());
        var consume=new PlayerItemConsumeEvent(player,potion(Material.POTION,PotionType.WEAVING));
        server.getPluginManager().callEvent(consume);
        assertEquals(9600,((PotionMeta)consume.getItem().getItemMeta()).getCustomEffects().getFirst().getDuration());
        var cancelled=new PlayerItemConsumeEvent(player,potion(Material.POTION,PotionType.WEAVING));cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertTrue(((PotionMeta)cancelled.getItem().getItemMeta()).getCustomEffects().isEmpty());
    }
}
