package com.gihansgamage.aksharakb

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class WordPredictor(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── English — top ~300 most-used words ranked by frequency ────
    private val englishWords = listOf(
        "the","be","to","of","and","a","in","that","have","it",
        "for","not","on","with","he","as","you","do","at","this",
        "but","his","by","from","they","we","say","her","she","or",
        "an","will","my","one","all","would","there","their","what","so",
        "up","out","if","about","who","get","which","go","me","when",
        "make","can","like","time","know","take","see","come","think","look",
        "want","give","use","find","tell","ask","seem","feel","try","leave",
        "call","keep","let","begin","show","hear","play","run","move","live",
        "believe","hold","bring","happen","write","provide","sit","stand","lose","pay",
        "meet","include","continue","set","learn","change","lead","understand","watch","follow",
        "people","year","way","day","man","woman","child","world","life","hand",
        "part","place","case","week","company","system","program","question","government","number",
        "night","point","home","water","room","mother","area","money","story","fact",
        "month","lot","right","study","book","eye","job","word","business","issue",
        "side","kind","head","house","service","friend","father","power","hour","game",
        "line","end","among","after","city","name","team","minute","body","car",
        "information","back","parent","face","others","level","office","door","health","person",
        "art","war","history","party","result","morning","reason","research","girl","guy",
        "moment","air","teacher","force","education","never","always","often",
        "good","new","first","last","long","great","little","own","right","big",
        "high","different","small","large","next","early","young","important","few","public",
        "bad","same","able","old","real","best","free","sure","true","hard",
        "beautiful","happy","simple","easy","fast","better","other","every","strong","open",
        "hello","hi","hey","thanks","thank","please","sorry","yes","no","okay",
        "ok","love","help","need","going","here","where","why","because","how",
        "really","just","very","also","well","still","even","back","then","too",
        "already","yet","again","soon","maybe","probably","actually","definitely","however",
        "though","although","anyway","else","either","both","each","most","some","any",
        "more","less","much","many","enough","only","quite","rather","almost","nearly",
        "phone","message","email","today","tomorrow","yesterday","morning","evening","night",
        "android","app","google","facebook","instagram","whatsapp","youtube","twitter","camera","photo",
        "him","them","these","those","its","our","your","their","mine","yours",
        "now","then","here","there","before","after","between","through","during","without",
        "within","along","following","across","behind","beyond","plus","except","up","down",
        "gihan","sanjaya","gamage","gihan s gamage","maths crew","baba","babe","menika"
    )

    // ── Sinhala ────────────────────────────────────────────────────
    private val sinhalaWords = listOf(
        "ඔබ","මම","අපි","ඔවුන්","ඔහු","ඇය","ඔව්","නැත","හොඳ","නරක",
        "ස්තූතියි","කරුණාකර","නමස්කාර","සුබ","පාන","ආදරය","උදව්","ජීවිතය",
        "ගෙදර","නිවස","ආහාර","වතුර","අද","හෙට","ඊයේ","උදේ","සවස","රාත්‍රිය",
        "සතුට","දුක","ප්‍රශ්නය","පිළිතුර","මිනිසා","ශ්‍රී","ලංකාව","සිංහල",
        "භාෂාව","කොළඹ","දිනය","වේලාව","ආදරේ","යාළුවා","අම්මා","තාත්තා",
        "දරුවා","රස","කෑම","ගමන","කාලය","හදවත","දෙවියෝ","ශාන්ති","ධර්මය",
        "රට","ජනතාව","රජය","නීතිය","ශිෂ්‍යයා","ගුරුවරයා","රෝහල","ඖෂධය",
        "ගමේ","නගරය","ව්‍යාපාරය","ජයග්‍රහණය","විශ්වාසය","ශක්තිය","බලය"
    )

    // ── Tamil ──────────────────────────────────────────────────────
    private val tamilWords = listOf(
        "நான்","நீ","அவன்","அவள்","நாம்","அவர்கள்","வணக்கம்","நன்றி",
        "தயவுசெய்து","ஆம்","இல்லை","நல்லது","கெட்டது","வீடு","பள்ளி",
        "உணவு","தண்ணீர்","அன்பு","உதவி","இன்று","நாளை","நேற்று","காலை",
        "மாலை","இரவு","மகிழ்ச்சி","துக்கம்","கேள்வி","பதில்","வாழ்க்கை",
        "மனிதன்","இலங்கை","தமிழ்","மொழி","நகரம்","நேரம்","காதல்","நண்பன்",
        "அம்மா","அப்பா","குழந்தை","சாப்பாடு","பயணம்","இதயம்","அரசு",
        "மக்கள்","நாடு","சக்தி","நம்பிக்கை","வெற்றி","கல்வி","ஆசிரியர்"
    )

    // User-learned words with frequency count
    private val userWords = mutableMapOf<String, Int>()

    /**
     * Suggest words. Calls [callback] immediately with local results,
     * then calls it again with internet results if available.
     */
    fun getSuggestions(input: String, language: String, callback: (List<String>) -> Unit) {
        // Phase 1: instant local suggestions
        val localResults = getLocalSuggestions(input, language)
        callback(localResults)

        // Phase 2: async internet suggestions (English only for now)
        if (input.length >= 2 && language == "EN") {
            executor.execute {
                try {
                    val internetResults = fetchInternetSuggestions(input)
                    if (internetResults.isNotEmpty()) {
                        // Merge: internet first, then local, deduplicated
                        val merged = (internetResults + localResults).distinct().take(10)
                        mainHandler.post { callback(merged) }
                    }
                } catch (_: Exception) {
                    // Silently fail — local results already shown
                }
            }
        }
    }

    /** Synchronous local lookup — for backward compatibility with learnAndReset */
    fun getSuggestions(input: String, language: String): List<String> {
        return getLocalSuggestions(input, language)
    }

    private fun getLocalSuggestions(input: String, language: String): List<String> {
        if (input.isEmpty()) return getPopularWords(language)

        val wordList = when (language) {
            "SI" -> sinhalaWords
            "TA" -> tamilWords
            else -> englishWords
        }
        val inputLower = input.lowercase()

        val userMatches = userWords.keys
            .filter { it.lowercase().startsWith(inputLower) }
            .sortedByDescending { userWords[it] ?: 0 }

        val builtinStarts = wordList
            .filter { it.lowercase().startsWith(inputLower) && it.lowercase() != inputLower }
        val builtinContains = wordList
            .filter { !it.lowercase().startsWith(inputLower) && it.lowercase().contains(inputLower) }

        return (userMatches + builtinStarts + builtinContains).distinct().take(6)
    }

    /** Hit Google Suggest API and parse results */
    private fun fetchInternetSuggestions(query: String): List<String> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // Use firefox client which returns compact JSON: ["q",["s1","s2",...],...]
        val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 2000
        conn.readTimeout = 2000

        return try {
            val response = conn.inputStream.bufferedReader().readText()
            // Response format: ["query",["suggestion1","suggestion2",...],...metadata...]
            // We want ONLY the second element (index 1) which is the string array.
            // Find the second '[' which opens the suggestions sub-array
            val firstBracket = response.indexOf('[')                 // outer [
            val subArrayStart = response.indexOf('[', firstBracket + 1) // suggestions [
            val subArrayEnd   = response.indexOf(']', subArrayStart + 1)
            if (subArrayStart < 0 || subArrayEnd <= subArrayStart) return emptyList()

            // Extract just the suggestions sub-array string and pull all quoted tokens
            val subArray = response.substring(subArrayStart + 1, subArrayEnd)
            Regex("\"([^\"]+)\"")
                .findAll(subArray)
                .map { it.groupValues[1] }
                .filter { !it.contains(' ') && it.length > 1 }
                .take(8)
                .toList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    fun getEmojiSuggestions(input: String): List<String> {
        val map = mapOf(
            "happy"    to listOf("😊","😄","🙂","😁","🥰"),
            "sad"      to listOf("😢","😭","😔","😞","🥺"),
            "love"     to listOf("❤️","🥰","💕","💗","😍"),
            "laugh"    to listOf("😂","🤣","😆","😝","😹"),
            "cool"     to listOf("😎","🤙","👍","🔥","✨"),
            "ok"       to listOf("👍","✅","👌","🆗","☑️"),
            "okay"     to listOf("👍","✅","👌","🆗","☑️"),
            "thanks"   to listOf("🙏","😊","💯","❤️","🤗"),
            "thank"    to listOf("🙏","😊","💯","❤️","🤗"),
            "hi"       to listOf("👋","🙋","😊","🤗","👊"),
            "hello"    to listOf("👋","🙋","😊","🤗","🌟"),
            "yes"      to listOf("✅","👍","💯","☑️","🙌"),
            "no"       to listOf("❌","🚫","👎","🙅","⛔"),
            "good"     to listOf("👍","✅","💯","🙌","😊"),
            "bad"      to listOf("👎","❌","😞","🙅","💔"),
            "fire"     to listOf("🔥","💥","🌟","⚡","✨"),
            "food"     to listOf("🍛","🍜","🍚","🍱","😋"),
            "home"     to listOf("🏠","🏡","🛖","🏘️","🏗️"),
            "work"     to listOf("💼","💻","📊","🖥️","⌨️"),
            "sleep"    to listOf("😴","💤","🛌","🌙","😪"),
            "angry"    to listOf("😡","🤬","😤","💢","🔥"),
            "wow"      to listOf("😮","😲","🤯","😱","🙀"),
            "money"    to listOf("💰","💵","🤑","💸","💳"),
            "music"    to listOf("🎵","🎶","🎸","🎤","🎧"),
            "heart"    to listOf("❤️","💕","💗","💓","💖"),
            "sorry"    to listOf("🙏","😔","💔","😢","🥺"),
            "please"   to listOf("🙏","😊","💯","❤️","🤗"),
            "run"      to listOf("🏃","💨","🏅","⚡","🦵"),
            "eat"      to listOf("🍽️","😋","🥗","🍜","🍛"),
            "birthday" to listOf("🎂","🎉","🎁","🎈","🎊"),
            "party"    to listOf("🎉","🎊","🎈","🥳","🍾"),
            "car"      to listOf("🚗","🚕","🏎️","🚙","⛽"),
            "phone"    to listOf("📱","📞","☎️","📲","🤙"),
            "sun"      to listOf("☀️","🌤️","🌞","🔆","🌈"),
            "rain"     to listOf("🌧️","☔","💧","🌊","⛈️")
        )
        val inputLower = input.lowercase()
        val result = mutableListOf<String>()
        for ((key, values) in map) {
            if (key.startsWith(inputLower)) { result.addAll(values); if (result.size >= 8) break }
        }
        return if (result.isEmpty()) listOf("😊","❤️","👍","🙏","😂") else result.take(5)
    }

    private fun getPopularWords(lang: String) = when (lang) {
        "SI" -> listOf("ඔව්","නැහැ","ස්තූතියි","හොඳ","ආදරේ","කරුණාකර")
        "TA" -> listOf("ஆம்","இல்லை","நன்றி","நல்லது","காதல்","தயவுசெய்து")
        else -> listOf("the","I","you","is","are","it","this","that","with","for")
    }

    fun learnWord(word: String) {
        if (word.length > 1) userWords[word] = (userWords[word] ?: 0) + 1
    }
}