package io.github.utyoinog.yagamiime;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class YagamiInputMethodService extends InputMethodService {
    private static final int BACKGROUND = Color.rgb(209, 213, 219);
    private static final int KEY_BACKGROUND = Color.rgb(250, 250, 250);
    private static final int SPECIAL_BACKGROUND = Color.rgb(174, 180, 189);
    private static final int PRESSED_BACKGROUND = Color.rgb(205, 208, 213);
    private static final int ACCENT = Color.rgb(49, 92, 74);
    private static final int PAGE_LETTERS = 0;
    private static final int PAGE_NUMBERS = 1;
    private static final int PAGE_SYMBOLS = 2;
    private static final long DELETE_REPEAT_INTERVAL_MS = 55;

    private final Handler deleteHandler = new Handler(Looper.getMainLooper());
    private final Runnable beginRepeatedBackspace = this::startRepeatedBackspace;
    private final Runnable repeatedBackspace = this::repeatBackspace;
    private NativeBridge nativeBridge;
    private ClipboardHistory clipboardHistory;
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;
    private LinearLayout candidates;
    private LinearLayout keyRows;
    private TextView modeKey;
    private TextView shiftKey;
    private TextView clipboardKey;
    private final List<TextView> letterKeys = new ArrayList<>();
    private String learningLanguage;
    private boolean showingClipboard;
    private boolean chinese = true;
    private boolean deletingRepeatedly;
    private boolean shifted;
    private int keyboardPage = PAGE_LETTERS;

    @Override
    public void onCreate() {
        super.onCreate();
        clipboardHistory = new ClipboardHistory(this);
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboardListener = this::capturePrimaryClipboard;
        if (clipboardManager != null) {
            clipboardManager.addPrimaryClipChangedListener(clipboardListener);
        }
        reloadNativeBridge();
    }

    private void reloadNativeBridge() {
        String selectedLanguage = AppSettings.learningLanguage(this);
        if (nativeBridge != null && selectedLanguage.equals(learningLanguage)) {
            return;
        }
        try {
            File dictionary = copyAsset("dict.tsv");
            File glossary = copyAsset(AppSettings.glossaryAsset(selectedLanguage));
            NativeBridge replacement = new NativeBridge(
                    dictionary.getAbsolutePath(), glossary.getAbsolutePath());
            if (nativeBridge != null) {
                nativeBridge.close();
            }
            nativeBridge = replacement;
            learningLanguage = selectedLanguage;
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.startup_failed, error.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public View onCreateInputView() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(3), 0, dp(3), dp(5));
        keyboard.setBackgroundColor(BACKGROUND);
        LinearLayout candidateBar = new LinearLayout(this);
        candidateBar.setOrientation(LinearLayout.HORIZONTAL);
        candidateBar.setGravity(Gravity.CENTER_VERTICAL);
        candidateBar.setBackgroundColor(Color.rgb(247, 248, 249));
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setBackgroundColor(Color.rgb(247, 248, 249));
        candidates = new LinearLayout(this);
        candidates.setOrientation(LinearLayout.HORIZONTAL);
        candidates.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(candidates, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(58)));
        candidateBar.addView(scroller, new LinearLayout.LayoutParams(0, dp(58), 1));
        clipboardKey = specialKey("剪贴板");
        clipboardKey.setTextSize(13);
        clipboardKey.setContentDescription(getString(R.string.clipboard));
        clipboardKey.setOnClickListener(view -> toggleClipboardHistory());
        LinearLayout.LayoutParams clipboardParams = new LinearLayout.LayoutParams(dp(62), dp(48));
        clipboardParams.setMargins(dp(3), dp(5), dp(3), dp(5));
        candidateBar.addView(clipboardKey, clipboardParams);
        keyboard.addView(candidateBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        keyRows = new LinearLayout(this);
        keyRows.setOrientation(LinearLayout.VERTICAL);
        keyboard.addView(keyRows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        showLetterLayout();
        updateCandidates();
        return keyboard;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        reloadNativeBridge();
        capturePrimaryClipboard();
        showingClipboard = false;
        if (clipboardKey != null) {
            clipboardKey.setText(R.string.clipboard);
        }
        clearComposition();
        showLetterLayout();
    }

    @Override
    public void onFinishInput() {
        stopRepeatedBackspace();
        clearComposition();
        super.onFinishInput();
    }

    @Override
    public void onDestroy() {
        stopRepeatedBackspace();
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }
        if (nativeBridge != null) {
            nativeBridge.close();
        }
        super.onDestroy();
    }

    private void addLetterRow(LinearLayout keyboard, String letters, int sideInset) {
        LinearLayout row = row();
        row.setPadding(sideInset, 0, sideInset, 0);
        for (int index = 0; index < letters.length(); index++) {
            char letter = letters.charAt(index);
            TextView key = key(String.valueOf(letter));
            key.setTag(letter);
            letterKeys.add(key);
            key.setOnClickListener(view -> letter(letter));
            row.addView(key, weightedKey());
        }
        keyboard.addView(row, rowParams());
    }

    private void showLetterLayout() {
        if (keyRows == null) {
            return;
        }
        setShifted(false);
        keyboardPage = PAGE_LETTERS;
        keyRows.removeAllViews();
        letterKeys.clear();
        addLetterRow(keyRows, "qwertyuiop", 0);
        addLetterRow(keyRows, "asdfghjkl", dp(17));
        addThirdRow(keyRows);
        addLetterBottomRow(keyRows);
    }

    private void showNumberLayout() {
        if (hasComposition()) {
            commitCandidate(0);
        }
        keyboardPage = PAGE_NUMBERS;
        showSymbolRows(
                new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
                new String[]{"-", "/", ":", ";", "(", ")", "$", "&", "@", "\""},
                "#+=");
    }

    private void showSymbolLayout() {
        keyboardPage = PAGE_SYMBOLS;
        showSymbolRows(
                new String[]{"[", "]", "{", "}", "#", "%", "^", "*", "+", "="},
                new String[]{"_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"},
                "123");
    }

    private void showSymbolRows(String[] firstRow, String[] secondRow, String pageLabel) {
        if (keyRows == null) {
            return;
        }
        setShifted(false);
        keyRows.removeAllViews();
        letterKeys.clear();
        shiftKey = null;
        addSymbolRow(keyRows, firstRow, 0);
        addSymbolRow(keyRows, secondRow, dp(10));
        addSymbolThirdRow(keyRows, pageLabel);
        addSymbolBottomRow(keyRows);
    }

    private void addSymbolRow(LinearLayout keyboard, String[] symbols, int sideInset) {
        LinearLayout row = row();
        row.setPadding(sideInset, 0, sideInset, 0);
        for (String symbol : symbols) {
            TextView key = key(symbol);
            key.setOnClickListener(view -> punctuation(symbol));
            row.addView(key, weightedKey());
        }
        keyboard.addView(row, rowParams());
    }

    private void addSymbolThirdRow(LinearLayout keyboard, String pageLabel) {
        LinearLayout row = row();
        TextView page = specialKey(pageLabel);
        page.setOnClickListener(view -> {
            if (keyboardPage == PAGE_NUMBERS) {
                showSymbolLayout();
            } else {
                showNumberLayout();
            }
        });
        row.addView(page, weightedKey(1.35f));

        for (String symbol : new String[]{".", ",", "?", "!", "'"}) {
            TextView key = key(symbol);
            key.setOnClickListener(view -> punctuation(symbol));
            row.addView(key, weightedKey());
        }

        row.addView(deleteKey(), weightedKey(1.35f));
        keyboard.addView(row, rowParams());
    }

    // 触摸监听只接管长按计时，短按与无障碍操作都会回到标准 performClick。
    @SuppressLint("ClickableViewAccessibility")
    private void addThirdRow(LinearLayout keyboard) {
        LinearLayout row = row();
        shiftKey = specialKey("⇧");
        shiftKey.setOnClickListener(view -> toggleShift());
        row.addView(shiftKey, weightedKey(1.35f));

        String letters = "zxcvbnm";
        for (int index = 0; index < letters.length(); index++) {
            char letter = letters.charAt(index);
            TextView key = key(String.valueOf(letter));
            key.setTag(letter);
            letterKeys.add(key);
            key.setOnClickListener(view -> letter(letter));
            row.addView(key, weightedKey());
        }

        row.addView(deleteKey(), weightedKey(1.35f));
        keyboard.addView(row, rowParams());
    }

    @SuppressLint("ClickableViewAccessibility")
    private TextView deleteKey() {
        TextView delete = specialKey("⌫");
        delete.setOnClickListener(view -> backspace());
        delete.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                stopRepeatedBackspace();
                view.setPressed(true);
                deleteHandler.postDelayed(
                        beginRepeatedBackspace, ViewConfiguration.getLongPressTimeout());
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                boolean repeated = deletingRepeatedly;
                stopRepeatedBackspace();
                view.setPressed(false);
                if (!repeated) {
                    view.performClick();
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                stopRepeatedBackspace();
                view.setPressed(false);
                return true;
            }
            return true;
        });
        return delete;
    }

    private void addLetterBottomRow(LinearLayout keyboard) {
        LinearLayout row = row();
        TextView numbers = specialKey("123");
        numbers.setOnClickListener(view -> showNumberLayout());
        row.addView(numbers, weightedKey(1.1f));

        modeKey = specialKey(chinese ? "中" : "EN");
        modeKey.setTextColor(chinese ? ACCENT : Color.DKGRAY);
        modeKey.setOnClickListener(view -> toggleMode());
        row.addView(modeKey, weightedKey(1f));

        TextView comma = specialKey("，");
        comma.setOnClickListener(view -> punctuation(chinese ? "，" : ","));
        row.addView(comma, weightedKey(0.8f));

        TextView space = key("空格");
        space.setOnClickListener(view -> space());
        space.setOnLongClickListener(view -> {
            nextInputMethod();
            return true;
        });
        row.addView(space, weightedKey(4.4f));

        TextView period = specialKey("。");
        period.setOnClickListener(view -> punctuation(chinese ? "。" : "."));
        row.addView(period, weightedKey(0.8f));

        TextView enter = key("换行", ACCENT);
        enter.setTextColor(Color.WHITE);
        enter.setTextSize(14);
        enter.setOnClickListener(view -> enter());
        row.addView(enter, weightedKey(2.1f));
        keyboard.addView(row, rowParams());
    }

    private void addSymbolBottomRow(LinearLayout keyboard) {
        LinearLayout row = row();
        TextView letters = specialKey("ABC");
        letters.setOnClickListener(view -> showLetterLayout());
        row.addView(letters, weightedKey(1.1f));

        modeKey = specialKey(chinese ? "中" : "EN");
        modeKey.setTextColor(chinese ? ACCENT : Color.DKGRAY);
        modeKey.setOnClickListener(view -> toggleMode());
        row.addView(modeKey, weightedKey(1f));

        TextView space = key("空格");
        space.setOnClickListener(view -> space());
        space.setOnLongClickListener(view -> {
            nextInputMethod();
            return true;
        });
        row.addView(space, weightedKey(4.8f));

        TextView enter = key("换行", ACCENT);
        enter.setTextColor(Color.WHITE);
        enter.setTextSize(14);
        enter.setOnClickListener(view -> enter());
        row.addView(enter, weightedKey(2.1f));
        keyboard.addView(row, rowParams());
    }

    private void letter(char letter) {
        hideClipboardHistory();
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        char output = shifted ? Character.toUpperCase(letter) : letter;
        if (shifted) {
            setShifted(false);
        }
        if (!chinese || nativeBridge == null || Character.isUpperCase(output)) {
            if (chinese && hasComposition()) {
                commitCandidate(0);
            }
            connection.commitText(String.valueOf(output), 1);
            return;
        }
        nativeBridge.push(output);
        updateCandidates();
    }

    private void toggleShift() {
        setShifted(!shifted);
    }

    private void setShifted(boolean enabled) {
        shifted = enabled;
        for (TextView letterKey : letterKeys) {
            char letter = (char) letterKey.getTag();
            letterKey.setText(String.valueOf(enabled ? Character.toUpperCase(letter) : letter));
        }
        if (shiftKey != null) {
            shiftKey.setTextColor(enabled ? Color.WHITE : Color.rgb(31, 34, 38));
            shiftKey.setBackground(keyBackground(enabled ? ACCENT : SPECIAL_BACKGROUND));
        }
    }

    private void nextInputMethod() {
        boolean switched = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && switchToNextInputMethod(false);
        if (!switched) {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            manager.showInputMethodPicker();
        }
    }

    private void capturePrimaryClipboard() {
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            return;
        }
        ClipData clip = clipboardManager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence value = clip.getItemAt(0).coerceToText(this);
        if (value != null) {
            clipboardHistory.add(value.toString());
            if (showingClipboard) {
                showClipboardHistory();
            }
        }
    }

    private void toggleClipboardHistory() {
        showingClipboard = !showingClipboard;
        clipboardKey.setText(showingClipboard ? R.string.back_to_candidates : R.string.clipboard);
        if (showingClipboard) {
            capturePrimaryClipboard();
            showClipboardHistory();
        } else {
            updateCandidates();
        }
    }

    private void hideClipboardHistory() {
        if (!showingClipboard) {
            return;
        }
        showingClipboard = false;
        if (clipboardKey != null) {
            clipboardKey.setText(R.string.clipboard);
        }
        updateCandidates();
    }

    private void showClipboardHistory() {
        if (candidates == null) {
            return;
        }
        candidates.removeAllViews();
        List<String> items = clipboardHistory.items();
        if (items.isEmpty()) {
            hint(getString(R.string.clipboard_empty));
            return;
        }
        for (String item : items) {
            TextView entry = new TextView(this);
            entry.setGravity(Gravity.CENTER_VERTICAL);
            entry.setPadding(dp(14), 0, dp(14), 0);
            entry.setTextSize(15);
            entry.setTextColor(Color.rgb(24, 40, 34));
            entry.setSingleLine(true);
            entry.setEllipsize(TextUtils.TruncateAt.END);
            entry.setMaxWidth(dp(220));
            entry.setText(item.replaceAll("\\s+", " "));
            entry.setBackground(keyBackground(Color.rgb(247, 248, 249)));
            entry.setOnClickListener(view -> pasteClipboardItem(item));
            candidates.addView(entry, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(58)));
        }
    }

    private void pasteClipboardItem(String text) {
        if (hasComposition()) {
            commitCandidate(0);
        }
        commitText(text);
        hideClipboardHistory();
    }

    private void space() {
        if (chinese && hasComposition()) {
            commitCandidate(0);
        } else {
            commitText(" ");
        }
    }

    private void punctuation(String punctuation) {
        if (chinese && hasComposition()) {
            commitCandidate(0);
        }
        commitText(punctuation);
    }

    private void enter() {
        if (chinese && hasComposition() && nativeBridge != null) {
            String raw = nativeBridge.takeRaw();
            commitText(raw);
            updateCandidates();
            return;
        }
        EditorInfo info = getCurrentInputEditorInfo();
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        int action = info == null
                ? EditorInfo.IME_ACTION_UNSPECIFIED
                : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (acceptsNewline(info)
                || action == EditorInfo.IME_ACTION_NONE
                || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            connection.commitText("\n", 1);
        } else {
            connection.performEditorAction(action);
        }
    }

    private boolean acceptsNewline(EditorInfo info) {
        if (info == null) {
            return true;
        }
        boolean text = (info.inputType & InputType.TYPE_MASK_CLASS)
                == InputType.TYPE_CLASS_TEXT;
        boolean multiline = (info.inputType & (InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) != 0;
        boolean noEnterAction = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        return text && (multiline || noEnterAction);
    }

    private void backspace() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        if (chinese && nativeBridge != null && nativeBridge.backspace()) {
            updateCandidates();
        } else {
            connection.deleteSurroundingText(1, 0);
        }
    }

    private void startRepeatedBackspace() {
        deletingRepeatedly = true;
        backspace();
        deleteHandler.postDelayed(repeatedBackspace, DELETE_REPEAT_INTERVAL_MS);
    }

    private void repeatBackspace() {
        if (!deletingRepeatedly) {
            return;
        }
        backspace();
        deleteHandler.postDelayed(repeatedBackspace, DELETE_REPEAT_INTERVAL_MS);
    }

    private void stopRepeatedBackspace() {
        deletingRepeatedly = false;
        deleteHandler.removeCallbacks(beginRepeatedBackspace);
        deleteHandler.removeCallbacks(repeatedBackspace);
    }

    private void toggleMode() {
        if (hasComposition()) {
            commitCandidate(0);
        }
        chinese = !chinese;
        modeKey.setText(chinese ? "中" : "EN");
        modeKey.setTextColor(chinese ? ACCENT : Color.DKGRAY);
    }

    private void commitCandidate(int index) {
        if (nativeBridge == null) {
            return;
        }
        String text = nativeBridge.commit(index);
        if (text.isEmpty()) {
            text = nativeBridge.takeRaw();
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(text, 1);
        }
        updateCandidates();
    }

    private void updateCandidates() {
        if (candidates == null) {
            return;
        }
        if (showingClipboard) {
            showClipboardHistory();
            return;
        }
        candidates.removeAllViews();
        if (nativeBridge == null || !chinese) {
            hint("English");
            return;
        }
        try {
            JSONObject snapshot = new JSONObject(nativeBridge.snapshot());
            String preedit = snapshot.optString("preedit");
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) {
                if (preedit.isEmpty()) {
                    connection.setComposingText("", 1);
                    connection.finishComposingText();
                } else {
                    connection.setComposingText(preedit, 1);
                }
            }
            JSONArray items = snapshot.getJSONArray("candidates");
            if (items.length() == 0) {
                hint(preedit.isEmpty() ? getString(R.string.idle_hint) : preedit);
                return;
            }
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.getJSONObject(index);
                String text = item.getString("text");
                String gloss = item.optString("gloss");
                TextView candidate = new TextView(this);
                candidate.setGravity(Gravity.CENTER);
                candidate.setPadding(dp(16), dp(3), dp(16), dp(3));
                candidate.setTextSize(19);
                candidate.setTextColor(Color.rgb(24, 40, 34));
                candidate.setBackground(keyBackground(Color.rgb(247, 248, 249)));
                candidate.setText(candidateText(text, gloss));
                int selected = index;
                candidate.setOnClickListener(view -> commitCandidate(selected));
                candidates.addView(candidate, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(58)));
            }
        } catch (Exception error) {
            hint("候选加载失败");
        }
    }

    private boolean hasComposition() {
        if (nativeBridge == null) {
            return false;
        }
        try {
            return !new JSONObject(nativeBridge.snapshot()).optString("preedit").isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void clearComposition() {
        if (nativeBridge != null) {
            nativeBridge.clear();
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.setComposingText("", 1);
            connection.finishComposingText();
        }
        updateCandidates();
    }

    private void commitText(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(text, 1);
        }
    }

    private void hint(String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextSize(15);
        hint.setTextColor(Color.GRAY);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(dp(14), 0, 0, 0);
        candidates.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(58)));
    }

    private CharSequence candidateText(String text, String gloss) {
        if (gloss.isEmpty()) {
            return text;
        }
        SpannableString value = new SpannableString(text + "\n" + gloss);
        int glossStart = text.length() + 1;
        value.setSpan(new RelativeSizeSpan(0.62f), glossStart, value.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new ForegroundColorSpan(Color.rgb(112, 116, 121)),
                glossStart, value.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private TextView key(String label) {
        return key(label, KEY_BACKGROUND);
    }

    private TextView specialKey(String label) {
        TextView key = key(label, SPECIAL_BACKGROUND);
        key.setTextSize(15);
        return key;
    }

    private TextView key(String label, int backgroundColor) {
        TextView key = new TextView(this);
        key.setText(label);
        key.setTextSize(21);
        key.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        key.setTextColor(Color.rgb(31, 34, 38));
        key.setGravity(Gravity.CENTER);
        key.setBackground(keyBackground(backgroundColor));
        key.setElevation(dp(1));
        key.setPadding(0, 0, 0, dp(1));
        key.setClickable(true);
        key.setFocusable(true);
        return key;
    }

    private StateListDrawable keyBackground(int color) {
        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[]{android.R.attr.state_pressed},
                roundedDrawable(PRESSED_BACKGROUND));
        selector.addState(new int[]{}, roundedDrawable(color));
        return selector;
    }

    private GradientDrawable roundedDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private LinearLayout.LayoutParams weightedKey() {
        return weightedKey(1f);
    }

    private LinearLayout.LayoutParams weightedKey(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(45), weight);
        params.setMargins(dp(3), dp(4), dp(3), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
    }

    private File copyAsset(String name) throws Exception {
        File directory = new File(getFilesDir(), "yagami-data");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建数据目录");
        }
        File target = new File(directory, name);
        if (target.exists() && target.length() > 0) {
            return target;
        }
        try (InputStream input = getAssets().open(name);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        }
        return target;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
