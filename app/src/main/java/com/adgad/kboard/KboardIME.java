package com.adgad.kboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;


import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;


/**
 * Created by arjun on 11/03/15.
 */
public class KboardIME  extends InputMethodService
    implements KboardView.OnKeyboardActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener{

    private static final String TAG = "kboard";
    private InputMethodManager mInputMethodManager;
    private SharedPreferences sharedPref;
    private KboardView kv;
    private KBoard keyboard;
    private Vibrator vib;
    private List<Keyboard.Key> mKeys;

    private List<String> keys;
    private boolean mPassiveAggressive;
    private boolean mAutoSpace;
    private boolean mAutoSend;
    private boolean mVibrateOnClick;
    private boolean mSoundOnClick;
    private int mScreen = 0;
    private int totalScreens = 0;
    private int mRows = 5;
    private int mKeysPerScreen = 12;
    private int mKeysPerRow = 4;



    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);

        mInputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        vib = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        initPrefs();
    }

    private void initPrefs() {
        mAutoSpace = sharedPref.getBoolean("autospace", true);
        mAutoSend = sharedPref.getBoolean("autosend", false);
        mVibrateOnClick = sharedPref.getBoolean("vibrate_on", false);
        mSoundOnClick = sharedPref.getBoolean("sound_on", false);
        mPassiveAggressive = sharedPref.getBoolean("passive_aggressive", false);
        mRows = Integer.parseInt(Objects.requireNonNull(sharedPref.getString("rows", "5")));
        mKeysPerRow = (mRows == 1) ? 1 : 4;
        mKeysPerScreen = mRows * mKeysPerRow;
        setKeys();
    }


    private void setKeys() {
        Gson gson = new Gson();

        String defaultJson = gson.toJson(Keys.getDefault());
        String keysAsString = sharedPref.getString(Keys.STORAGE_KEY, defaultJson);
        keys = gson.fromJson(keysAsString, ArrayList.class);
        totalScreens = (int)Math.ceil((double)keys.size() / (mRows * mKeysPerRow));

    }
    @Override public void onInitializeInterface() {
        setKeyboard();
    }

    private void setKeyboard() {

        if(mRows == 12) {
            keyboard = new KBoard(this, R.xml.twelve_rows);
        } else if (mRows == 11) {
            keyboard = new KBoard(this, R.xml.eleven_rows);
        } else if (mRows == 10) {
            keyboard = new KBoard(this, R.xml.ten_rows);
        } else if (mRows == 9) {
            keyboard = new KBoard(this, R.xml.nine_rows);
        } else if (mRows == 8) {
            keyboard = new KBoard(this, R.xml.eight_rows);
        } else if (mRows == 7) {
            keyboard = new KBoard(this, R.xml.seven_rows);
        } else if (mRows == 6) {
            keyboard = new KBoard(this, R.xml.six_rows);
        } else if (mRows == 5) {
            keyboard = new KBoard(this, R.xml.five_rows);
        } else if (mRows == 4) {
            keyboard = new KBoard(this, R.xml.four_rows);
        } else if (mRows == 1) {
            keyboard = new KBoard(this, R.xml.one_row);
        } else {
            keyboard = new KBoard(this, R.xml.normal);
        }
        mKeys = keyboard.getKeys();
        resetKeyChars();
    }

    @Override
    public View onCreateInputView() {
        setKeyboard();
        kv = (KboardView)getLayoutInflater().inflate(R.layout.material_dark, null);
        kv.setKeyboard(keyboard);
        kv.setBackgroundColor(getResources().getColor(R.color.white));
        kv.setPreviewEnabled(false);
        kv.setOnKeyboardActionListener(this);
        return kv;
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        keyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    private String getKeyString(int code) {
        if (code == -6) {
            return (mScreen + 1) + "/" + totalScreens;
        } else if (code < -100 && code >= (-100 - mKeysPerScreen)) {
            int indOfKey = -(code + 101 - (mScreen * mKeysPerScreen));
            if (indOfKey < keys.size()) {
                return keys.get(indOfKey);
            } else {
                return "NO_VALUE";
            }
        } else {
            return "";
        }
    }


    private void resetKeyChars() {
        String newString;
        for(Keyboard.Key key:mKeys) {
            newString = getKeyString(key.codes[0]);
            if(newString == "NO_VALUE") {
                key.label = "";
                key.popupCharacters = "";
            }
            else if(newString != "") {
                key.label = newString;
                key.popupCharacters = newString;
            }
        }
        if(kv != null) {
            kv.invalidateAllKeys();
        }
    }

    private void playClick(){
        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
           am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.3f);
    }

    private void vibrate(){
        vib.vibrate(40);
    }

    @Override
    public void onRelease(int primaryCode) {
        InputConnection ic = getCurrentInputConnection();
        KCommands commands = new KCommands(
                this,
                ic,
                getCurrentInputEditorInfo(),
                keys,
                mAutoSpace,
                mPassiveAggressive);

        if(mSoundOnClick) {
            playClick();
        }

        if(mVibrateOnClick) {
            vibrate();
        }

        switch(primaryCode) {
            case -5: //backspace

                //commands.d(1);
                break;
            case -6: //MAD
                switchScreens();
                break;
            case 10: //enter
               ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
               ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
               break;
            case -201: //subtype switcher
                switchIME();
                break;
            case -301: //settings
                Intent i = new Intent(this, PrefsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                break;
            case -401: //undo
                commands.undo();
                break;
            case -402: //redo
                commands.redo();
                break;
            default:
                String keyString = getKeyString(primaryCode);
                if((keyString.startsWith("/") && keyString.contains("!"))) {
                    parseCommand(commands, keyString);
                } else {
                    sendReply(commands, keyString);
                }
                break;
            }
    }

    private void parseCommand(KCommands kc, String cmd) {
        String[] cmdSplit = cmd.split("!", 2);
        String cmdAction = cmdSplit[1];
        kc.e(1, cmdAction);
    }

    private void sendReply(KCommands commands, String key) {
        if(key == "NO_VALUE") {
            return;
        }
        commands.i(1, key);
        if(mAutoSend) {
            commands.s(1);
        }
    }

    private void switchIME() {
        //final String LATIN = "com.android.inputmethod.latin/.LatinIME";
// 'this' is an InputMethodService
            try {
                InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
                final IBinder token = Objects.requireNonNull(this.getWindow().getWindow()).getAttributes().token;
                imm.switchToLastInputMethod(token);
                //imm.setInputMethod(token, LATIN);
                //imm.switchToNextInputMethod(token, false);
            } catch (Throwable t) { // java.lang.NoSuchMethodError if API_level<11
                //mInputMethodManager.showInputMethodPicker();
                Log.e(TAG, "cannot set the previous input method:");
                t.printStackTrace();
            }
                //this.switchToPreviousInputMethod();


    }
    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        switch(primaryCode) {
            case -5:
                //commands.d(1);
                keyDownUp(67);
                return;
            default:
        }
    }

    private void keyDownUp(int keyEventCode) {
        InputConnection ic = getCurrentInputConnection();
        ic.sendKeyEvent(new KeyEvent(0, keyEventCode));
        ic.sendKeyEvent(new KeyEvent(1, keyEventCode));
    }

    @Override
    public void onPress(int primaryCode) {
        InputConnection ic = getCurrentInputConnection();
        KCommands commands = new KCommands(
                this,
                ic,
                getCurrentInputEditorInfo(),
                keys,
                mAutoSpace,
                mPassiveAggressive);

    }

    @Override
    public void onText(CharSequence text) {
    }

    @Override
    public void swipeDown() {
    }

    @Override
    public void swipeLeft() {
        switchScreens();
    }

    private void switchScreens() {
        mScreen = (mScreen < totalScreens - 1) ? mScreen + 1 : 0;
        resetKeyChars();
    }

    @Override
    public void swipeRight() {
        switchScreens();

    }

    @Override
    public void swipeUp() {
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        initPrefs();
        setKeyboard();
        if(keyboard != null && mKeys != null && kv != null) {
            kv.setKeyboard(keyboard);
            resetKeyChars();
        }
    }

    public static class Keys {
        public static ArrayList<String> getDefault() {
            ArrayList<String> defaultKeys = new ArrayList<>();
            defaultKeys.add("👍");
            defaultKeys.add("ಠ_ಠ");
            defaultKeys.add("haha");
            defaultKeys.add("¯\\_(ツ)_/¯");
            defaultKeys.add("/exec!dt(!),e($0)");
            defaultKeys.add("\uD83D\uDE12");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                defaultKeys.add("/🅰🅱🅲!ds,fancy(darksquare)");
                defaultKeys.add("/🄰🄱🄲!ds,fancy(square)");
                defaultKeys.add("/🅐🅑🅒!ds,fancy(darkcircle)");
                defaultKeys.add("/ⓐⓑⓒ!ds,fancy(circle)");
                defaultKeys.add("/𝚊𝚋𝚌!ds,fancy(monospace)");
                defaultKeys.add("/𝕒𝕓𝕔!ds,fancy(double)");
                defaultKeys.add("/𝔞𝔟𝔠!ds,fancy(fancy)");
                defaultKeys.add("/𝖆𝖇𝖈!ds,fancy(fancybold)");
                defaultKeys.add("/ɐqɔ!ds,fancy(inverted)");
                defaultKeys.add("/Adↄ!ds,fancy(reversed)");
                defaultKeys.add("/ﾑ乃c!ds,fancy(cjkthai)");
                defaultKeys.add("/ₐbc!ds,fancy(subscript)");
                defaultKeys.add("/ᵃᵇᶜ!ds,fancy(superscript)");
                defaultKeys.add("/ﾑ乃ς!ds,fancy(zyrillic)");
                defaultKeys.add("/ﾑ乃ς!ds,fancy(zyrillian)");
                defaultKeys.add("/Zalgo!ds,zalgo(8;8;8)");
            }

            defaultKeys.add("/catfact!curl(https://kboard-api.glitch.me/catfact");
            defaultKeys.add("ಥ_ಥ");
            defaultKeys.add("( ͡° \u035Cʖ ͡°)");
            defaultKeys.add("(╯°□°）╯︵ ┻━┻");
            //defaultKeys.add("ノ┬─┬ノ︵ ( \o°o)\");
            defaultKeys.add("┬──┬◡ﾉ(° -°ﾉ)");
            defaultKeys.add("ᕕ( ᐛ )ᕗ");
            defaultKeys.add("(⊙_☉)");
            defaultKeys.add("ᕦ(ò_óˇ)ᕤ");
            defaultKeys.add("(‿|‿)");
            defaultKeys.add("( . Y . )");
            defaultKeys.add("(◡ ‿ ◡ ✿)");
            defaultKeys.add("ლ(ಠ益ಠლ)");
            defaultKeys.add("（^人^）");
            defaultKeys.add("┣▇▇▇═──");
            defaultKeys.add("~=[,,_,,]:3");
            defaultKeys.add("ʕ •ᴥ•ʔ");
            defaultKeys.add("┌∩┐(◣_◢)┌∩┐");
            defaultKeys.add("(⊙ω⊙)");
            defaultKeys.add("t(-.-t)");
            defaultKeys.add("( ˘ ³˘)♥");
            defaultKeys.add("(• ε •)");
            defaultKeys.add("¯\\(º_o)/¯");
            defaultKeys.add("(ﾉಥ益ಥ）ﾉ ┻━┻");
            defaultKeys.add("(╯°□°)╯︵ ʞooqǝɔɐɟ");
            defaultKeys.add("ヾ(⌐■_■)ノ♪");
            defaultKeys.add("| (• ◡•)| (❍ᴥ❍ʋ)");
            defaultKeys.add("(╥﹏╥)");
            defaultKeys.add("┬┴┬┴┤(･_├┬┴┬┴");
            defaultKeys.add("ʕノ•ᴥ•ʔノ ︵ ┻━┻");
            defaultKeys.add("(づ￣ ³￣)づ");
            defaultKeys.add("◉‿◉");
            defaultKeys.add("(ノ ゜Д゜)ノ ︵ ┻━┻");
            defaultKeys.add("(/ .□.)\\ ︵╰(゜Д゜)╯︵ /(.□. \\)");
            defaultKeys.add("(ó ì_í)=óò=(ì_í ò)");
            defaultKeys.add("(╯°Д°）╯︵ /(.□ . \\)");
            defaultKeys.add("ᕙ(⇀‸↼‶)ᕗ");
            defaultKeys.add("(   ° ᴗ°)~ð  (/❛o❛\\)");
            defaultKeys.add("(=^ェ^=)");
            defaultKeys.add("ノ(ジ)ー'");
            defaultKeys.add("(づ｡◕‿‿◕｡)づ");
            defaultKeys.add("╮ (. ❛ ᴗ ❛.) ╭");
            defaultKeys.add("(/¯◡ ‿ ◡)/¯ ~ ┻━┻");
            defaultKeys.add("ᶘ ᵒᴥᵒᶅ");
            defaultKeys.add("(ノ^_^)ノ┻━┻ ┬─┬ ノ( ^_^ノ)");
            defaultKeys.add("(V) (°,,,,°) (V)");
            defaultKeys.add("(っ˘ڡ˘ς)");
            defaultKeys.add("(╯°□°)╯︵ ┻━┻ ︵ ╯(°□° ╯)");
            defaultKeys.add("（。々°）");
            defaultKeys.add("ლ(╹◡╹ლ)");
            defaultKeys.add("♥‿♥");
            defaultKeys.add("(◐‿◑)");
            defaultKeys.add("︻デ═一");
            defaultKeys.add("♪┏(・o･)┛♪┗ ( ･o･) ┓♪");
            defaultKeys.add("(+[__]∙:∙)");
            defaultKeys.add("┬┴┬┴┤ʕ•ᴥ├┬┴┬┴");
            defaultKeys.add("( ＾◡＾)っ✂╰⋃╯");
            defaultKeys.add("<コ:彡");
            defaultKeys.add("☆.。.:*・°☆.。.:*・°☆.。.:*・°☆.。.:*・°☆");
            defaultKeys.add("(°ロ°)☝");
            defaultKeys.add("( ・∀・)っ旦");
            defaultKeys.add("\\(-ㅂ-)/ ♥ ♥ ♥");
            defaultKeys.add("(σ・・)σ");
            defaultKeys.add("＼(＾O＾)／");
            defaultKeys.add("(ﾉಥДಥ)ﾉ︵┻━┻･/");
            defaultKeys.add("¬_¬");
            defaultKeys.add("(⌐■_■)");
            defaultKeys.add("[¬º-°]¬");
            defaultKeys.add("｡◕ ‿ ◕｡");
            defaultKeys.add("(－‸ლ)");
            defaultKeys.add("♒((⇀‸↼))♒");
            defaultKeys.add("┗[© ♒ ©]┛ ︵ ┻━┻");
            defaultKeys.add("(☞ﾟ∀ﾟ)☞");
            defaultKeys.add("（ -.-）ノ-=≡≡卍");
            defaultKeys.add("（*＾＾）/~~~~◎");
            defaultKeys.add("人◕ ‿‿ ◕人");
            defaultKeys.add("ಠ_ರೃ");
            defaultKeys.add("(._.) ~ ︵ ┻━┻");
            defaultKeys.add("[^._.^]ﾉ彡");
            defaultKeys.add("(ノ・∀・)ノ");
            defaultKeys.add("し(*・∀・)／♡＼(・∀・*) ///");
            defaultKeys.add("ლ(o◡oლ)");
            defaultKeys.add("(｡´ ‿｀♡)");
            defaultKeys.add("(∩｀-´)⊃━☆ﾟ.*･｡ﾟ");
            defaultKeys.add("ヽ(^o^)ρ┳┻┳°σ(^o^)/");
            defaultKeys.add("(ノ`Д ́)ノ");
            defaultKeys.add("(ﾉ^_^)ﾉ");
            defaultKeys.add("|̲̅̅●̲̅̅|̲̅̅=̲̅̅|̲̅̅●̲̅̅|");
            defaultKeys.add("(ღ˘⌣˘ღ)");
            defaultKeys.add("(✿◕‿◕✿)");
            defaultKeys.add("(°◡°♡).:｡");
            defaultKeys.add("─=≡Σ(([ ⊐•̀⌂•́]⊐");
            defaultKeys.add("( ° ͜ʖ °)");
            defaultKeys.add("٩(̾●̮̮̃̾•̃̾)۶");
            defaultKeys.add("└[∵┌]└[ ∵ ]┘[┐∵]┘");
            defaultKeys.add("ᕙ( ͡° ͜ʖ ͡°)ᕗ");
            defaultKeys.add("(*☌ᴗ☌)｡");
            defaultKeys.add("(・ω・)");
            defaultKeys.add("ｏ(￣ρ￣)ｏ");
            defaultKeys.add("(｡◕‿◕｡)");
            defaultKeys.add("(●°u°●)​ 」");
            defaultKeys.add("☆*✲ﾟ*｡(((´♡‿♡`+)))｡*ﾟ✲*☆");
            defaultKeys.add("(ÒДÓױ)");
            defaultKeys.add("ʕ⁎̯͡⁎ʔ༄");
            defaultKeys.add("(Ó_#)ò=(°□°ò)");
            defaultKeys.add("(- o - ) zzZ ☽");
            defaultKeys.add("(~^.^)~");
            defaultKeys.add("ʘ‿ʘ");
            defaultKeys.add("(｡-_-｡ )人( ｡-_-｡)");
            defaultKeys.add("(._.) ( l: ) ( .-. ) ( :l ) (._.)");
            defaultKeys.add("|ʘ‿ʘ)╯");
            defaultKeys.add("(ლ `Д ́)ლ");
            defaultKeys.add("(⊙＿⊙')");
            defaultKeys.add("(:3 っ)っ");
            defaultKeys.add("d-_-b");
            defaultKeys.add("\\m/...(>.<)…\\m/");
            defaultKeys.add("♪ (｡´＿●`)ﾉ┌iiii┐ヾ(´○＿`*) ♪");
            defaultKeys.add("\\( •_•)_†");
            defaultKeys.add("٩(͡๏̯͡๏)۶");
            defaultKeys.add("ϵ( 'Θ' )϶");
            defaultKeys.add("(-.-(-.(-(-.(-.-).-)-).-)-.-)");
            defaultKeys.add("( ́・ω・`)");
            defaultKeys.add("♪~♪ ヽ໒(⌒o⌒)७ﾉ ♪~♪");
            defaultKeys.add("┌( ಠ_ಠ)┘");
            defaultKeys.add("\\|°▿▿▿▿°|/");
            defaultKeys.add("ヾ(＠⌒ー⌒＠)ノ");
            defaultKeys.add("༼;´༎ຶ ۝ ༎ຶ༽");
            defaultKeys.add("┐(￣ー￣)┌");
            defaultKeys.add("( ・∀・)っ♨");
            defaultKeys.add("¯\\_(シ)_/¯");
            defaultKeys.add("¯\\_ಠ_ಠ_/¯");
            defaultKeys.add("(・ε・)");
            defaultKeys.add("(　-_･)σ - - - - - - - - ･");
            defaultKeys.add("dL-_-b");
            defaultKeys.add("<(-'.'-)>");
            defaultKeys.add("◎ヽ(^･ω･^=)~");
            defaultKeys.add("(´∀｀)♡");
            defaultKeys.add("(｡･ω･｡)ﾉ♡");
            defaultKeys.add("(  ⚆ _ ⚆ )");
            defaultKeys.add("♡＾▽＾♡");
            defaultKeys.add("(̿▀̿ ̿Ĺ̯̿̿▀̿ ̿)̄");
            defaultKeys.add("[+..••]");
            defaultKeys.add("ε=ε=ε=ε=ε=ε=┌(;￣◇￣)┘");


            defaultKeys.add("/Italicise Previous!dw,i(_$0_)");
            defaultKeys.add("/Italicise Next!i(__),j");
            defaultKeys.add("/Bolden Previous!dw,i(*$0*)");
            defaultKeys.add("/Bolden Next!i(**),j");
            defaultKeys.add("/Copy All!yy");
            defaultKeys.add("/Paste!p");
            defaultKeys.add("/-1w!b");
            defaultKeys.add("/+1w!w");
            defaultKeys.add("/Delete Word!dw");
            return defaultKeys;
        }

        public static final String STORAGE_KEY = "userKeys-defaults";
    }
}
