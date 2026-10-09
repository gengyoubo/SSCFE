package github.com.gengyoubo.sscfe.water;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModWaterContent;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.onixary.shapeShifterCurseForge.form.FormManager;
import net.onixary.shapeShifterCurseForge.power.FormPowerEvents;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID)
public final class AxolotlWaterService {
    private static final UUID TANK_SPEED_ID = UUID.fromString("c50b9c8f-5832-4bf7-aec0-0c236fa45b8e");
    private static final AttributeModifier TANK_SPEED = new AttributeModifier(TANK_SPEED_ID,
            "sscfe.water_tank_weight", -0.1D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private AxolotlWaterService() {}

    public static boolean isAxolotl(Player player) {
        return "axolotl_form".equals(FormManager.current(player).groupId().getPath());
    }

    private static List<ItemStack> equipped(Player player) {
        return ModList.get().isLoaded("curios") ? CuriosWaterCompat.equipped(player) : List.of();
    }

    /** Called before the core's moisture update so depletion cannot trigger gill damage first. */
    public static boolean protectMoisture(Player player) {
        if (!isAxolotl(player)) return false;
        List<ItemStack> equipment = equipped(player);
        if (player.isInWater() && !player.level().isClientSide) {
            for (ItemStack stack : equipment) {
                if (stack.is(ModWaterContent.MOISTURIZER.get())) {
                    WaterItemStorage.fill(stack, WaterItemStorage.MOISTURIZER_CAPACITY, FluidAction.EXECUTE);
                }
            }
        }
        if (player.isCreative() || !FormPowerEvents.isDryLandForCustomWaterBreathing(player)) return false;
        for (ItemStack stack : equipment) {
            if (!stack.is(ModWaterContent.MOISTURIZER.get())) continue;
            if (!player.level().isClientSide && !player.isInWater() && WaterItemStorage.amount(stack) < 2) {
                refillMoisturizer(stack, equipment);
            }
            if (WaterItemStorage.amount(stack) <= 0) continue;
            if (!player.level().isClientSide && !player.isInWater() && player.tickCount % 20 == 0) {
                WaterItemStorage.drain(stack, 2, FluidAction.EXECUTE);
            }
            return true;
        }
        return false;
    }

    private static void refillMoisturizer(ItemStack moisturizer, List<ItemStack> equipment) {
        // The large tank is a shared supply, rather than another source of free automatic water.
        if (equipment.isEmpty()) return;
        int space = WaterItemStorage.fill(moisturizer, WaterItemStorage.MOISTURIZER_CAPACITY, FluidAction.SIMULATE);
        for (ItemStack tank : equipment) {
            if (!tank.is(ModWaterContent.LARGE_WATER_TANK.get())) continue;
            int transferred = WaterItemStorage.drain(tank, space, FluidAction.EXECUTE);
            WaterItemStorage.fill(moisturizer, transferred, FluidAction.EXECUTE);
            space -= transferred;
            if (space <= 0) break;
        }
    }

    /** Shared, atomic water consumption for future water-powered accessories and moves (mB). */
    public static boolean consumeWater(Player player, int amount) {
        if (amount < 0 || player.level().isClientSide || !isAxolotl(player)) return false;
        List<ItemStack> tanks = equipped(player).stream()
                .filter(stack -> stack.is(ModWaterContent.LARGE_WATER_TANK.get())).toList();
        if (tanks.stream().mapToInt(WaterItemStorage::amount).sum() < amount) return false;
        int remaining = amount;
        for (ItemStack tank : tanks) {
            remaining -= WaterItemStorage.drain(tank, remaining, FluidAction.EXECUTE);
            if (remaining == 0) break;
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        updateEquipment(event.player);
    }

    public static void updateEquipment(Player player) {
        List<ItemStack> equipment = isAxolotl(player) ? equipped(player) : List.of();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean hasTank = equipment.stream().anyMatch(stack -> stack.is(ModWaterContent.LARGE_WATER_TANK.get()));
        if (speed != null) {
            if (hasTank && !speed.hasModifier(TANK_SPEED)) speed.addTransientModifier(TANK_SPEED);
            else if (!hasTank && speed.hasModifier(TANK_SPEED)) speed.removeModifier(TANK_SPEED_ID);
        }
        if (player.tickCount % 20 == 0 && player.getFoodData().needsFood() && player.getAirSupply() > 0
                && equipment.stream().anyMatch(stack -> stack.is(ModWaterContent.WATER_AS_FOOD.get()))) {
            player.setAirSupply(player.getAirSupply() - 1);
            // FoodData multiplies the saturation modifier by food * 2: 0.5 gives exactly one point.
            player.getFoodData().eat(1, 0.5F);
        }
    }
}
