package com.warfactory.medical.ragdoll.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.BodyHooks;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.gametest.TestBodies;
import com.warfactory.medical.ragdoll.MedicalRagdollEntity;
import com.warfactory.medical.ragdoll.RagdollPhysicsState;
import com.warfactory.medical.ragdoll.Ragdolls;
import de.maxhenkel.corpse.corelib.death.Death;
import de.maxhenkel.corpse.entities.CorpseEntity;
import de.maxhenkel.corpse.gui.CorpseContainerBase;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RagdollGameTest {
    private static TestBodies.Victim patient(GameTestHelper helper, int x) {
        // Vanilla randomizes test coordinates by millions of blocks; Bullet uses single-precision world positions.
        for (int bx = x - 6; bx <= x + 6; bx++) for (int bz = 2; bz <= 14; bz++) {
            helper.getLevel().setChunkForced(bx >> 4, bz >> 4, true);
            helper.getLevel().setBlockAndUpdate(new net.minecraft.core.BlockPos(bx, -61, bz),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        var patient = TestBodies.victim(helper);
        patient.setPos(x + .5, -60, 8.5);
        return TestBodies.spawn(helper, patient);
    }

    private static MedicalRagdollEntity body(GameTestHelper helper, Entity target) {
        var body = Ragdolls.TYPE.get().create(helper.getLevel());
        if (body == null) throw new AssertionError("ragdoll not registered");
        body.bind(target);
        body.setPos(target.getX(), target.getY() + .9, target.getZ());
        helper.getLevel().addFreshEntity(body);
        return body;
    }

    private static CorpseEntity corpse(GameTestHelper helper, TestBodies.Victim owner) {
        var items = NonNullList.withSize(36, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.DIAMOND, 7));
        var death = new Death.Builder(owner.getUUID(), UUID.randomUUID()).playerName("wfmed_victim")
                .mainInventory(items).armorInventory(NonNullList.withSize(4, ItemStack.EMPTY))
                .offHandInventory(NonNullList.withSize(1, ItemStack.EMPTY))
                .posX(owner.getX()).posY(owner.getY()).posZ(owner.getZ()).build();
        CorpseEntity corpse = CorpseEntity.createFromDeath(owner, death);
        helper.getLevel().addFreshEntity(corpse);
        return corpse;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public void medicalRecoveryRemovesOnlyTheProxy(GameTestHelper helper) {
        var patient = patient(helper, 8);
        patient.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
        var profile = TestBodies.profileOf(helper, patient);
        profile.setOverdoseUnconscious(true);
        profile.recompute(MedicalConfig.toPhysiologyParams());
        var rag = body(helper, patient);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!rag.isRemoved(), "downed ragdoll removed prematurely");
            helper.assertTrue(RagdollPhysicsState.save(rag).size() == 6, "native Bullet bodies did not build");
            helper.assertTrue(BodyHooks.TREATMENT_TARGET.get().apply(rag) == patient, "treatment targets wrong patient");
            helper.assertTrue(patient.getInventory().getItem(0).getCount() == 3, "downing changed inventory");
            helper.assertTrue(!patient.getAbilities().invulnerable && !patient.isInvisible(), "stock Saro took player authority");
            profile.setOverdoseUnconscious(false);
            profile.recompute(MedicalConfig.toPhysiologyParams());
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(rag.isRemoved() && patient.isAlive(), "medical recovery failed to remove proxy");
                helper.assertTrue(patient.getInventory().getItem(0).getCount() == 3, "revival changed inventory");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public void corpseGetsOneProxyAndItsOriginalLootMenu(GameTestHelper helper) {
        var owner = patient(helper, 24);
        var corpse = corpse(helper, owner);
        owner.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
        helper.runAfterDelay(40, () -> {
            var rag = Ragdolls.find(corpse);
            helper.assertTrue(rag != null, "native Corpse join did not create physics proxy");
            helper.assertTrue(RagdollPhysicsState.save(rag).size() == 6, "no native corpse physics");
            var up = rag.getPartTransform(de.sarocesch.sarosragdoll.ragdoll.physics.EnumRagdollBodyPart.CHEST)
                    .getTransform().getRotation().mult(com.jme3.math.Vector3f.UNIT_Y, new com.jme3.math.Vector3f());
            helper.assertTrue(Math.abs(up.y) < .8, "idle corpse remained balanced upright instead of collapsing");
            var looter = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(), owner.getGameProfile()) {
                @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,
                        java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extra) {
                    containerMenu = provider.createMenu(1, getInventory(), this);
                    return java.util.OptionalInt.of(1);
                }
            };
            looter.setPos(rag.getX(), rag.getY(), rag.getZ() - 1);
            rag.interact(looter, InteractionHand.MAIN_HAND);
            helper.assertTrue(looter.containerMenu instanceof CorpseContainerBase menu && menu.getCorpse() == corpse,
                    "right-click did not open the original Corpse inventory");
            helper.assertTrue(corpse.getDeath().getMainInventory().getFirst().getCount() == 7, "loot changed on interaction");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(MedicalRagdollEntity.class,
                    corpse.getBoundingBox().inflate(4), r -> corpse.getUUID().equals(r.targetUuid())).size() == 1, "duplicate proxy");
            var menu = looter.containerMenu;
            int lootSlot = java.util.stream.IntStream.range(0, menu.slots.size())
                    .filter(i -> menu.getSlot(i).getItem().is(Items.DIAMOND)).findFirst().orElseThrow();
            menu.quickMoveStack(looter, lootSlot);
            helper.assertTrue(looter.getInventory().countItem(Items.DIAMOND) == 7
                    && corpse.getDeath().getMainInventory().getFirst().isEmpty(), "native menu didn't transfer loot exactly once");
            looter.closeContainer();
            looter.setPos(rag.getX() + 12, rag.getY(), rag.getZ());
            helper.assertTrue(rag.interact(looter, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "remote loot allowed");
            rag.hurt(owner.damageSources().playerAttack(owner), 1);
            helper.assertTrue(!rag.isRemoved() && !corpse.isRemoved(), "melee deleted a corpse");
            rag.discard();
            helper.assertTrue(!corpse.isRemoved() && corpse.getDeath().getMainInventory().getFirst().isEmpty()
                    && looter.getInventory().countItem(Items.DIAMOND) == 7,
                    "proxy removal destroyed or duplicated native loot");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public void actualDeathReusesTheDownedBodyWithoutDuplicatingLoot(GameTestHelper helper) {
        var connectionFixture = patient(helper, 56);
        var patient = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "wfmed_death"),
                net.minecraft.server.level.ClientInformation.createDefault());
        // FakePlayer suppresses die(); a real ServerPlayer plus its silent connection exercises native death/drop events.
        patient.connection = connectionFixture.connection;
        patient.setPos(connectionFixture.getX(), connectionFixture.getY(), connectionFixture.getZ());
        patient.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        connectionFixture.discard();
        helper.getLevel().addFreshEntity(patient);
        patient.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
        var profile = com.warfactory.medical.body.attachment.MedicalAttachments.get(patient).getProfile();
        profile.setBloodMl(profile.getMaxBloodMl() * .69);
        profile.setUnconsciousLatched(true);
        profile.recompute(MedicalConfig.toPhysiologyParams());
        var rag = body(helper, patient);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(RagdollPhysicsState.save(rag).size() == 6, "no downed native pose");
            patient.kill();
            helper.assertTrue(!patient.isAlive(), "fixture failed to cause actual death");
            helper.runAfterDelay(2, () -> {
                var corpses = helper.getLevel().getEntitiesOfClass(CorpseEntity.class,
                        rag.getBoundingBox().inflate(4), c -> c.getCorpseUUID().filter(patient.getUUID()::equals).isPresent());
                helper.assertTrue(corpses.size() == 1, "actual death did not create exactly one native corpse");
                var corpse = corpses.getFirst();
                helper.assertTrue(rag.target() == corpse && Ragdolls.find(corpse) == rag, "death replaced rather than rebound body");
                int gold = corpse.getDeath().getAllItems().stream().filter(i -> i.is(Items.GOLD_INGOT)).mapToInt(ItemStack::getCount).sum();
                helper.assertTrue(gold == 5 && patient.getInventory().getItem(0).isEmpty(), "death duplicated or lost inventory");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public void corpseLinkAndNativePoseSurviveSaveReload(GameTestHelper helper) {
        var owner = patient(helper, 40);
        var corpse = corpse(helper, owner);
        owner.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
        helper.runAfterDelay(40, () -> {
            var original = Ragdolls.find(corpse);
            helper.assertTrue(original != null, "no corpse proxy");
            CompoundTag saved = new CompoundTag();
            original.saveWithoutId(saved);
            helper.assertTrue(saved.getList("MedicalPose", 10).size() == 6, "native pose wasn't saved");
            original.discard();
            var restored = Ragdolls.TYPE.get().create(helper.getLevel());
            restored.load(saved);
            helper.getLevel().addFreshEntity(restored);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(restored.target() == corpse && corpse.getUUID().equals(restored.targetUuid()), "UUID link lost on reload");
                helper.assertTrue(RagdollPhysicsState.save(restored).size() == 6, "native pose not restored");
                helper.assertTrue(restored.distanceToSqr(corpse) < 1, "corpse anchor detached after reload");
                helper.assertTrue(corpse.getDeath().getMainInventory().getFirst().getCount() == 7, "reload changed loot");
                helper.succeed();
            });
        });
    }
}
