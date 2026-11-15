package com.example.Hami

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/*
   Per-parent safe word list.
   Stored at: parents/{parentUid}/safeWords/{normalizedWord}
   Each parent has their own list; it applies to ALL of that parent's children.
   The child device reads this list before creating an alert so suppressed words
   never generate a document, notification, or alarm.
 */

object SafeWordsRepository {

    private const val TAG = "HamiSafeWords"
    private val db = FirebaseFirestore.getInstance()

    /*
       Normalizes an Arabic word so matching is stable across diacritics,
       alef variants, taa marbuta, and yaa/alef maqsura.
     */
    fun normalizeWord(word: String): String {
        return word.trim().lowercase()
            .replace("[\\u064B-\\u0652]".toRegex(), "")   // diacritics
            .replace("[أإآ]".toRegex(), "ا")               // alef variants
            .replace("ى".toRegex(), "ي")                    // alef maqsura
            .replace("ة".toRegex(), "ه")                    // taa marbuta
            .replace("\\s+".toRegex(), "")
    }

    /*
       Fetches the full safe word set for a parent.
       Called on the CHILD device before creating an alert.
     */
    fun getSafeWords(parentId: String, onResult: (Set<String>) -> Unit) {
        if (parentId.isBlank()) { onResult(emptySet()); return }
        db.collection("parents").document(parentId)
            .collection("safeWords")
            .get()
            .addOnSuccessListener { snapshot ->
                val words = snapshot.documents.mapNotNull { it.getString("word") }.toSet()
                Log.d(TAG, "📖 Loaded ${words.size} safe words for parent $parentId")
                onResult(words)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to load safe words: ${e.message}")
                onResult(emptySet())
            }
    }

    /*
       Adds a word to the parent's safe list.
       Uses normalized word as document ID so duplicates are impossible.
     */
    fun addSafeWord(
        parentId: String,
        originalWord: String,
        alertId: String,
        onResult: (Boolean) -> Unit
    ) {
        val normalized = normalizeWord(originalWord)
        if (normalized.isBlank()) { onResult(false); return }

        val data = mapOf(
            "word" to normalized,
            "originalWord" to originalWord,
            "addedAt" to com.google.firebase.Timestamp.now(),
            "addedFromAlertId" to alertId
        )

        db.collection("parents").document(parentId)
            .collection("safeWords").document(normalized)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "✅ Safe word added: $normalized")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to add safe word: ${e.message}")
                onResult(false)
            }
    }
    /*
   Removes a word from the parent's safe list.
   Uses the normalized word as document ID (same as addSafeWord).
  */
    fun removeSafeWord(
        parentId: String,
        word: String,
        onResult: (Boolean) -> Unit
    ) {
        val normalized = normalizeWord(word)
        if (parentId.isBlank() || normalized.isBlank()) { onResult(false); return }

        db.collection("parents").document(parentId)
            .collection("safeWords").document(normalized)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "🗑️ Safe word removed: $normalized")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to remove safe word: ${e.message}")
                onResult(false)
            }
    }

    /*
       Returns the parent's safe words as a list of (normalizedWord, originalWord) pairs.
       Used by the Settings management UI so it can display the original Arabic form.
     */
    fun getSafeWordEntries(
        parentId: String,
        onResult: (List<Pair<String, String>>) -> Unit
    ) {
        if (parentId.isBlank()) { onResult(emptyList()); return }
        db.collection("parents").document(parentId)
            .collection("safeWords")
            .get()
            .addOnSuccessListener { snapshot ->
                val entries = snapshot.documents.mapNotNull { doc ->
                    val word = doc.getString("word") ?: return@mapNotNull null
                    val original = doc.getString("originalWord") ?: word
                    word to original
                }
                onResult(entries)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to load safe word entries: ${e.message}")
                onResult(emptyList())
            }
    }

    /*
       Checks a single word against the parent's safe list.
       Synchronous-style helper used by the detection pipeline after caching.
     */
    fun isSafe(parentId: String, word: String, onResult: (Boolean) -> Unit) {
        getSafeWords(parentId) { set ->
            onResult(set.contains(normalizeWord(word)))
        }
    }
}