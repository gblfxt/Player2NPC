package com.gblfxt.llmoblings.ai;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public class CompanionAction {
    private final String action;
    private final String message;
    private final JsonObject data;

    public CompanionAction(String action, @Nullable String message) {
        this(action, message, new JsonObject());
    }

    public CompanionAction(String action, @Nullable String message, JsonObject data) {
        this.action = action;
        this.message = message;
        this.data = data;
    }

    public static CompanionAction fromJson(JsonObject json) {
        String action = json.has("action") ? json.get("action").getAsString() : "idle";
        String message = json.has("message") ? json.get("message").getAsString() : null;
        return new CompanionAction(action, message, json);
    }

    public String getAction() {
        return action;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public String getString(String key, String defaultValue) {
        return data.has(key) ? data.get(key).getAsString() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        return data.has(key) ? data.get(key).getAsInt() : defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        return data.has(key) ? data.get(key).getAsDouble() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return data.has(key) ? data.get(key).getAsBoolean() : defaultValue;
    }

    public boolean has(String key) {
        return data.has(key);
    }

    public void setParameter(String key, String value) {
        data.addProperty(key, value);
    }

    public void setParameter(String key, int value) {
        data.addProperty(key, value);
    }

    @Override
    public String toString() {
        return "CompanionAction{action='" + action + "', message='" + message + "', data=" + data + "}";
    }
}
