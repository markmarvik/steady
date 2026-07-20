package com.steady.habittracker.data

/**
 * Curated consistency / habit quotes for daily motivational notifications (#32).
 * Rotates by day-of-year; all local, no network.
 */
object MotivationalQuotes {

    data class Quote(val text: String, val attribution: String)

    val ALL: List<Quote> = listOf(
        Quote("You do not rise to the level of your goals. You fall to the level of your systems.", "James Clear · Atomic Habits"),
        Quote("Habits are the compound interest of self-improvement.", "James Clear · Atomic Habits"),
        Quote("Every action you take is a vote for the type of person you wish to become.", "James Clear · Atomic Habits"),
        Quote("The most effective way to change your habits is to focus not on what you want to achieve, but on who you wish to become.", "James Clear"),
        Quote("Motivation is what gets you started. Habit is what keeps you going.", "Jim Ryun"),
        Quote("We are what we repeatedly do. Excellence, then, is not an act, but a habit.", "Will Durant (after Aristotle)"),
        Quote("Small disciplines repeated with consistency every day lead to great achievements gained slowly over time.", "John C. Maxwell"),
        Quote("Success is the product of daily habits—not once-in-a-lifetime transformations.", "James Clear"),
        Quote("The secret of getting ahead is getting started.", "Mark Twain"),
        Quote("Discipline is choosing between what you want now and what you want most.", "Abraham Lincoln (attrib.)"),
        Quote("It's not what we do once in a while that shapes our lives. It's what we do consistently.", "Tony Robbins"),
        Quote("A year from now you may wish you had started today.", "Karen Lamb"),
        Quote("The chains of habit are too weak to be felt until they are too strong to be broken.", "Samuel Johnson"),
        Quote("First we make our habits, then our habits make us.", "Charles C. Noble"),
        Quote("Consistency is what transforms average into excellence.", "Unknown"),
        Quote("Don't break the chain. Just show up today.", "Jerry Seinfeld (habit method)"),
        Quote("The difference between who you are and who you want to be is what you do.", "Unknown"),
        Quote("Habit is a cable; we weave a thread of it each day, and at last we cannot break it.", "Horace Mann"),
        Quote("What you do every day matters more than what you do once in a while.", "Gretchen Rubin"),
        Quote("The best time to plant a tree was 20 years ago. The second best time is now.", "Chinese proverb"),
        Quote("Cue, craving, response, reward — design the loop, and the habit follows.", "James Clear"),
        Quote("Make it obvious. Make it attractive. Make it easy. Make it satisfying.", "James Clear · Atomic Habits"),
        Quote("Identity-based habits: win the identity first, then the outcome.", "James Clear"),
        Quote("Never miss twice. Missing once is an accident; missing twice is the start of a new habit.", "James Clear"),
        Quote("Your outcomes are a lagging measure of your habits.", "James Clear"),
        Quote("Champions don't do extraordinary things. They do ordinary things extraordinarily well — consistently.", "Unknown"),
        Quote("Energy and persistence conquer all things.", "Benjamin Franklin"),
        Quote("The man who moves a mountain begins by carrying away small stones.", "Confucius"),
        Quote("Be regular and orderly in your life so that you may be violent and original in your work.", "Gustave Flaubert"),
        Quote("We first make our habits, and then our habits make us.", "John Dryden"),
        Quote("An ounce of practice is generally worth more than a ton of theory.", "E.F. Schumacher"),
        Quote("Focus on the process, not the prize.", "James Clear"),
        Quote("Sow a thought, reap an action; sow an action, reap a habit; sow a habit, reap a character.", "Charles Reade"),
        Quote("The only way to do great work is to love what you do — and show up for it daily.", "Steve Jobs (adapted)"),
        Quote("Steady progress beats heroic sprints that end in burnout.", "Steady"),
        Quote("Protect the streak: one honest rep today keeps tomorrow possible.", "Steady"),
        Quote("Longevity is built in the quiet days nobody posts about.", "Steady"),
        Quote("If it's important, put it on the timeline — then log it.", "Steady"),
        Quote("Consistency compounds. Skip the drama; keep the system.", "Steady"),
        Quote("Your future self is watching what you do in the next hour.", "Steady")
    )

    fun forDayOfYear(dayOfYear: Int): Quote {
        if (ALL.isEmpty()) return Quote("Stay consistent.", "Steady")
        val idx = ((dayOfYear - 1) % ALL.size + ALL.size) % ALL.size
        return ALL[idx]
    }

    fun forToday(date: java.time.LocalDate = java.time.LocalDate.now()): Quote =
        forDayOfYear(date.dayOfYear)
}
