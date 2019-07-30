package think.rpgitems.power.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import think.rpgitems.I18n;
import think.rpgitems.power.*;

import java.util.Random;

/**
 * Power deathcommand.
 * <p>
 * With a 1/{@link #chance chance} to kill the target then execute the {@link #command}
 * for {@link #count} times. {@link #desc Description} will be displayed in item lore.
 * `${x}` `${y}` and `${z}` in the command will be replaced with the death location of the enemy.
 * </p>
 */
@SuppressWarnings("WeakerAccess")
@PowerMeta(immutableTrigger = true, implClass = PowerDeathCommand.Impl.class)
public class PowerDeathCommand extends BasePower {

    private static final Random rand = new Random();
    @Property(order = 1, required = true)
    private String command = "";
    @Property(order = 0)
    private int chance = 20;
    @Property(order = 3)
    private String desc = "";
    @Property(order = 2)
    private int count = 1;
    @Property
    private int cost = 0;

    /**
     * Chance of triggering this power
     */
    public int getChance() {
        return chance;
    }

    /**
     * Command to be executed
     */
    public String getCommand() {
        return command;
    }

    /**
     * Cost of this power
     */
    public int getCost() {
        return cost;
    }

    /**
     * Times to run the {@link #command}
     */
    public int getCount() {
        return count;
    }

    /**
     * Description in display text
     */
    public String getDesc() {
        return desc;
    }

    @Override
    public String getName() {
        return "deathcommand";
    }

    @Override
    public String displayText() {
        return I18n.format("power.deathcommand", getChance(), getDesc().equals("") ? "execute some command" : getDesc());
    }

    public class Impl implements PowerHit {
        @Override
        public PowerResult<Double> hit(Player player, ItemStack stack, LivingEntity entity, double damage, EntityDamageByEntityEvent event) {
            if (getRand().nextInt(getChance()) == 0) {
                if (!getItem().consumeDurability(stack, getCost())) return PowerResult.cost();
                Location loc = entity.getLocation();
                int x = (int) loc.getX();
                int y = (int) loc.getY();
                int z = (int) loc.getZ();
                entity.setHealth(0);
                String cmd = getCommand().replace("${x}", String.valueOf(x)).replace("${y}", String.valueOf(y)).replace("${z}", String.valueOf(z));
                for (int i = 0; i < getCount(); i++) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                return PowerResult.ok(damage);
            }
            return PowerResult.noop();
        }

        @Override
        public Power getPower() {
            return PowerDeathCommand.this;
        }
    }


    public static Random getRand() {
        return rand;
    }

    public void setChance(int chance) {
        this.chance = chance;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
