package com.warfactory.medical.network;

import com.warfactory.medical.client.PoseStreamClient;
import com.warfactory.medical.core.damage.HitAuthority;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * AUTHORITY-MODE notice, server -> client. Tells a joining client whether the server wants it to stream its
 * own posed rig ({@link HitAuthority#CLIENT_HINT}) or not ({@link HitAuthority#SERVER}). The server is the
 * sole authority on the mode; the client only obeys. Sent once on login (a live mode change takes effect on
 * the client's next reconnect).
 *
 * <p>Side-safety: {@link #handleClient()} isolates the client mutation behind {@link DistExecutor} so this
 * class never classloads the client-only {@code PoseStreamClient} on a dedicated server.</p>
 */
public record HitAuthorityPacket(boolean streamPose) {

    public static HitAuthorityPacket decode(FriendlyByteBuf buf) {
        return new HitAuthorityPacket(buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(streamPose);
    }

    /**
     * Client-thread handler: enable or disable this client's pose streaming.
     */
    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> PoseStreamClient.setEnabled(streamPose));
    }
}
