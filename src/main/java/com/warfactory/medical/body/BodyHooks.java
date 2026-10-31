package com.warfactory.medical.body;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wf.wflib.core.api.Hook;
import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Seams other WFMedical systems fill; body names none of them. */
public final class BodyHooks {

    /** Drives the profile's active treatment; unset => it never advances. */
    public static final Hook<ActiveTreatment> ACTIVE_TREATMENT = Hook.of("wfmedical active treatment",
            new ActiveTreatment() {
                @Override
                public void tick(ServerPlayer player, MedicalProfile profile, long nowTick) {
                }

                @Override
                public void cancel(ServerPlayer player, String reason) {
                }
            });

    /** Client-side reads; set by the client half. Unset => no local stats, nobody downed. */
    public static final Hook<ClientView> CLIENT = Hook.of("wfmedical client view", new ClientView() {
        @Override
        public DerivedStats localStats() {
            return null;
        }

        @Override
        public boolean isDowned(int entityId) {
            return false;
        }
    });

    /** Non-player entities carrying a medical profile (logout bodies). */
    public static final Hook<Predicate<Entity>> PERSISTENT_BODY = Hook.of("wfmedical persistent body", e -> false);

    /** Login: after the profile sync. */
    public static final OrderedRegistry<Consumer<ServerPlayer>> JOIN = OrderedRegistry.create("wfmedical join");

    /** {@code (viewer, target)} when a player starts tracking another, after the downed state. */
    public static final OrderedRegistry<BiConsumer<ServerPlayer, ServerPlayer>> START_TRACKING =
            OrderedRegistry.create("wfmedical start tracking");

    /** Subcommands under {@code /wfmedical}; descending priority = listing order. */
    public static final OrderedRegistry<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> SUBCOMMANDS =
            OrderedRegistry.create("wfmedical subcommands");

    private BodyHooks() {
    }

    public interface ActiveTreatment {
        void tick(ServerPlayer player, MedicalProfile profile, long nowTick);

        void cancel(ServerPlayer player, String reason);
    }

    public interface ClientView {
        @Nullable
        DerivedStats localStats();

        boolean isDowned(int entityId);
    }
}
