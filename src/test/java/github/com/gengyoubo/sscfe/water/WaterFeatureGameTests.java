package github.com.gengyoubo.sscfe.water;

import com.mojang.authlib.GameProfile;
import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModWaterContent;
import github.com.gengyoubo.sscfe.sound.WaterPurpleMusicEnvelope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.onixary.shapeShifterCurseForge.api.SscApi;
import net.onixary.shapeShifterCurseForge.form.FormRegistry;
import net.onixary.shapeShifterCurseForge.power.FormPowerEvents;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

@GameTestHolder(Sscfe.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterFeatureGameTests {
    @GameTest(template = "empty")
    public static void purplePacketPreservesCasterAndCoreAnchors(GameTestHelper helper) {
        Vec3 caster = new Vec3(140, 75, -230);
        Vec3 direction = new Vec3(1, 0, 0);
        Vec3 origin = WaterPurpleRules.chargeOrigin(caster.add(0, 1.6D, 0), direction, 24D);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            for (var stage : WaterCurseNetwork.Stage.values()) {
                var original = new WaterCurseNetwork.Effect(UUID.randomUUID(), helper.getLevel().dimension().location(),
                        stage, true, 100L, 120L, 600, caster, origin, direction);
                WaterCurseNetwork.Effect.encode(original, buffer);
                var decoded = WaterCurseNetwork.Effect.decode(buffer);
                helper.assertTrue(original.equals(decoded) && decoded.casterPosition().equals(caster)
                        && decoded.origin().equals(origin) && buffer.readableBytes() == 0,
                        "Charge, release and cancel packets preserve separate aura and projectile anchors");
                buffer.clear();
            }
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void purplePenetratesThreeMonsters(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(level,
                new GameProfile(UUID.randomUUID(), "piercing-purple-test"));
        Vec3 eyes = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO).atY(220));
        Vec3 direction = new Vec3(0, 1, 0);
        Vec3 origin = WaterPurpleRules.chargeOrigin(eyes, direction, 12D);
        player.setPos(eyes.x, eyes.y - player.getEyeHeight(), eyes.z);
        player.getAbilities().mayBuild = false;
        var targets = new java.util.ArrayList<net.minecraft.world.entity.monster.Husk>();
        try {
            for (int distance : new int[]{20, 40, 60}) {
                var target = net.minecraft.world.entity.EntityType.HUSK.create(level);
                helper.assertTrue(target != null, "Piercing test target exists");
                target.setNoAi(true); target.setNoGravity(true); target.setHealth(1F);
                target.setPos(origin.x, origin.y + distance, origin.z);
                level.addFreshEntity(target); targets.add(target);
            }
            var beam = new WaterCurseService.Beam(player, level, origin, direction, 60);
            for (int tick = 1; tick <= 12; tick++) {
                helper.assertTrue(!beam.tick(8192), "Hitting or killing a monster never ends projectile flight");
                if (tick == 4) helper.assertTrue(!targets.get(0).isAlive()
                        && targets.get(1).isAlive() && targets.get(2).isAlive(),
                        "The first monster dies while the two later targets remain untouched");
                if (tick == 8) helper.assertTrue(!targets.get(1).isAlive() && targets.get(2).isAlive(),
                        "The projectile continues to kill the second monster");
            }
            helper.assertTrue(!targets.get(2).isAlive(), "The same projectile reaches and kills the third monster");
            for (int tick = 13; tick < 60; tick++) helper.assertTrue(!beam.tick(8192),
                    "The projectile continues beyond the monsters until maximum range");
            helper.assertTrue(beam.tick(8192), "Only reaching the range limit completes this projectile");
        } finally {
            targets.forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void purpleChargeAndReleaseShareConfiguredOrigin(GameTestHelper helper) throws Exception {
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "purple-origin-test")) {
            @Override public boolean isCreative() { return true; }
        };
        var charm = new ItemStack(ModWaterContent.WATER_CURSE.get());
        charm.getOrCreateTag().putInt("WaterCurseMode", WaterCurseItem.Mode.PURPLE.ordinal());
        player.setItemInHand(InteractionHand.MAIN_HAND, charm);
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO).atY(200));
        player.setPos(position.x, position.y, position.z); player.setXRot(-90F); player.setYRot(0F);
        Vec3 expected = WaterPurpleRules.chargeOrigin(player.getEyePosition(), player.getLookAngle().normalize(), 24D);
        double originalDistance = WaterCurseConfig.CHARGE_DISTANCE.get();
        try {
            WaterCurseConfig.CHARGE_DISTANCE.set(24D);
            WaterCurseService.input(player, WaterCurseNetwork.Action.START, 60);
        } finally {
            WaterCurseConfig.CHARGE_DISTANCE.set(originalDistance);
        }
        // Read the actual captured cast and released beam rather than reconstructing their coordinates in the test.
        var castsField = WaterCurseService.class.getDeclaredField("CASTS");
        castsField.setAccessible(true);
        var cast = ((java.util.Map<?, ?>) castsField.get(null)).get(player.getUUID());
        helper.assertTrue(cast != null, "Origin test starts a real server cast");
        var originField = cast.getClass().getDeclaredField("origin"); originField.setAccessible(true);
        helper.assertTrue(expected.equals(originField.get(cast)), "Charge origin captures the configured distance at cast start");
        var beamsField = WaterCurseService.class.getDeclaredField("BEAMS"); beamsField.setAccessible(true);
        helper.runAfterDelay(64, () -> {
            try {
                var beams = (java.util.List<?>) beamsField.get(null);
                helper.assertTrue(beams.stream().anyMatch(value -> value instanceof WaterCurseService.Beam beam
                        && beam.player == player && beam.origin.equals(expected)),
                        "Released attack starts at the charge core even after the configuration changes");
            } catch (IllegalAccessException error) {
                throw new AssertionError(error);
            } finally {
                WaterCurseService.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void purpleTravelsAtOneHundredBlocksPerSecond(GameTestHelper helper) {
        helper.assertTrue(WaterPurpleRules.travelDistance(600, 1) == 5D
                && WaterPurpleRules.travelDistance(600, 20) == 100D
                && WaterPurpleRules.travelDistance(600, 599) == 2995D,
                "The core travels five blocks per tick, or one hundred blocks per second");
        helper.assertTrue(WaterPurpleRules.flightTicks(600) == 600
                && WaterPurpleRules.travelDistance(600, 600) == 3000D
                && WaterPurpleRules.travelDistance(600, 800) == 3000D,
                "Full range takes thirty seconds and movement stops at the range limit");
        helper.assertTrue(WaterPurpleRules.flightTicks(60) == 60
                && WaterPurpleRules.travelDistance(60, 60) == 300D
                && WaterPurpleRules.flightTicks(1140) == 600,
                "Reduced range finishes sooner; longer chanting does not alter projectile speed or range");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void purpleDamageAndTerrainFollowTheCore(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(level,
                new GameProfile(UUID.randomUUID(), "moving-purple-test"));
        BlockPos originBlock = helper.absolutePos(BlockPos.ZERO).atY(240);
        Vec3 origin = Vec3.atCenterOf(originBlock);
        player.setPos(origin.x, origin.y, origin.z);
        var target = net.minecraft.world.entity.EntityType.HUSK.create(level);
        helper.assertTrue(target != null, "Moving-core test target exists");
        target.setNoAi(true); target.setNoGravity(true);
        target.setPos(origin.x, origin.y + 20, origin.z);
        level.addFreshEntity(target);
        BlockPos terrain = originBlock.above(35);
        level.setBlock(terrain, Blocks.STONE.defaultBlockState(), 2);
        var beam = new WaterCurseService.Beam(player, level, origin, new Vec3(0, 1, 0), 60);
        try {
            float health = target.getHealth();
            helper.assertTrue(!beam.tick(8192) && target.getHealth() == health
                    && level.getBlockState(terrain).is(Blocks.STONE),
                    "The first tick cannot damage or destroy distant parts of the ray");
            beam.tick(8192); beam.tick(8192);
            helper.assertTrue(target.getHealth() == health, "Damage waits for the travelling core to arrive");
            beam.tick(8192);
            helper.assertTrue(target.getHealth() < health, "A swept core hits at twenty blocks on tick four");
            beam.tick(8192); beam.tick(8192);
            helper.assertTrue(level.getBlockState(terrain).is(Blocks.STONE), "Terrain thirty-five blocks away survives through tick six");
            beam.tick(8192);
            helper.assertTrue(level.getBlockState(terrain).isAir(), "Terrain breaks when the front reaches thirty-five blocks on tick seven");
            for (int tick = 8; tick < 60; tick++) {
                helper.assertTrue(!beam.tick(8192), "The beam remains active until its full flight completes");
            }
            helper.assertTrue(beam.tick(8192), "The three-hundred-block ray completes on tick sixty");
        } finally {
            target.discard();
            level.setBlock(terrain, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void musicFadesInAndContinuesAfterRelease(GameTestHelper helper) {
        var music = new WaterPurpleMusicEnvelope(0, 40, 40, 60);
        helper.assertTrue(music.gain(0) == 0F && music.gain(20) == 0.5F && music.gain(40) == 1F,
                "Chanting music fades from silence to full volume over two seconds");
        music.release(600);
        helper.assertTrue(music.gain(600) == 1F && music.gain(639) == 1F && music.gain(640) == 1F,
                "Release continues the same music for two seconds before fading");
        helper.assertTrue(music.gain(670) == 0.5F && !music.finished(670)
                && music.gain(700) == 0F && music.finished(700), "Music fades out over three seconds after the hold");
        music.release(680);
        helper.assertTrue(music.finished(700), "Duplicate release cannot restart the fade");
        var shortCast = new WaterPurpleMusicEnvelope(0, 100, 0, 20);
        shortCast.release(50);
        helper.assertTrue(shortCast.gain(50) == 0.5F && shortCast.gain(60) == 0.25F && shortCast.finished(70),
                "Release during fade-in fades from the actual volume without jumping to full volume");
        var immediate = new WaterPurpleMusicEnvelope(0, 0, 0, 0);
        helper.assertTrue(immediate.gain(0) == 1F, "Zero fade-in starts at configured volume");
        immediate.release(10);
        helper.assertTrue(immediate.gain(10) == 0F && immediate.finished(10), "Zero hold and fade-out stop at release");
        var nextCast = new WaterPurpleMusicEnvelope(610, 40, 40, 60);
        helper.assertTrue(nextCast.gain(630) == 0.5F && music.gain(670) == 0.5F,
                "A new cast has an independent envelope while the previous cast fades out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeCurseIgnoresFormAndResourceCosts(GameTestHelper helper) {
        TestPlayer player = new TestPlayer(helper.getLevel());
        player.creative = true;
        helper.assertTrue(!AxolotlWaterService.isAxolotl(player), "Creative test uses a non-axolotl form");
        ItemStack charm = new ItemStack(ModWaterContent.WATER_CURSE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, charm);
        player.setAirSupply(0);
        helper.assertTrue(WaterCurseService.accessory(player) == charm, "Creative held charm needs no Curios equipment");
        helper.assertTrue(WaterCurseService.canPay(player, 600) && WaterCurseService.pay(player, 600)
                && player.getAirSupply() == 0 && AxolotlWaterService.availableWater(player) == 0,
                "Creative full-power payment succeeds without a tank or moisture and consumes nothing");
        charm.getOrCreateTag().putInt("WaterCurseMode", WaterCurseItem.Mode.SHIELD.ordinal());
        WaterCurseItem.configure(player, charm);
        helper.assertTrue(WaterCurseItem.enabled(charm, WaterCurseItem.Mode.SHIELD), "Creative non-axolotl can configure charm");
        var shield = new LivingHurtEvent(player, player.damageSources().generic(), 15F);
        WaterCurseService.waterCombat(shield);
        helper.assertTrue(shield.getAmount() == 9F && player.getAirSupply() == 0, "Creative shield works without moisture");
        charm.getOrCreateTag().putInt("WaterCurseEnabledModes", 1 << WaterCurseItem.Mode.WEAPON.ordinal());
        TestPlayer target = axolotl(helper);
        var attack = new LivingHurtEvent(target, target.damageSources().playerAttack(player), 4F);
        WaterCurseService.waterCombat(attack);
        helper.assertTrue(attack.getAmount() == 6F && player.getAirSupply() == 0, "Creative water attack works without moisture");
        player.creative = false;
        helper.assertTrue(WaterCurseService.accessory(player).isEmpty() && !WaterCurseService.canPay(player, 600)
                && !WaterCurseService.pay(player, 600), "Survival restores form, equipment and resource requirements");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void creativeLockedCastIgnoresCooldownAndStance(GameTestHelper helper) {
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "creative-purple-test")) {
            @Override public boolean isCreative() { return true; }
        };
        helper.assertTrue(!AxolotlWaterService.isAxolotl(player), "Creative cast uses a non-axolotl form");
        ItemStack charm = new ItemStack(ModWaterContent.WATER_CURSE.get());
        charm.getOrCreateTag().putInt("WaterCurseMode", WaterCurseItem.Mode.PURPLE.ordinal());
        player.setItemInHand(InteractionHand.MAIN_HAND, charm);
        player.setAirSupply(0);
        player.setPos(0, 240, 0); player.setYRot(0); player.setXRot(-90F);
        player.getCooldowns().addCooldown(ModWaterContent.WATER_CURSE.get(), 4000);
        var boat = net.minecraft.world.entity.EntityType.BOAT.create(helper.getLevel());
        helper.assertTrue(boat != null, "Creative test boat exists");
        boat.setPos(0, 240, 0);
        helper.assertTrue(player.startRiding(boat, true), "Creative test begins while riding");
        WaterCurseService.input(player, WaterCurseNetwork.Action.START, 60);
        helper.assertTrue(WaterCurseService.casting(player) && !player.isPassenger(),
                "Creative starts with no resources despite cooldown and riding, then dismounts for locking");
        player.getCooldowns().removeCooldown(ModWaterContent.WATER_CURSE.get());
        helper.runAfterDelay(64, () -> {
            helper.assertTrue(!WaterCurseService.casting(player) && player.getAirSupply() == 0
                    && AxolotlWaterService.availableWater(player) == 0, "Creative automatically fires without resource consumption");
            helper.assertTrue(!player.getCooldowns().isOnCooldown(ModWaterContent.WATER_CURSE.get()), "Creative release adds no cooldown");
            WaterCurseService.input(player, WaterCurseNetwork.Action.START, 60);
            helper.assertTrue(WaterCurseService.casting(player), "Creative can immediately cast again");
            WaterCurseService.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void purpleAnimationEndsAtFiring(GameTestHelper helper) {
        var early = WaterPurpleAnimationTimeline.sample(559, 600, false);
        var finalStart = WaterPurpleAnimationTimeline.sample(560, 600, false);
        var finalMiddle = WaterPurpleAnimationTimeline.sample(580, 600, false);
        helper.assertTrue(early.clip().equals("mizu_mulasaki1") && early.seconds() == 2F,
                "Clip one holds its final pose until second 28");
        helper.assertTrue(finalStart.clip().equals("mizu_mulasaki2") && finalStart.seconds() == 0F
                && finalMiddle.seconds() == 1F && WaterPurpleAnimationTimeline.sample(600, 600, false).seconds() == 2F,
                "Clip two plays forwards from seconds 28 to 30");
        helper.assertTrue(WaterPurpleAnimationTimeline.sample(1100, 1140, false).clip().equals("mizu_mulasaki2")
                && WaterPurpleAnimationTimeline.sample(1100, 1140, false).seconds() == 0F,
                "Custom timing starts clip two exactly two seconds before firing");
        helper.assertTrue(WaterPurpleAnimationTimeline.sample(20, 60, false).seconds() == 0F
                && WaterPurpleAnimationTimeline.sample(0, 600, true).seconds() == 2F
                && WaterPurpleAnimationTimeline.sample(5, 600, true) == null,
                "Short casts and the firing pose finish correctly without replaying clip two");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void purplePowerPaymentAndSafety(GameTestHelper helper) {
        TestPlayer player = axolotl(helper);
        ItemStack tank = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        equip(player, "back", tank);
        WaterItemStorage.fill(tank, 32000, FluidAction.EXECUTE);
        player.setAirSupply(149);
        helper.assertTrue(!WaterCurseService.pay(player, 600) && WaterItemStorage.amount(tank) == 32000,
                "Insufficient moisture never drains water");
        player.setAirSupply(300);
        WaterItemStorage.drain(tank, 1, FluidAction.EXECUTE);
        helper.assertTrue(!WaterCurseService.pay(player, 600) && player.getAirSupply() == 300,
                "Insufficient water never drains moisture");
        WaterItemStorage.fill(tank, 1, FluidAction.EXECUTE);
        helper.assertTrue(WaterCurseService.pay(player, 600) && WaterItemStorage.amount(tank) == 0 && player.getAirSupply() == 150,
                "Full power spends exactly 32 B and fifty percent maximum moisture");
        helper.assertTrue(WaterPurpleRules.waterCost(60) == 3200 && WaterPurpleRules.moistureCost(60, 300) == 15
                        && WaterPurpleRules.range(60) == 300 && WaterPurpleRules.damagePerTick(60) == 2.5F,
                "Three-second cast has ten percent power and cost");
        helper.assertTrue(WaterPurpleRules.waterCost(1140) == 32000 && WaterPurpleRules.range(2400) == 3000
                        && WaterPurpleRules.damagePerTick(2400) == 25F,
                "Presentation beyond thirty seconds cannot increase power");
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.assertTrue(WaterCurseService.breakable(Blocks.STONE.defaultBlockState(), helper.getLevel(), pos), "Ordinary terrain can break");
        helper.assertTrue(!WaterCurseService.breakable(Blocks.OBSIDIAN.defaultBlockState(), helper.getLevel(), pos)
                && !WaterCurseService.breakable(Blocks.BEDROCK.defaultBlockState(), helper.getLevel(), pos)
                && !WaterCurseService.breakable(Blocks.CHEST.defaultBlockState(), helper.getLevel(), pos)
                && !WaterCurseService.breakable(ModWaterContent.LARGE_WATER_TANK_BLOCK.get().defaultBlockState(), helper.getLevel(), pos),
                "Obsidian, bedrock, containers and machines are protected");
        helper.assertTrue(WaterCurseService.ore(Blocks.DIAMOND_ORE.defaultBlockState())
                && !WaterCurseService.ore(Blocks.STONE.defaultBlockState()), "Only ores are eligible for limited drops");
        helper.assertTrue(WaterPurpleRules.distanceToRaySquared(new Vec3(10, 2, 0), Vec3.ZERO, new Vec3(1, 0, 0), 30) == 4,
                "Ray damage uses distance to the locked direction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void curseSettingsAndShield(GameTestHelper helper) {
        TestPlayer player = axolotl(helper);
        ItemStack charm = new ItemStack(ModWaterContent.WATER_CURSE.get());
        equip(player, "charm", charm);
        charm.getOrCreateTag().putInt("WaterCurseMode", WaterCurseItem.Mode.SHIELD.ordinal());
        WaterCurseItem.configure(player, charm);
        helper.assertTrue(WaterCurseItem.enabled(charm, WaterCurseItem.Mode.SHIELD), "Right-click enables selected shield");
        player.setShiftKeyDown(true);
        WaterCurseItem.configure(player, charm);
        helper.assertTrue(WaterCurseItem.mode(charm) == WaterCurseItem.Mode.PURPLE
                && WaterCurseItem.enabled(charm, WaterCurseItem.Mode.SHIELD), "Changing mode preserves shield protection");
        player.setShiftKeyDown(false);
        WaterCurseItem.configure(player, charm);
        ItemStack reloaded = ItemStack.of(charm.save(new CompoundTag()));
        helper.assertTrue(WaterCurseItem.special(reloaded) && WaterCurseItem.enabled(reloaded, WaterCurseItem.Mode.SHIELD),
                "Variant and individual toggles persist through save/load");
        player.setAirSupply(100);
        var small = new LivingHurtEvent(player, player.damageSources().generic(), 5F);
        WaterCurseService.waterCombat(small);
        helper.assertTrue(small.getAmount() == 0F && player.getAirSupply() == 99, "Shield fully blocks five damage for one moisture");
        var large = new LivingHurtEvent(player, player.damageSources().generic(), 15F);
        WaterCurseService.waterCombat(large);
        helper.assertTrue(large.getAmount() == 9F && player.getAirSupply() == 98, "Shield subtracts five then reduces the remainder ten percent");
        TestPlayer attacker = axolotl(helper);
        ItemStack fist = new ItemStack(ModWaterContent.WATER_CURSE.get());
        fist.getOrCreateTag().putInt("WaterCurseEnabledModes", 1 << WaterCurseItem.Mode.FIST.ordinal());
        equip(attacker, "charm", fist);
        attacker.setAirSupply(100);
        var waterPunch = new LivingHurtEvent(player, player.damageSources().playerAttack(attacker), 6F);
        WaterCurseService.waterCombat(waterPunch);
        helper.assertTrue(Math.abs(waterPunch.getAmount() - 3.6F) < 0.0001F && attacker.getAirSupply() == 99,
                "Water attack multiplier applies before shield reduction");
        player.setAirSupply(0);
        var dry = new LivingHurtEvent(player, player.damageSources().generic(), 5F);
        WaterCurseService.waterCombat(dry);
        helper.assertTrue(dry.getAmount() == 5F, "Dry shield cannot provide free protection");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("sscfe", "water_curse")).isPresent(),
                "Water curse crafting recipe loads");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lockedCastDamageAndAutomaticRelease(GameTestHelper helper) {
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "purple-test"));
        var data = SscApi.currentForm(player).orElseThrow();
        data.setFormId("shape-shifter-curse:axolotl_2"); data.setFormGroupId("shape-shifter-curse:axolotl_form");
        data.setFormTier(2); data.setContentEnabled(true);
        CuriosApi.getCuriosInventory(player).orElseThrow(AssertionError::new).reset();
        ItemStack charm = new ItemStack(ModWaterContent.WATER_CURSE.get());
        ItemStack tank = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        charm.getOrCreateTag().putInt("WaterCurseMode", WaterCurseItem.Mode.PURPLE.ordinal());
        equip(player, "charm", charm); equip(player, "back", tank);
        WaterItemStorage.fill(tank, 32000, FluidAction.EXECUTE);
        player.setAirSupply(300);
        // Fire upwards, well above the parallel test structures.
        player.setPos(player.getX(), 240, player.getZ()); player.setYRot(0F); player.setXRot(-90F);
        WaterCurseService.input(player, WaterCurseNetwork.Action.START, 60);
        helper.assertTrue(WaterCurseService.casting(player), "One click begins a cast");
        var listener = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        Vec3 before = player.position();
        listener.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(
                player.getX() + 1, player.getY(), player.getZ(), 90F, 0F, true));
        helper.assertTrue(player.position().equals(before) && player.getYRot() == 0F && player.getXRot() == -90F,
                "Movement and rotation packets are rejected on the server while casting");
        WaterCurseService.damaged(new LivingDamageEvent(player, player.damageSources().generic(), 0F));
        helper.assertTrue(WaterCurseService.casting(player), "Zero final damage does not interrupt");
        WaterCurseService.damaged(new LivingDamageEvent(player, player.damageSources().generic(), 1F));
        helper.assertTrue(!WaterCurseService.casting(player) && WaterItemStorage.amount(tank) == 32000 && player.getAirSupply() == 300,
                "Positive final damage cancels without payment");
        WaterCurseService.input(player, WaterCurseNetwork.Action.START, 60);
        helper.assertTrue(WaterCurseService.casting(player), "Can retry interrupted cast");
        helper.runAfterDelay(2, () -> {
            player.setPos(player.getX() + 1, 240, player.getZ()); player.setYRot(90); player.setXRot(0);
        });
        helper.runAfterDelay(4, () -> helper.assertTrue(player.getXRot() == -90F && player.getYRot() == 0F,
                "Server restores the initial aim during casting"));
        helper.runAfterDelay(64, () -> {
            helper.assertTrue(!WaterCurseService.casting(player), "Server automatically releases when configured time ends");
            helper.assertTrue(WaterItemStorage.amount(tank) == 28800 && player.getAirSupply() == 285,
                    "Automatic three-second release pays scaled costs exactly once");
            helper.assertTrue(player.getCooldowns().isOnCooldown(ModWaterContent.WATER_CURSE.get()), "Successful cast has cooldown");
            WaterCurseService.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void waterCapabilities(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        var handler = WaterItemStorage.handler(stack);
        helper.assertTrue(handler != null && handler.getTankCapacity(0) == 32_000, "32 B item capability");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0, "Reject lava");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 40_000), FluidAction.SIMULATE) == 32_000,
                "Simulated fill clamps capacity");
        helper.assertTrue(WaterItemStorage.amount(stack) == 0, "Simulation cannot mutate storage");
        helper.assertTrue(WaterItemStorage.fill(stack, 40_000, FluidAction.EXECUTE) == 32_000, "Fill clamps capacity");
        helper.assertTrue(WaterItemStorage.drain(stack, 500, FluidAction.SIMULATE) == 500
                && WaterItemStorage.amount(stack) == 32_000, "Drain simulation cannot mutate storage");
        ItemStack reloaded = ItemStack.of(stack.save(new CompoundTag()));
        helper.assertTrue(WaterItemStorage.amount(reloaded) == 32_000, "Item water persists through save/load");
        helper.assertTrue(WaterItemStorage.amount(stack.copy()) == 32_000, "Item copies preserve water");
        TestPlayer player = axolotl(helper);
        ItemStack emptyTank = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, emptyTank);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));
        WaterItemStorage.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(WaterItemStorage.amount(emptyTank) == 1000
                && player.getOffhandItem().is(Items.BUCKET), "Other-hand water bucket fills the held tank");
        WaterItemStorage.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(WaterItemStorage.amount(emptyTank) == 0
                && player.getOffhandItem().is(Items.WATER_BUCKET), "Other-hand empty bucket drains the held tank");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placedTankAndBuckets(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var block = ModWaterContent.LARGE_WATER_TANK_BLOCK.get();
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        var entity = (WaterTankBlockEntity) helper.getLevel().getBlockEntity(pos);
        ItemStack stack = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        WaterItemStorage.fill(stack, 12_345, FluidAction.EXECUTE);
        block.setPlacedBy(helper.getLevel(), pos, block.defaultBlockState(), null, stack);
        helper.assertTrue(entity.tank().getFluidAmount() == 12_345, "Placement preserves partial water amount");
        for (Direction side : Direction.values()) {
            var cap = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
            helper.assertTrue(cap.getTankCapacity(0) == 32_000, "Every side exposes Forge fluid storage");
            helper.assertTrue(cap.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0, "Block rejects lava");
        }
        var saved = entity.saveWithoutMetadata();
        var restored = new WaterTankBlockEntity(pos, block.defaultBlockState());
        restored.load(saved);
        helper.assertTrue(restored.tank().getFluidAmount() == 12_345, "Block water survives reload");
        var filledBucket = FluidUtil.tryFillContainer(new ItemStack(Items.BUCKET), entity.tank(), 1000, null, true);
        helper.assertTrue(filledBucket.isSuccess() && filledBucket.getResult().is(Items.WATER_BUCKET)
                && entity.tank().getFluidAmount() == 11_345, "Bucket removes exactly 1 B");
        var emptyBucket = FluidUtil.tryEmptyContainer(filledBucket.getResult(), entity.tank(), 1000, null, true);
        helper.assertTrue(emptyBucket.isSuccess() && emptyBucket.getResult().is(Items.BUCKET)
                && entity.tank().getFluidAmount() == 12_345, "Bucket returns exactly 1 B");
        var drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), pos, entity);
        helper.assertTrue(drops.size() == 1 && WaterItemStorage.amount(drops.get(0)) == 12_345,
                "Breaking the tank preserves water in its drop");
        entity.invalidateCaps();
        helper.assertTrue(!entity.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(), "Unloaded capability invalidates");
        entity.reviveCaps();
        helper.assertTrue(entity.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(), "Capability revives");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tankLevelSync(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var state = ModWaterContent.LARGE_WATER_TANK_BLOCK.get().defaultBlockState();
        helper.getLevel().setBlock(pos, state, 3);
        var serverTank = (WaterTankBlockEntity) helper.getLevel().getBlockEntity(pos);
        var clientTank = new WaterTankBlockEntity(pos, state);
        var pipe = serverTank.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP)
                .orElseThrow(AssertionError::new);
        helper.assertTrue(serverTank.fillFraction() == 0, "Empty tank has no rendered water");
        pipe.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        clientTank.handleUpdateTag(serverTank.getUpdateTag());
        helper.assertTrue(clientTank.tank().getFluidAmount() == 1000 && clientTank.fillFraction() == 1F / 32F,
                "Chunk sync preserves the one-bucket water level");
        pipe.fill(new FluidStack(Fluids.WATER, 15_000), FluidAction.EXECUTE);
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        helper.assertTrue(clientTank.fillFraction() == 0.5F, "Update packet raises water level to half full");
        pipe.fill(new FluidStack(Fluids.WATER, 20_000), FluidAction.EXECUTE);
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        helper.assertTrue(clientTank.fillFraction() == 1F, "Water level clamps at full capacity");
        pipe.drain(24_000, FluidAction.EXECUTE);
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        helper.assertTrue(clientTank.fillFraction() == 0.25F, "Pipe extraction lowers the synchronized water level");
        pipe.drain(32_000, FluidAction.EXECUTE);
        clientTank.onDataPacket(null, serverTank.getUpdatePacket());
        helper.assertTrue(clientTank.tank().isEmpty() && clientTank.fillFraction() == 0F,
                "Draining all water clears the client fluid volume");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void moisturizerAndCoreMixin(GameTestHelper helper) throws Exception {
        TestPlayer player = axolotl(helper);
        ItemStack moisturizer = new ItemStack(ModWaterContent.MOISTURIZER.get());
        equip(player, "belt", moisturizer);
        WaterItemStorage.fill(moisturizer, 100, FluidAction.EXECUTE);
        player.setAirSupply(150);
        var moistureTick = FormPowerEvents.class.getDeclaredMethod("tickCustomWaterBreathing", Player.class);
        moistureTick.setAccessible(true);
        player.tickCount = 1;
        for (int i = 0; i < 200; i++) moistureTick.invoke(null, player);
        helper.assertTrue(player.getAirSupply() == 150 && WaterItemStorage.amount(moisturizer) == 100,
                "Mixin prevents land moisture loss between payment ticks");
        player.tickCount = 20;
        moistureTick.invoke(null, player);
        helper.assertTrue(player.getAirSupply() == 150 && WaterItemStorage.amount(moisturizer) == 98,
                "Moisturizer spends exactly 2 mB per second");
        WaterItemStorage.drain(moisturizer, 98, FluidAction.EXECUTE);
        player.tickCount = 21;
        player.getRandom().setSeed(1);
        for (int i = 0; i < 200; i++) moistureTick.invoke(null, player);
        helper.assertTrue(player.getAirSupply() < 150, "Empty moisturizer resumes the core's moisture drain");
        player.inWater = true;
        int moistureBeforeWater = player.getAirSupply();
        player.tickCount = 40;
        moistureTick.invoke(null, player);
        helper.assertTrue(WaterItemStorage.amount(moisturizer) == 3000, "Water automatically refills the moisturizer");
        helper.assertTrue(player.getAirSupply() == moistureBeforeWater,
                "Partial immersion protects moisture without spending stored water");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedSupplyFoodAndWeight(GameTestHelper helper) {
        TestPlayer player = axolotl(helper);
        ItemStack tank = new ItemStack(ModWaterContent.LARGE_WATER_TANK.get());
        ItemStack moisturizer = new ItemStack(ModWaterContent.MOISTURIZER.get());
        equip(player, "back", tank);
        equip(player, "belt", moisturizer);
        equip(player, "charm", new ItemStack(ModWaterContent.WATER_AS_FOOD.get()));
        WaterItemStorage.fill(tank, 32_000, FluidAction.EXECUTE);
        player.tickCount = 20;
        helper.assertTrue(AxolotlWaterService.protectMoisture(player), "Large tank supplies an empty moisturizer");
        helper.assertTrue(WaterItemStorage.amount(tank) == 29_000 && WaterItemStorage.amount(moisturizer) == 2998,
                "Transfer conserves water and only pays the regular 2 mB cost");
        helper.assertTrue(!AxolotlWaterService.consumeWater(player, 30_000)
                && WaterItemStorage.amount(tank) == 29_000, "Insufficient shared water never partially drains");
        helper.assertTrue(AxolotlWaterService.consumeWater(player, 1000)
                && WaterItemStorage.amount(tank) == 28_000, "Shared supply drains the requested amount");
        player.setAirSupply(100);
        player.getFoodData().setFoodLevel(19);
        player.getFoodData().setSaturation(0);
        double initialSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        AxolotlWaterService.updateEquipment(player);
        helper.assertTrue(player.getAirSupply() == 99 && player.getFoodData().getFoodLevel() == 20
                && player.getFoodData().getSaturationLevel() == 1.0F, "1 moisture becomes 1 hunger and 1 saturation");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - initialSpeed * 0.9D) < 1E-6,
                "Equipped tank reduces total speed by ten percent");
        AxolotlWaterService.updateEquipment(player);
        helper.assertTrue(player.getAirSupply() == 99, "Full hunger consumes no moisture");
        player.setAirSupply(0);
        player.getFoodData().setFoodLevel(19);
        AxolotlWaterService.updateEquipment(player);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 19 && player.getAirSupply() == 0,
                "Zero moisture cannot provide free food");
        SscApi.currentForm(player).orElseThrow().setFormId(FormRegistry.ORIGINAL_BEFORE_ENABLE.toString());
        AxolotlWaterService.updateEquipment(player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - initialSpeed) < 1E-6,
                "Leaving axolotl form removes the tank's speed penalty");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 19, "Other forms cannot use the food accessory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wetMoondustCrafting(GameTestHelper helper) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        var grid = new TransientCraftingContainer(menu, 3, 3);
        for (int slot : new int[]{1, 3, 5, 7}) grid.setItem(slot, new ItemStack(Items.WATER_BUCKET));
        grid.setItem(4, new ItemStack(net.onixary.shapeShifterCurseForge.registry.ModItems.UNTREATED_MOONDUST.get()));
        var recipe = helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("sscfe", "wet_moondust"))
                .orElseThrow();
        @SuppressWarnings("unchecked")
        var crafting = (net.minecraft.world.item.crafting.CraftingRecipe) recipe;
        helper.assertTrue(crafting.matches(grid, helper.getLevel()), "Wet moondust matches the issue's pattern");
        helper.assertTrue(crafting.assemble(grid, helper.getLevel().registryAccess()).is(ModWaterContent.WET_MOONDUST.get()),
                "Recipe creates wet moondust");
        var remains = crafting.getRemainingItems(grid);
        for (int slot : new int[]{1, 3, 5, 7}) {
            helper.assertTrue(remains.get(slot).is(Items.BUCKET), "Water buckets leave empty buckets");
        }
        for (String id : new String[]{"moisturizer", "tank_core", "large_water_tank", "water_as_food"}) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("sscfe", id)).isPresent(),
                    "Recipe loads: " + id);
        }
        helper.succeed();
    }

    private static TestPlayer axolotl(GameTestHelper helper) {
        TestPlayer player = new TestPlayer(helper.getLevel());
        var data = SscApi.currentForm(player).orElseThrow();
        data.setFormId("shape-shifter-curse:axolotl_2");
        data.setFormGroupId("shape-shifter-curse:axolotl_form");
        data.setFormTier(2);
        data.setContentEnabled(true);
        CuriosApi.getCuriosInventory(player).orElseThrow(AssertionError::new).reset();
        return player;
    }

    private static void equip(Player player, String slot, ItemStack stack) {
        CuriosApi.getCuriosInventory(player).orElseThrow(AssertionError::new)
                .getStacksHandler(slot).orElseThrow().getStacks().setStackInSlot(0, stack);
    }

    private static final class TestPlayer extends Player {
        private boolean inWater;
        private boolean creative;
        private TestPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "water-test")); }
        @Override public boolean isCreative() { return creative; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isInWater() { return inWater; }
    }
}
