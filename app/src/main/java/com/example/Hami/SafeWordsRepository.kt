package com.example.Hami

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/*
   Per-parent safe word list.
   Stored inside: parent/{parentUid} under the field "safeWords" as an Array.
 */
object SafeWordsRepository {

    private const val TAG = "HamiSafeWords"
    private val db = FirebaseFirestore.getInstance()

    // Normalizes Arabic word
    fun normalizeWord(word: String): String {
        return word.trim().lowercase()
            .replace("[\\u064B-\\u0652]".toRegex(), "")   // diacritics
            .replace("[أإآ]".toRegex(), "ا")               // alef variants
            .replace("ى".toRegex(), "ي")                    // alef maqsura
            .replace("ة".toRegex(), "ه")                    // taa marbuta
            .replace("\\s+".toRegex(), "")
    }

    // Fetches the safe word list for the child device
    fun getSafeWords(parentId: String, onResult: (Set<String>) -> Unit) {
        if (parentId.isBlank()) { onResult(emptySet()); return }
        db.collection("parent").document(parentId)
            .get()
            .addOnSuccessListener { document ->
                val list = document.get("safeWords") as? List<*> ?: emptyList<Any>()
                val words = list.filterIsInstance<String>().toSet()
                Log.d(TAG, "📖 Loaded ${words.size} safe words for parent $parentId")
                onResult(words)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to load safe words: ${e.message}")
                onResult(emptySet())
            }
    }

    // Adds a word to the array
    fun addSafeWord(
        parentId: String,
        originalWord: String,
        alertId: String,
        onResult: (Boolean) -> Unit
    ) {
        val normalized = normalizeWord(originalWord)
        if (normalized.isBlank()) { onResult(false); return }

        db.collection("parent").document(parentId)
            .update("safeWords", FieldValue.arrayUnion(normalized))
            .addOnSuccessListener {
                Log.d(TAG, "✅ Safe word added: $normalized")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to add safe word: ${e.message}")
                onResult(false)
            }
    }

    // Removes a word from the array
    fun removeSafeWord(
        parentId: String,
        word: String,
        onResult: (Boolean) -> Unit
    ) {
        val normalized = normalizeWord(word)
        if (parentId.isBlank() || normalized.isBlank()) { onResult(false); return }

        db.collection("parent").document(parentId)
            .update("safeWords", FieldValue.arrayRemove(normalized))
            .addOnSuccessListener {
                Log.d(TAG, "🗑️ Safe word removed: $normalized")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to remove safe word: ${e.message}")
                onResult(false)
            }
    }

    // Returns the list to the settings screen
    fun getSafeWordEntries(
        parentId: String,
        onResult: (List<Pair<String, String>>) -> Unit
    ) {
        if (parentId.isBlank()) { onResult(emptyList()); return }
        db.collection("parent").document(parentId)
            .get()
            .addOnSuccessListener { document ->
                val list = document.get("safeWords") as? List<*> ?: emptyList<Any>()
                // Since we only store the normalized word in the array, we use it as both
                val entries = list.filterIsInstance<String>().map { it to it }
                onResult(entries)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to load safe word entries: ${e.message}")
                onResult(emptyList())
            }
    }
}