package com.gihansgamage.aksharakb

import android.content.Context

class WordPredictor(private val context: Context) {

    // Basic word lists per language - extend these for production
    private val englishWords = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "it",
        "for", "not", "on", "with", "he", "as", "you", "do", "at", "this",
        "but", "his", "by", "from", "they", "we", "say", "her", "she", "or",
        "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know",
        "take", "people", "into", "year", "your", "good", "some", "could",
        "them", "see", "other", "than", "then", "now", "look", "only", "come",
        "its", "over", "think", "also", "back", "after", "use", "two", "how",
        "our", "work", "first", "well", "way", "even", "new", "want", "because",
        "any", "these", "give", "day", "most", "us", "hello", "hi", "thanks",
        "please", "sorry", "yes", "no", "okay", "ok", "love", "hate", "help",
        "need", "want", "going", "come", "here", "there", "where", "when",
        "why", "how", "what", "who", "which", "android", "phone", "call",
        "message", "email", "today", "tomorrow", "yesterday", "morning",
        "evening", "night", "home", "work", "school", "food", "water",
        "happy", "sad", "good", "bad", "big", "small", "new", "old"
    )

    private val sinhalaWords = listOf(
        "ඔබ", "මම", "අපි", "ඔවුන්", "ඔහු", "ඇය",
        "නමස්කාර", "ස්තූතියි", "කරුණාකර", "ඔව්", "නැත",
        "හොඳ", "නරක", "ලොකු", "කුඩා", "නිවස", "පාසල",
        "ආහාර", "වතුර", "ආදරය", "උදව්", "කැමති",
        "යනවා", "එනවා", "කරනවා", "කියනවා", "දෙනවා",
        "ගන්නවා", "බලනවා", "ඇහෙනවා", "දැනෙනවා",
        "අද", "හෙට", "ඊයේ", "උදේ", "සවස", "රාත්‍රිය",
        "සතුට", "දුක", "ප්‍රශ්නය", "පිළිතුර"
    )

    private val tamilWords = listOf(
        "நான்", "நீ", "அவன்", "அவள்", "நாம்", "அவர்கள்",
        "வணக்கம்", "நன்றி", "தயவுசெய்து", "ஆம்", "இல்லை",
        "நல்லது", "கெட்டது", "பெரியது", "சிறியது",
        "வீடு", "பள்ளி", "உணவு", "தண்ணீர்", "அன்பு",
        "உதவி", "விரும்புகிறேன்", "போகிறேன்", "வருகிறேன்",
        "செய்கிறேன்", "சொல்கிறேன்", "தருகிறேன்",
        "இன்று", "நாளை", "நேற்று", "காலை", "மாலை", "இரவு",
        "மகிழ்ச்சி", "துக்கம்", "கேள்வி", "பதில்"
    )

    // User-learned words (frequency map)
    private val userWords = mutableMapOf<String, Int>()

    fun getSuggestions(input: String, language: String): List<String> {
        if (input.isEmpty()) return getPopularWords(language)

        val wordList = when (language) {
            "SI" -> sinhalaWords
            "TA" -> tamilWords
            else -> englishWords
        }

        val inputLower = input.lowercase()

        // Combine built-in and user-learned words
        val allWords = wordList.toMutableList()
        allWords.addAll(userWords.keys.filter { it.isNotEmpty() })

        return allWords
            .filter { it.lowercase().startsWith(inputLower) && it != input }
            .sortedByDescending { userWords.getOrDefault(it, 0) }
            .take(5)
            .ifEmpty {
                // Fuzzy match fallback
                allWords
                    .filter { it.lowercase().contains(inputLower) }
                    .take(3)
            }
    }

    fun getEmojiSuggestions(input: String): List<String> {
        val emojiMap = mapOf(
            "happy" to listOf("😊", "😄", "🙂", "😁", "🥰"),
            "sad" to listOf("😢", "😭", "😔", "😞", "🥺"),
            "love" to listOf("❤️", "🥰", "💕", "💗", "😍"),
            "laugh" to listOf("😂", "🤣", "😆", "😝", "😹"),
            "cool" to listOf("😎", "🤙", "👍", "🔥", "✨"),
            "ok" to listOf("👍", "✅", "👌", "🆗", "☑️"),
            "thanks" to listOf("🙏", "😊", "💯", "❤️", "🤗"),
            "hi" to listOf("👋", "🙋", "😊", "🤗", "👊"),
            "yes" to listOf("✅", "👍", "💯", "☑️", "🙌"),
            "no" to listOf("❌", "🚫", "👎", "🙅", "⛔"),
            "food" to listOf("🍛", "🍜", "🍚", "🍱", "😋"),
            "home" to listOf("🏠", "🏡", "🏘️", "🏗️", "🛖"),
            "work" to listOf("💼", "💻", "📊", "🖥️", "⌨️"),
            "sleep" to listOf("😴", "💤", "🛌", "🌙", "😪"),
            "angry" to listOf("😡", "🤬", "😤", "💢", "🔥"),
            "surprise" to listOf("😮", "😲", "🤯", "😱", "🙀"),
            "think" to listOf("🤔", "💭", "🧠", "💡", "🤨"),
            "money" to listOf("💰", "💵", "🤑", "💸", "💳"),
            "time" to listOf("⏰", "🕐", "⌚", "📅", "🗓️"),
            "phone" to listOf("📱", "📞", "☎️", "📲", "🤙"),
            "music" to listOf("🎵", "🎶", "🎸", "🎤", "🎧"),
            "sport" to listOf("⚽", "🏀", "🎾", "🏏", "⚾"),
            "car" to listOf("🚗", "🚙", "🏎️", "🚕", "🚐"),
            "fire" to listOf("🔥", "💥", "🌟", "⚡", "✨"),
            "star" to listOf("⭐", "🌟", "✨", "💫", "🌙"),
        )

        val inputLower = input.lowercase()
        val emojis = mutableListOf<String>()

        for ((key, values) in emojiMap) {
            if (key.startsWith(inputLower) || inputLower.startsWith(key.substring(0, minOf(3, key.length)))) {
                emojis.addAll(values)
                if (emojis.size >= 5) break
            }
        }

        // Always show some popular emojis
        if (emojis.isEmpty()) {
            emojis.addAll(listOf("😊", "❤️", "👍", "🙏", "😂"))
        }

        return emojis.take(5)
    }

    private fun getPopularWords(language: String): List<String> {
        return when (language) {
            "SI" -> listOf("ඔව්", "නැත", "ස්තූතියි", "හොඳ", "ඔබ")
            "TA" -> listOf("ஆம்", "இல்லை", "நன்றி", "நல்லது", "நீ")
            else -> listOf("the", "you", "hello", "thanks", "good")
        }
    }

    fun learnWord(word: String) {
        if (word.length > 1) {
            userWords[word] = (userWords[word] ?: 0) + 1
        }
    }
}