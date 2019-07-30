package think.rpgitems.power.impl;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import think.rpgitems.power.*;

import java.util.Collections;

import static think.rpgitems.power.Utils.attachPermission;
import static think.rpgitems.power.Utils.checkAndSetCooldown;

/**
 * Power command.
 * <p>
 * The item will run {@link #command} on click
 * giving the permission {@link #permission} just for the use of the command.
 * </p>
 */
@PowerMeta(defaultTrigger = "RIGHT_CLICK", generalInterface = PowerPlain.class, implClass = PowerCommand.Impl.class)
public class PowerCommand extends BasePower {

    @Property(order = 4, required = true)
    private String command = "";
    @Property(order = 3)
    private String display = "Runs command";
    @Property(order = 8)
    private String permission = "";
    @Property(order = 1)
    private long cooldown = 0;
    @Property
    private int cost = 0;

    @Property
    private boolean requireHurtByEntity = true;

    @Override
    public void init(ConfigurationSection section) {
        if (section.isBoolean("isRight")) {
            boolean isRight = section.getBoolean("isRight", true);
            triggers = Collections.singleton(isRight ? Trigger.RIGHT_CLICK : Trigger.LEFT_CLICK);
        }
        super.init(section);
    }

    public class Impl implements PowerRightClick, PowerLeftClick, PowerSprint, PowerSneak, PowerHurt, PowerHitTaken, PowerPlain, PowerBowShoot {
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
        public PowerResult<Void> hurt(Player target, ItemStack stack, EntityDamageEvent event) {
            if (!isRequireHurtByEntity() || event instanceof EntityDamageByEntityEvent) {
                return fire(target, stack);
            }
            return PowerResult.noop();
        }

        @Override
        public PowerResult<Double> takeHit(Player target, ItemStack stack, double damage, EntityDamageEvent event) {
            if (!isRequireHurtByEntity() || event instanceof EntityDamageByEntityEvent) {
                return fire(target, stack).with(damage);
            }
            return PowerResult.noop();
        }

        @Override
        public PowerResult<Float> bowShoot(Player player, ItemStack stack, EntityShootBowEvent event) {
            return fire(player, stack).with(event.getForce());
        }

        @Override
        public PowerResult<Void> rightClick(Player player, ItemStack stack, PlayerInteractEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Void> fire(Player target, ItemStack stack) {
            if (!checkAndSetCooldown(getPower(), target, getCooldown(), true, false, getCommand()))
                return PowerResult.cd();
            if (!getItem().consumeDurability(stack, getCost())) return PowerResult.cost();
            return executeCommand(target);
        }

        /**
         * Execute command
         *
         * @param player player
         * @return PowerResult
         */
        protected PowerResult<Void> executeCommand(Player player) {
            if (!player.isOnline()) return PowerResult.noop();

            String cmd = handlePlayerPlaceHolder(player, getCommand());
            if (getPermission().equals("console")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } else {
                boolean wasOp = player.isOp();
                attachPermission(player, getPermission());
                if (getPermission().equals("*")) {
                    try {
                        player.setOp(true);
                        player.performCommand(cmd);
                    } finally {
                        if (!wasOp) {
                            player.setOp(false);
                        }
                    }
                } else {
                    player.performCommand(cmd);
                }
            }
            return PowerResult.ok();
        }

        @Override
        public Power getPower() {
            return PowerCommand.this;
        }
    }
    public static String handlePlayerPlaceHolder(Player player, String cmd) {
        cmd = cmd.replaceAll("\\{player}", player.getName());
        cmd = cmd.replaceAll("\\{player\\.x}", Float.toString(-player.getLocation().getBlockX()));
        cmd = cmd.replaceAll("\\{player\\.y}", Float.toString(-player.getLocation().getBlockY()));
        cmd = cmd.replaceAll("\\{player\\.z}", Float.toString(-player.getLocation().getBlockZ()));
        cmd = cmd.replaceAll("\\{player\\.yaw}", Float.toString(90 + player.getEyeLocation().getYaw()));
        cmd = cmd.replaceAll("\\{player\\.pitch}", Float.toString(-player.getEyeLocation().getPitch()));
        cmd = cmd.replaceAll("\\{yaw}", Float.toString(player.getLocation().getYaw() + 90));
        cmd = cmd.replaceAll("\\{pitch}", Float.toString(-player.getLocation().getPitch()));
        return cmd;
    }

    /**
     * Command to be executed
     */
    public String getCommand() {
        return command;
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
     * Display text of this power
     */
    public String getDisplay() {
        return display;
    }

    @Override
    public String getName() {
        return "command";
    }

    @Override
    public String displayText() {
        return ChatColor.GREEN + getDisplay();
    }

    /**
     * Permission will be given to user executing the {@code command}
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Whether to require hurt by entity for HURT trigger
     */
    public boolean isRequireHurtByEntity() {
        return requireHurtByEntity;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public void setCooldown(long cooldown) {
        this.cooldown = cooldown;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public void setRequireHurtByEntity(boolean requireHurtByEntity) {
        this.requireHurtByEntity = requireHurtByEntity;
    }
}
