package com.warfactory.medical.core.damage;

/**
 * Who computes the posed limb rig used to classify a hit against a player / persistent body.
 *
 * <p>WFMedical is a <b>reactive</b> damage interceptor (it hooks {@code LivingHurtEvent} server-side after
 * TACZ / vanilla dealt the damage) rather than an originator of shots, so it cannot be client-authoritative
 * the way a fire-from-the-client mod (e.g. MWF-SHINING) is. The trade instead offloads only the expensive
 * <i>pose rebuild</i>:</p>
 *
 * <ul>
 *   <li>{@link #SERVER} — the server rebuilds the victim's rig itself via {@code HumanoidRig.compute}
 *       (backed by the per-tick cache). Fully authoritative and deterministic; the default.</li>
 *   <li>{@link #CLIENT_HINT} — the victim's client streams its own posed rig to the server; the server
 *       <b>still runs the ray test itself</b> (so an attacker can never pick the limb) but skips the costly
 *       rebuild, validating the supplied pose against a cheap bound and falling back to {@link #SERVER}
 *       compute whenever the hint is absent, stale, or implausible. The victim only vouches for its own
 *       pose, never the hit outcome.</li>
 * </ul>
 */
public enum HitAuthority {
    /**
     * Server rebuilds the rig itself (authoritative, deterministic). Default.
     */
    SERVER,
    /**
     * Victim streams its own pose; server ray-tests + validates it, falling back to a server rebuild.
     */
    CLIENT_HINT
}
