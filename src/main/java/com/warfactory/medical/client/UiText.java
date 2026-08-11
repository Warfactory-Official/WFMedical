package com.warfactory.medical.client;

public final class UiText {

    private UiText() {
    }


    /**
     * Doubles {@code %} so a literal percent survives format substitution.
     *
     * <p>Only needed for text handed to LDLib's {@code TextTexture}, which pushes it through
     * {@code LocalizationUtils.format} -> {@code I18n} and therefore treats {@code %} as a format
     * specifier. Text built into a {@code Component.literal} (the LDLib2 {@code Label} path) is NOT
     * substituted -- escaping it there renders a visible {@code %%}.
     */
    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.indexOf('%') < 0 ? raw : raw.replace("%", "%%");
    }
}
