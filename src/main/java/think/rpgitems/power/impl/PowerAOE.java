package think.rpgitems.power.impl;

import org.bukkit.Effect;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import think.rpgitems.I18n;
import think.rpgitems.power.*;
import think.rpgitems.utils.PotionEffectUtils;

import static think.rpgitems.power.Utils.checkCooldown;
import static think.rpgitems.power.Utils.getNearbyEntities;

/**
 * Power aoe.
 * <p>
 * On right click the aoe power will apply {@link #type effect}
 * to all entities within the {@link #range range} for {@link #duration duration} ticks
 * at power {@link #amplifier amplifier}.
 * By default, the user will be targeted by the potion
 * as well if not set via {@link #selfapplication selfapplication}.
 * </p>
 */
@PowerMeta(defaultTrigger = "RIGHT_CLICK", withSelectors = true, generalInterface = PowerPlain.class, implClass = PowerAOE.Impl.class)
public class PowerAOE extends BasePower {

    @Property(order = 0)
    private long cooldown = 0;
    @Property(order = 4, required = true)
    private int amplifier = 1;
    @Property(order = 3)
    private int duration = 15;
    @Property(order = 1)
    private int range = 5;
    @Property(order = 5)
    private boolean selfapplication = true;

    @Property(order = 2)
    @Deserializer(PotionEffectUtils.class)
    @Serializer(PotionEffectUtils.class)
    @AcceptedValue(preset = Preset.POTION_EFFECT_TYPE)
    private PotionEffectType type;
    @Property(alias = "name")
    private String display = null;
    @Property
    private int cost = 0;

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public class Impl implements PowerRightClick, PowerLeftClick, PowerOffhandClick, PowerPlain, PowerHit, PowerBowShoot {
        @Override
        public PowerResult<Void> rightClick(final Player player, ItemStack stack, PlayerInteractEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Void> leftClick(final Player player, ItemStack stack, PlayerInteractEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Void> offhandClick(final Player player, ItemStack stack, PlayerInteractEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Double> hit(Player player, ItemStack stack, LivingEntity entity, double damage, EntityDamageByEntityEvent event) {
            return fire(player, stack).with(damage);
        }

        @Override
        public PowerResult<Float> bowShoot(Player player, ItemStack stack, EntityShootBowEvent event) {
            return fire(player, stack).with(event.getForce());
        }

        @Override
        public PowerResult<Void> fire(Player player, ItemStack stack) {
            if (!checkCooldown(getPower(), player, getCooldown(), true, true)) return PowerResult.cd();
            if (!getItem().consumeDurability(stack, getCost())) return PowerResult.cost();
            PotionEffect effect = new PotionEffect(getType(), getDuration(), getAmplifier() - 1);
            if (isSelfapplication()) {
                player.addPotionEffect(effect);
            }
            player.getWorld().playEffect(player.getLocation(), Effect.POTION_BREAK, getType().getColor().asRGB());
            for (Entity ent : getNearbyEntities(getPower(), player.getLocation(), player, getRange())) {
                if (ent instanceof LivingEntity && !player.equals(ent)) {
                    ((LivingEntity) ent).addPotionEffect(effect);
                }
            }
            return PowerResult.ok();
        }

        @Override
        public Power getPower() {
            return PowerAOE.this;
        }
    }

    /**
     * Amplifier of the potion
     */
    public int getAmplifier() {
        return amplifier;
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
     * Duration of the potion
     */
    public int getDuration() {
        return duration;
    }


    @Override
    public String getName() {
        return "aoe";
    }

    @Override
    public String displayText() {
        return getDisplay() != null ? getDisplay() : I18n.format("power.aoe.display", getType().getName(), getAmplifier(), getDuration(), isSelfapplication() ? I18n.format("power.aoe.selfapplication.including") : I18n.format("power.aoe.selfapplication.excluding"), getRange(), (double) getCooldown() / 20d);
    }

    /**
     * Range of the potion
     */
    public int getRange() {
        return range;
    }

    /**
     * Type of the potion
     */
    public PotionEffectType getType() {
        return type;
    }

    /**
     * Whether the potion will be apply to the user
     */
    public boolean isSelfapplication() {
        return selfapplication;
    }

    public void setAmplifier(int amplifier) {
        this.amplifier = amplifier;
    }

    public void setCooldown(long cooldown) {
        this.cooldown = cooldown;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public void setSelfapplication(boolean selfapplication) {
        this.selfapplication = selfapplication;
    }

    public void setType(PotionEffectType type) {
        this.type = type;
    }
}
