package io.github.utyoinog.yagamiime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

public final class SetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int padding = dp(24);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(padding, padding * 2, padding, padding);
        page.setBackgroundColor(Color.rgb(248, 250, 249));

        TextView title = text(getString(R.string.setup_title), 24);
        title.setTextColor(Color.rgb(28, 50, 41));
        page.addView(title, matchWrap());

        TextView body = text(getString(R.string.setup_body), 16);
        body.setTextColor(Color.DKGRAY);
        body.setPadding(0, dp(24), 0, dp(24));
        page.addView(body, matchWrap());

        TextView languageTitle = text(getString(R.string.learning_language), 16);
        languageTitle.setTextColor(Color.rgb(28, 50, 41));
        languageTitle.setPadding(0, 0, 0, dp(8));
        page.addView(languageTitle, matchWrap());

        Spinner language = new Spinner(this);
        ArrayAdapter<CharSequence> languages = ArrayAdapter.createFromResource(
                this, R.array.learning_languages, android.R.layout.simple_spinner_item);
        languages.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        language.setAdapter(languages);
        language.setSelection(languageIndex(AppSettings.learningLanguage(this)), false);
        language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AppSettings.setLearningLanguage(
                        SetupActivity.this, AppSettings.LANGUAGE_CODES[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        LinearLayout.LayoutParams languageParams = matchWrap();
        languageParams.setMargins(0, 0, 0, dp(24));
        page.addView(language, languageParams);

        Button clearClipboard = new Button(this);
        clearClipboard.setText(R.string.clear_clipboard_history);
        clearClipboard.setOnClickListener(view -> {
            new ClipboardHistory(this).clear();
            clearClipboard.setText(R.string.clipboard_history_cleared);
        });
        page.addView(clearClipboard, matchWrap());

        Button enable = new Button(this);
        enable.setText(R.string.enable_ime);
        enable.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        page.addView(enable, matchWrap());

        Button choose = new Button(this);
        choose.setText(R.string.select_ime);
        choose.setOnClickListener(view -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            manager.showInputMethodPicker();
        });
        page.addView(choose, matchWrap());
        setContentView(page);
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private int languageIndex(String language) {
        for (int index = 0; index < AppSettings.LANGUAGE_CODES.length; index++) {
            if (AppSettings.LANGUAGE_CODES[index].equals(language)) {
                return index;
            }
        }
        return 0;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
