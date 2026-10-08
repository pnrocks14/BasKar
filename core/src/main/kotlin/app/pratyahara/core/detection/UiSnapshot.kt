package app.pratyahara.core.detection

/**
 * A minimal, in-memory description of what is on screen, built from the accessibility node tree.
 * It is never logged, stored or sent anywhere; it exists only for the duration of one evaluation.
 */
data class UiSnapshot(
    val packageName: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val nodes: List<UiNode>,
)

data class UiNode(
    val className: String?,
    /** Resource name without the package, e.g. "clips_viewer_view_pager". */
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val selected: Boolean,
    val scrollable: Boolean,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2

    /** Text or content description, lowercased, for loose label matching. */
    val label: String get() = listOfNotNull(text, contentDescription).joinToString(" ").lowercase()
}
