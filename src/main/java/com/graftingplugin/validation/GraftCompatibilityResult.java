package com.graftingplugin.validation;

public record GraftCompatibilityResult(boolean success, String message) {

    public static GraftCompatibilityResult ok() {
        return new GraftCompatibilityResult(true, "ok");
    }

    public static GraftCompatibilityResult failure(String message) {
        return new GraftCompatibilityResult(false, message);
    }
}
