package com.example.Hami.quiz.manager

import android.content.Context
import com.example.Hami.quiz.QUIZ_TRIGGER_THRESHOLD
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Minimal key-value abstraction for unit testing. */
interface QuizKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

private class PrefsKeyValueStore(context: Context) : QuizKeyValueStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(
            "hami_quiz_prefs",
            Context.MODE_PRIVATE
        )

    override fun getString(key: String): String? =
        prefs.getString(key, null)

    // Save synchronously to preserve the weekly trigger state.
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }
}

/**
 * A pending educational quiz.
 *
 * milestone is kept for backward compatibility
 * with the previous quiz implementation.
 */
data class PendingQuizTrigger(
    val week: String,
    val milestone: Int,
    val category: String
)

/** Result of processing one detected alert. */
data class AlertCountResult(
    val counted: Boolean,
    val weeklyCount: Int,
    val newTrigger: PendingQuizTrigger?
)

/**
 * Hami Weekly Quiz Alert Counter
 *
 * Rules:
 *
 * 1. Each child has an independent weekly counter.
 * 2. A week starts on Monday and ends on Sunday.
 * 3. Each unique alert is counted only once.
 * 4. The quiz is triggered at the fifth alert.
 * 5. Only ONE quiz is triggered per child per week.
 * 6. Further alerts do not trigger additional quizzes.
 * 7. A new week starts a fresh counter.
 * 8. Quiz completion does not reset the weekly count.
 * 9. The fifth alert determines the quiz category.
 * 10. No message content is stored.
 *
 * The previous persistent state format is preserved.
 */
class QuizAlertCounter(
    private val store: QuizKeyValueStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val threshold: Int = QUIZ_TRIGGER_THRESHOLD
) {

    constructor(context: Context) : this(
        PrefsKeyValueStore(context)
    )

    /**
     * Registers a valid harmful alert.
     *
     * eventTimeMillis determines the alert's calendar week.
     * nowMillis is used to detect the current calendar week.
     */
    fun registerAlert(
        childId: String,
        alertId: String,
        category: String,
        eventTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): AlertCountResult = synchronized(LOCK) {

        val eventWeek = weekIdOf(eventTimeMillis)
        val currentWeek = weekIdOf(nowMillis)

        var state = load(childId)

        // Reset the counter when a new week begins.
        if (state.week < currentWeek) {
            state = State(week = currentWeek)
        }

        // Ignore alerts from a previous week.
        if (eventWeek < state.week) {
            return AlertCountResult(
                counted = false,
                weeklyCount = state.count,
                newTrigger = null
            )
        }

        // Prevent duplicate alert counting.
        if (state.recentIds.contains(alertId)) {
            return AlertCountResult(
                counted = false,
                weeklyCount = state.count,
                newTrigger = null
            )
        }

        // Register the unique alert ID.
        state.recentIds.add(alertId)

        // Maintain the existing bounded deduplication list.
        while (state.recentIds.size > MAX_RECENT_IDS) {
            state.recentIds.removeAt(0)
        }

        // Increase the weekly count.
        state.count += 1

        var trigger: PendingQuizTrigger? = null

        /*
         * UPDATED WEEKLY QUIZ LOGIC
         *
         * OLD:
         * A quiz was triggered at 5, 10, 15, 20...
         *
         * NEW:
         * A quiz is triggered only at the fifth alert.
         *
         * lastMilestone == 0 means the weekly quiz
         * has not been triggered yet.
         *
         * Once triggered, lastMilestone becomes 1.
         * It remains 1 until the next calendar week.
         */
        if (
            state.count == threshold &&
            state.lastMilestone == 0
        ) {

            state.lastMilestone = 1

            trigger = PendingQuizTrigger(
                week = state.week,
                milestone = 1,
                category = category
            )

            state.pending.add(trigger)
        }

        // Persist the updated weekly state.
        save(childId, state)

        AlertCountResult(
            counted = true,
            weeklyCount = state.count,
            newTrigger = trigger
        )
    }

    /**
     * Returns the oldest pending quiz trigger.
     *
     * Pending quizzes from previous weeks
     * are not displayed in the new week.
     */
    fun peekPending(
        childId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PendingQuizTrigger? = synchronized(LOCK) {

        var state = load(childId)

        val currentWeek = weekIdOf(nowMillis)

        if (state.week < currentWeek) {

            state = State(week = currentWeek)

            save(childId, state)
        }

        state.pending.firstOrNull()
    }

    /**
     * Consumes a pending quiz trigger after
     * it is displayed or deliberately skipped.
     */
    fun consumePending(
        childId: String,
        trigger: PendingQuizTrigger
    ) = synchronized(LOCK) {

        val state = load(childId)

        if (state.pending.remove(trigger)) {
            save(childId, state)
        }
    }

    /**
     * Returns the number of valid alerts
     * detected for this child this week.
     */
    fun getWeeklyCount(
        childId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Int = synchronized(LOCK) {

        val state = load(childId)

        if (state.week < weekIdOf(nowMillis)) {
            0
        } else {
            state.count
        }
    }

    /**
     * Returns the Monday date that identifies
     * the calendar week containing millis.
     */
    fun weekIdOf(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(zone)
            .toLocalDate()
            .with(
                TemporalAdjusters.previousOrSame(
                    DayOfWeek.MONDAY
                )
            )
            .toString()

    // ---------------------------------------
    // Persistent weekly state
    // ---------------------------------------

    private class State(
        var week: String,
        var count: Int = 0,
        var lastMilestone: Int = 0,
        val recentIds: MutableList<String> = mutableListOf(),
        val pending: MutableList<PendingQuizTrigger> = mutableListOf()
    )

    private fun key(childId: String): String =
        "state_$childId"

    /**
     * Loads the child's stored weekly state.
     *
     * Backward compatibility:
     *
     * Older Hami versions created pending quiz
     * triggers at milestones 1, 2, 3, etc.
     *
     * Milestone 1 = fifth alert
     * Milestone 2 = tenth alert
     * Milestone 3 = fifteenth alert
     *
     * The new implementation keeps only
     * milestone 1 for the current week.
     *
     * Existing higher milestones are ignored.
     */
    private fun load(childId: String): State {

        val raw = store.getString(key(childId))
            ?: return State(week = "")

        return try {

            val o = JSONObject(raw)

            State(
                week = o.optString("week", ""),
                count = o.optInt("count", 0),
                lastMilestone = o.optInt("lastMilestone", 0)
            ).also { st ->

                // Restore recently counted alert IDs.
                o.optJSONArray("recentIds")?.let { array ->

                    for (i in 0 until array.length()) {

                        st.recentIds.add(
                            array.getString(i)
                        )
                    }
                }

                // Restore only the first weekly quiz.
                o.optJSONArray("pending")?.let { array ->

                    for (i in 0 until array.length()) {

                        val pendingObject =
                            array.getJSONObject(i)

                        val pendingWeek =
                            pendingObject.getString("week")

                        val pendingMilestone =
                            pendingObject.getInt("milestone")

                        val pendingCategory =
                            pendingObject.getString("category")

                        /*
                         * Prevent older queued quizzes
                         * at 10, 15, 20... alerts
                         * from appearing after the update.
                         */
                        if (
                            pendingWeek == st.week &&
                            pendingMilestone == 1 &&
                            st.pending.isEmpty()
                        ) {

                            st.pending.add(
                                PendingQuizTrigger(
                                    week = pendingWeek,
                                    milestone = 1,
                                    category = pendingCategory
                                )
                            )
                        }
                    }
                }
            }

        } catch (e: Exception) {

            // Recover safely from corrupted local state.
            State(week = "")
        }
    }

    /**
     * Saves the child's weekly alert counter,
     * trigger state, and pending quiz.
     */
    private fun save(
        childId: String,
        s: State
    ) {

        val o = JSONObject()
            .put("week", s.week)
            .put("count", s.count)
            .put("lastMilestone", s.lastMilestone)
            .put(
                "recentIds",
                JSONArray(s.recentIds)
            )
            .put(
                "pending",
                JSONArray().also { array ->

                    s.pending.forEach { pending ->

                        array.put(
                            JSONObject()
                                .put(
                                    "week",
                                    pending.week
                                )
                                .put(
                                    "milestone",
                                    pending.milestone
                                )
                                .put(
                                    "category",
                                    pending.category
                                )
                        )
                    }
                }
            )

        store.putString(
            key(childId),
            o.toString()
        )
    }

    private companion object {

        val LOCK = Any()

        const val MAX_RECENT_IDS = 200
    }
}