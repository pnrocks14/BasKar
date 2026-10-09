package app.pratyahara.core.buddy

enum class QuoteTopic { SCREENS, FOCUS, PROCRASTINATION, MINDSET, WISDOM }

/** One thing worth reading mid-scroll. [source] says where it comes from, so nothing is made up. */
data class Quote(val text: String, val source: String, val topic: QuoteTopic)

/**
 * Short, research-backed nudges shown while scrolling, on the pause screen and on the dashboard.
 * Every research line names its study; keep it that way when adding more. Wording is casual, the facts aren't.
 */
object Quotes {
    val all: List<Quote> = listOf(
        // Screens and attention
        Quote("just having your phone nearby, even face down, drains focus. out of sight works better.", "Ward et al., 2017, \"Brain Drain\"", QuoteTopic.SCREENS),
        Quote("people who cut social media to 30 min a day felt less lonely and less depressed within 3 weeks.", "Hunt et al., 2018, University of Pennsylvania", QuoteTopic.SCREENS),
        Quote("endless feeds have no natural stopping point. that's on purpose. you have to make one.", "Stopping cues, consumer psychology research", QuoteTopic.SCREENS),
        Quote("unpredictable rewards keep you pulling, same as a slot machine. that's every swipe.", "Variable-ratio reinforcement, B.F. Skinner", QuoteTopic.SCREENS),
        Quote("a week with notifications off made people more attentive and less hyperactive.", "Kushlev, Proulx & Dunn, 2016, CHI", QuoteTopic.SCREENS),
        Quote("checking email just 3 times a day lowered people's daily stress.", "Kushlev & Dunn, 2015", QuoteTopic.SCREENS),
        Quote("more social media at night goes with worse sleep in young adults.", "Levenson et al., 2016, Preventive Medicine", QuoteTopic.SCREENS),
        Quote("scrolling other people's highlights lowers how you see yourself. they only post their best moments.", "Vogel et al., 2014, upward social comparison", QuoteTopic.SCREENS),
        // Focus
        Quote("after an interruption it takes about 23 minutes to get fully back on task.", "Gloria Mark, UC Irvine", QuoteTopic.FOCUS),
        Quote("switching between tasks can eat up to 40% of your productive time.", "American Psychological Association, on Meyer & Rubinstein", QuoteTopic.FOCUS),
        Quote("bored people came up with more creative ideas. let yourself be bored for a bit.", "Mann & Cadman, 2014", QuoteTopic.FOCUS),
        Quote("focus is a muscle. the more you skip the quick hit, the stronger it gets.", "Cal Newport, Deep Work", QuoteTopic.FOCUS),
        Quote("work expands to fill the time you give it. give it less.", "Parkinson's law, C. Northcote Parkinson, 1955", QuoteTopic.FOCUS),
        // Procrastination
        Quote("procrastination isn't a time problem, it's a mood problem. you're dodging a feeling, not a task.", "Sirois & Pychyl, 2013", QuoteTopic.PROCRASTINATION),
        Quote("students who forgave themselves for procrastinating procrastinated less next time. go easy, then go.", "Wohl, Pychyl & Bennett, 2010", QuoteTopic.PROCRASTINATION),
        Quote("\"when X happens, I'll do Y\" plans make you way more likely to follow through.", "Gollwitzer & Sheeran, 2006, meta-analysis", QuoteTopic.PROCRASTINATION),
        Quote("writing tomorrow's to-do list before bed helped people fall asleep faster.", "Scullin et al., 2018, Baylor University", QuoteTopic.PROCRASTINATION),
        Quote("can't start? do just 2 minutes of it. starting is the hard part.", "The two-minute rule, James Clear", QuoteTopic.PROCRASTINATION),
        Quote("unfinished tasks keep nagging your brain. finishing one small thing quiets it.", "The Zeigarnik effect", QuoteTopic.PROCRASTINATION),
        // Mindset
        Quote("a new habit took 66 days on average to feel automatic. missing one day didn't break it.", "Lally et al., 2010, UCL", QuoteTopic.MINDSET),
        Quote("you don't rise to your goals, you fall to your systems.", "James Clear, Atomic Habits", QuoteTopic.MINDSET),
        Quote("how we spend our days is, of course, how we spend our lives.", "Annie Dillard", QuoteTopic.MINDSET),
        Quote("it's not that we have a short time to live, it's that we waste a lot of it.", "Seneca, On the Shortness of Life", QuoteTopic.MINDSET),
        // Wisdom
        Quote("yoga is the stilling of the restless mind.", "Patanjali, Yoga Sutras 1.2", QuoteTopic.WISDOM),
        Quote("you have a right to your work, not to its results. just do the next thing well.", "Bhagavad Gita 2.47", QuoteTopic.WISDOM),
        Quote("pratyahara: pulling your senses back from what pulls at them. that's this app's whole idea.", "Patanjali, Yoga Sutras 2.54", QuoteTopic.WISDOM),
    )

    /** A stable pick for a given seed, so the same moment shows the same quote across recompositions. */
    fun pick(seed: Long): Quote = all[Math.floorMod(seed, all.size.toLong()).toInt()]

    /** Quote of the day: the same all day, a different one tomorrow. */
    fun ofDay(epochDay: Long): Quote = pick(epochDay * 7 + 3)
}
