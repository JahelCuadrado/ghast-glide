package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.anchor.GhastAnchors;
import com.einsteinmods.ghastglide.anchor.GhastAttachments;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A ghast rider with "Build through your ghast" places blocks inside the ghast's box. Placement
 * goes through {@code ItemStack#useOn}, as {@code ServerPlayerGameMode#useItemOn} does.
 */
public final class BuildThroughGhastGameTest {
  // Ghast (4x4x4) spans x/z 2..6 and y 3..7; the rider's feet sit at y 6.4, above this block.
  private static final Vec3 GHAST_POSITION = new Vec3(4.0, 3.0, 4.0);
  private static final BlockPos INSIDE_GHAST = new BlockPos(3, 4, 3);
  // A pig (0.9 wide) centred in that block: it overlaps the block as well as the ghast.
  private static final Vec3 PIG_POSITION = new Vec3(3.5, 4.0, 3.5);
  private static final float LOOK_STRAIGHT_DOWN = 90.0F;

  // Unstuck: with sky access the dismounted ghast rises out of the placed block.
  private static final Vec3 UNSTUCK_GHAST_POSITION = new Vec3(4.0, 2.0, 4.0);
  private static final BlockPos INSIDE_UNSTUCK_GHAST = new BlockPos(3, 3, 3);
  private static final int UNSTUCK_MAX_TICKS = 100;

  private static final GlideSettings BUILDING = GlideSettings.DEFAULTS.withBuildThroughGhast(true);

  @GameTest(maxTicks = 20)
  public void riderPlacesBlockInsideGhastWithSettingOn(GameTestHelper helper) {
    Player rider = rider(helper, ghast(helper, GHAST_POSITION), BUILDING, Items.STONE);

    InteractionResult result = place(helper, rider, INSIDE_GHAST);

    helper.assertTrue(result.consumesAction(), "placement must succeed, got " + result);
    helper.assertBlockPresent(Blocks.STONE, INSIDE_GHAST);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void ghastObstructsWithSettingOff(GameTestHelper helper) {
    Player rider =
        rider(helper, ghast(helper, GHAST_POSITION), GlideSettings.DEFAULTS, Items.STONE);

    InteractionResult result = place(helper, rider, INSIDE_GHAST);

    helper.assertFalse(result.consumesAction(), "vanilla must reject the block, got " + result);
    helper.assertBlockPresent(Blocks.AIR, INSIDE_GHAST);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void otherMobStillObstructsWithSettingOn(GameTestHelper helper) {
    Player rider = rider(helper, ghast(helper, GHAST_POSITION), BUILDING, Items.STONE);
    Pig pig = helper.spawn(EntityTypes.PIG, PIG_POSITION);
    pig.setNoAi(true);
    pig.setNoGravity(true);

    InteractionResult result = place(helper, rider, INSIDE_GHAST);

    helper.assertFalse(result.consumesAction(), "the pig must still block, got " + result);
    helper.assertBlockPresent(Blocks.AIR, INSIDE_GHAST);
    helper.succeed();
  }

  /** {@code StandingAndWallBlockItem#getPlacementState} runs its own obstruction check. */
  @GameTest(maxTicks = 20)
  public void riderPlacesStandingOrWallItemInsideGhast(GameTestHelper helper) {
    Player rider = rider(helper, ghast(helper, GHAST_POSITION), BUILDING, Items.SKELETON_SKULL);

    InteractionResult result = place(helper, rider, INSIDE_GHAST);

    helper.assertTrue(result.consumesAction(), "skull placement must succeed, got " + result);
    helper.assertBlockPresent(Blocks.SKELETON_SKULL, INSIDE_GHAST);
    helper.succeed();
  }

  /**
   * With noclip off the block can still be placed inside the ghast; once the rider leaves, {@code
   * DismountUnstuck} moves the ghast out of it.
   */
  @GameTest(maxTicks = UNSTUCK_MAX_TICKS, skyAccess = true)
  public void withoutNoclipPlacedBlockIsLeftByUnstuckAfterDismount(GameTestHelper helper) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, UNSTUCK_GHAST_POSITION);
    harness(ghast);
    Player rider = rider(helper, ghast, BUILDING.withNoclipEnabled(false), Items.STONE);

    InteractionResult result = place(helper, rider, INSIDE_UNSTUCK_GHAST);
    helper.assertTrue(result.consumesAction(), "placement must succeed, got " + result);
    helper.assertFalse(
        helper.getLevel().noBlockCollision(ghast, ghast.getBoundingBox()),
        "ghast must start around the placed block");
    ghast.ejectPassengers();

    helper.succeedWhen(
        () -> {
          AABB box = ghast.getBoundingBox();
          helper.assertTrue(
              helper.getLevel().noBlockCollision(ghast, box), "ghast still overlaps the block");
          helper.assertFalse(ghast.hasAttached(GhastAttachments.UNSTUCK), "move must finish");
          helper.assertTrue(GhastAnchors.isAnchored(ghast), "freed ghast must anchor");
          helper.assertBlockPresent(Blocks.STONE, INSIDE_UNSTUCK_GHAST);
        });
  }

  private static HappyGhast ghast(GameTestHelper helper, Vec3 position) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, position);
    ghast.setNoAi(true);
    harness(ghast);
    return ghast;
  }

  private static void harness(HappyGhast ghast) {
    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
  }

  private static Player rider(
      GameTestHelper helper, HappyGhast ghast, GlideSettings settings, Item item) {
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    GameType.SURVIVAL.updatePlayerAbilities(rider.getAbilities());
    rider.startRiding(ghast, true, false);
    ServerGlideSessions.update(rider.getUUID(), settings);
    rider.setXRot(LOOK_STRAIGHT_DOWN);
    rider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    helper.assertTrue(rider.getVehicle() == ghast, "rider must ride the ghast");
    return rider;
  }

  /** Uses the held item on the top face of {@code relativePos} (air, so the block goes there). */
  private static InteractionResult place(
      GameTestHelper helper, Player rider, BlockPos relativePos) {
    BlockPos pos = helper.absolutePos(relativePos);
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    return rider.getMainHandItem().useOn(new UseOnContext(rider, InteractionHand.MAIN_HAND, hit));
  }
}
