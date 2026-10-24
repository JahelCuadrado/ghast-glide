package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "Mine at normal speed while riding": a ghast rider breaks a block in as many ticks as a player on
 * the ground. Ticks come from {@code BlockState#getDestroyProgress}, the per-tick progress that
 * {@code ServerPlayerGameMode} adds up until it reaches 1.
 */
public final class MiningWhileRidingGameTest {
  private static final Vec3 GHAST_POSITION = new Vec3(4.0, 3.0, 4.0);
  private static final BlockPos BLOCK = new BlockPos(1, 1, 1);

  /** Vanilla {@code Player#getDestroySpeed} divides by this off the ground. */
  private static final float AIRBORNE_DIVISOR = 5.0F;

  private static final float RATIO_TOLERANCE = 1.0E-4F;

  @GameTest(maxTicks = 20)
  public void riderWithSettingOnMinesInGroundTicks(GameTestHelper helper) {
    float grounded = progress(helper, groundedPlayer(helper));
    float riding = progress(helper, rider(helper, GlideSettings.DEFAULTS));

    helper.assertValueEqual(
        ticksToBreak(riding), ticksToBreak(grounded), "ticks to break while riding");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void riderWithSettingOffMinesFiveTimesSlower(GameTestHelper helper) {
    float grounded = progress(helper, groundedPlayer(helper));
    float riding =
        progress(helper, rider(helper, GlideSettings.DEFAULTS.withMineAtNormalSpeed(false)));

    float ratio = grounded / riding;
    helper.assertTrue(
        Math.abs(ratio - AIRBORNE_DIVISOR) < RATIO_TOLERANCE,
        "vanilla rider must mine 5x slower: "
            + ticksToBreak(riding)
            + " vs "
            + ticksToBreak(grounded)
            + " ticks");
    helper.succeed();
  }

  private static Player groundedPlayer(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setOnGround(true);
    return player;
  }

  /** A passenger of a harnessed ghast, kept off the ground as {@code Player#tick} does. */
  private static Player rider(GameTestHelper helper, GlideSettings settings) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, GHAST_POSITION);
    ghast.setNoAi(true);
    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    rider.startRiding(ghast, true, false);
    rider.setOnGround(false);
    ServerGlideSessions.update(rider.getUUID(), settings);
    helper.assertTrue(rider.getVehicle() == ghast, "rider must ride the ghast");
    return rider;
  }

  private static float progress(GameTestHelper helper, Player player) {
    helper.setBlock(BLOCK, Blocks.STONE);
    BlockPos pos = helper.absolutePos(BLOCK);
    BlockState state = helper.getLevel().getBlockState(pos);
    return state.getDestroyProgress(player, helper.getLevel(), pos);
  }

  private static int ticksToBreak(float progressPerTick) {
    return (int) Math.ceil(1.0F / progressPerTick);
  }
}
