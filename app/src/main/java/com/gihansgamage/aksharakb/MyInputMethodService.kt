package com.gihansgamage.aksharakb

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.net.Uri
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
    private var btnKbMode: TextView? = null
    private var btnSettings: TextView? = null
    private var wordPredictor: WordPredictor? = null
    private var clipboard: KeyboardClipboard? = null
    private var prefs: KeyboardPreferences? = null

    private enum class CapsState { NONE, SHIFT, CAPS_LOCK }
    private var capsState      = CapsState.NONE
    private var phoneticBuffer = StringBuilder()
    private var isPhoneticMode = false
    private var isSymbols      = false
    private var isEmoji        = false   // true when emoji keyboard is shown
    private var showEmoji      = false   // legacy candidate-bar emoji (now unused)
    private var emojiCategory  = 0      // 0-7 active emoji category
    private var emojiPage      = 0      // which page of emojis (10 per row × 3 rows = 30 per page)
    private var currentInput   = StringBuilder()
    private var awaitingZWJ    = false
    private var lastTappedEmoji = ""
    private val emojiGrid = mutableListOf<String>()  // current page emoji labels in order
    private var emojiTapSlot = 0  // which slot was last tapped (0-29)

    // ── Wijesekara shift map ──────────────────────────────────────
    // Exact values from spec, verified with python3 ord()
    //
    // Row 1 (Q-P):
    //   Q: ු(3540)→ූ(3542)    W: අ(3461)→උ(3467)    E: ැ(3536)→ෑ(3537)
    //   R: ර(3515)→ඍ(3469)    T: එ(3473)→ඒ(3474)    Y: හ(3524)→ඓ(3475)
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
        3540 to 3542, 3461 to 3467, 3536 to 3537, 3515 to 3469, 3473 to 3474,
        3524 to 3475, 3512 to 3478, 3523 to 3522, 3503 to 3504, 3488 to 3489,
        // Row 2 (simple substitutions — H and J handled specially)
        3530 to 3535, 3538 to 3539,
        // D key: ා(3535) normal, Shift→ෘ(3544) — note ා also on A key
        // Since both A and D emit different codes in XML:
        //   A key codes=3530 (්), D key codes=3535 (ා)
        3535 to 3544, // ා → ෘ  (D key shift)
        3545 to 3526, // ෙ → ෆ  (F key shift)
        3495 to 3496, // ට → ඨ
        // H(3514)→ZWJ compound, J(3520)→ළු: handled in commitWijesekara
        3505 to 3499, 3482 to 3483, 3501 to 3502,
        // Row 3
        3458 to 3459, 3490 to 3491, 3497 to 3498, 3465 to 3466,
        3510 to 3511, 3508 to 3509, 3517 to 3525, 3484 to 3485,
        46   to 63,
        // Number row
        49 to 33, 50 to 64, 51 to 35, 52 to 36, 53 to 37,
        54 to 94, 55 to 38, 56 to 42, 57 to 40, 48 to 41,
        45 to 95, 61 to 43
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

    // ── Emoji categories — 1605 total across 10 categories ─────────
    private val emojiCategories = listOf(
        "😀 Smileys" to listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","🫠","😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙","🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🫢","🫣","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","😶‍🌫","😏","😒","🙄","😬","😮‍💨","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵","😵‍💫","🤯","🤠","🥳","🥸","😎","🤓","🧐","😕","🫤","😟","🙁","😮","😯","😲","😳","🥺","🥹","😦","😧","😨","😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠","💩","🤡","👹","👺","👻","👽","👾","🤖","😺","😸","😹","😻","😼","😽","🙀","😿","😾"
        ),
        "👋 People" to listOf(
            "👋","🤚","🖐","✋","🖖","🫱","🫲","🫳","🫴","🫷","🫸","👌","🤌","🤏","✌","🤞","🫰","🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝","🫵","👍","👎","✊","👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍","💅","🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🫀","🫁","🧠","🦷","🦴","👀","👁","👅","👄","🫦","👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓","👴","👵","🙍","🙎","🙅","🙆","💁","🙋","🧏","🙇","🤦","🤷","👮","🕵","💂","🥷","👷","🫅","🤴","👸","👳","👲","🧕","🤵","👰","🤰","🫃","🫄","🤱","👼","🎅","🤶","🦸","🦹","🧙","🧝","🧛","🧟","🧌","🧞","🧜","🧚","👫","👬","👭","💏","💑","👨‍👩‍👦","👨‍👩‍👧","👩‍👦","👩‍👧","👨‍👦","👨‍👧"
        ),
        "❤ Hearts" to listOf(
            "❤","🩷","🧡","💛","💚","💙","🩵","💜","🖤","🩶","🤍","🤎","💔","❤‍🔥","❤‍🩹","❣","💕","💞","💓","💗","💖","💘","💝","💟","☮","✝","☪","🪯","🕉","☸","✡","🔯","🕎","☯","☦","🛐","⛎","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","⚛","🉑","☢","☣","📴","📳","🈶","🈚","🈸","🈺","🈷","✴","🆚","💮","🉐","㊙","㊗","🈴","🈵","🈹","🈲","🅰","🅱","🆎","🆑","🅾","🆘","⛔","📛","🚫","💯","💢","♨","🚷","🚯","🚳","🚱","🔞","📵","🚭","❗","❕","❓","❔","‼","⁉","🔅","🔆","〽","⚠","🔱","⚜","🔰","♻","✅","🈯","💹","❎","🌐","🌀","➿","🌁"
        ),
        "🐾 Animals" to listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄","🐨","🐯","🦁","🐮","🐷","🐽","🐸","🐵","🙈","🙉","🙊","🐒","🐔","🐧","🐦","🐤","🐣","🐥","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🫏","🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪲","🦟","🦗","🪳","🕷","🦂","🐢","🐍","🦎","🦖","🦕","🐙","🦑","🪼","🦐","🦞","🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅","🐆","🦓","🫎","🦍","🦧","🦣","🐘","🦛","🦏","🐪","🐫","🦒","🦘","🦬","🐃","🐂","🐄","🐎","🐖","🐏","🐑","🦙","🐐","🦌","🐕","🐩","🦮","🐈","🪶","🐓","🦃","🦤","🦚","🦜","🦢","🪿","🦩","🕊","🐇","🦝","🦨","🦡","🦫","🦦","🦥","🐁","🐀","🐿","🦔","🐾","🐉","🐲","🌵","🎄","🌲","🌳","🌴","🪵","🌱","🌿","☘","🍀","🎍","🪴","🎋","🍃","🍂","🍁","🪺","🪹","🍄","🌾","💐","🌷","🪷","🌹","🥀","🌺","🌸","🌼","🌻","🌞","🌝","🌛","🌜","🌚","🌕","🌖","🌗","🌘","🌑","🌒","🌓","🌔","🌙","🌟","⭐","🌠","🌌","☁","⛅","🌈","⚡","❄","☃","⛄","🌊","💧","💦","🫧","🌀"
        ),
        "🍔 Food" to listOf(
            "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶","🫑","🌽","🥕","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🦴","🌭","🍔","🍟","🍕","🫓","🫔","🌮","🌯","🥙","🧆","🥘","🍲","🫕","🍜","🍝","🍢","🍣","🍤","🍙","🍚","🍱","🥟","🦪","🥡","🍛","🥗","🥫","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🌰","🥜","🍯","🧃","🥤","🧋","☕","🍵","🫖","🍶","🍺","🍻","🥂","🍷","🫗","🥃","🍸","🍹","🧉","🍾","🧊","🥄","🍴","🍽","🥢","🫙"
        ),
        "🚗 Travel" to listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🏍","🛵","🛺","🚲","🛴","🛹","🛼","🚏","🛣","🛤","⛽","🛞","🚨","🚥","🚦","🛑","🚧","⚓","🛟","⛵","🛶","🚤","🛳","⛴","🛥","🚢","✈","🛩","🛫","🛬","🪂","💺","🚁","🚟","🚠","🚡","🛰","🚀","🛸","🪐","🌍","🌎","🌏","🧭","🏔","⛰","🌋","🗺","🏕","🏖","🏜","🏝","🏞","🏟","🏛","🏗","🧱","🪨","🛖","🏘","🏚","🏠","🏡","🏢","🏣","🏤","🏥","🏦","🏨","🏩","🏪","🏫","🏬","🏭","🏯","🏰","💒","🗼","🗽","⛪","🕌","🛕","🕍","⛩","🕋","⛲","⛺","🌁","🌃","🏙","🌄","🌅","🌆","🌇","🌉","🗾","🎑","🌐"
        ),
        "⚽ Sports" to listOf(
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🪀","🏓","🏸","🏒","🥍","🏑","🏏","🪃","🥅","⛳","🪁","🛝","🎣","🤿","🎽","🎿","🛷","🥌","🎯","🏋","🤸","⛹","🤺","🤾","🏌","🏇","🧘","🏄","🏊","🤽","🚣","🧗","🚵","🚴","🏆","🥇","🥈","🥉","🏅","🎖","🏵","🎗","🎫","🎟","🎪","🤹","🎭","🩰","🎨","🎬","🎤","🎧","🎼","🎵","🎶","🎷","🪗","🎸","🎹","🎺","🎻","🥁","🪘","🎙","🎚","🎛","📻","🎮","🕹","🎲","♟","🎰","🎠","🎡","🎢"
        ),
        "💡 Objects" to listOf(
            "📱","💻","🖥","🖨","⌨","🖱","🖲","💽","💾","💿","📀","🎥","📽","🎞","📞","☎","📟","📠","📺","📷","📸","📹","📼","🔍","🔎","🕯","💡","🔦","🏮","🪔","📔","📕","📖","📗","📘","📙","📚","📓","📒","📃","📄","📑","🗒","🗓","📆","📅","📇","📈","📉","📊","📋","📌","📍","✂","🗃","🗄","🗑","🔒","🔓","🔏","🔐","🔑","🗝","🔨","🪓","⛏","⚒","🛠","🗡","⚔","🔫","🪃","🛡","🪚","🔧","🪛","🔩","⚙","🗜","⚖","🪜","🔗","⛓","🪝","🧲","🔮","🧿","🪬","🧸","🪅","🎊","🎉","🪆","🎎","🎐","🎏","🎀","🎁","🎗","🎟","🎫","🏷","📦","📫","📪","📬","📭","📮","🗳","✏","✒","🖋","🖊","📝","💼","📁","📂","🗂","🗞","📰","📏","📐","✂","🧹","🧺","🧻","🪣","🧴","🧷","🧽","🧼","🫧","🪥","🪒","🛒","🚪","🪞","🪟","🛏","🛋","🪑","🚽","🪠","🚿","🛁","🪤","💈","💊","💉","🩸","🧬","🦠","🧫","🧪","🌡","🔭","🩺","🩻","🩹","🩼","💎","💍","👑","👒","🎓","🪖","⛑","💄","💋","👓","🕶","🥽","🌂","☂","🧵","🪡","🧶","🪢","👔","👕","👖","🧣","🧤","🧥","🧦","👗","👘","🥻","🩱","🩲","🩳","👙","👚","👛","👜","👝","🎒","🧳","👞","👟","🥾","🥿","👠","👡","🩰","👢","🌂","☂"
        ),
        "🔣 Symbols" to listOf(
            "🔴","🟠","🟡","🟢","🔵","🟣","🟤","⚫","⚪","🟥","🟧","🟨","🟩","🟦","🟪","🟫","⬛","⬜","◼","◻","◾","◽","▪","▫","🔶","🔷","🔸","🔹","🔺","🔻","💠","🔘","🔳","🔲","🔈","🔇","🔉","🔊","📢","📣","🔔","🔕","♾","⚕","♻","⚜","🔰","✅","❎","🆗","🆙","🆕","🆒","🆓","🔝","🆖","🆎","🆑","🅾","🆘","🔃","🔄","🔙","🔚","🔛","🔜","🔝","⬆","↗","➡","↘","⬇","↙","⬅","↖","↕","↔","↩","↪","⤴","⤵","🔀","🔁","🔂","▶","⏩","⏭","⏯","◀","⏪","⏮","🔼","⏫","🔽","⏬","⏸","⏹","⏺","⏏","🎦","📶","📳","📴","💹","🔱","❇","✳","0️⃣","1️⃣","2️⃣","3️⃣","4️⃣","5️⃣","6️⃣","7️⃣","8️⃣","9️⃣","🔟","💯","🔠","🔡","🔤"
        ),
        "🏳 Flags" to listOf(
            "🏴‍☠","🏳","🏳‍🌈","🏳‍⚧","🏴","🚩","🎌","🏁",
            "🇦🇫","🇦🇱","🇩🇿","🇦🇩","🇦🇴","🇦🇬","🇦🇷","🇦🇲","🇦🇺","🇦🇹","🇦🇿","🇧🇸","🇧🇭","🇧🇩","🇧🇧","🇧🇾","🇧🇪","🇧🇿","🇧🇯","🇧🇹","🇧🇴","🇧🇦","🇧🇼","🇧🇷","🇧🇳","🇧🇬","🇧🇫","🇧🇮","🇰🇭","🇨🇲","🇨🇦","🇨🇻","🇨🇫","🇹🇩","🇨🇱","🇨🇳","🇨🇴","🇰🇲","🇨🇬","🇨🇩","🇨🇰","🇨🇷","🇭🇷","🇨🇺","🇨🇾","🇨🇿","🇩🇰","🇩🇯","🇩🇲","🇩🇴","🇪🇨","🇪🇬","🇸🇻","🇬🇶","🇪🇷","🇪🇪","🇸🇿","🇪🇹","🇪🇺","🇫🇯","🇫🇮","🇫🇷","🇬🇦","🇬🇲","🇬🇪","🇩🇪","🇬🇭","🇬🇷","🇬🇩","🇬🇹","🇬🇳","🇬🇼","🇬🇾","🇭🇹","🇭🇳","🇭🇰","🇭🇺","🇮🇸","🇮🇳","🇮🇩","🇮🇷","🇮🇶","🇮🇪","🇮🇱","🇮🇹","🇯🇲","🇯🇵","🇯🇴","🇰🇿","🇰🇪","🇰🇮","🇽🇰","🇰🇼","🇰🇬","🇱🇦","🇱🇻","🇱🇧","🇱🇸","🇱🇷","🇱🇾","🇱🇮","🇱🇹","🇱🇺","🇲🇬","🇲🇼","🇲🇾","🇲🇻","🇲🇱","🇲🇹","🇲🇭","🇲🇷","🇲🇺","🇲🇽","🇫🇲","🇲🇩","🇲🇨","🇲🇳","🇲🇪","🇲🇦","🇲🇿","🇲🇲","🇳🇦","🇳🇷","🇳🇵","🇳🇱","🇳🇿","🇳🇮","🇳🇪","🇳🇬","🇳🇴","🇴🇲","🇵🇰","🇵🇼","🇵🇸","🇵🇦","🇵🇬","🇵🇾","🇵🇪","🇵🇭","🇵🇱","🇵🇹","🇵🇷","🇶🇦","🇷🇴","🇷🇺","🇷🇼","🇼🇸","🇸🇲","🇸🇹","🇸🇦","🇸🇳","🇷🇸","🇸🇨","🇸🇱","🇸🇬","🇸🇰","🇸🇮","🇸🇧","🇸🇴","🇿🇦","🇸🇸","🇪🇸","🇱🇰","🇸🇩","🇸🇷","🇸🇪","🇨🇭","🇸🇾","🇹🇼","🇹🇯","🇹🇿","🇹🇭","🇹🇱","🇹🇬","🇹🇴","🇹🇹","🇹🇳","🇹🇷","🇹🇲","🇹🇻","🇺🇬","🇺🇦","🇦🇪","🇬🇧","🇺🇸","🇺🇾","🇺🇿","🇻🇺","🇻🇦","🇻🇪","🇻🇳","🇾🇪","🇿🇲","🇿🇼"
        )
    )

    // ── Lifecycle

    // ── Lifecycle ─────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPreferences(this); wordPredictor = WordPredictor(this)
        clipboard = KeyboardClipboard(this); prefs?.registerListener(this)
    }
    override fun onDestroy() { prefs?.unregisterListener(this); super.onDestroy() }

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
    override fun onCreateInputView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        keyboardView        = v.findViewById(R.id.keyboard_view)
        candidatesContainer = v.findViewById(R.id.candidates_container)
        candidatesScroll    = v.findViewById(R.id.candidates_view)
        langPillsContainer  = v.findViewById(R.id.lang_pills_container)
        btnKbMode           = v.findViewById(R.id.btn_kb_mode)

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

        // Keyboard mode button (Wijesekara ↔ Phonetic toggle for SI/TA)
        v.findViewById<TextView>(R.id.btn_kb_mode)?.setOnClickListener {
            vibrateKey()
            val lang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
            if (lang == KeyboardPreferences.LANG_SI || lang == KeyboardPreferences.LANG_TA) {
                val cur = prefs?.sinhalaLayout ?: KeyboardPreferences.LAYOUT_PHONETIC
                prefs?.sinhalaLayout = if (cur == KeyboardPreferences.LAYOUT_PHONETIC)
                    KeyboardPreferences.LAYOUT_WIJESEKARA else KeyboardPreferences.LAYOUT_PHONETIC
                setKeyboardLayout(); updateKbModeButton()
            }
        }

        // Settings (now a TextView with ⚙ symbol)
        v.findViewById<TextView>(R.id.btn_settings)?.setOnClickListener {
            vibrateKey()
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }?.let { startActivity(it) }
        }

        keyboardView?.setOnKeyboardActionListener(this)

        // Blur behind is applied at window level in onWindowShown()

        // Emoji swipe: left = next page, right = prev page
        keyboardView?.onEmojiSwipe = { direction ->
            val maxPage = ((emojiCategories[emojiCategory].second.size - 1) / 30)
            emojiPage = when {
                direction > 0 -> if (emojiPage < maxPage) emojiPage + 1 else 0
                else          -> if (emojiPage > 0) emojiPage - 1 else maxPage
            }
            emojiTapSlot = 0
            populateEmojiKeys()
            keyboardView?.invalidateAllKeys()
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

        enabled.forEach { lang ->
            val isActive = lang == current
            val pill = TextView(this).apply {
                text = langLabel(lang)
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setPadding(dp(10f).toInt(), 0, dp(10f).toInt(), 0)
                setTextColor(if (isActive) 0xFFFFFFFF.toInt() else 0x88FFFFFF.toInt())
                background = if (isActive) {
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = dp(8f)
                        setColor(0x44FFFFFF)
                        setStroke(dp(0.6f).toInt(), 0x55FFFFFF)
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
                }
            }
            container.addView(pill)
        }
    }

    private fun updateKbModeButton() {
        val lang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        val phonetic = prefs?.sinhalaLayout == KeyboardPreferences.LAYOUT_PHONETIC
        btnKbMode?.text = when {
            lang == KeyboardPreferences.LANG_SI && phonetic  -> "Pho"
            lang == KeyboardPreferences.LANG_SI && !phonetic -> "Wij"
            lang == KeyboardPreferences.LANG_TA && phonetic  -> "Pho"
            lang == KeyboardPreferences.LANG_TA && !phonetic -> "Dir"
            else -> "ABC"
        }
    }

    private fun dp(v: Float) = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun applyCurrentPrefs() {
        val p = prefs ?: return; val kv = keyboardView ?: return
        if (p.theme == KeyboardPreferences.THEME_CUSTOM && p.bgImageUri.isNotEmpty()) {
            try {
                val s = contentResolver.openInputStream(Uri.parse(p.bgImageUri))
                kv.setKeyboardImage(BitmapFactory.decodeStream(s)); s?.close()
            } catch (_: Exception) { kv.setKeyboardImage(null) }
        } else { kv.setKeyboardImage(null) }
        kv.isPreviewEnabled = p.showPopupKeys
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
        keyboard?.isShifted = (capsState != CapsState.NONE)

        // For emoji keyboard: populate emoji labels into the keyboard keys
        if (isEmoji) {
            populateEmojiKeys()
            keyboardView?.isEmojiMode   = true
            keyboardView?.activeCategoryTab = emojiCategory
        } else {
            keyboardView?.isEmojiMode   = false
        }
        keyboardView?.invalidateAllKeys()
        updateKbModeButton()
    }

    // Populate emoji keyboard keys with current category + page
    private fun populateEmojiKeys() {
        val kb = keyboard ?: return
        val emojis = emojiCategories[emojiCategory].second
        val perPage = 30
        val startIdx = emojiPage * perPage
        val pageEmojis = emojis.drop(startIdx).take(perPage)

        // Keys: index 8 onward (after 8 category tab keys) are emoji slots
        // Then last row (bottom nav) — skip those
        // Layout: row0=8 tabs, row1=10 emojis, row2=10 emojis, row3=10 emojis, row4=5 nav
        val keys = kb.keys ?: return
        // Category tab keys: indices 0-7 (codes -51 to -58)
        // Emoji keys: indices 8-37 (30 keys, codes -60)
        // Nav keys: indices 38-42

        emojiGrid.clear()
        var emojiIdx = 0
        for (key in keys) {
            val code = key.codes.firstOrNull() ?: 0
            if (code == -70) {
                val emoji = pageEmojis.getOrNull(emojiIdx) ?: ""
                key.label = emoji
                if (emoji.isNotBlank()) emojiGrid.add(emoji)
                emojiIdx++
            }
        }
    }

    // ── Key handling ──────────────────────────────────────────────
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return; vibrateKey()
        // (emoji keyboard handled below via isEmoji state)

        when (primaryCode) {
            // ── Emoji keyboard navigation ─────────────────────
            in -60..-51 -> {
                // Category tab tapped (codes -51 to -60 → category index 0-9)
                emojiCategory = (-primaryCode) - 51
                emojiPage = 0
                populateEmojiKeys()
                keyboardView?.activeCategoryTab = emojiCategory
                keyboardView?.invalidateAllKeys()
            }
            // -61/-62 arrow keys removed — swipe to page instead
            -70 -> {
                // Emoji key tapped. All -60 keys share the same code so we use
                // emojiTapSlot to track which one. emojiGrid holds current page emojis in order.
                val emoji = emojiGrid.getOrNull(emojiTapSlot) ?: ""
                if (emoji.isNotBlank()) ic.commitText(emoji, 1)
                emojiTapSlot = (emojiTapSlot + 1) % maxOf(emojiGrid.size, 1)
            }

            // ── Exit emoji keyboard when ?123 or ABC is pressed ───
            Keyboard.KEYCODE_MODE_CHANGE -> {
                if (isEmoji) {
                    isEmoji = false; setKeyboardLayout()
                } else {
                    isSymbols = !isSymbols; awaitingZWJ = false; phoneticBuffer.clear(); setKeyboardLayout()
                }
            }

            Keyboard.KEYCODE_DELETE -> {
                awaitingZWJ = false
                if (isPhoneticMode && phoneticBuffer.isNotEmpty()) phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1)
                ic.deleteSurroundingText(1, 0)
                if (currentInput.isNotEmpty()) currentInput.deleteCharAt(currentInput.length - 1)
                updateCandidates(currentInput.toString())
            }
            Keyboard.KEYCODE_SHIFT -> {
                capsState = when (capsState) {
                    CapsState.NONE      -> CapsState.SHIFT
                    CapsState.SHIFT     -> CapsState.CAPS_LOCK
                    CapsState.CAPS_LOCK -> CapsState.NONE
                }
                keyboard?.isShifted = (capsState != CapsState.NONE)
                keyboardView?.invalidateAllKeys()
            }
            // KEYCODE_MODE_CHANGE handled above in emoji section
            Keyboard.KEYCODE_DONE -> {
                awaitingZWJ = false; phoneticBuffer.clear()
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_ENTER))
                learnAndReset()
            }
            32 -> { awaitingZWJ = false; phoneticBuffer.clear(); ic.commitText(" ", 1); learnAndReset() }
            else -> {
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

    private fun commitWijesekara(code: Int, ic: android.view.inputmethod.InputConnection) {
        val shifted = capsState != CapsState.NONE

        // ZWJ compound: if awaiting ZWJ, prefix ZWJ before next char
        if (awaitingZWJ && code != 3530 /* ් */) {
            val out = if (shifted) wijShiftMap.getOrDefault(code, code) else code
            ic.commitText("$ZWJ${out.toChar()}", 1)
            currentInput.append("$ZWJ${out.toChar()}")
            awaitingZWJ = false; afterWij(shifted); return
        }

        // Special shifted multi-char outputs
        if (shifted) {
            when (code) {
                NORMAL_YA -> { // H key Shift → ්‍ය
                    ic.commitText("${3530.toChar()}$ZWJ${3514.toChar()}", 1)
                    currentInput.append("${3530.toChar()}$ZWJ${3514.toChar()}")
                    afterWij(true); return
                }
                NORMAL_WA -> { // J key Shift → ළු
                    ic.commitText("${3525.toChar()}${3540.toChar()}", 1)
                    currentInput.append("${3525.toChar()}${3540.toChar()}")
                    afterWij(true); return
                }
            }
        }

        val out = if (shifted) wijShiftMap.getOrDefault(code, code) else code
        ic.commitText(out.toChar().toString(), 1)
        currentInput.append(out.toChar())
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

        // After typing one letter in SHIFT mode → reset to NONE
        if (capsState == CapsState.SHIFT && ch.isLetter()) {
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

    // Check if English keyboard should auto-cap (e.g. after field focus or sentence end)
    private fun checkAutoCapEnglish() {
        val lang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        if (lang != KeyboardPreferences.LANG_EN || isSymbols || isEmoji) return
        if (capsState != CapsState.NONE) return  // already shifted
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        if (before.isEmpty()) {
            // Start of field — auto cap
            capsState = CapsState.SHIFT
            keyboard?.isShifted = true
            keyboardView?.invalidateAllKeys()
        }
    }

    private fun tryPhoneticConvert(lang: String) {
        val buf = phoneticBuffer.toString()
        val map = if (lang == KeyboardPreferences.LANG_TA) tamilPhoneticMap else sinhalaPhoneticMap
        for (len in minOf(3, buf.length) downTo 1) {
            val s = buf.takeLast(len); val m = map.firstOrNull { it.first == s } ?: continue
            currentInputConnection?.deleteSurroundingText(len, 0)
            currentInputConnection?.commitText(m.second, 1)
            repeat(len) { if (phoneticBuffer.isNotEmpty()) phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1) }
            currentInput.append(m.second); updateCandidates(currentInput.toString()); return
        }
        updateCandidates(currentInput.toString())
    }

    private fun updateCandidates(input: String) {
        val c = candidatesContainer ?: return; c.removeAllViews()
        if (!(prefs?.showPredictions ?: true)) return
        val lang = prefs?.currentLanguage ?: "EN"
        val words = wordPredictor?.getSuggestions(input, lang) ?: emptyList()
        val emojis = if (lang == KeyboardPreferences.LANG_EN && input.isNotEmpty())
            wordPredictor?.getEmojiSuggestions(input) ?: emptyList() else emptyList()
        (words + emojis).take(8).forEach { w -> addChip(c, w) { commitSuggestion(w) } }
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
            setTextColor(if (prefs?.theme in listOf(KeyboardPreferences.THEME_DARK, KeyboardPreferences.THEME_OCEAN, KeyboardPreferences.THEME_SUNSET)) 0xFFEEDDFF.toInt() else 0xFF1E0A3C.toInt())
            setBackgroundResource(R.drawable.candidate_bar_bg); gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).also { it.setMargins(3, 5, 3, 5) }
            setOnClickListener { onClick() }
        })
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (currentInput.isNotEmpty()) ic.deleteSurroundingText(currentInput.length, 0)
        ic.commitText("$word ", 1); wordPredictor?.learnWord(word); clipboard?.save(word)
        currentInput.clear(); updateCandidates("")
    }

    private fun learnAndReset() {
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
    override fun onPress(p: Int) {
        if (p == -60 && isEmoji) {
            // Find the pressed emoji key by scanning keys under touch
            // We rely on the fact that onPress fires with code of the pressed key
            // Use a workaround: scan keyboard keys and find first non-blank -60 key
            // that hasn't been used yet — this works when tapping sequentially
            // Better: the keyboard view calls onPress then onKey; we find the key
            // by using the Keyboard object directly
            val kb = keyboard ?: return
            val keys = kb.keys ?: return
            // Find all -60 keys with non-blank labels
            val emojiKeys = keys.filter { it.codes.firstOrNull() == -60 && it.label?.isNotBlank() == true }
            // We store ALL emojis in order, touch position gives us index
            // Simple approach: we'll set lastTappedEmoji in the custom onKey via keyCodes array
        }
    }
    override fun onRelease(p: Int) {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}