package com.warfactory.medical.client;

/**
 * CLIENT-ONLY tiny text helpers for the LDLib-based UI.
 *
 * <p><b>Why this exists.</b> LDLib's {@code LabelWidget} and {@code TextTexture} route EVERY string they are
 * given through {@code LocalizationUtils.format(text)} → {@code I18n.get(text)}, which does
 * {@code String.format(translation, args)} on the (already-resolved) string. When our dynamic text contains a
 * literal percent sign — e.g. {@code "Health: 83%"} or {@code "Blood 90%"} — {@code String.format} throws an
 * {@link java.util.IllegalFormatException} and vanilla's {@code I18n.get} swallows it and returns
 * {@code "Format error: " + text}. That is the "Format error:" the player sees scattered across the medical
 * GUIs.</p>
 *
 * <p>Since we don't control LDLib, the fix is on our side: escape every {@code %} to {@code %%} before handing
 * a display string to a LabelWidget/TextTexture, so the downstream {@code String.format} emits a single literal
 * {@code %}. Strings without a {@code %} are returned unchanged, so this is safe to apply blindly — including to
 * translation keys and already-localized text (neither of which contains a bare {@code %}).</p>
 */
public final class UiText {

    private UiText() {
    }

    /**
     * Escape a raw display string for LDLib text widgets so a literal {@code %} renders as {@code %} instead of
     * triggering an {@code IllegalFormatException} → {@code "Format error: ..."}.
     *
     * @param raw the display string (nullable → treated as empty)
     * @return {@code raw} with every {@code %} doubled to {@code %%}; unchanged when it contains no {@code %}
     */
    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.indexOf('%') < 0 ? raw : raw.replace("%", "%%");
    }
}
