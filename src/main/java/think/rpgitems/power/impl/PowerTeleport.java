package think.rpgitems.power.impl;

import cat.nyaa.nyaacore.Pair;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import think.rpgitems.I18n;
import think.rpgitems.RPGItems;
import think.rpgitems.power.*;

import static think.rpgitems.power.Utils.checkCooldown;

/**
 * Power teleport.
 * <p>
 * The teleport power will teleport you
 * in the direction you're looking in
 * or to the place where the projectile hit
 * with maximum distance of {@link #distance} blocks
 * </p>
 */
@SuppressWarnings("WeakerAccess")
@PowerMeta(defaultTrigger = {"RIGHT_CLICK", "PROJECTILE_HIT"}, generalInterface = PowerPlain.class, implClass = PowerTeleport.Impl.class)
public class PowerTeleport extends BasePower {

    @Property(order = 1)
    private int distance = 5;
    @Property(order = 0)
    private long cooldown = 0;
    @Property
    private int cost = 0;

    @Property
    private TargetMode targetMode = TargetMode.DEFAULT;

    public class Impl implements PowerSneak, PowerLeftClick, PowerSprint, PowerRightClick, PowerProjectileHit, PowerPlain, PowerBowShoot {

        @Override
        public PowerResult<Void> rightClick(Player player, ItemStack stack, PlayerInteractEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Void> leftClick(Player player, ItemStack stack, PlayerInteractEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Void> sneak(Player player, ItemStack stack, PlayerToggleSneakEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Void> sprint(Player player, ItemStack stack, PlayerToggleSprintEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Float> bowShoot(Player player, ItemStack itemStack, EntityShootBowEvent e) {
            return fire(player, itemStack).with(e.getForce());
        }

        public PowerResult<Void> fire(Player player, ItemStack stack) {
            if (!checkCooldown(getPower(), player, getCooldown(), true, true)) return PowerResult.cd();
            if (!getItem().consumeDurability(stack, getCost())) return PowerResult.cost();
            World world = player.getWorld();
            Location location = player.getLocation();
            Location start = location.clone().add(new Vector(0, 1.6, 0));
            Location eyeLocation = player.getEyeLocation();
            Vector direction = eyeLocation.getDirection();
            Block lastSafe = world.getBlockAt(start);
            Location newLoc = lastSafe.getLocation();

            boolean ignorePassable = true;
            switch (getTargetMode()) {
                case RAY_TRACING_EXACT:
                case RAY_TRACING_EXACT_SWEEP:
                    ignorePassable = false;
                case RAY_TRACING:
                case RAY_TRACING_SWEEP: {
                    RayTraceResult result = player.getWorld().rayTraceBlocks(eyeLocation, direction, getDistance(), FluidCollisionMode.NEVER, ignorePassable);
                    Block firstUnsafe = result == null ? null : result.getHitBlock();
                    if (firstUnsafe == null) {
                        newLoc = location.add(direction.clone().multiply(getDistance()));
                        break;
                    } else {
                        newLoc = result.getHitPosition().toLocation(world);
                    }
                    if (getTargetMode() == TargetMode.RAY_TRACING || getTargetMode() == TargetMode.RAY_TRACING_EXACT) {
                        break;
                    }
                    Vector move = newLoc.toVector().subtract(location.toVector());
                    Pair<Vector, Vector> sweep = Utils.sweep(player.getBoundingBox(), BoundingBox.of(firstUnsafe), move);
                    if (sweep != null) {
                        newLoc = location.clone().add(sweep.getKey());
                    }
                    break;

                }
                case DEFAULT: {
                    try {
                        BlockIterator bi = new BlockIterator(player, getDistance());
                        while (bi.hasNext()) {
                            Block block = bi.next();
                            if (!block.getType().isSolid() || (block.getType() == Material.AIR)) {
                                lastSafe = block;
                            } else {
                                break;
                            }
                        }
                    } catch (IllegalStateException ex) {
                        ex.printStackTrace();
                        RPGItems.logger.info("This exception may be harmless");
                    }
                    newLoc = lastSafe.getLocation();
                    break;
                }
            }

            newLoc.setPitch(eyeLocation.getPitch());
            newLoc.setYaw(eyeLocation.getYaw());
            Vector velocity = player.getVelocity();
            boolean gliding = player.isGliding();
            player.teleport(newLoc);
            if (gliding) {
                player.setVelocity(velocity);
            }
            world.playEffect(newLoc, Effect.ENDER_SIGNAL, 0);
            world.playSound(newLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.3f);
            return PowerResult.ok();
        }

        @Override
        public PowerResult<Void> projectileHit(Player player, ItemStack stack, ProjectileHitEvent event) {
            if (!checkCooldown(getPower(), player, getCooldown(), true, true)) return PowerResult.cd();
            if (!getItem().consumeDurability(stack, getCost())) return PowerResult.cost();
            World world = player.getWorld();
            Location start = player.getLocation();
            Location newLoc = event.getEntity().getLocation();
            if (start.distanceSquared(newLoc) >= getDistance() * getDistance()) {
                player.sendMessage(I18n.format("message.too.far"));
                return PowerResult.noop();
            }
            newLoc.setPitch(start.getPitch());
            newLoc.setYaw(start.getYaw());
            player.teleport(newLoc);
            world.playEffect(newLoc, Effect.ENDER_SIGNAL, 0);
            world.playSound(newLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.3f);
            return PowerResult.ok();
        }

        @Override
        public Power getPower() {
            return PowerTeleport.this;
        }
    }

    /**
     * Cooldown time of this power
     */
    public long getCooldown() {
        return cooldown;
    }

    /**
     * Cost of this power
     */
    public int getCost() {
        return cost;
    }

    /**
     * Maximum distance.
     */
    public int getDistance() {
        return distance;
    }

    @Override
    public String getName() {
        return "teleport";
    }

    @Override
    public String displayText() {
        return I18n.format("power.teleport", getDistance(), (double) getCooldown() / 20d);
    }

    public TargetMode getTargetMode() {
        return targetMode;
    }

    public void setCooldown(long cooldown) {
        this.cooldown = cooldown;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public void setTargetMode(TargetMode targetMode) {
        this.targetMode = targetMode;
    }

    public enum TargetMode {
        DEFAULT,
        RAY_TRACING_SWEEP,
        RAY_TRACING_EXACT,
        RAY_TRACING_EXACT_SWEEP,
        RAY_TRACING
    }
}
