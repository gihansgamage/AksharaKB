package com.gihansgamage.aksharakb

import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.gihansgamage.aksharakb.data.KeyboardPreferences

class MyInputMethodService : InputMethodService(),
    KeyboardView.OnKeyboardActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var keyboardView: MyKeyboardView? = null
    private var keyboard: Keyboard? = null
    private var candidatesContainer: LinearLayout? = null
    private var candidatesScroll: HorizontalScrollView? = null
    private var langPillsContainer: LinearLayout? = null
    private var emojiPanel: android.view.ViewGroup? = null
    private var emojiTabs: LinearLayout? = null
    private var emojiGrid: android.widget.LinearLayout? = null
    private var emojiScrollView: android.widget.ScrollView? = null
    private var emojiActionBar: LinearLayout? = null
    private var activeCategoryIndex = 0
    private val recentEmojis = mutableListOf<String>()
    private val MAX_RECENT = 40
    private var btnSettings: TextView? = null
    private var wordPredictor: WordPredictor? = null
    private var clipboard: KeyboardClipboard? = null
    private var prefs: KeyboardPreferences? = null
    private fun isDark() = prefs?.theme == KeyboardPreferences.THEME_DARK


    private enum class CapsState { NONE, SHIFT, CAPS_LOCK }
    private var capsState      = CapsState.NONE
    private var phoneticBuffer = StringBuilder()
    private var isPhoneticMode = false
    private var isSymbols      = false
    private var isEmoji        = false   // true when emoji keyboard is shown
    private var isComposingWord = false
    private var showEmoji      = false   // legacy candidate-bar emoji (now unused)
    private var emojiCategory  = 0      // 0-7 active emoji category
    private var emojiPage      = 0      // which page of emojis (10 per row × 3 rows = 30 per page)
    private var currentInput   = StringBuilder()
    private var awaitingZWJ    = false
    private var vowelAwaitingReorder = false
    private var lastTappedEmoji = ""
    private var lastPressedEmoji = ""  // emoji captured in onPress

    // ── Wijesekara shift map ──────────────────────────────────────
    // Exact values from spec, verified with python3 ord()
    //
    // Row 1 (Q-P):
    //   Q: ු(3540)→ූ(3542)    W: අ(3461)→උ(3467)    E: ැ(3536)→ෑ(3537)
    //   R: ර(3515)→ඍ(3469)    T: එ(3473)→ඔ(3476)    Y: හ(3524)→ඓ(3475)
    //   U: ම(3512)→ඖ(3478)    I: ස(3523)→ෂ(3522)    O: ද(3503)→ධ(3504)
    //   P: ච(3488)→ඡ(3489)
    // Row 2 (A-;):
    //   A: ්(3530)→ා(3535)    S: ි(3538)→ී(3539)    D: ා(3535)→ෘ(3544)
    //   F: ෙ(3545)→ෆ(3526)    G: ට(3495)→ඨ(3496)    H: ය(3514)→[ZWJ+ය]
    //   J: ව(3520)→[ළ+ු]      K: න(3505)→ණ(3499)    L: ක(3482)→ඛ(3483)
    //   ;: ත(3501)→ථ(3502)
    // Row 3 (Z-M):
    //   Z: ං(3458)→ඃ(3459)    X: ජ(3490)→ඣ(3491)    C: ඩ(3497)→ඪ(3498)
    //   V: ඉ(3465)→ඊ(3466)    B: බ(3510)→භ(3511)    N: ප(3508)→ඵ(3509)
    //   M: ල(3517)→ළ(3525)    ,: ග(3484)→ඝ(3485)    .: .(46)→?(63)
    // Number row:
    //   1→! 2→@ 3→# 4→$ 5→% 6→^ 7→& 8→* 9→( 0→) -→_ =→+
    private val wijShiftMap = mapOf(
        // Row 1
        3540 to 3542, 3461 to 3467, 3536 to 3537, 3515 to 3469, 3473 to 3476,
        3524 to 3475, 3512 to 3513, 3523 to 3522, 3503 to 3504, 3488 to 3489,
        // Row 2 (simple substitutions — H and J handled specially)
        3530 to 3551, 3538 to 3539,
        // D key: ා(3535) normal, Shift→ෘ(3544) — note ා also on A key
        // Since both A and D emit different codes in XML:
        //   A key codes=3530 (්), D key codes=3535 (ා)
        3535 to 3544, // ා → ෘ  (D key shift)
        3545 to 3526, // ෙ → ෆ  (F key shift)
        3495 to 3496, // ට → ඨ
        // H(3514)→ZWJ compound, J(3520)→ළු: handled in commitWijesekara
        3505 to 3499, 3482 to 3483, 3501 to 3502,
        // Row 3
        3458 to 3459, 3490 to 3486, 3497 to 3498, 3465 to 3498,
        3510 to 3511, 3508 to 3509, 3517 to 3525, 3484 to 3485,
        46   to 3485,
        // Number row
        // Number row (removed from shift map to keep numbers as numbers)
    )

    private val NORMAL_YA  = 3514  // H key
    private val NORMAL_WA  = 3520  // J key
    private val ZWJ        = "\u200D"

    // ── Phonetic maps ─────────────────────────────────────────────
    private val sinhalaPhoneticMap = listOf(
        "aa" to "ආ","ii" to "ඊ","uu" to "ඌ","ee" to "ඒ","oo" to "ඕ",
        "kh" to "ඛ","gh" to "ඝ","ng" to "ඞ","ch" to "ච","jh" to "ඣ",
        "ny" to "ඤ","th" to "ථ","dh" to "ධ","ph" to "ඵ","bh" to "භ",
        "sh" to "ශ","ll" to "ළ","nj" to "ඤ",
        "a" to "අ","i" to "ඉ","u" to "උ","e" to "එ","o" to "ඔ",
        "k" to "ක","g" to "ග","c" to "ච","j" to "ජ","t" to "ට",
        "d" to "ඩ","n" to "න","p" to "ප","b" to "බ","m" to "ම",
        "y" to "ය","r" to "ර","l" to "ල","v" to "ව","w" to "ව",
        "s" to "ස","h" to "හ","f" to "ෆ","q" to "ක","x" to "ෂ","z" to "ශ"
    )
    private val tamilPhoneticMap = listOf(
        "aa" to "ஆ","ii" to "ஈ","uu" to "ஊ","ee" to "ஏ","oo" to "ஓ",
        "ai" to "ஐ","au" to "ஔ","ng" to "ங","ch" to "ச","ny" to "ஞ",
        "th" to "த","nn" to "ண","nh" to "ன","zh" to "ழ","ll" to "ள","rr" to "ற",
        "a" to "அ","i" to "இ","u" to "உ","e" to "எ","o" to "ஒ",
        "k" to "க","c" to "ச","t" to "ட","p" to "ப","m" to "ம",
        "y" to "ய","r" to "ர","l" to "ல","v" to "வ","w" to "வ",
        "s" to "ச","h" to "ஹ","n" to "ந","z" to "ழ","f" to "ஃ",
        "j" to "ஜ","g" to "க","d" to "ட","b" to "ப","q" to "க"
    )

    // ── Emoji categories ─────────────────────────────────────────
    private val emojiCategories = listOf(
        "😀 Smileys" to listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","🫠","😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙","🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🫢","🫣","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","😏","😒","🙄","😬","😮‍💨","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵","😵‍💫","🤯","🤠","🥳","🥸","😎","🤓","🧐","😕","🫤","😟","🙁","😮","😯","😲","😳","🥺","🥹","😦","😧","😨","😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠","💩","🤡","👹","👺","👻","👽","👾","🤖","😺","😸","😹","😻","😼","😽","🙀","😿","😾"
        ),
        "👋 People" to listOf(
            "👋","🤚","🖐","✋","🖖","🫱","🫲","🫳","🫴","👌","🤌","🤏","✌","🤞","🫰","🤟","🤘","🤙","👈","👉","👆","👇","☝","🫵","👍","👎","✊","👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍","💅","🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🫀","🫁","🧠","🦷","🦴","👀","👁","👅","👄","🫦","👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓","👴","👵","🙍","🙎","🙅","🙆","💁","🙋","🧏","🙇","🤦","🤷","👮","🕵","💂","🥷","👷","🤴","👸","👳","👲","🧕","🤵","👰","🤰","🫃","🫄","🤱","👼","🎅","🤶","🦸","🦹","🧙","🧝","🧛","🧟","🧞","🧜","🧚","👫","👬","👭","💏","💑","👨‍👩‍👦","👨‍👩‍👧","👩‍👦","👩‍👧","👨‍👦","👨‍👧"
        ),
        "❤ Hearts" to listOf(
            "❤","🩷","🧡","💛","💚","💙","🩵","💜","🖤","🩶","🤍","🤎","💔","❤‍🔥","❤‍🩹","❣","💕","💞","💓","💗","💖","💘","💝","💟","💋","😍","🥰","😘","🫶","🤗","🫂","💌","💒","🌹","🌺","🌸","🌼","🌻","🌷","🪷","🎁","🎀","🎊","🎉"
        ),
        "🐶 Animals" to listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄","🐨","🐯","🦁","🐮","🐷","🐽","🐸","🐵","🙈","🙉","🙊","🐒","🐔","🐧","🐦","🐤","🐣","🐥","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🫏","🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪲","🦟","🦗","🪳","🕷","🦂","🐢","🐍","🦎","🦖","🦕","🐙","🦑","🪼","🦐","🦞","🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅","🐆","🦓","🫎","🦍","🦧","🦣","🐘","🦛","🦏","🐪","🐫","🦒","🦘","🦬","🐃","🐂","🐄","🐎","🐖","🐏","🐑","🦙","🐐","🦌","🐕","🐩","🦮","🐈","🐓","🦃","🦤","🦚","🦜","🦢","🪿","🦩","🕊","🐇","🦝","🦨","🦡","🦫","🦦","🦥","🐁","🐀","🐿","🦔","🐾","🐉","🐲"
        ),
        "🌿 Nature" to listOf(
            "🌵","🎄","🌲","🌳","🌴","🪵","🌱","🌿","☘","🍀","🎍","🪴","🎋","🍃","🍂","🍁","🪺","🪹","🍄","🌾","💐","🌷","🪷","🌹","🥀","🌺","🌸","🌼","🌻","🌞","🌝","🌛","🌜","🌚","🌕","🌖","🌗","🌘","🌑","🌒","🌓","🌔","🌙","🌟","⭐","🌠","🌌","☀","🌤","⛅","🌥","☁","🌦","🌧","⛈","🌩","🌨","❄","☃","⛄","🌬","🌀","🌈","🌂","☂","☔","⛱","⚡","🌊","💧","💦","🫧","🌫","🌊","🏔","⛰","🌋","🗻","🏕","🏖","🏜","🏝","🏞","🌅","🌄","🌠","🎇","🎆","🌇","🌆","🏙","🌃","🌉","🌌","🌁"
        ),
        "🍔 Food" to listOf(
            "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶","🫑","🌽","🥕","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🦴","🌭","🍔","🍟","🍕","🫓","🫔","🌮","🌯","🥙","🧆","🥘","🍲","🫕","🍜","🍝","🍢","🍣","🍤","🍙","🍚","🍱","🥟","🦪","🥡","🍛","🥗","🥫","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🌰","🥜","🍯","🧃","🥤","🧋","☕","🍵","🫖","🍶","🍺","🍻","🥂","🍷","🫗","🥃","🍸","🍹","🧉","🍾","🧊","🥄","🍴","🍽","🥢","🫙"
        ),
        "🚗 Travel" to listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🏍","🛵","🛺","🚲","🛴","🛹","🛼","🚏","🛣","🛤","⛽","🛞","🚨","🚥","🚦","🛑","🚧","⚓","🛟","⛵","🛶","🚤","🛳","⛴","🛥","🚢","✈","🛩","🛫","🛬","🪂","💺","🚁","🚟","🚠","🚡","🛰","🚀","🛸","🪐","🌍","🌎","🌏","🧭","🏔","⛰","🌋","🗺","🏕","🏖","🏜","🏝","🏞","🏟","🏛","🏗","🧱","🪨","🛖","🏘","🏚","🏠","🏡","🏢","🏣","🏤","🏥","🏦","🏨","🏩","🏪","🏫","🏬","🏭","🏯","🏰","💒","🗼","🗽","⛪","🕌","🛕","🕍","⛩","🕋","⛲","⛺","🌁","🌃","🏙","🌄","🌅","🌆","🌇","🌉","🗾","🎑"
        ),
        "⚽ Sports" to listOf(
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🪀","🏓","🏸","🏒","🥍","🏑","🏏","🪃","🥅","⛳","🪁","🛝","🎣","🤿","🎽","🎿","🛷","🥌","🎯","🏋","🤸","⛹","🤺","🤾","🏌","🏇","🧘","🏄","🏊","🤽","🚣","🧗","🚵","🚴","🏆","🥇","🥈","🥉","🏅","🎖","🏵","🎗","🎫","🎟","🎪","🤹","🎭","🩰","🎨","🎬","🎤","🎧","🎼","🎵","🎶","🎷","🪗","🎸","🎹","🎺","🎻","🥁","🪘","🎙","🎚","🎛","📻","🎮","🕹","🎲","♟","🎰","🎠","🎡","🎢"
        ),
        "💡 Objects" to listOf(
            "📱","💻","🖥","🖨","⌨","🖱","💽","💾","💿","📀","🎥","📽","🎞","📞","☎","📟","📠","📺","📷","📸","📹","📼","🔍","🔎","🕯","💡","🔦","🏮","🪔","📔","📕","📖","📗","📘","📙","📚","📓","📒","📃","📄","📑","🗒","🗓","📆","📅","📇","📈","📉","📊","📋","📌","📍","✂","🗃","🗄","🗑","🔒","🔓","🔏","🔐","🔑","🗝","🔨","🪓","⛏","⚒","🛠","🗡","⚔","🔫","🪃","🛡","🪚","🔧","🪛","🔩","⚙","🗜","⚖","🪜","🔗","⛓","🪝","🧲","🔮","🧿","🪬","🧸","🪅","🎊","🎉","🪆","🎎","🎐","🎏","🎀","🎁","📦","📫","📪","📬","📭","📮","🗳","✏","✒","🖋","🖊","📝","💼","📁","📂","🗂","🗞","📰","📏","📐","🧹","🧺","🧻","🪣","🧴","🧷","🧽","🧼","🫧","🪥","🪒","🛒","🚪","🪞","🪟","🛏","🛋","🪑","🚽","🪠","🚿","🛁","🪤","💈","💊","💉","🩸","🧬","🦠","🧫","🧪","🌡","🔭","🩺","🩻","🩹","🩼","💎","💍","👑","👒","🎓","🪖","⛑","💄","💋","👓","🕶","🥽","🌂","☂","🧵","🪡","🧶","🪢","👔","👕","👖","🧣","🧤","🧥","🧦","👗","👘","🥻","🩱","🩲","🩳","👙","👚","👛","👜","👝","🎒","🧳","👞","👟","🥾","🥿","👠","👡","🩰","👢"
        ),
        "🔣 Symbols" to listOf(
            "🔴","🟠","🟡","🟢","🔵","🟣","🟤","⚫","⚪","🟥","🟧","🟨","🟩","🟦","🟪","🟫","⬛","⬜","◼","◻","◾","◽","▪","▫","🔶","🔷","🔸","🔹","🔺","🔻","💠","🔘","🔳","🔲","🔈","🔇","🔉","🔊","📢","📣","🔔","🔕","♾","⚕","♻","⚜","🔰","✅","❎","🆗","🆙","🆕","🆒","🆓","🔝","🆖","🆎","🆑","🅾","🆘","🔃","🔄","🔙","🔚","🔛","🔜","🔝","⬆","↗","➡","↘","⬇","↙","⬅","↖","↕","↔","↩","↪","⤴","⤵","🔀","🔁","🔂","▶","⏩","⏭","⏯","◀","⏪","⏮","🔼","⏫","🔽","⏬","⏸","⏹","⏺","⏏","🎦","📶","📳","📴","💹","🔱","❇","✳","💯","🔠","🔡","🔤","❗","❕","❓","❔","‼","⁉","⚠","♻","✅","❌","⭕","🔞","📵","🚫","🚳","🚭","🚯","🚱","🚷","📛","⛔"
        ),
        "🏳 Flags" to listOf(
            "🏴‍☠","🏳","🏳‍🌈","🏳‍⚧","🏴","🚩","🎌","🏁",
            "🇦🇫","🇦🇱","🇩🇿","🇦🇩","🇦🇴","🇦🇬","🇦🇷","🇦🇲","🇦🇺","🇦🇹","🇦🇿","🇧🇸","🇧🇭","🇧🇩","🇧🇧","🇧🇾","🇧🇪","🇧🇿","🇧🇯","🇧🇹","🇧🇴","🇧🇦","🇧🇼","🇧🇷","🇧🇳","🇧🇬","🇧🇫","🇧🇮","🇰🇭","🇨🇲","🇨🇦","🇨🇻","🇨🇫","🇹🇩","🇨🇱","🇨🇳","🇨🇴","🇰🇲","🇨🇬","🇨🇩","🇨🇰","🇨🇷","🇭🇷","🇨🇺","🇨🇾","🇨🇿","🇩🇰","🇩🇯","🇩🇲","🇩🇴","🇪🇨","🇪🇬","🇸🇻","🇬🇶","🇪🇷","🇪🇪","🇸🇿","🇪🇹","🇪🇺","🇫🇯","🇫🇮","🇫🇷","🇬🇦","🇬🇲","🇬🇪","🇩🇪","🇬🇭","🇬🇷","🇬🇩","🇬🇹","🇬🇳","🇬🇼","🇬🇾","🇭🇹","🇭🇳","🇭🇰","🇭🇺","🇮🇸","🇮🇳","🇮🇩","🇮🇷","🇮🇶","🇮🇪","🇮🇱","🇮🇹","🇯🇲","🇯🇵","🇯🇴","🇰🇿","🇰🇪","🇰🇮","🇽🇰","🇰🇼","🇰🇬","🇱🇦","🇱🇻","🇱🇧","🇱🇸","🇱🇷","🇱🇾","🇱🇮","🇱🇹","🇱🇺","🇲🇬","🇲🇼","🇲🇾","🇲🇻","🇲🇱","🇲🇹","🇲🇭","🇲🇷","🇲🇺","🇲🇽","🇫🇲","🇲🇩","🇲🇨","🇲🇳","🇲🇪","🇲🇦","🇲🇿","🇲🇲","🇳🇦","🇳🇷","🇳🇵","🇳🇱","🇳🇿","🇳🇮","🇳🇪","🇳🇬","🇳🇴","🇴🇲","🇵🇰","🇵🇼","🇵🇸","🇵🇦","🇵🇬","🇵🇾","🇵🇪","🇵🇭","🇵🇱","🇵🇹","🇵🇷","🇶🇦","🇷🇴","🇷🇺","🇷🇼","🇼🇸","🇸🇲","🇸🇹","🇸🇦","🇸🇳","🇷🇸","🇸🇨","🇸🇱","🇸🇬","🇸🇰","🇸🇮","🇸🇧","🇸🇴","🇿🇦","🇸🇸","🇪🇸","🇱🇰","🇸🇩","🇸🇷","🇸🇪","🇨🇭","🇸🇾","🇹🇼","🇹🇯","🇹🇿","🇹🇭","🇹🇱","🇹🇬","🇹🇴","🇹🇹","🇹🇳","🇹🇷","🇹🇲","🇹🇻","🇺🇬","🇺🇦","🇦🇪","🇬🇧","🇺🇸","🇺🇾","🇺🇿","🇻🇺","🇻🇦","🇻🇪","🇻🇳","🇾🇪","🇿🇲","🇿🇼"
        )
    )

    // ── Lifecycle ─────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPreferences(this); wordPredictor = WordPredictor(this)
        clipboard = KeyboardClipboard(this); prefs?.registerListener(this)
    }
    override fun onDestroy() { prefs?.unregisterListener(this); super.onDestroy() }

    override fun onWindowShown() {
        super.onWindowShown()
        window?.window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    w.setBackgroundBlurRadius(60)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            KeyboardPreferences.KEY_KEY_HEIGHT, KeyboardPreferences.KEY_THEME,
            KeyboardPreferences.KEY_BG_IMAGE_URI, KeyboardPreferences.KEY_SHOW_BORDER,
            KeyboardPreferences.KEY_SINHALA_LAYOUT, KeyboardPreferences.KEY_SHOW_NUMPAD,
            KeyboardPreferences.KEY_SHOW_POPUP -> applyCurrentPrefs()
            KeyboardPreferences.KEY_ENABLED_LANGS -> {
                val en = prefs?.enabledLanguages ?: listOf(KeyboardPreferences.LANG_EN)
                val cur = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
                if (cur !in en) prefs?.currentLanguage = en.firstOrNull() ?: KeyboardPreferences.LANG_EN
                setKeyboardLayout(); rebuildLangPills()
            }
        }
    }

    // ── View ──────────────────────────────────────────────────────
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Re-apply glass background to the single candidate_bar container every time the keyboard appears
        val root = keyboardView?.rootView
        val glassRes = if (isDark()) R.drawable.candidate_bar_glass_dark
                       else          R.drawable.candidate_bar_glass_light
        val emojiRes = if (isDark()) R.drawable.emoji_panel_bg_dark
                       else          R.drawable.emoji_panel_bg_light
        root?.findViewById<android.view.View>(R.id.candidate_bar)?.setBackgroundResource(glassRes)
        root?.findViewById<android.view.View>(R.id.emoji_panel)?.setBackgroundResource(emojiRes)
        root?.findViewById<android.view.View>(R.id.keyboard_panel)?.setBackgroundResource(0)
        
        // Root keyboard container: apply glass-like tint (low opacity)
        val bgCol = if (isDark()) 0x44333333.toInt() else 0x44CCCCCC.toInt()
        root?.setBackgroundColor(bgCol)
        
        // Dynamically update emoji and settings icon colors based on theme
        val iconColor = if (isDark()) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        listOf(R.id.btn_emoji, R.id.btn_settings).forEach { id ->
            root?.findViewById<TextView>(id)?.setTextColor(iconColor)
        }
        updateLangIcon(root)
        
        // Refresh lang pills and keyboard in case theme changed
        keyboardView?.refreshPrefs()
        rebuildLangPills()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ checkAutoCapEnglish() }, 100)
    }

    override fun onCreateInputView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        // Apply liquid glass to the single candidate_bar container
        val glassRes = if (isDark()) R.drawable.candidate_bar_glass_dark
                       else          R.drawable.candidate_bar_glass_light
        val emojiRes = if (isDark()) R.drawable.emoji_panel_bg_dark
                       else          R.drawable.emoji_panel_bg_light
        v.findViewById<android.view.View>(R.id.candidate_bar)?.setBackgroundResource(glassRes)
        v.findViewById<android.view.View>(R.id.emoji_panel)?.setBackgroundResource(emojiRes)
        v.findViewById<android.view.View>(R.id.keyboard_panel)?.setBackgroundResource(0)
        // Root keyboard container: apply glass-like tint (low opacity)
        val bgCol = if (isDark()) 0x44333333.toInt() else 0x44CCCCCC.toInt()
        v.setBackgroundColor(bgCol)
        updateLangIcon(v)
        keyboardView        = v.findViewById(R.id.keyboard_view)
        // Theme-aware colors for candidate bar icons
        val dark = isDark()
        val iconColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        listOf(R.id.btn_emoji, R.id.btn_settings).forEach { id ->
            v.findViewById<TextView>(id)?.setTextColor(iconColor)
        }
        v.findViewById<TextView>(R.id.btn_emoji)?.text    = "☻"
        v.findViewById<TextView>(R.id.btn_settings)?.text = "⚙"
        updateLangIcon(v)

        candidatesContainer = v.findViewById(R.id.candidates_container)
        candidatesScroll    = v.findViewById(R.id.candidates_view)
        langPillsContainer  = v.findViewById(R.id.lang_pills_container)
        emojiPanel          = v.findViewById(R.id.emoji_panel)
        emojiTabs           = v.findViewById(R.id.emoji_tabs)
        emojiGrid           = v.findViewById(R.id.emoji_grid)
        emojiScrollView     = v.findViewById(R.id.emoji_scroll)
        emojiActionBar      = v.findViewById(R.id.emoji_action_bar)
        buildEmojiActionBar()

        // Single language switch icon — tap to cycle languages
        v.findViewById<TextView>(R.id.btn_lang_single)?.setOnClickListener {
            vibrateKey()
            val en  = prefs?.enabledLanguages ?: listOf(KeyboardPreferences.LANG_EN)
            val cur = prefs?.currentLanguage  ?: KeyboardPreferences.LANG_EN
            prefs?.currentLanguage = en[(en.indexOf(cur) + 1) % en.size]
            isSymbols = false; capsState = CapsState.NONE; awaitingZWJ = false
            phoneticBuffer.clear(); currentInput.clear()
            setKeyboardLayout(); updateLangIcon(v); updateCandidates("")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ checkAutoCapEnglish() }, 100)
        }

        // Emoji button
        v.findViewById<TextView>(R.id.btn_emoji)?.setOnClickListener {
            vibrateKey()
            isEmoji = !isEmoji
            if (isEmoji) {
                emojiCategory = 0; emojiPage = 0
            } else {
                updateCandidates(currentInput.toString())
            }
            setKeyboardLayout()
        }



        // Settings (now a TextView with ⚙ symbol)
        v.findViewById<TextView>(R.id.btn_settings)?.setOnClickListener {
            vibrateKey()
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }?.let { startActivity(it) }
        }

        keyboardView?.setOnKeyboardActionListener(this)

        // Long-press popup char selected — commit the char directly
        keyboardView?.onPopupCharSelected = { ch ->
            vibrateKey()
            currentInputConnection?.commitText(ch, 1)
        }

        // Blur behind is applied at window level in onWindowShown()

        // Emoji swipe: left = next page, right = prev page
        keyboardView?.onEmojiSwipe = { direction ->
            val maxPage = ((emojiCategories[emojiCategory].second.size - 1) / 30)
            buildEmojiPanel(activeCategoryIndex)
        }
        applyCurrentPrefs()
        rebuildLangPills()
        updateKbModeButton()
        updateCandidates("")
        // Auto-cap on field start for English
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ checkAutoCapEnglish() }, 100)
        return v
    }

    // Rebuild language pills from enabled languages
    private fun rebuildLangPills() {
        val container = langPillsContainer ?: return
        container.removeAllViews()
        val enabled = prefs?.enabledLanguages ?: listOf(KeyboardPreferences.LANG_EN)
        val current = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN

        fun langLabel(lang: String) = when (lang) {
            KeyboardPreferences.LANG_SI -> "සිං"
            KeyboardPreferences.LANG_TA -> "தமி"
            else -> "En"
        }

        val dark = isDark()
        val textActive   = if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A2E.toInt()
        val textInactive = if (dark) 0x88FFFFFF.toInt() else 0x88000033.toInt()
        val pillFill     = if (dark) 0x44FFFFFF           else 0x44000066
        val pillStroke   = if (dark) 0x55FFFFFF           else 0x44000066

        enabled.forEach { lang ->
            val isActive = lang == current
            val pill = TextView(this).apply {
                text = langLabel(lang)
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setPadding(dp(10f).toInt(), 0, dp(10f).toInt(), 0)
                setTextColor(if (isActive) textActive else textInactive)
                background = if (isActive) {
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = dp(8f)
                        setColor(pillFill)
                        setStroke(dp(0.6f).toInt(), pillStroke)
                    }
                } else null
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).also { it.setMargins(dp(2f).toInt(), dp(8f).toInt(), dp(2f).toInt(), dp(8f).toInt()) }
                setOnClickListener {
                    vibrateKey()
                    prefs?.currentLanguage = lang
                    isSymbols = false; capsState = CapsState.NONE; awaitingZWJ = false
                    phoneticBuffer.clear(); currentInput.clear()
                    setKeyboardLayout(); rebuildLangPills(); updateCandidates("")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ checkAutoCapEnglish() }, 100)
                }
            }
            container.addView(pill)
        }
    }

    private fun updateKbModeButton() { /* btn_kb_mode removed from layout */ }

    private fun updateLangIcon(rootView: android.view.View? = null) {
        val dark = isDark()
        val col  = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val root = rootView ?: (keyboardView?.parent as? android.view.View)
        val lang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        val label = when (lang) {
            KeyboardPreferences.LANG_SI -> "සිං"
            KeyboardPreferences.LANG_TA -> "தமி"
            else -> "En"
        }
        root?.findViewById<TextView>(R.id.btn_lang_single)?.apply {
            text = label; textSize = 14f; setTextColor(col)
        }
    }

    private fun dp(v: Float) = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun applyCurrentPrefs() {
        val p = prefs ?: return; val kv = keyboardView ?: return
        kv.isPreviewEnabled = false  // always use custom preview drawn in MyKeyboardView
        kv.refreshPrefs(); setKeyboardLayout()
    }

    private fun setKeyboardLayout() {
        val p = prefs ?: return; val lang = p.currentLanguage
        val phonetic = p.sinhalaLayout == KeyboardPreferences.LAYOUT_PHONETIC
        isPhoneticMode = (lang == KeyboardPreferences.LANG_SI && phonetic) ||
                (lang == KeyboardPreferences.LANG_TA && phonetic)
        val numPad = p.showNumberPad
        val xmlId = when {
            isEmoji   -> R.xml.emoji_keyboard
            isSymbols && capsState != CapsState.NONE -> R.xml.symbols_shift
            isSymbols -> R.xml.symbols
            lang == KeyboardPreferences.LANG_SI -> when {
                phonetic && numPad  -> R.xml.sinhala_phonetic
                phonetic && !numPad -> R.xml.sinhala_phonetic_no_numbers
                !phonetic && numPad -> R.xml.wijesekara
                else                -> R.xml.wijesekara_no_numbers
            }
            lang == KeyboardPreferences.LANG_TA -> when {
                phonetic && numPad  -> R.xml.tamil_phonetic
                phonetic && !numPad -> R.xml.tamil_phonetic_no_numbers
                !phonetic && numPad -> R.xml.tamil
                else                -> R.xml.tamil   // tamil direct has no number row already
            }
            else -> if (numPad) R.xml.qwerty else R.xml.qwerty_no_numbers
        }
        keyboard = Keyboard(this, xmlId)
        keyboardView?.keyboard = keyboard
        keyboardView?.shiftMap = wijShiftMap   // for correct shifted-label rendering
        keyboard?.isShifted = (capsState != CapsState.NONE)

        // For emoji keyboard: populate emoji labels into the keyboard keys
        if (isEmoji) {
            keyboardView?.visibility = android.view.View.INVISIBLE
            emojiPanel?.visibility   = android.view.View.VISIBLE
            keyboardView?.isEmojiMode = false
            // Set emoji panel height = keyboard height so it never overflows
            val kbHeight = keyboardView?.measuredHeight ?: 0
            if (kbHeight > 0) {
                emojiPanel?.layoutParams = (emojiPanel?.layoutParams
                        as? android.view.ViewGroup.LayoutParams)?.also {
                    it.height = kbHeight
                } ?: android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, kbHeight)
            }
            buildEmojiActionBar()
            buildEmojiPanel(emojiCategory)
        } else {
            keyboardView?.visibility = android.view.View.VISIBLE
            emojiPanel?.visibility   = android.view.View.GONE
            keyboardView?.isEmojiMode = false
        }
        keyboardView?.invalidateAllKeys()
        updateKbModeButton()
    }

    private fun buildEmojiPanel(catIndex: Int) {
        // -1 = recent category
        activeCategoryIndex = catIndex.coerceIn(-1, emojiCategories.lastIndex)
        val tabs   = emojiTabs   ?: return
        val grid   = emojiGrid   ?: return
        val scroll = emojiScrollView ?: return
        val dark   = isDark()
        val glassColor = if (dark) 0x88252836.toInt() else 0x99FFFFFF.toInt()
        val tabBg  = if (dark) 0x44FFFFFF else 0x44000000
        val rowBg  = if (dark) 0x44252836.toInt() else 0x55FFFFFF.toInt()
        val textCol = if (dark) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
        val actionBg = glassColor

        // Emoji panel gracefully inherits candidate_bar_glass theme from onCreate/onStart.
        // Removed hardcoded background overrides to perfectly match candidate bar.

        // ── Category tabs ──────────────────────────────────────────
        tabs.removeAllViews()

        // Recent tab first
        fun addTab(idx: Int, icon: String) {
            tabs.addView(android.widget.TextView(this).apply {
                text     = icon
                textSize = 18f
                gravity  = android.view.Gravity.CENTER
                setPadding(dp(10f).toInt(), 0, dp(10f).toInt(), 0)
                alpha    = if (idx == activeCategoryIndex) 1f else 0.38f
                background = if (idx == activeCategoryIndex) {
                    android.graphics.drawable.GradientDrawable().apply {
                        shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = dp(8f)
                        setColor(tabBg)
                    }
                } else null
                layoutParams = LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ).also { it.setMargins(dp(2f).toInt(), dp(4f).toInt(), dp(2f).toInt(), dp(4f).toInt()) }
                setOnClickListener { buildEmojiPanel(idx); scroll.smoothScrollTo(0, 0) }
            })
        }
        addTab(-1, "🕐")   // recent
        emojiCategories.forEachIndexed { idx, (name, _) ->
            addTab(idx, name.split(" ").first())
        }

        // ── Emoji grid ─────────────────────────────────────────────
        grid.removeAllViews()
        val emojis = when {
            activeCategoryIndex == -1 -> recentEmojis.toList().ifEmpty {
                listOf("😊","❤","👍","😂","🙏","🔥","😍","🤗")
            }
            else -> emojiCategories[activeCategoryIndex].second
        }
        val cols = 8

        val rowCount = (emojis.size + cols - 1) / cols
        for (row in 0 until rowCount) {
            val rowLayout = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(46f).toInt()
                ).also { it.setMargins(dp(3f).toInt(), dp(2f).toInt(), dp(3f).toInt(), 0) }
                // Removed alternating row tint so the underlying glass theme shines purely.
                background = null
            }
            for (col in 0 until cols) {
                val emoji = emojis.getOrNull(row * cols + col) ?: ""
                rowLayout.addView(android.widget.TextView(this).apply {
                    text      = emoji
                    textSize  = 24f
                    gravity   = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    if (emoji.isNotBlank()) setOnClickListener {
                        vibrateKey()
                        currentInputConnection?.commitText(emoji, 1)
                        recentEmojis.remove(emoji)
                        recentEmojis.add(0, emoji)
                        if (recentEmojis.size > MAX_RECENT) recentEmojis.removeAt(recentEmojis.lastIndex)
                    }
                })
            }
            grid.addView(rowLayout)
        }
    }

    private fun buildEmojiActionBar() {
        val bar = emojiActionBar ?: return
        bar.removeAllViews()
        val dark     = isDark()
        val textCol  = if (dark) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
        val actionBg = if (dark) 0x33FFFFFF else 0x22000000 // subtle translucent buttons

        fun actionKey(label: String, weight: Float, onClick: () -> Unit) =
            android.widget.TextView(this).apply {
                text      = label
                textSize  = 15f
                gravity   = android.view.Gravity.CENTER
                setTextColor(textCol)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(10f)
                    setColor(actionBg)
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                    .also { it.setMargins(dp(3f).toInt(), dp(3f).toInt(), dp(3f).toInt(), dp(3f).toInt()) }
                setOnClickListener { onClick() }
            }

        // Order: ABC/සිං | space | ⌫  (language left, delete right — swapped)
        val lang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        val abcLabel = when (lang) {
            KeyboardPreferences.LANG_SI -> "සිං"
            KeyboardPreferences.LANG_TA -> "தமி"
            else -> "En"
        }
        bar.addView(actionKey(abcLabel, 1.5f) {
            vibrateKey(); isEmoji = false; setKeyboardLayout()
        })
        bar.addView(actionKey("space", 4f) {
            vibrateKey(); currentInputConnection?.commitText(" ", 1)
        })
        bar.addView(actionKey("⌫", 1.5f) {
            vibrateKey(); currentInputConnection?.deleteSurroundingText(1, 0)
        })
    }

    // ── Key handling ──────────────────────────────────────────────
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return; vibrateKey()
        // (emoji keyboard handled below via isEmoji state)

        when (primaryCode) {
            // ── Emoji panel handled via native views — these codes unused ──
            in -60..-51 -> { /* category tabs handled by buildEmojiPanel onClick */ }
            // -61/-62 arrow keys removed — swipe to page instead
            -70 -> {
                // Emoji panel handles taps directly via onClick — this code path unused
                val emoji = lastPressedEmoji
                if (emoji.isNotBlank()) ic.commitText(emoji, 1)
                lastPressedEmoji = ""
            }

    // ── Exit emoji keyboard when ?123 or ABC is pressed ───
            Keyboard.KEYCODE_MODE_CHANGE -> {
                isComposingWord = false
                if (isEmoji) {
                    isEmoji = false; setKeyboardLayout()
                } else {
                    isSymbols = !isSymbols; awaitingZWJ = false; phoneticBuffer.clear(); setKeyboardLayout()
                }
            }

            Keyboard.KEYCODE_DELETE -> {
                isComposingWord = false
                awaitingZWJ = false; vowelAwaitingReorder = false
                if (isPhoneticMode && phoneticBuffer.isNotEmpty()) phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1)
                ic.deleteSurroundingText(1, 0)
                if (currentInput.isNotEmpty()) currentInput.deleteCharAt(currentInput.length - 1)
                updateCandidates(currentInput.toString())
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ checkAutoCapEnglish() }, 100)
            }
            Keyboard.KEYCODE_SHIFT -> {
                isComposingWord = false
                if (isSymbols) {
                    // Symbols: simple toggle NONE↔SHIFT only (no CAPS_LOCK)
                    capsState = if (capsState == CapsState.NONE) CapsState.SHIFT else CapsState.NONE
                    // Post layout change to avoid reentrancy during onKey dispatch
                    keyboardView?.post { setKeyboardLayout() }
                } else {
                    capsState = when (capsState) {
                        CapsState.NONE      -> CapsState.SHIFT
                        CapsState.SHIFT     -> CapsState.CAPS_LOCK
                        CapsState.CAPS_LOCK -> CapsState.NONE
                    }
                    keyboard?.isShifted = (capsState != CapsState.NONE)
                    keyboardView?.invalidateAllKeys()
                }
            }
            // KEYCODE_MODE_CHANGE handled above in emoji section
            Keyboard.KEYCODE_DONE -> {
                isComposingWord = false
                awaitingZWJ = false; vowelAwaitingReorder = false; phoneticBuffer.clear()
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_ENTER))
                learnAndReset()
            }
            32 -> { // Space
                isComposingWord = false
                awaitingZWJ = false; vowelAwaitingReorder = false; phoneticBuffer.clear(); ic.commitText(" ", 1); learnAndReset()
            }
            else -> {
                isComposingWord = true
                if (primaryCode <= 0) return
                val lang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
                val isWij = lang == KeyboardPreferences.LANG_SI &&
                        prefs?.sinhalaLayout == KeyboardPreferences.LAYOUT_WIJESEKARA && !isSymbols
                when {
                    isWij         -> commitWijesekara(primaryCode, ic)
                    isPhoneticMode -> handlePhonetic(primaryCode, ic, lang)
                    else           -> handleDirect(primaryCode, ic)
                }
            }
        }
    }

    private fun isSinhalaConsonant(c: Char): Boolean {
        val code = c.code
        return (code in 0x0D9A..0x0DC6)
    }

    private fun commitWijesekara(code: Int, ic: android.view.inputmethod.InputConnection) {
        val shifted = capsState != CapsState.NONE
        
        // Resolve shifted character: Prioritize wijShiftMap, then XML popup
        var outStr = code.toChar().toString()
        val isNumberKey = code in 48..57
        if (shifted && !isNumberKey) {
            val mapShift = wijShiftMap[code]
            if (mapShift != null) {
                outStr = mapShift.toChar().toString()
            } else {
                val key = keyboardView?.keyboard?.keys?.find { it.codes?.firstOrNull() == code }
                val popStr = key?.popupCharacters?.toString()?.trim() ?: ""
                val firstPop = popStr.split(" ").firstOrNull() ?: ""
                if (firstPop.isNotEmpty()) {
                    outStr = firstPop
                }
            }
        }

        // Smart Reordering: if fresh left-side vowel (ෙ, ේ, ෛ) was typed,
        // and current is a consonant, swap them.
        // Left-side vowels that require reordering with the following consonant
        val leftVowels = setOf('\u0DD9', '\u0DDA', '\u0DDB', '\u0DDC', '\u0DDD', '\u0DDE')
        val textBefore2 = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        
        // Mark fresh left-side vowel for reordering
        if (outStr.length == 1 && outStr[0] in leftVowels) {
            vowelAwaitingReorder = true
        }

        // Consolidated Smart Vowel Compositions (Shift-Aware)
        val isViramaChar = (outStr == "\u0DCA") // A key (unshifted)
        val isAalChar    = (outStr == "\u0DCF") // D key (unshifted)
        val isKombu2Char = (outStr == "\u0DD9") // F key (unshifted)
        val isGayanChar  = (outStr == "\u0DDF") // Shift+A

        // ඔ (0D94) + ් (0DCA) -> ඕ (0D95)
        if (textBefore2.endsWith("\u0D94") && isViramaChar) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText("\u0D95", 1)
            if (currentInput.isNotEmpty() && currentInput.last() == '\u0D94') {
                currentInput.deleteCharAt(currentInput.length - 1)
            }
            currentInput.append("\u0D95")
            updateCandidates(currentInput.toString())
            vowelAwaitingReorder = false
            afterWij(shifted); return
        }

        // ඔ (0D94) + ෟ (0DDF) -> ඖ (0D96)
        if (textBefore2.endsWith("\u0D94") && isGayanChar) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText("\u0D96", 1)
            if (currentInput.isNotEmpty() && currentInput.last() == '\u0D94') {
                currentInput.deleteCharAt(currentInput.length - 1)
            }
            currentInput.append("\u0D96")
            updateCandidates(currentInput.toString())
            vowelAwaitingReorder = false 
            afterWij(shifted); return
        }

        // ෙ (0DD9) + ් (0DCA) -> ේ (0DDA)
        if (textBefore2.endsWith("\u0DD9") && isViramaChar) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText("\u0DDA", 1)
            if (currentInput.isNotEmpty() && currentInput.last() == '\u0DD9') {
                currentInput.deleteCharAt(currentInput.length - 1)
            }
            currentInput.append("\u0DDA")
            updateCandidates(currentInput.toString())
            
            val charBeforeVowel = if (textBefore2.length >= 2) textBefore2[textBefore2.length - 2] else '\u0000'
            val isBeforeConsonant = charBeforeVowel.code in 0x0D9A..0x0DC6
            vowelAwaitingReorder = !isBeforeConsonant
            
            afterWij(shifted); return
        }

        // ෙ (0DD9) + ෙ (0DD9) -> ෛ (0DDB)
        if (textBefore2.endsWith("\u0DD9") && isKombu2Char) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText("\u0DDB", 1)
            if (currentInput.isNotEmpty() && currentInput.last() == '\u0DD9') {
                currentInput.deleteCharAt(currentInput.length - 1)
            }
            currentInput.append("\u0DDB")
            updateCandidates(currentInput.toString())
            
            val charBeforeVowel = if (textBefore2.length >= 2) textBefore2[textBefore2.length - 2] else '\u0000'
            val isBeforeConsonant = charBeforeVowel.code in 0x0D9A..0x0DC6
            vowelAwaitingReorder = !isBeforeConsonant
            
            afterWij(shifted); return
        }
        // ෙ (0DD9) + ් (0DCA) -> ේ (0DDA)
        if (isViramaChar) {
            if (textBefore2.endsWith("\u0DD9")) { // Consonant + ෙ
                ic.deleteSurroundingText(1, 0)
                ic.commitText("\u0DDA", 1)
                if (currentInput.isNotEmpty() && currentInput.last() == '\u0DD9') {
                    currentInput.deleteCharAt(currentInput.length - 1)
                }
                currentInput.append("\u0DDA")
                updateCandidates(currentInput.toString())
                val charBeforeVowel = if (textBefore2.length >= 2) textBefore2[textBefore2.length - 2] else '\u0000'
                val isBeforeConsonant = charBeforeVowel.code in 0x0D9A..0x0DC6
                vowelAwaitingReorder = !isBeforeConsonant
                afterWij(shifted); return
            } else if (textBefore2.length >= 2 && textBefore2[0] == '\u0DD9' && isSinhalaConsonant(textBefore2[1])) {
                // ෙ + Consonant -> Consonant + ේ
                val cons = textBefore2[1]
                ic.deleteSurroundingText(2, 0)
                ic.commitText(cons.toString() + "\u0DDA", 1)
                if (currentInput.isNotEmpty() && currentInput.length >= 2 && currentInput[currentInput.length - 1] == cons && currentInput[currentInput.length - 2] == '\u0DD9') {
                    currentInput.setLength(currentInput.length - 2)
                    currentInput.append(cons).append("\u0DDA")
                } else {
                    currentInput.setLength(0); currentInput.append(ic.getTextBeforeCursor(20, 0))
                }
                updateCandidates(currentInput.toString())
                vowelAwaitingReorder = false
                afterWij(shifted); return
            }
        }

        // ෙ (0DD9) + ා (0DCF) -> ො (0DDC)
        if (isAalChar) {
            if (textBefore2.endsWith("\u0DD9")) { // Consonant + ෙ
                ic.deleteSurroundingText(1, 0)
                ic.commitText("\u0DDC", 1)
                if (currentInput.isNotEmpty() && currentInput.last() == '\u0DD9') {
                    currentInput.deleteCharAt(currentInput.length - 1)
                }
                currentInput.append("\u0DDC")
                updateCandidates(currentInput.toString())
                val charBeforeVowel = if (textBefore2.length >= 2) textBefore2[textBefore2.length - 2] else '\u0000'
                val isBeforeConsonant = charBeforeVowel.code in 0x0D9A..0x0DC6
                vowelAwaitingReorder = !isBeforeConsonant
                afterWij(shifted); return
            } else if (textBefore2.length >= 2 && textBefore2[0] == '\u0DD9' && isSinhalaConsonant(textBefore2[1])) {
                // ෙ + Consonant -> Consonant + ො
                val cons = textBefore2[1]
                ic.deleteSurroundingText(2, 0)
                ic.commitText(cons.toString() + "\u0DDC", 1)
                if (currentInput.isNotEmpty() && currentInput.length >= 2 && currentInput[currentInput.length - 1] == cons && currentInput[currentInput.length - 2] == '\u0DD9') {
                    currentInput.setLength(currentInput.length - 2)
                    currentInput.append(cons).append("\u0DDC")
                } else {
                    currentInput.setLength(0); currentInput.append(ic.getTextBeforeCursor(20, 0))
                }
                updateCandidates(currentInput.toString())
                vowelAwaitingReorder = false
                afterWij(shifted); return
            }
        }

        // ේ (0DDA) + ා (0DCF) -> ෝ (0DDD)  OR  ො (0DDC) + ් (0DCA) -> ෝ (0DDD)
        if ((textBefore2.endsWith("\u0DDA") && isAalChar) || (textBefore2.endsWith("\u0DDC") && isViramaChar)) {
            val lastCharBeforeComposition = textBefore2.last()
            ic.deleteSurroundingText(1, 0)
            ic.commitText("\u0DDD", 1)
            if (currentInput.isNotEmpty() && currentInput.last() == lastCharBeforeComposition) {
                currentInput.deleteCharAt(currentInput.length - 1)
            }
            currentInput.append("\u0DDD")
            updateCandidates(currentInput.toString())
            val charBeforeVowel = if (textBefore2.length >= 2) textBefore2[textBefore2.length - 2] else '\u0000'
            val isBeforeConsonant = charBeforeVowel.code in 0x0D9A..0x0DC6
            vowelAwaitingReorder = !isBeforeConsonant
            afterWij(shifted); return
        } else if (isAalChar && textBefore2.length >= 2 && textBefore2[0] == '\u0DDA' && isSinhalaConsonant(textBefore2[1])) {
            // ේ + Consonant -> Consonant + ෝ
            val cons = textBefore2[1]
            ic.deleteSurroundingText(2, 0)
            ic.commitText(cons.toString() + "\u0DDD", 1)
            if (currentInput.isNotEmpty() && currentInput.length >= 2 && currentInput[currentInput.length - 1] == cons && currentInput[currentInput.length - 2] == '\u0DDA') {
                currentInput.setLength(currentInput.length - 2)
                currentInput.append(cons).append("\u0DDD")
            } else {
                currentInput.setLength(0); currentInput.append(ic.getTextBeforeCursor(20, 0))
            }
            updateCandidates(currentInput.toString())
            vowelAwaitingReorder = false
            afterWij(shifted); return
        }

        // ෙ (0DD9) + ෟ (0DDF) -> ෞ (0DDE)
        if (isGayanChar) {
            if (textBefore2.endsWith("\u0DD9")) { // Consonant + ෙ
                ic.deleteSurroundingText(1, 0)
                ic.commitText("\u0DDE", 1)
                if (currentInput.isNotEmpty() && currentInput.last() == '\u0DD9') {
                    currentInput.deleteCharAt(currentInput.length - 1)
                }
                currentInput.append("\u0DDE")
                updateCandidates(currentInput.toString())
                val charBeforeVowel = if (textBefore2.length >= 2) textBefore2[textBefore2.length - 2] else '\u0000'
                val isBeforeConsonant = charBeforeVowel.code in 0x0D9A..0x0DC6
                vowelAwaitingReorder = !isBeforeConsonant
                afterWij(shifted); return
            } else if (textBefore2.length >= 2 && textBefore2[0] == '\u0DD9' && isSinhalaConsonant(textBefore2[1])) {
                // ෙ + Consonant -> Consonant + ෞ
                val cons = textBefore2[1]
                ic.deleteSurroundingText(2, 0)
                ic.commitText(cons.toString() + "\u0DDE", 1)
                if (currentInput.isNotEmpty() && currentInput.length >= 2 && currentInput[currentInput.length - 1] == cons && currentInput[currentInput.length - 2] == '\u0DD9') {
                    currentInput.setLength(currentInput.length - 2)
                    currentInput.append(cons).append("\u0DDE")
                } else {
                    currentInput.setLength(0); currentInput.append(ic.getTextBeforeCursor(20, 0))
                }
                updateCandidates(currentInput.toString())
                vowelAwaitingReorder = false
                afterWij(shifted); return
            }
        }

        // Independent Vowel ඖ composition fallback: ඒ (0D92) + ෟ (0DDF) -> ඖ (0D96)
        if (textBefore2.endsWith("\u0D92") && isGayanChar) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText("\u0D96", 1)
            if (currentInput.isNotEmpty() && currentInput.last() == '\u0D92') {
                currentInput.deleteCharAt(currentInput.length - 1)
            }
            currentInput.append("\u0D96")
            updateCandidates(currentInput.toString())
            vowelAwaitingReorder = false 
            afterWij(shifted); return
        }

        // Smart Vowel Composition: අ (0D85) + ා (0DCF) -> ආ (0D86), අ + ැ (0DD0) -> ඇ, අ + ෑ (0DD1) -> ඈ
        if (textBefore2.endsWith("\u0D85")) {
            val combined = when {
                isAalChar -> "\u0D86" // ආ
                outStr == "\u0DD0" -> "\u0D87" // ඇ
                outStr == "\u0DD1" -> "\u0D88" // ඈ
                else -> null
            }
            if (combined != null) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(combined, 1)
                if (currentInput.isNotEmpty() && currentInput.last() == '\u0D85') {
                    currentInput.deleteCharAt(currentInput.length - 1)
                }
                currentInput.append(combined)
                updateCandidates(currentInput.toString())
                vowelAwaitingReorder = false
                afterWij(shifted); return
            }
        }

        // A vowel is reorderable if vowelAwaitingReorder is true and outStr is a consonant.
        val firstChar = outStr.firstOrNull() ?: '\u0000'
        val isConsonant = firstChar.code in 0x0D9A..0x0DC6
        
        if (vowelAwaitingReorder && isConsonant && textBefore2.isNotEmpty() && textBefore2.last() in leftVowels) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(outStr + textBefore2.last(), 1)
            
            // Fix currentInput sync: remove the vowel, then add outStr + vowel
            if (currentInput.isNotEmpty() && currentInput.last() == textBefore2.last()) {
                currentInput.deleteCharAt(currentInput.length - 1)
            }
            currentInput.append(outStr).append(textBefore2.last())
            
            updateCandidates(currentInput.toString())
            vowelAwaitingReorder = false // Only reorder ONCE
            afterWij(shifted); return
        }

        // ZWJ compound: if awaiting ZWJ, prefix ZWJ before next char
        if (awaitingZWJ && code != 3530 /* ් */) {
            ic.commitText("$ZWJ$outStr", 1)
            currentInput.append("$ZWJ$outStr")
            awaitingZWJ = false; vowelAwaitingReorder = false; afterWij(shifted); return
        }

        // Reset reorder state if we type something else that shouldn't be reordered
        if (!isConsonant && outStr[0] !in leftVowels) {
            vowelAwaitingReorder = false
        }

        ic.commitText(outStr, 1)
        currentInput.append(outStr)
        updateCandidates(currentInput.toString())
        afterWij(shifted)
    }

    private fun afterWij(wasShifted: Boolean) {
        updateCandidates(currentInput.toString())
        if (wasShifted && capsState == CapsState.SHIFT) {
            capsState = CapsState.NONE; keyboard?.isShifted = false; keyboardView?.invalidateAllKeys()
        }
    }

    override fun onText(text: CharSequence?) {
        text ?: return
        val ic = currentInputConnection ?: return
        val lang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        val isWij = lang == KeyboardPreferences.LANG_SI &&
                prefs?.sinhalaLayout == KeyboardPreferences.LAYOUT_WIJESEKARA
        // Long-press ් then selecting "්‍" popup → set ZWJ mode
        if (isWij && text.toString() == "්‍") { awaitingZWJ = true; return }
        ic.commitText(text, 1); currentInput.append(text)
        updateCandidates(currentInput.toString())
        isComposingWord = true
    }

    private fun handlePhonetic(code: Int, ic: android.view.inputmethod.InputConnection, lang: String) {
        val ch = code.toChar()
        if (ch.isLetter()) {
            phoneticBuffer.append(ch.lowercaseChar()); ic.commitText(ch.toString(), 1); tryPhoneticConvert(lang)
        } else { phoneticBuffer.clear(); ic.commitText(ch.toString(), 1) }
    }

    private fun handleDirect(code: Int, ic: android.view.inputmethod.InputConnection) {
        val lang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        val isEnglish = lang == KeyboardPreferences.LANG_EN

        var ch = code.toChar()

        // English auto-caps: if shift is active, uppercase the letter
        if (capsState != CapsState.NONE && ch.isLetter()) ch = ch.uppercaseChar()
        ic.commitText(ch.toString(), 1)
        currentInput.append(ch)
        updateCandidates(currentInput.toString())

        // Only reset SHIFT after typing a letter — not after space/punct.
        // This way shift stays active until the user actually types a letter or taps shift again.
        if (capsState == CapsState.SHIFT && !isSymbols && ch.isLetter()) {
            capsState = CapsState.NONE
            keyboard?.isShifted = false
            keyboardView?.invalidateAllKeys()
        }

        // English auto-caps: after sentence-ending punctuation + space,
        // automatically activate SHIFT so next letter starts capitalized
        if (isEnglish && !isSymbols) {
            val textBefore = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""
            val shouldCap  = when {
                currentInput.isEmpty()                          -> true  // very start
                textBefore.endsWith(". ")                       -> true
                textBefore.endsWith("? ")                       -> true
                textBefore.endsWith("! ")                       -> true
                textBefore.endsWith("\n")         -> true
                textBefore.length <= 1 && ch == ' '            -> false
                else                                            -> false
            }
            if (shouldCap && capsState == CapsState.NONE && !ch.isLetter()) {
                // Only auto-cap on next LETTER key, not on space itself
            } else if (shouldCap && capsState == CapsState.NONE && ch == ' ') {
                // Check if prev non-space char was sentence end
                val prevText = ic.getTextBeforeCursor(4, 0)?.toString()?.trimEnd() ?: ""
                if (prevText.endsWith(".") || prevText.endsWith("?") || prevText.endsWith("!")) {
                    capsState = CapsState.SHIFT
                    keyboard?.isShifted = true
                    keyboardView?.invalidateAllKeys()
                }
            }
        }
    }

    // Auto-cap English: fires on field open and after sentence punctuation.
    // Sets SHIFT (auto-off after 1 letter) — not CAPS_LOCK.
    private fun checkAutoCapEnglish() {
        val lang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        if (lang != KeyboardPreferences.LANG_EN || isSymbols || isEmoji) return
        if (capsState != CapsState.NONE) return  // already shifted
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""
        val shouldCap = before.isEmpty() ||
                before.endsWith(". ") || before.endsWith("? ") || before.endsWith("! ") ||
                before.endsWith("\n")
        if (shouldCap) {
            capsState = CapsState.SHIFT
            keyboard?.isShifted = true
            keyboardView?.invalidateAllKeys()
        }
    }

    private fun tryPhoneticConvert(lang: String) {
        val buf = phoneticBuffer.toString()
        if (lang == KeyboardPreferences.LANG_TA) {
            // Tamil: use existing flat map
            for (len in minOf(3, buf.length) downTo 1) {
                val s = buf.takeLast(len); val m = tamilPhoneticMap.firstOrNull { it.first == s } ?: continue
                currentInputConnection?.deleteSurroundingText(len, 0)
                currentInputConnection?.commitText(m.second, 1)
                repeat(len) { if (phoneticBuffer.isNotEmpty()) phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1) }
                currentInput.append(m.second); updateCandidates(currentInput.toString()); return
            }
        } else {
            val ic = currentInputConnection ?: return

            // ── Phase 1: Context-aware vowel attachment ──────────────────
            // If the last committed Sinhala character is HAL (®), and the phonetic
            // buffer starts with a vowel pattern, attach it as a diacritic instead of
            // outputting an independent vowel.
            // e.g.: 'm' already produced ම® → typing 'a' → delete ®, leave ම (inherent a)
            //       'm' already produced ම® → typing 'aa' → delete ®, add ා → මමා
            val textBefore = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (textBefore.isNotEmpty() && textBefore.last() == '\u0DCA' /* HAL */) {
                val vowelMatch = SinhalaPhonetic.tryGetVowelSign(buf)
                if (vowelMatch != null) {
                    val (len, sign) = vowelMatch
                    ic.deleteSurroundingText(1, 0)          // delete the HAL
                    if (sign.isNotEmpty()) ic.commitText(sign, 1)  // add diacritic (empty = inherent a)
                    repeat(len) { if (phoneticBuffer.isNotEmpty()) phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1) }
                    currentInput.append(sign)
                    updateCandidates(currentInput.toString()); return
                }
            }

            // ── Phase 2: Normal syllable conversion ──────────────────────
            val match = SinhalaPhonetic.tryConvert(buf)
            if (match != null) {
                val (len, sinhala) = match
                ic.deleteSurroundingText(len, 0)
                ic.commitText(sinhala, 1)
                repeat(len) { if (phoneticBuffer.isNotEmpty()) phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1) }
                currentInput.append(sinhala); updateCandidates(currentInput.toString()); return
            }
        }
        updateCandidates(currentInput.toString())
    }

    private fun updateCandidates(input: String) {
        val c = candidatesContainer ?: return; c.removeAllViews()
        val rootView = keyboardView?.rootView
        
        // Candidate bar width and visibility are now static for consistency

        if (!(prefs?.showPredictions ?: true)) return
        val lang = prefs?.currentLanguage ?: "EN"
        val emojis = if (lang == KeyboardPreferences.LANG_EN && input.isNotEmpty())
            wordPredictor?.getEmojiSuggestions(input) ?: emptyList() else emptyList()

        // Async callback: called twice — instantly with local, then again with internet merged
        wordPredictor?.getSuggestions(input, lang) { words ->
            val combined = (words + emojis).distinct().take(10)
            c.removeAllViews()
            combined.forEach { w -> addChip(c, w) { commitSuggestion(w) } }
        }
    }

    private fun showEmojiPanel(catIndex: Int) {
        val c = candidatesContainer ?: return
        c.removeAllViews()
        emojiCategory = catIndex.coerceIn(0, emojiCategories.lastIndex)

        // Category tab icons — use representative emoji for each category tab
        val catIcons = listOf("😀", "👋", "❤", "🐾", "🍔", "🚗", "⚽", "💡")
        catIcons.forEachIndexed { idx, icon ->
            val isActive = idx == emojiCategory
            c.addView(TextView(this).apply {
                text = icon
                textSize = 18f
                setPadding(dp(9f).toInt(), 0, dp(9f).toInt(), 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                alpha = if (isActive) 1.0f else 0.40f
                background = if (isActive) {
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = dp(8f)
                        setColor(0x33FFFFFF)
                        setStroke(dp(0.5f).toInt(), 0x44FFFFFF)
                    }
                } else null
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).also { it.setMargins(dp(1f).toInt(), dp(6f).toInt(), dp(1f).toInt(), dp(6f).toInt()) }
                setOnClickListener { emojiCategory = idx; showEmojiPanel(idx) }
            })
        }

        // Vertical divider
        c.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(0.5f).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                .also { it.setMargins(dp(5f).toInt(), dp(8f).toInt(), dp(5f).toInt(), dp(8f).toInt()) }
            setBackgroundColor(0x44FFFFFF)
        })

        // All emojis for selected category — scrollable
        emojiCategories[emojiCategory].second.forEach { emoji ->
            c.addView(TextView(this).apply {
                text = emoji
                textSize = 24f
                setPadding(dp(5f).toInt(), 0, dp(5f).toInt(), 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).also { it.setMargins(dp(1f).toInt(), dp(3f).toInt(), dp(1f).toInt(), dp(3f).toInt()) }
                setOnClickListener {
                    currentInputConnection?.commitText(emoji, 1)
                    // keep panel open so user can pick more
                }
            })
        }
    }

    private fun addChip(container: LinearLayout, text: String, onClick: () -> Unit) {
        container.addView(TextView(this).apply {
            this.text = text; textSize = 14f; setPadding(18, 0, 18, 0)
            setTextColor(if (isDark()) 0xFFEEEEFF.toInt() else 0xFF1A1A2E.toInt())
            setBackgroundResource(R.drawable.candidate_bar_bg); gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).also { it.setMargins(3, 5, 3, 5) }
            setOnClickListener { onClick() }
        })
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        isComposingWord = false
        if (currentInput.isNotEmpty()) ic.deleteSurroundingText(currentInput.length, 0)
        ic.commitText("$word ", 1); wordPredictor?.learnWord(word); clipboard?.save(word)
        currentInput.clear(); updateCandidates("")
    }

    private fun learnAndReset() {
        isComposingWord = false
        if (currentInput.isNotEmpty()) { wordPredictor?.learnWord(currentInput.toString()); clipboard?.save(currentInput.toString()) }
        currentInput.clear(); updateCandidates("")
    }

    private fun vibrateKey() {
        if (!(prefs?.vibrateOnKey ?: true)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                    ?.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
            else {
                @Suppress("DEPRECATION") val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v?.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") v?.vibrate(14)
            }
        } catch (_: Exception) {}
    }

    override fun swipeLeft()  { vibrateKey(); val en = prefs?.enabledLanguages ?: return; if (en.size <= 1) return; val c = prefs?.currentLanguage ?: en[0]; prefs?.currentLanguage = en[(en.indexOf(c)+1)%en.size]; isSymbols=false; capsState=CapsState.NONE; awaitingZWJ=false; phoneticBuffer.clear(); currentInput.clear(); setKeyboardLayout(); rebuildLangPills(); updateCandidates("") }
    override fun swipeRight() { swipeLeft() }
    override fun onPress(primaryCode: Int) {
        if (primaryCode == -70 && isEmoji) {
            val kb = keyboard ?: return
            val kv = keyboardView ?: return
            val key = kb.keys?.firstOrNull { k ->
                kv.lastTouchX >= k.x.toFloat() && kv.lastTouchX < (k.x + k.width).toFloat() &&
                        kv.lastTouchY >= k.y.toFloat() && kv.lastTouchY < (k.y + k.height).toFloat()
            }
            lastPressedEmoji = key?.label?.toString()?.trim() ?: ""
        }
    }
    override fun onRelease(p: Int) {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}