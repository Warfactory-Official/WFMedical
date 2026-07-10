package com.warfactory.medical.client;

public final class UiText {

    private UiText() {
    }


    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.indexOf('%') < 0 ? raw : raw.replace("%", "%%");
    }
}
