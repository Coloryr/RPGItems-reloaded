/*
 *  This file is part of RPG Items.
 *
 *  RPG Items is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  RPG Items is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with RPG Items.  If not, see <http://www.gnu.org/licenses/>.
 */
package think.rpgitems;

import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import think.rpgitems.item.ItemManager;
import think.rpgitems.item.LocaleInventory;
import think.rpgitems.item.RPGItem;
import think.rpgitems.power.PowerRanged;
import think.rpgitems.power.PowerRangedOnly;
import think.rpgitems.support.WGHandler;
import think.rpgitems.support.WorldGuard;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static think.rpgitems.RPGItems.plugin;

public class Events implements Listener {

    public static HashSet<Integer> removeArrows = new HashSet<>();
    public static HashMap<Integer, Integer> rpgProjectiles = new HashMap<>();
    static HashMap<String, Integer> recipeWindows = new HashMap<>();
    public static HashMap<String, Set<Integer>> drops = new HashMap<>();
    static boolean useLocaleInv = false;
    private HashSet<LocaleInventory> localeInventories = new HashSet<>();
    private Random random = new Random();
    private SetMultimap<Class<? extends Event>, Consumer<? extends Event>> eventMap = MultimapBuilder.SortedSetMultimapBuilder.hashKeys().hashSetValues().build();

    @SuppressWarnings("unchecked")
    public <T extends Event> Events addEventListener(Class<T> clz, Consumer<T> listener) {
        eventMap.put(clz, listener);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> Events removeEventListener(Class<T> clz, Consumer<T> listener) {
        eventMap.remove(clz, listener);
        return this;
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> Set<Consumer<T>> getEventListener(Class<T> clz) {
        return eventMap.get(clz).stream().map(l -> (Consumer<T>) l).collect(Collectors.toSet());
    }

    static private boolean canStack(ItemStack a, ItemStack b) {
        if (a != null && a.getType() == Material.AIR) a = null;
        if (b != null && b.getType() == Material.AIR) b = null;
        if (a == null && b == null) return true;
        if (a != null && b != null) {
            ItemStack ap = a.clone(), bp = b.clone();
            ap.setAmount(1);
            bp.setAmount(1);
            return ap.equals(bp);
        } else {
            return false;
        }
    }

    @EventHandler
    public void onItemEnchant(EnchantItemEvent e) {
        if (ItemManager.toRPGItem(e.getItem()) != null) {
            if (!e.getEnchanter().hasPermission("rpgitem.allowenchant.new"))
                e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getCause().equals(RemoveCause.EXPLOSION))
            if (e.getEntity().hasMetadata("RPGItems.Rumble")) {
                e.getEntity().removeMetadata("RPGItems.Rumble", plugin); // Allow the entity to be broken again
                e.setCancelled(true);
            }
    }

    @EventHandler
    public void onBreak(BlockPhysicsEvent e) { // Is not triggered when the block a torch is attached to is removed
        if (e.getChangedType().equals(Material.TORCH))
            if (e.getBlock().hasMetadata("RPGItems.Torch")) {
                e.setCancelled(true); // Cancelling this does not work
                e.getBlock().removeMetadata("RPGItems.Torch", plugin);
                e.getBlock().setType(Material.AIR);
            }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.getBlock().getType().equals(Material.TORCH))
            if (e.getBlock().hasMetadata("RPGItems.Torch"))
                e.setCancelled(true);

        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        RPGItem rItem;
        if ((rItem = ItemManager.toRPGItem(item)) != null) {
            boolean can = rItem.consumeDurability(item, rItem.blockBreakingCost);
            if (!can) {
                e.setCancelled(true);
            }
            if (rItem.getDurability(item) <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInMainHand(item);
            }
        }

    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        String type = e.getEntity().getType().toString();
        Random random = new Random();
        if (drops.containsKey(type)) {
            Set<Integer> items = drops.get(type);
            Iterator<Integer> it = items.iterator();
            while (it.hasNext()) {
                int id = it.next();
                RPGItem item = ItemManager.getItemById(id);
                if (item == null) {
                    it.remove();
                    continue;
                }
                double chance = item.dropChances.get(type);
                if (random.nextDouble() < chance / 100d) {
                    e.getDrops().add(item.toItemStack());
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        final Projectile entity = e.getEntity();
        if (removeArrows.contains(entity.getEntityId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                removeArrows.remove(entity.getEntityId());
                entity.remove();
            });
        }
        if (rpgProjectiles.containsKey(entity.getEntityId())) {
            try {
                RPGItem rItem = ItemManager.getItemById(rpgProjectiles.get(entity.getEntityId()));

                if (rItem == null || !(entity.getShooter() instanceof Player))
                    return;
                Player player = (Player) entity.getShooter();
                if (player.isOnline() && !player.isDead()) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    RPGItem hItem = ItemManager.toRPGItem(item);
                    if (rItem != hItem) {
                        item = player.getInventory().getItemInOffHand();
                        hItem = ItemManager.toRPGItem(item);
                        if (rItem != hItem) {
                            return;
                        }
                    }
                    List<PowerRanged> ranged = rItem.getPower(PowerRanged.class, true);
                    if (!ranged.isEmpty()) {
                        double distance = player.getLocation().distance(e.getEntity().getLocation());
                        if (ranged.get(0).rm > distance || distance > ranged.get(0).r) {
                            return;
                        }
                    }
                    rItem.projectileHit(player, item, entity);
                }
            } finally {
                Bukkit.getScheduler().runTask(plugin, () -> rpgProjectiles.remove(entity.getEntityId()));
            }
        }
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent e) {
        e.getProjectile().setMetadata("rpgitems.force", new FixedMetadataValue(plugin, e.getForce()));
    }

    @EventHandler
    public void onProjectileFire(ProjectileLaunchEvent e) {
        ProjectileSource shooter = e.getEntity().getShooter();
        if (shooter instanceof Player) {
            Player player = (Player) shooter;
            ItemStack item = player.getInventory().getItemInMainHand();
            RPGItem rItem = ItemManager.toRPGItem(item);

            if (rItem == null) {
                item = player.getInventory().getItemInOffHand();
                rItem = ItemManager.toRPGItem(item);
                if (rItem == null) {
                    return;
                }
            }
            if (!rItem.hasPower(PowerRanged.class) && !rItem.hasPower(PowerRangedOnly.class) && item.getType() != Material.BOW && item.getType() != Material.SNOWBALL && item.getType() != Material.EGG && item.getType() != Material.POTION) {
                return;
            }
            if (!WorldGuard.canPvP(player) && !rItem.ignoreWorldGuard)
                return;
            if (!rItem.checkPermission(player, true)) {
                e.setCancelled(true);
            }
            rpgProjectiles.put(e.getEntity().getEntityId(), rItem.getID());
        }
    }

    @EventHandler
    public void onPlayerAction(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() == Action.PHYSICAL) return;
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        RPGItem rItem = ItemManager.toRPGItem(e.getItem());
        if (rItem == null) return;
        Material im = e.getMaterial();
        if (im == Material.BOW || im == Material.SNOWBALL || im == Material.EGG || im == Material.POTION || im == Material.TRIDENT || im == Material.AIR)
            return;
        if (!WorldGuard.canPvP(p) && !rItem.ignoreWorldGuard)
            return;
        if (!rItem.checkPermission(p, true)) {
            return;
        }

        Action action = e.getAction();
        if (action == Action.RIGHT_CLICK_AIR) {
            rItem.rightClick(p, e.getItem(), e.getClickedBlock());
        } else if (action == Action.RIGHT_CLICK_BLOCK &&
                           !(e.getClickedBlock().getType().isInteractable() && !p.isSneaking())) {
            rItem.rightClick(p, e.getItem(), e.getClickedBlock());
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            rItem.leftClick(p, e.getItem(), e.getClickedBlock());
        }
        RPGItem.updateItem(e.getItem());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack item;
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            item = e.getPlayer().getInventory().getItemInOffHand();
        } else {
            item = e.getItemInHand();
        }
        if (item == null)
            return;

        RPGItem rItem = ItemManager.toRPGItem(item);
        if (rItem == null)
            return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        PlayerInventory in = player.getInventory();
        for (int i = 0; i < in.getSize(); i++) {
            ItemStack item = in.getItem(i);
            if (ItemManager.toRPGItem(item) != null)
                RPGItem.updateItem(item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (ItemManager.toRPGItem(item) != null)
                RPGItem.updateItem(item);
        }
        if (WorldGuard.isEnabled() && WorldGuard.useWorldGuard && WorldGuard.useCustomFlag) {
            WGHandler.onPlayerJoin(e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        final Player p = (Player) e.getEntity();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                PlayerInventory in = p.getInventory();
                for (int i = 0; i < in.getSize(); i++) {
                    ItemStack item = in.getItem(i);
                    if (ItemManager.toRPGItem(item) != null)
                        RPGItem.updateItem(item);
                }
                for (ItemStack item : p.getInventory().getArmorContents()) {
                    if (ItemManager.toRPGItem(item) != null)
                        RPGItem.updateItem(item);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (recipeWindows.containsKey(e.getPlayer().getName())) {
            int id = recipeWindows.remove(e.getPlayer().getName());
            RPGItem item = ItemManager.getItemById(id);
            if (item.recipe == null) {
                item.recipe = new ArrayList<>();
            }
            item.recipe.clear();
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    int i = x + y * 9;
                    ItemStack it = e.getInventory().getItem(i);
                    item.recipe.add(it);
                }
            }
            item.hasRecipe = true;
            item.resetRecipe(true);
            ItemManager.save(plugin);
            e.getPlayer().sendMessage(ChatColor.AQUA + "Recipe set for " + item.getName());
        } else if (useLocaleInv && e.getView() instanceof LocaleInventory) {
            localeInventories.remove(e.getView());
            ((LocaleInventory) e.getView()).getView().close();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (useLocaleInv && e.getView() instanceof LocaleInventory) {
            LocaleInventory inv = (LocaleInventory) e.getView();
            InventoryClickEvent clickEvent = new InventoryClickEvent(inv.getView(), e.getSlotType(), e.getSlot(), e.getClick(), e.getAction());
            Bukkit.getServer().getPluginManager().callEvent(clickEvent);
            if (clickEvent.isCancelled()) {
                e.setCancelled(true);
            } else {
                switch (clickEvent.getResult()) {
                    case DEFAULT: // Can't really do this with current events
                    case ALLOW:
                        System.out.println("ok...");
                        System.out.println(inv.getView().getItem(e.getRawSlot()));
                        inv.getView().setItem(e.getRawSlot(), clickEvent.getCursor());
                        System.out.println(inv.getView().getItem(e.getRawSlot()));
                        break;
                    case DENY:
                        break;
                }
            }
            for (LocaleInventory localeInv : localeInventories) {
                if (localeInv != inv)
                    localeInv.reload();
            }
        }
        if (e.getClickedInventory() instanceof AnvilInventory) {
            if (e.getRawSlot() == 2) {
                HumanEntity p = e.getWhoClicked();
                ItemStack ind1 = e.getView().getItem(0);
                ItemStack ind2 = e.getView().getItem(1);
                if (ItemManager.toRPGItem(ind1) != null || ItemManager.toRPGItem(ind2) != null) {
                    if (!p.hasPermission("rpgitem.allowenchant.new"))
                        e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryOpen(final InventoryOpenEvent e) {
        if (e.getView() instanceof LocaleInventory || e.getInventory().getLocation() == null)
            return;
        if (e.getInventory().getType() != InventoryType.CHEST || !useLocaleInv) {
            Inventory in = e.getInventory();
            Iterator<ItemStack> it = in.iterator();
            try {
                while (it.hasNext()) {
                    ItemStack item = it.next();
                    if (ItemManager.toRPGItem(item) != null)
                        RPGItem.updateItem(item);
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                // Fix for the bug with anvils in craftbukkit
            }
        } else {
            LocaleInventory localeInv = new LocaleInventory((Player) e.getPlayer(), e.getView());
            e.setCancelled(true);
            e.getPlayer().openInventory(localeInv);
            localeInventories.add(localeInv);
        }
    }

    private double playerDamager(EntityDamageByEntityEvent e, double damage) {
        Player player = (Player) e.getDamager();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.BOW || item.getType() == Material.SNOWBALL || item.getType() == Material.EGG || item.getType() == Material.POTION || item.getType() == Material.TRIDENT)
            return damage;

        RPGItem rItem = ItemManager.toRPGItem(item);
        if (rItem == null)
            return damage;
        if (!WorldGuard.canPvP(player) && !rItem.ignoreWorldGuard)
            return damage;
        if (!rItem.checkPermission(player, true)) {
            e.setCancelled(true);
            return 0;
        }
        if (rItem.hasPower(PowerRangedOnly.class)) {
            e.setCancelled(true);
            return 0;
        }
        boolean can = rItem.consumeDurability(item, rItem.hittingCost);
        if (!can) {
            e.setCancelled(true);
            return 0;
        }
        double originDamage = damage;
        switch (rItem.damageMode) {
            case MULTIPLY:
            case FIXED:
            case ADDITIONAL:
                damage = rItem.getDamageMin() != rItem.getDamageMax() ? (rItem.getDamageMin() + random.nextInt(rItem.getDamageMax() - rItem.getDamageMin() + 1)) : rItem.getDamageMin();

                if (rItem.damageMode == RPGItem.DamageMode.MULTIPLY) {
                    damage *= originDamage;
                    break;
                }

                Collection<PotionEffect> potionEffects = player.getActivePotionEffects();
                double strength = 0, weak = 0;
                for (PotionEffect pe : potionEffects) {
                    if (pe.getType().equals(PotionEffectType.INCREASE_DAMAGE)) {
                        strength = 3 * (pe.getAmplifier() + 1);//MC 1.9+
                    }
                    if (pe.getType().equals(PotionEffectType.WEAKNESS)) {
                        weak = 4 * (pe.getAmplifier() + 1);//MC 1.9+
                    }
                }
                damage = damage + strength - weak;

                if (rItem.damageMode == RPGItem.DamageMode.ADDITIONAL) {
                    damage += originDamage;
                }
                break;
            case VANILLA:
                //no-op
                break;

        }
        if (e.getEntity() instanceof LivingEntity) {
            rItem.hit(player, item, (LivingEntity) e.getEntity(), damage);
        }
        if (rItem.getDurability(item) <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
        return damage;
    }

    private double projectileDamager(EntityDamageByEntityEvent e, double damage) {
        Projectile entity = (Projectile) e.getDamager();

        Integer projectileID = rpgProjectiles.get(entity.getEntityId());
        if (projectileID == null) return damage;
        RPGItem rItem = ItemManager.getItemById(projectileID);
        if (rItem == null || !(entity.getShooter() instanceof Player))
            return damage;
        if (!((Player) entity.getShooter()).isOnline()) {
            return damage;
        }
        Player player = (Player) entity.getShooter();
        ItemStack item = player.getInventory().getItemInMainHand();
        RPGItem hItem = ItemManager.toRPGItem(item);
        if (rItem != hItem) {
            item = player.getInventory().getItemInOffHand();
            hItem = ItemManager.toRPGItem(item);
            if (rItem != hItem) {
                return damage;
            }
        }
        double originDamage = damage;
        switch (rItem.damageMode) {
            case FIXED:
            case ADDITIONAL:
            case MULTIPLY:
                damage = rItem.getDamageMin() != rItem.getDamageMax() ? (rItem.getDamageMin() + random.nextInt(rItem.getDamageMax() - rItem.getDamageMin() + 1)) : rItem.getDamageMin();

                if (rItem.damageMode == RPGItem.DamageMode.MULTIPLY) {
                    damage *= originDamage;
                    break;
                }

                //Apply force adjustments
                if (e.getDamager().hasMetadata("rpgitems.force")) {
                    damage *= e.getDamager().getMetadata("rpgitems.force").get(0).asFloat();
                }
                if (rItem.damageMode == RPGItem.DamageMode.ADDITIONAL) {
                    damage += originDamage;
                }
                break;
            case VANILLA:
                //no-op
                break;
        }
        if (e.getEntity() instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) e.getEntity();
            rItem.hit((Player) entity.getShooter(), item, le, damage);
        }
        return damage;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent ev) {
        double damage = ev.getDamage();
        if (ev.getDamager() instanceof Player) {
            damage = playerDamager(ev, damage);
        } else if (ev.getDamager() instanceof Projectile) {
            damage = projectileDamager(ev, damage);
        }
        if (ev.getEntity() instanceof Player) {
            damage = playerHit(ev, damage);
        }
        ev.setDamage(damage);

    }

    private double playerHit(EntityDamageByEntityEvent e, double damage) {
        Player p = (Player) e.getEntity();
        if (e.isCancelled() || !WorldGuard.canPvP(p))
            return damage;
        ItemStack[] armour = p.getInventory().getArmorContents();
        boolean hasRPGItem = false;
        for (ItemStack pArmour : armour) {
            RPGItem pRItem = ItemManager.toRPGItem(pArmour);
            if (pRItem == null) {
                continue;
            } else {
                hasRPGItem = true;
            }
            if (!WorldGuard.canPvP(p) && !pRItem.ignoreWorldGuard)
                return damage;
            if (!pRItem.checkPermission(p, true)) {
                continue;
            }
            boolean can;
            if (!pRItem.hitCostByDamage) {
                can = pRItem.consumeDurability(pArmour, pRItem.hitCost);
            } else {
                can = pRItem.consumeDurability(pArmour, (int) (pRItem.hitCost * damage / 100d));
            }
            if (can && pRItem.getArmour() > 0) {
                damage -= Math.round(damage * (((double) pRItem.getArmour()) / 100d));
            }
        }
        if (hasRPGItem) {
            p.getInventory().setArmorContents(armour);
        }
        return damage;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerHitTaken(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player) {
            ev.setDamage(playerHitTaken((Player) ev.getEntity(), ev));
        }
    }

    private double playerHitTaken(Player e, EntityDamageEvent ev) {
        double ret = Double.MAX_VALUE;
        for (ItemStack item : e.getInventory().getContents()) {
            RPGItem ri = ItemManager.toRPGItem(item);
            if (ri == null) continue;
            double d = ri.takeHit(e, item, ev);
            if (d < 0) continue;
            if (d < ret) ret = d;
        }
        return ret == Double.MAX_VALUE ? ev.getDamage() : ret;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerHurt(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player) {
            Player e = (Player) ev.getEntity();
            for (ItemStack item : e.getInventory().getContents()) {
                RPGItem ri = ItemManager.toRPGItem(item);
                if (ri == null) continue;
                ri.hurt(e, item, ev);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onItemCraft(PrepareItemCraftEvent e) {
        RPGItem rpg = ItemManager.toRPGItem(e.getInventory().getResult());
        if (rpg != null) {
            if (!rpg.hasRecipe) {
                e.getInventory().setResult(new ItemStack(Material.AIR));
                return;
            }
            List<ItemStack> temp = rpg.recipe;
            if (temp != null && temp.size() == 9) {
                int idx = 0;
                for (ItemStack s : temp) {
                    if (!canStack(s, e.getInventory().getMatrix()[idx])) {
                        idx = -1;
                        break;
                    }
                    idx++;
                }
                if (idx < 0) {
                    e.getInventory().setResult(new ItemStack(Material.AIR));
                } else {
                    Random random = new Random();
                    if (random.nextInt(ItemManager.toRPGItem(e.getInventory().getResult()).recipechance) != 0) {
                        ItemStack baseitem = new ItemStack(e.getInventory().getResult().getType());
                        e.getInventory().setResult(baseitem);
                    }
                }
            } else {
                e.getInventory().setResult(new ItemStack(Material.AIR));
            }
        }
    }

    @SuppressWarnings("unchecked")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityTeleport(EntityTeleportEvent e) {
        getEventListener(EntityTeleportEvent.class).forEach(l -> l.accept(e));
    }

    @SuppressWarnings("unchecked")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent e) {
        getEventListener(PlayerMoveEvent.class).forEach(l -> l.accept(e));
    }

    @SuppressWarnings("unchecked")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerTeleportEvent e) {
        getEventListener(PlayerTeleportEvent.class).forEach(l -> l.accept(e));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (e.getItem().getType().getMaxDurability() - (e.getItem().getDurability() + e.getDamage()) <= 0) {
            if (ItemManager.toRPGItem(e.getItem()) != null) {
                e.setCancelled(true);
            }
        }
    }
}