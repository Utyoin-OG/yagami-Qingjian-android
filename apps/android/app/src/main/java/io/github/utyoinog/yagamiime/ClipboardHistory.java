package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ClipboardHistory {
    private static final int LIMIT = 50;
    private static final String PREFERENCES = "yagami_clipboard";
    private static final String ITEMS = "items";

    private final SharedPreferences preferences;

    ClipboardHistory(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    void add(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<String> items = new ArrayList<>(items());
        items.remove(text);
        items.add(0, text);
        if (items.size() > LIMIT) {
            items = new ArrayList<>(items.subList(0, LIMIT));
        }
        JSONArray array = new JSONArray();
        for (String item : items) {
            array.put(item);
        }
        preferences.edit().putString(ITEMS, array.toString()).apply();
    }

    List<String> items() {
        String stored = preferences.getString(ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(stored);
            List<String> items = new ArrayList<>(Math.min(array.length(), LIMIT));
            for (int index = 0; index < array.length() && index < LIMIT; index++) {
                String item = array.optString(index);
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
            return Collections.unmodifiableList(items);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    void clear() {
        preferences.edit().remove(ITEMS).apply();
    }
}
