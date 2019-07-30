package think.rpgitems.power.impl;

import com.udojava.evalex.Expression;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import think.rpgitems.power.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static think.rpgitems.power.Utils.*;

@PowerMeta(marker = true, implClass = PowerEvalCondition.class)
public class PowerEvalCondition extends BasePower implements PowerCondition<BigDecimal> {

    @Property(order = 0, required = true)
    public String id;

    @Property
    public boolean isStatic = false;

    @Property
    public boolean isCritical = false;

    @Property(required = true)
    public String expression;

    @Override
    public String id() {
        return id;
    }

    @Override
    public Power getPower() {
        return PowerEvalCondition.this;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public boolean isCritical() {
        return isCritical;
    }

    @Override
    public PowerResult<BigDecimal> check(Player player, ItemStack stack, Map<Power, PowerResult> c) {
        Expression e = new Expression(expression);
        e.and("playerYaw", lazyNumber(() -> (double) player.getLocation().getYaw()))
         .and("playerPitch", lazyNumber(() -> (double) player.getLocation().getPitch()))
         .and("playerX", lazyNumber(() -> player.getLocation().getX()))
         .and("playerY", lazyNumber(() -> player.getLocation().getY()))
         .and("playerZ", lazyNumber(() -> player.getLocation().getZ()))
         .and("playerLastDamage", lazyNumber(player::getLastDamage));

        e.addLazyFunction(scoreBoard(player));
        e.addLazyFunction(context(player));
        e.addLazyFunction(now());
        BigDecimal result = e.eval();
        return result.equals(BigDecimal.ONE) ? PowerResult.ok(result) : PowerResult.fail(result);
    }

    @Override
    public String getName() {
        return "evalcondition";
    }

    @Override
    public String displayText() {
        return null;
    }

    @Override
    public Set<String> getConditions() {
        return Collections.emptySet();
    }
}
