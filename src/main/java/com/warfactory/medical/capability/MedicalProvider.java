package com.warfactory.medical.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Attaches a {@link MedicalData} instance to a player entity and exposes it through the
 * {@link MedicalCapabilities#MEDICAL} capability. Serialization delegates to the data object.
 */
public final class MedicalProvider implements ICapabilitySerializable<CompoundTag> {

    private final IMedicalData data = new MedicalData();
    private final LazyOptional<IMedicalData> optional = LazyOptional.of(() -> data);

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        return MedicalCapabilities.MEDICAL.orEmpty(capability, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.save();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.load(nbt);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
