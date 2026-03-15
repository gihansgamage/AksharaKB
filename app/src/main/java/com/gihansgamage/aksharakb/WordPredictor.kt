package com.gihansgamage.aksharakb

import android.content.Context

class WordPredictor(private val context: Context) {

    private val englishWords = listOf(
        "the","be","to","of","and","a","in","that","have","it","for","not","on",
        "with","he","as","you","do","at","this","but","his","by","from","they",
        "we","say","her","she","or","an","will","my","one","all","would","there",
        "their","what","so","up","out","if","about","who","get","which","go","me",
        "when","make","can","like","time","no","just","him","know","take","people",
        "into","year","your","good","some","could","them","see","other","than","then",
        "now","look","only","come","over","think","also","back","after","use","two",
        "how","our","work","first","well","way","even","new","want","because","any",
        "these","give","day","most","us","hello","hi","thanks","please","sorry","yes",
        "okay","ok","love","hate","help","need","going","here","where","why","what",
        "android","phone","call","message","email","today","tomorrow","yesterday",
        "morning","evening","night","home","water","happy","sad","big","small","old",
        "great","nice","beautiful","important","different","large","early","young",
        "long","right","high","place","world","still","every","right","very","always",
        "between","life","few","north","open","seem","together","next","white","children",
        "begin","got","walk","example","ease","paper","group","always","music","those",
        "both","mark","book","letter","until","mile","river","car","feet","care","second",
        "enough","plain","girl","usual","young","ready","above","ever","red","list",
        "though","feel","talk","bird","soon","body","dog","family","direct","pose"
    )

    private val sinhalaWords = listOf(
        "ඔබ","මම","අපි","ඔවුන්","ඔහු","ඇය","නමස්කාර","ස්තූතියි","කරුණාකර",
        "ඔව්","නැත","හොඳ","නරක","ලොකු","කුඩා","නිවස","පාසල","ආහාර","වතුර",
        "ආදරය","උදව්","අද","හෙට","ඊයේ","උදේ","සවස","රාත්‍රිය","සතුට","දුක",
        "ප්‍රශ්නය","පිළිතුර","ජීවිතය","මිනිසා","ශ්‍රී","ලංකාව","සිංහල","භාෂාව",
        "කොළඹ","දිනය","වේලාව","ආදරේ","යාළුවා","අම්මා","තාත්තා","දරුවා",
        "ගෙදර","රස","කෑම","ගමන","කාලය","හදවත","දෙවියෝ","ශාන්ති","ධර්මය"
    )

    private val tamilWords = listOf(
        "நான்","நீ","அவன்","அவள்","நாம்","அவர்கள்","வணக்கம்","நன்றி",
        "தயவுசெய்து","ஆம்","இல்லை","நல்லது","கெட்டது","பெரியது","சிறியது",
        "வீடு","பள்ளி","உணவு","தண்ணீர்","அன்பு","உதவி","இன்று","நாளை",
        "நேற்று","காலை","மாலை","இரவு","மகிழ்ச்சி","துக்கம்","கேள்வி","பதில்",
        "வாழ்க்கை","மனிதன்","இலங்கை","தமிழ்","மொழி","நகரம்","நேரம்","காதல்",
        "நண்பன்","அம்மா","அப்பா","குழந்தை","சாப்பாடு","பயணம்","இதயம்"
    )

    private val userWords = mutableMapOf<String, Int>()

    fun getSuggestions(input: String, language: String): List<String> {
        if (input.isEmpty()) return getPopularWords(language)
        val wordList = when (language) {
            "SI" -> sinhalaWords
            "TA" -> tamilWords
            else -> englishWords
        }
        val inputLower = input.lowercase()
        val all = wordList + userWords.keys.toList()
        val startsWith = all.filter { it.lowercase().startsWith(inputLower) && it != input }
            .sortedByDescending { userWords.getOrDefault(it, 0) }
        val contains = all.filter { !it.lowercase().startsWith(inputLower) && it.lowercase().contains(inputLower) }
            .sortedByDescending { userWords.getOrDefault(it, 0) }
        return (startsWith + contains).distinct().take(5)
    }

    fun getEmojiSuggestions(input: String): List<String> {
        val map = mapOf(
            "happy"    to listOf("😊","😄","🙂","😁","🥰"),
            "sad"      to listOf("😢","😭","😔","😞","🥺"),
            "love"     to listOf("❤️","🥰","💕","💗","😍"),
            "laugh"    to listOf("😂","🤣","😆","😝","😹"),
            "cool"     to listOf("😎","🤙","👍","🔥","✨"),
            "ok"       to listOf("👍","✅","👌","🆗","☑️"),
            "thanks"   to listOf("🙏","😊","💯","❤️","🤗"),
            "hi"       to listOf("👋","🙋","😊","🤗","👊"),
            "yes"      to listOf("✅","👍","💯","☑️","🙌"),
            "no"       to listOf("❌","🚫","👎","🙅","⛔"),
            "food"     to listOf("🍛","🍜","🍚","🍱","😋"),
            "home"     to listOf("🏠","🏡","🛖","🏘️","🏗️"),
            "work"     to listOf("💼","💻","📊","🖥️","⌨️"),
            "sleep"    to listOf("😴","💤","🛌","🌙","😪"),
            "angry"    to listOf("😡","🤬","😤","💢","🔥"),
            "surprise" to listOf("😮","😲","🤯","😱","🙀"),
            "think"    to listOf("🤔","💭","🧠","💡","🤨"),
            "money"    to listOf("💰","💵","🤑","💸","💳"),
            "time"     to listOf("⏰","🕐","⌚","📅","🗓️"),
            "phone"    to listOf("📱","📞","☎️","📲","🤙"),
            "music"    to listOf("🎵","🎶","🎸","🎤","🎧"),
            "fire"     to listOf("🔥","💥","🌟","⚡","✨"),
            "star"     to listOf("⭐","🌟","✨","💫","🌙"),
            "good"     to listOf("👍","✅","💯","🙌","😊"),
            "bad"      to listOf("👎","❌","😞","🙅","💔"),
            "cry"      to listOf("😢","😭","🥺","💔","😔"),
            "run"      to listOf("🏃","💨","🏅","⚡","🦵"),
            "eat"      to listOf("🍽️","😋","🥗","🍜","🍛"),
            "drink"    to listOf("🥤","☕","🧃","🍵","💧"),
            "heart"    to listOf("❤️","💕","💗","💓","💖"),
            "sun"      to listOf("☀️","🌤️","🌞","🔆","🌈"),
            "rain"     to listOf("🌧️","☔","💧","🌊","⛈️"),
        )
        val inputLower = input.lowercase()
        val result = mutableListOf<String>()
        for ((key, values) in map) {
            if (key.startsWith(inputLower)) { result.addAll(values); if (result.size >= 8) break }
        }
        return if (result.isEmpty()) listOf("😊","❤️","👍","🙏","😂") else result.take(5)
    }

    private fun getPopularWords(lang: String) = when (lang) {
        "SI" -> listOf("ඔව්","නැත","ස්තූතියි","හොඳ","ඔබ")
        "TA" -> listOf("ஆம்","இல்லை","நன்றி","நல்லது","நீ")
        else -> listOf("the","you","hello","thanks","good")
    }

    fun learnWord(word: String) {
        if (word.length > 1) userWords[word] = (userWords[word] ?: 0) + 1
    }
}