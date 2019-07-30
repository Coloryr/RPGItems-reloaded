package think.rpgitems.power.impl;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.librazy.nclangchecker.LangKey;
import think.rpgitems.RPGItems;
import think.rpgitems.data.LightContext;
import think.rpgitems.power.*;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static think.rpgitems.Events.*;
import static think.rpgitems.power.Utils.checkCooldown;

/**
 * @Author ReinWD
 * @email ReinWDD@gmail.com
 * Wrote & Maintained by ReinWD
 * if you have any issue, please send me email or @ReinWD in issues.
 * Accepted language: 中文, English.
 */
@PowerMeta(defaultTrigger = "RIGHT_CLICK", generalInterface = PowerPlain.class, implClass = PowerBeam.Impl.class)
public class PowerBeam extends BasePower {
    @Property
    private int length = 10;

    @Property
    private Particle particle = Particle.LAVA;

    @Property
    private int amount = 200;

    @Property
    private Mode mode = Mode.BEAM;

    @Property
    private boolean pierce = true;

    @Property
    private boolean ignoreWall = true;

    @Property
    private double damage = 20;

    @Property
    private int movementTicks = 40;

    @Property
    private double offsetX = 0;

    @Property
    private double offsetY = 0;

    @Property
    private double offsetZ = 0;

    @Property
    private double spawnsPerBlock = 2;

    @Property
    private int cost = 0;
    @Property
    private long cooldown = 0;

    @Property
    private boolean cone = false;

    @Property
    private double coneRange = 30;

    @Property
    private boolean homing = false;

    @Property
    private double homingAngle = 1;

    @Property
    private double homingRange = 30;

    @Property
    private HomingTargetMode homingTargetMode = HomingTargetMode.ONE_TARGET;

    @Property
    private Target homingTarget = Target.MOBS;

    @Property
    private int stepsBeforeHoming = 5;

    @Property
    private int burstCount = 1;

    @Property
    private int beamAmount = 1;

    @Property
    private int burstInterval = 1;

    @Property
    private int bounce = 0;

    @Property
    private boolean hitSelfWhenBounced = false;

    @Property
    private double gravity = 0;

    @Property
    @Serializer(ExtraDataSerializer.class)
    @Deserializer(ExtraDataSerializer.class)
    private Object extraData;

    @Property
    private double speed = 0;

    @Property
    private boolean requireHurtByEntity = true;


    @Property
    private boolean suppressMelee = false;

    private Set<Material> transp = Stream.of(Material.values())
            .filter(Material::isBlock)
            .filter(material -> !material.isSolid() || !material.isOccluding())
            .collect(Collectors.toSet());


    public class Impl implements PowerPlain, PowerRightClick, PowerLeftClick, PowerSneak, PowerSneaking, PowerSprint, PowerBowShoot, PowerHitTaken, PowerHit, PowerHurt {
        @Override
        public PowerResult<Void> fire(Player player, ItemStack stack) {
            if (!checkCooldown(getPower(), player, getCooldown(), true, true)) return PowerResult.cd();
            if (!getItem().consumeDurability(stack, getCost())) return PowerResult.cost();
            return beam(player, stack);
        }


        @Override
        public PowerResult<Void> leftClick(Player player, ItemStack stack, PlayerInteractEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Void> rightClick(Player player, ItemStack stack, PlayerInteractEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Void> sneak(Player player, ItemStack stack, PlayerToggleSneakEvent event) {
            return fire(player, stack);
        }

        @Override
        public PowerResult<Void> sneaking(Player player, ItemStack stack) {
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

        @Override
        public PowerResult<Double> hit(Player player, ItemStack stack, LivingEntity entity, double damage, EntityDamageByEntityEvent event) {
            return fire(player, stack).with(event.getDamage());
        }

        @Override
        public PowerResult<Double> takeHit(Player target, ItemStack stack, double damage, EntityDamageEvent event) {
            if (!isRequireHurtByEntity() || event instanceof EntityDamageByEntityEvent) {
                return fire(target, stack).with(event.getDamage());
            }
            return PowerResult.noop();
        }

        private PowerResult<Void> beam(LivingEntity from, ItemStack stack) {
            if (getBurstCount() > 0) {
                for (int i = 0; i < getBurstCount(); i++) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (isCone()) {
                                for (int j = 0; j < getBeamAmount(); j++) {
                                    internalFireBeam(from, stack);
                                }
                            } else {
                                internalFireBeam(from, stack);
                            }
                        }
                    }.runTaskLaterAsynchronously(RPGItems.plugin, i * getBurstInterval());
                }
                return PowerResult.ok();
            } else {
                return internalFireBeam(from, stack);
            }
        }


        private PowerResult<Void> internalFireBeam(LivingEntity from, ItemStack stack) {
            Location fromLocation = from.getEyeLocation();
            Vector towards = from.getEyeLocation().getDirection();

            if (isCone()) {
                double phi = getRandom().nextDouble() * 360;
                double theta;
                if (getConeRange() > 0) {
                    theta = getRandom().nextDouble() * getConeRange();
                    Vector clone = towards.clone();
                    Vector cross = clone.clone().add(getCrosser());
                    Vector vertical = clone.getCrossProduct(cross).getCrossProduct(towards);
                    towards.rotateAroundAxis(vertical, Math.toRadians(theta));
                    towards.rotateAroundAxis(clone, Math.toRadians(phi));
                }
            }


            Entity target = null;
            if (from instanceof Player && isHoming()) {
                target = getNextTarget(from.getEyeLocation().getDirection(), fromLocation, from);
            }

            switch (getMode()) {
                case BEAM:
                    new PlainTask(from, towards, getAmount(), getLength(), target, getBounce(), stack).runTask(RPGItems.plugin);
                    break;
                case PROJECTILE:
                    new MovingTask(from, towards, getAmount(), getLength(), target, getBounce(), stack).runTask(RPGItems.plugin);
                    break;
            }
            return PowerResult.ok();
        }

        @Override
        public PowerResult<Void> hurt(Player target, ItemStack stack, EntityDamageEvent event) {
            if (!isRequireHurtByEntity() || event instanceof EntityDamageByEntityEvent) {
                return fire(target, stack);
            }
            return PowerResult.noop();
        }

        @Override
        public Power getPower() {
            return PowerBeam.this;
        }
    }


    public int getAmount() {
        return amount;
    }

    public int getBeamAmount() {
        return beamAmount;
    }

    public int getBounce() {
        return bounce;
    }

    public int getBurstCount() {
        return burstCount;
    }

    public int getBurstInterval() {
        return burstInterval;
    }

    public double getConeRange() {
        return coneRange;
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

    private final Vector crosser = new Vector(1, 1, 1);

    public Vector getCrosser() {
        return crosser;
    }

    public double getDamage() {
        return damage;
    }

    public Object getExtraData() {
        return extraData;
    }

    public double getGravity() {
        return gravity;
    }

    public Vector getGravityVector() {
        return gravityVector;
    }

    public double getHomingAngle() {
        return homingAngle;
    }

    public double getHomingRange() {
        return homingRange;
    }

    public Target getHomingTarget() {
        return homingTarget;
    }

    public HomingTargetMode getHomingTargetMode() {
        return homingTargetMode;
    }

    public int getLength() {
        return length;
    }

    public double getLengthPerSpawn() {
        return 1 / getSpawnsPerBlock();
    }

    public Mode getMode() {
        return mode;
    }

    public int getMovementTicks() {
        return movementTicks;
    }

    @Override
    public @LangKey(skipCheck = true) String getName() {
        return "beam";
    }

    @Override
    public String displayText() {
        return null;
    }

    private Random random = new Random();

    private Vector yUnit = new Vector(0, 1, 0);

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public double getOffsetZ() {
        return offsetZ;
    }

    public Particle getParticle() {
        return particle;
    }

    public Random getRandom() {
        return random;
    }

    public double getSpawnsPerBlock() {
        return spawnsPerBlock;
    }

    public double getSpeed() {
        return speed;
    }

    public int getStepsBeforeHoming() {
        return stepsBeforeHoming;
    }

    public Set<Material> getTransp() {
        return transp;
    }

    public Vector getyUnit() {
        return yUnit;
    }

    public boolean isCone() {
        return cone;
    }

    public boolean isHitSelfWhenBounced() {
        return hitSelfWhenBounced;
    }

    public boolean isHoming() {
        return homing;
    }

    public boolean isIgnoreWall() {
        return ignoreWall;
    }

    public boolean isPierce() {
        return pierce;
    }

    public boolean isRequireHurtByEntity() {
        return requireHurtByEntity;
    }

    public boolean isSpawnInWorld() {
        return spawnInWorld;
    }

    /**
     * Whether to suppress the hit trigger
     */
    public boolean isSuppressMelee() {
        return suppressMelee;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void setBeamAmount(int beamAmount) {
        this.beamAmount = beamAmount;
    }

    public void setBounce(int bounce) {
        this.bounce = bounce;
    }

    public void setBurstCount(int burstCount) {
        this.burstCount = burstCount;
    }

    public void setBurstInterval(int burstInterval) {
        this.burstInterval = burstInterval;
    }

    public void setCone(boolean cone) {
        this.cone = cone;
    }

    public void setConeRange(double coneRange) {
        this.coneRange = coneRange;
    }

    public void setCooldown(long cooldown) {
        this.cooldown = cooldown;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public void setExtraData(Object extraData) {
        this.extraData = extraData;
    }

    public void setGravity(double gravity) {
        this.gravity = gravity;
    }

    public void setGravityVector(Vector gravityVector) {
        this.gravityVector = gravityVector;
    }

    public void setHitSelfWhenBounced(boolean hitSelfWhenBounced) {
        this.hitSelfWhenBounced = hitSelfWhenBounced;
    }

    public void setHoming(boolean homing) {
        this.homing = homing;
    }

    public void setHomingAngle(double homingAngle) {
        this.homingAngle = homingAngle;
    }

    public void setHomingRange(double homingRange) {
        this.homingRange = homingRange;
    }

    public void setHomingTarget(Target homingTarget) {
        this.homingTarget = homingTarget;
    }

    public void setHomingTargetMode(HomingTargetMode homingTargetMode) {
        this.homingTargetMode = homingTargetMode;
    }

    public void setIgnoreWall(boolean ignoreWall) {
        this.ignoreWall = ignoreWall;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setMovementTicks(int movementTicks) {
        this.movementTicks = movementTicks;
    }

    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    public void setOffsetY(double offsetY) {
        this.offsetY = offsetY;
    }

    public void setOffsetZ(double offsetZ) {
        this.offsetZ = offsetZ;
    }

    public void setParticle(Particle particle) {
        this.particle = particle;
    }

    public void setPierce(boolean pierce) {
        this.pierce = pierce;
    }

    public void setRandom(Random random) {
        this.random = random;
    }

    public void setRequireHurtByEntity(boolean requireHurtByEntity) {
        this.requireHurtByEntity = requireHurtByEntity;
    }

    public void setSpawnInWorld(boolean spawnInWorld) {
        this.spawnInWorld = spawnInWorld;
    }

    public void setSpawnsPerBlock(double spawnsPerBlock) {
        this.spawnsPerBlock = spawnsPerBlock;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void setStepsBeforeHoming(int stepsBeforeHoming) {
        this.stepsBeforeHoming = stepsBeforeHoming;
    }

    public void setSuppressMelee(boolean suppressMelee) {
        this.suppressMelee = suppressMelee;
    }

    public void setTransp(Set<Material> transp) {
        this.transp = transp;
    }

    public void setyUnit(Vector yUnit) {
        this.yUnit = yUnit;
    }

    class PlainTask extends BukkitRunnable {
        private int bounces;
        private double length;
        private final ItemStack stack;
        private LivingEntity from;
        private Vector towards;
        private final int apS;
        private Entity target;
        boolean bounced = false;

        public PlainTask(LivingEntity from, Vector towards, int amount, double actualLength, Entity target, int bounces, ItemStack stack) {
            this.from = from;
            this.towards = towards;
            this.length = actualLength;
            this.stack = stack;
            this.apS = amount / ((int) Math.floor(actualLength));
            this.target = target;
            this.bounces = bounces;
        }

        @Override
        public void run() {
            World world = from.getWorld();
            towards.normalize();
            Location lastLocation = from.getEyeLocation();
            double lpT = length / ((double) getMovementTicks());
            double partsPerTick = lpT / getLengthPerSpawn();
            for (int i = 0; i < getMovementTicks(); i++) {
                boolean isStepHit = false;
                Vector step = new Vector(0, 0, 0);
                for (int j = 0; j < partsPerTick; j++) {
                    boolean isHit = tryHit(from, lastLocation, stack, bounced && isHitSelfWhenBounced());
                    isStepHit = isHit || isStepHit;
                    Block block = lastLocation.getBlock();
                    if (getTransp().contains(block.getType())) {
                        spawnParticle(from, world, lastLocation, (int) Math.ceil(apS / partsPerTick));
                    } else if (!isIgnoreWall()) {
                        if (bounces > 0) {
                            bounces--;
                            bounced = true;
                            makeBounce(block, towards, lastLocation.clone().subtract(step));
                        } else {
                            LightContext.removeTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM);
                            return;
                        }
                    }
                    step = towards.clone().normalize().multiply(getLengthPerSpawn());
                    lastLocation.add(step);
                    towards = addGravity(towards, partsPerTick);
                    towards = homingCorrect(towards, lastLocation, target, i, () -> target = getNextTarget(from.getEyeLocation().getDirection(), from.getEyeLocation(), from));
                }
                if (isStepHit && getHomingTargetMode().equals(HomingTargetMode.MULTI_TARGET)) {
                    target = getNextTarget(from.getEyeLocation().getDirection(), from.getEyeLocation(), from);
                }
                if (isStepHit && !isPierce()) {
                    LightContext.clear();
                    return;
                }
            }
            LightContext.clear();
        }


    }

    private Vector gravityVector = new Vector(0, -getGravity() / 20, 0);

    private Vector addGravity(Vector towards, double partsPerTick) {
        double gravityPerTick = (-getGravity() / 20d) / partsPerTick;
        getGravityVector().setY(gravityPerTick);
        return towards.add(getGravityVector());
    }

    private class MovingTask extends BukkitRunnable {
        private final LivingEntity from;
        private int bounces;
        private Vector towards;
        private final ItemStack stack;
        private final int amountPerSec;
        private final List<BukkitRunnable> runnables = new LinkedList<>();
        private Entity target;
        boolean bounced = false;

        public MovingTask(LivingEntity from, Vector towards, int apS, double actualLength, Entity target, int bounces, ItemStack stack) {
            this.from = from;
            this.towards = towards;
            this.stack = stack;
            this.amountPerSec = apS / ((int) Math.floor(actualLength));
            this.target = target;
            this.bounces = bounces;
        }

        @Override
        public void run() {
            World world = from.getWorld();
            double lpT = ((double) getLength()) / ((double) getMovementTicks());
            double partsPerTick = lpT / getLengthPerSpawn();
            Location lastLocation = from.getEyeLocation();
            towards.normalize();
            final int[] finalI = {0};
            BukkitRunnable bukkitRunnable = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        boolean isStepHit = false;
                        Vector step = new Vector(0, 0, 0);
                        for (int k = 0; k < partsPerTick; k++) {
                            boolean isHit = tryHit(from, lastLocation, stack, bounced && isHitSelfWhenBounced());
                            isStepHit = isHit || isStepHit;
                            Block block = lastLocation.getBlock();
                            if (getTransp().contains(block.getType())) {
                                spawnParticle(from, world, lastLocation, (int) (amountPerSec / getSpawnsPerBlock()));
                            } else if (!isIgnoreWall()) {
                                if (bounces > 0) {
                                    bounces--;
                                    bounced = true;
                                    makeBounce(block, towards, lastLocation.clone().subtract(step));
                                } else {
                                    this.cancel();
                                    return;
                                }
                            }
                            step = towards.clone().normalize().multiply(getLengthPerSpawn());
                            lastLocation.add(step);
                            towards = addGravity(towards, partsPerTick);

                            towards = homingCorrect(towards, lastLocation, target, finalI[0], () -> target = getNextTarget(from.getEyeLocation().getDirection(), from.getEyeLocation(), from));
                        }
                        if (isStepHit && getHomingTargetMode().equals(HomingTargetMode.MULTI_TARGET)) {
                            target = getNextTarget(from.getEyeLocation().getDirection(), from.getEyeLocation(), from);
                        }
                        if (isStepHit && !isPierce()) {
                            this.cancel();
                            LightContext.clear();
                            return;
                        }
                        if (finalI[0] >= getMovementTicks()) {
                            this.cancel();
                            LightContext.clear();
                        }
                        finalI[0]++;
                    } catch (Exception ex) {
                        from.getServer().getLogger().log(Level.WARNING, "", ex);
                        this.cancel();
                        LightContext.clear();
                    }
                }
            };
            bukkitRunnable.runTaskTimer(RPGItems.plugin, 0, 1);
        }
    }

    private void makeBounce(Block block, Vector towards, Location lastLocation) {
        RayTraceResult rayTraceResult = block.rayTrace(lastLocation, towards, towards.length(), FluidCollisionMode.NEVER);
        if (rayTraceResult == null) {
            return;
        } else {
            towards.rotateAroundNonUnitAxis(rayTraceResult.getHitBlockFace().getDirection(), Math.toRadians(180)).multiply(-1);
        }
    }

    private Vector homingCorrect(Vector towards, Location lastLocation, Entity target, int i, Runnable runnable) {
        if (target == null || i < getStepsBeforeHoming()) {
            return towards;
        }
        if (target.isDead()) {
            runnable.run();
        }
        Location targetLocation;
        if (target instanceof LivingEntity) {
            targetLocation = ((LivingEntity) target).getEyeLocation();
        } else {
            targetLocation = target.getLocation();
        }

        Vector clone = towards.clone();
        Vector targetDirection = targetLocation.toVector().subtract(lastLocation.toVector());
        float angle = clone.angle(targetDirection);
        Vector crossProduct = clone.clone().getCrossProduct(targetDirection);
        double actualAng = getHomingAngle() / getSpawnsPerBlock();
        if (angle > Math.toRadians(actualAng)) {
            //↓a legacy but functionable way to rotate.
            //will create a enlarging circle
            clone.add(clone.clone().getCrossProduct(crossProduct).normalize().multiply(-1 * Math.tan(actualAng)));
            // ↓a better way to rotate.
            // will create a exact circle.
//            clone.rotateAroundAxis(crossProduct, actualAng);
        } else {
            clone = targetDirection.normalize();
        }
        return clone;
    }

    private LivingEntity getNextTarget(Vector towards, Location lastLocation, Entity from) {
        int radius = Math.min(this.getLength(), 300);
        return Utils.getLivingEntitiesInCone(from.getNearbyEntities(radius, this.getLength(), this.getLength()).stream()
                                                 .filter(entity -> entity instanceof LivingEntity && !entity.equals(from) && !entity.isDead())
                                                 .map(entity -> ((LivingEntity) entity))
                                                 .collect(Collectors.toList())
                , lastLocation.toVector(), getHomingRange(), towards).stream()
                    .filter(livingEntity -> {
                    switch (getHomingTarget()) {
                        case MOBS:
                            return !(livingEntity instanceof Player);
                        case PLAYERS:
                            return livingEntity instanceof Player && !((Player) livingEntity).getGameMode().equals(GameMode.SPECTATOR);
                        case ALL:
                            return !(livingEntity instanceof Player) || !((Player) livingEntity).getGameMode().equals(GameMode.SPECTATOR);
                    }
                    return true;
                })
                    .findFirst().orElse(null);
    }

    private boolean spawnInWorld = false;

    private void spawnParticle(LivingEntity from, World world, Location lastLocation, int i) {
        if ((lastLocation.distance(from.getEyeLocation()) < 1)) {
            return;
        }
        if (isSpawnInWorld()) {
            if (from instanceof Player) {
                ((Player) from).spawnParticle(this.getParticle(), lastLocation, i / 2, getOffsetX(), getOffsetY(), getOffsetZ(), getSpeed(), getExtraData());
            }
        }else {
            world.spawnParticle(this.getParticle(), lastLocation, i, getOffsetX(), getOffsetY(), getOffsetZ(), getSpeed(), getExtraData(), false);
        }
        setSpawnInWorld(!isSpawnInWorld());
    }

    private boolean tryHit(LivingEntity from, Location loc, ItemStack stack, boolean canHitSelf) {

        double offsetLength = new Vector(getOffsetX(), getOffsetY(), getOffsetZ()).length();
        double length = Double.isNaN(offsetLength) ? 0 : Math.max(offsetLength, 10);
        Collection<Entity> candidates = from.getWorld().getNearbyEntities(loc, length, length, length);
        boolean result = false;
        if (!isPierce()) {
            List<Entity> collect = candidates.stream()
                    .filter(entity -> (entity instanceof LivingEntity) && (canHitSelf || !entity.equals(from)) && !entity.isDead())
                    .filter(entity -> canHit(loc, entity))
                    .limit(1)
                    .collect(Collectors.toList());
            if (!collect.isEmpty()) {
                Entity entity = collect.get(0);
                if (entity instanceof LivingEntity) {
                    LightContext.putTemp(from.getUniqueId(), DAMAGE_SOURCE, getNamespacedKey().toString());
                    LightContext.putTemp(from.getUniqueId(), OVERRIDING_DAMAGE, getDamage());
                    LightContext.putTemp(from.getUniqueId(), SUPPRESS_MELEE, isSuppressMelee());
                    LightContext.putTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM, stack);
                    ((LivingEntity) entity).damage(getDamage(), from);
                    LightContext.clear();
                }
                return true;
            }
        } else {
            List<Entity> collect = candidates.stream()
                    .filter(entity -> (entity instanceof LivingEntity) && (canHitSelf || !entity.equals(from)))
                    .filter(entity -> canHit(loc, entity))
                    .collect(Collectors.toList());
            LightContext.putTemp(from.getUniqueId(), DAMAGE_SOURCE, getNamespacedKey().toString());
            LightContext.putTemp(from.getUniqueId(), OVERRIDING_DAMAGE, getDamage());
            LightContext.putTemp(from.getUniqueId(), SUPPRESS_MELEE, isSuppressMelee());
            LightContext.putTemp(from.getUniqueId(), DAMAGE_SOURCE_ITEM, stack);

            if (!collect.isEmpty()) {
                collect.stream()
                        .map(entity -> ((LivingEntity) entity))
                        .forEach(livingEntity -> {
                            livingEntity.damage(getDamage(), from);
                        });
                result = true;
            }
            LightContext.clear();

        }
        return result;
    }

    private boolean canHit(Location loc, Entity entity) {
        BoundingBox boundingBox = entity.getBoundingBox();
        BoundingBox particleBox;
        double x = Math.max(getOffsetX(), 0.1);
        double y = Math.max(getOffsetY(), 0.1);
        double z = Math.max(getOffsetZ(), 0.1);
        particleBox = BoundingBox.of(loc, x + 0.1, y + 0.1, z + 0.1);
        return boundingBox.overlaps(particleBox) || particleBox.overlaps(boundingBox);
    }

    private enum Mode {
        BEAM,
        PROJECTILE,
        ;

    }

    public class ExtraDataSerializer implements Getter, Setter {
        @Override
        public String get(Object object) {
            if (object instanceof Particle.DustOptions) {
                Color color = ((Particle.DustOptions) object).getColor();
                return color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + ((Particle.DustOptions) object).getSize();
            }
            return "";
        }

        @Override
        public Optional set(String value) throws IllegalArgumentException {
            String[] split = value.split(",", 4);
            int r = Integer.parseInt(split[0]);
            int g = Integer.parseInt(split[1]);
            int b = Integer.parseInt(split[2]);
            float size = Float.parseFloat(split[3]);
            return Optional.of(new Particle.DustOptions(Color.fromRGB(r, g, b), size));
        }
    }

    enum Target {
        MOBS, PLAYERS, ALL
    }

    private enum HomingTargetMode {
        ONE_TARGET, MULTI_TARGET;
    }
}
