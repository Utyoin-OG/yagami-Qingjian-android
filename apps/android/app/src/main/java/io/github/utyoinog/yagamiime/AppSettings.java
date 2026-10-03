package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    static final String[] LANGUAGE_CODES = {"en", "ja", "es"};

    private static final String PREFERENCES = "yagami_settings";
    private static final String LEARNING_LANGUAGE = "learning_language";
    private static final String DEFAULT_LANGUAGE = "en";

    private AppSettings() {
    }

    static String learningLanguage(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String language = preferences.getString(LEARNING_LANGUAGE, DEFAULT_LANGUAGE);
        for (String supported : LANGUAGE_CODES) {
            if (supported.equals(language)) {
                return language;
            }
        }
        return DEFAULT_LANGUAGE;
    }

    static void setLearningLanguage(Context context, String language) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(LEARNING_LANGUAGE, language)
                .apply();
    }

    static String glossaryAsset(String language) {
        return "glossary-" + language + ".tsv";
    }
}
