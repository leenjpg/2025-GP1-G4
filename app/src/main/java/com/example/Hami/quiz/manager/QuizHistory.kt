package com.example.Hami.quiz.manager

import android.content.Context
import android.util.Log
import com.example.Hami.quiz.QUIZ_LOG_TAG
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray

/*
   Per-child record of question ids that were actually DISPLAYED (ids only - no content).

   - Local SharedPreferences is the source of truth (works offline, survives app/service restarts,
     reboots and logout/login because logout only clears "HamiPrefs").
   - Best-effort mirror to Firestore: child/{childId}.quizPresentedIds (array, arrayUnion = atomic and
     concurrency-safe, never overwritten). On service start the remote ids are merged back in (union),
     so history also survives reinstall. Any Firestore failure is only logged.
 */
class QuizHistory(context: Context) : QuizHistoryStore {

    private val prefs = context.applicationContext
        .getSharedPreferences("hami_quiz_history", Context.MODE_PRIVATE)

    @Synchronized
    override fun getPresentedIds(childId: String): Set<String> = read(childId)

    @Synchronized
    override fun markPresented(childId: String, questionId: String) {
        val ids = read(childId)
        if (!ids.add(questionId)) return
        write(childId, ids)
        try {
            FirebaseFirestore.getInstance().collection("child").document(childId)
                .set(mapOf("quizPresentedIds" to FieldValue.arrayUnion(questionId)), SetOptions.merge())
                .addOnFailureListener { Log.w(QUIZ_LOG_TAG, "History mirror failed: ${it.message}") }
        } catch (e: Exception) {
            Log.w(QUIZ_LOG_TAG, "History mirror failed: ${e.message}")
        }
    }

    /** Union of remote ids into local history (never removes anything). */
    fun restoreFromRemote(childId: String) {
        try {
            FirebaseFirestore.getInstance().collection("child").document(childId).get()
                .addOnSuccessListener { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val remote = (doc.get("quizPresentedIds") as? List<String>).orEmpty()
                    if (remote.isNotEmpty()) synchronized(this) {
                        val ids = read(childId)
                        if (ids.addAll(remote)) write(childId, ids)
                    }
                }
                .addOnFailureListener { Log.w(QUIZ_LOG_TAG, "History restore failed: ${it.message}") }
        } catch (e: Exception) {
            Log.w(QUIZ_LOG_TAG, "History restore failed: ${e.message}")
        }
    }

    private fun read(childId: String): MutableSet<String> = try {
        val a = JSONArray(prefs.getString("ids_$childId", "[]"))
        (0 until a.length()).mapTo(mutableSetOf()) { a.getString(it) }
    } catch (e: Exception) { mutableSetOf() }

    private fun write(childId: String, ids: Set<String>) {
        prefs.edit().putString("ids_$childId", JSONArray(ids.toList()).toString()).commit()
    }
}

/** Abstraction used by the selector so it can be tested without Android. */
interface QuizHistoryStore {
    fun getPresentedIds(childId: String): Set<String>
    fun markPresented(childId: String, questionId: String)
}
