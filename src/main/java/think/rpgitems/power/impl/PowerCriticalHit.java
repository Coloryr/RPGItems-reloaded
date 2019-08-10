package think.rpgitems.power.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import think.rpgitems.I18n;
import think.rpgitems.power.*;

import java.util.concurrent.ThreadLocalRandom;

import static think.rpgitems.power.Utils.getAngleBetweenVectors;

@PowerMeta(immutableTrigger = true, implClass = PowerCriticalHit.Impl.class)
public class PowerCriticalHit extends BasePower {

    @Property
    public double chance = 20;

    @Property
    public double backstabChance = 20;

    @Property
    public double factor = 1.5;

    @Property
    public double backstabFactor = 1.5;

    @Property
    public boolean setBaseDamage = false;

    @Override
    public String getName() {
        return "criticalhit";
    }

    @Override
    public String displayText() {
        return (getBackstabChance() != 0 && getChance() != 0) ?
                       I18n.format("power.criticalhit.both", getChance(), getFactor(), getBackstabChance(), getBackstabFactor())
                       : (getChance() != 0) ?
                                 I18n.format("power.criticalhit.critical", getChance(), getFactor()) :
                                 I18n.format("power.criticalhit.backstab", getBackstabChance(), getBackstabFactor());
    }

    public double getBackstabChance() {
        return backstabChance;
    }

    public double getChance() {
        return chance;
    }

    public double getFactor() {
        return factor;
    }

    public double getBackstabFactor() {
        return backstabFactor;
    }

    public boolean isSetBaseDamage() {
        return setBaseDamage;
    }

    public void setSetBaseDamage(boolean setBaseDamage) {
        this.setBaseDamage = setBaseDamage;
    }

    public class Impl implements PowerHit {
        @Override
        public PowerResult<Double> hit(Player player, ItemStack stack, LivingEntity entity, double damage, EntityDamageByEntityEvent event) {
            if (ThreadLocalRandom.current().nextDouble(100) < getChance()) {
                damage *= getFactor();
            }
            if (getAngleBetweenVectors(((LivingEntity) event.getEntity()).getEyeLocation().getDirection(), player.getEyeLocation().getDirection()) < 90 && ThreadLocalRandom.current().nextDouble(100) < getBackstabChance()) {
                damage *= getBackstabFactor();
            }
            if (isSetBaseDamage()) {
                event.setDamage(damage);
            }
            return PowerResult.ok(damage);
        }

        @Override
        public Power getPower() {
            return PowerCriticalHit.this;
        }
    }
}
