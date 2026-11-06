package com.warfactory.medical.ragdoll;

import com.warfactory.medical.mixin.ragdoll.RagdollAccess;
import de.maxhenkel.corpse.entities.CorpseEntity;
import de.sarocesch.sarosragdoll.ragdoll.RagdollEntity;
import de.sarocesch.sarosragdoll.ragdoll.physics.EnumRagdollBodyPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/** Physics proxy only: the original patient/Corpse owns all medical state and inventory. */
public final class MedicalRagdollEntity extends RagdollEntity {
    private static final EntityDataAccessor<Optional<UUID>> TARGET = SynchedEntityData.defineId(MedicalRagdollEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(MedicalRagdollEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(MedicalRagdollEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(MedicalRagdollEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CORPSE = SynchedEntityData.defineId(MedicalRagdollEntity.class, EntityDataSerializers.BOOLEAN);
    private ListTag pendingPose;
    private int restoreAttempts;
    private int missingTicks;
    private int deadTicks;
    private boolean seenTarget;
    private boolean needsCollapse = true;

    public MedicalRagdollEntity(EntityType<?> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, Optional.empty());
        builder.define(OWNER, Optional.empty());
        builder.define(NAME, "");
        builder.define(TARGET_ID, -1);
        builder.define(CORPSE, false);
    }

    public void bind(Entity source) {
        entityData.set(TARGET, Optional.of(source.getUUID()));
        entityData.set(TARGET_ID, source.getId());
        entityData.set(CORPSE, source instanceof CorpseEntity);
        if (source instanceof Player player) {
            entityData.set(OWNER, Optional.of(player.getUUID()));
            entityData.set(NAME, player.getGameProfile().getName());
        } else if (source instanceof CorpseEntity corpse) {
            entityData.set(OWNER, corpse.getCorpseUUID());
            entityData.set(NAME, corpse.getCorpseName());
        }
        setSkin(ownerName());
        ((RagdollAccess) (Object) this).wfmedical$spawnYaw(source.getYRot() + 180);
        missingTicks = 0;
    }

    public @Nullable UUID targetUuid() { return entityData.get(TARGET).orElse(null); }
    public @Nullable UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public String ownerName() { return entityData.get(NAME); }
    public boolean isCorpse() { return entityData.get(CORPSE); }

    public @Nullable Entity target() {
        UUID uuid = targetUuid();
        if (uuid == null) return null;
        Entity source = level().getEntity(entityData.get(TARGET_ID));
        if (source != null && uuid.equals(source.getUUID())) return source;
        if (level() instanceof ServerLevel server) {
            source = server.getEntity(uuid);
            if (source != null) entityData.set(TARGET_ID, source.getId());
            return source;
        }
        // Replay seeks may recreate entities with different runtime IDs; UUIDs remain stable.
        return level().getEntities(this, getBoundingBox().inflate(8), entity -> uuid.equals(entity.getUUID()))
                .stream().findFirst().orElse(null);
    }

    @Override public void applyTransform(EnumRagdollBodyPart part, com.jme3.math.Vector3f position, com.jme3.math.Quaternion rotation) {
        boolean fresh = getPartTransform(part) == null;
        super.applyTransform(part, position, rotation);
        if (fresh) {
            var transform = getPartTransform(part);
            transform.getTransform().set(transform.getPhysicTransform());
            transform.getPrevTransform().set(transform.getPhysicTransform());
        }
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide() && needsCollapse) {
            var physics = ((RagdollAccess) (Object) this).wfmedical$physics();
            if (physics != null && physics.isInWorld()) {
                double yaw = Math.toRadians(getYRot());
                // An idle, perfectly balanced upright rig otherwise never falls over.
                physics.getBody(EnumRagdollBodyPart.CHEST).setAngularVelocity(new com.jme3.math.Vector3f(
                        (float) Math.cos(yaw) * 1.5F, 0, (float) Math.sin(yaw) * 1.5F));
                needsCollapse = false;
            }
        }
        if (pendingPose != null && !level().isClientSide()) {
            if (RagdollPhysicsState.restore(this, pendingPose)) {
                var chest = getPartTransform(EnumRagdollBodyPart.CHEST).getTransform().getPosition();
                setPos(chest.x, chest.y, chest.z);
                pendingPose = null;
            } else if (++restoreAttempts >= 20) pendingPose = null;
        }
        setBoundingBox(RagdollGeometry.bounds(this));
        if (level().isClientSide()) return;
        Entity source = target();
        if (source == null || source.isRemoved()) {
            if (seenTarget || ++missingTicks > 400) discard();
            return;
        }
        missingTicks = 0;
        seenTarget = true;
        if (source instanceof ServerPlayer player) {
            if (!player.isAlive()) {
                // Give native Corpse creation time to rebind; keepInventory may intentionally create none.
                if (++deadTicks > 100) discard();
                return;
            }
            if (!Ragdolls.isManaged(player)) {
                Vec3 safe = RagdollGeometry.recoveryPosition(player, this);
                player.teleportTo(safe.x, safe.y, safe.z);
                discard();
                return;
            }
        } else if (!(source instanceof CorpseEntity corpse) || corpse.isSkeleton()) {
            discard();
            return;
        }
        if (getY() < level().getMinBuildHeight()) { discard(); return; }
        if (getPartTransform(EnumRagdollBodyPart.CHEST) != null) {
            if (source instanceof ServerPlayer player && player.distanceToSqr(this) > .0025)
                player.teleportTo(getX(), getY(), getZ());
            else source.setPos(getX(), getY(), getZ());
            source.setDeltaMovement(Vec3.ZERO);
            source.fallDistance = 0;
        }
    }

    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!RagdollGeometry.canReach(player, this)) return InteractionResult.FAIL;
        return target() instanceof CorpseEntity corpse ? corpse.interact(player, hand) : InteractionResult.PASS;
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return false;
        Entity target = target();
        if (target instanceof Player player && player.isAlive()
                && source.getDirectEntity() != null
                && !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return player.hurt(source, amount);
        // A hit may move the body but must never delete the inventory-bearing corpse.
        if (source.getSourcePosition() != null) {
            setDeltaMovement(position().subtract(source.getSourcePosition()).normalize().scale(.1));
            wakePhysics();
        }
        return true;
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetUuid() != null) tag.putUUID("MedicalTarget", targetUuid());
        if (ownerId() != null) tag.putUUID("MedicalOwner", ownerId());
        tag.putString("MedicalName", ownerName());
        tag.putBoolean("MedicalCorpse", isCorpse());
        tag.put("MedicalPose", pendingPose == null ? RagdollPhysicsState.save(this) : pendingPose.copy());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        // Never restore Saro's attached-player/inventory mechanism for our proxies.
        needsCollapse = false;
        setSkin(tag.getString("MedicalName"));
        entityData.set(TARGET, tag.hasUUID("MedicalTarget") ? Optional.of(tag.getUUID("MedicalTarget")) : Optional.empty());
        entityData.set(OWNER, tag.hasUUID("MedicalOwner") ? Optional.of(tag.getUUID("MedicalOwner")) : Optional.empty());
        entityData.set(NAME, tag.getString("MedicalName"));
        entityData.set(CORPSE, tag.getBoolean("MedicalCorpse"));
        pendingPose = tag.getList("MedicalPose", Tag.TAG_COMPOUND);
        if (pendingPose.isEmpty()) pendingPose = null;
    }
}
