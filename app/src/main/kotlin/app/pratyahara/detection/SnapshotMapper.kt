package app.pratyahara.detection

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.pratyahara.core.detection.UiNode
import app.pratyahara.core.detection.UiSnapshot

/**
 * Turns the live node tree into a [UiSnapshot] for one evaluation. The snapshot lives only in memory
 * and is discarded right after scoring.
 */
object SnapshotMapper {
    private const val MAX_NODES = 900
    private const val MAX_DEPTH = 60

    fun map(root: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): UiSnapshot {
        val out = ArrayList<UiNode>(256)
        val rect = Rect()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            node.getBoundsInScreen(rect)
            out += UiNode(
                className = node.className?.toString(),
                viewId = node.viewIdResourceName?.substringAfter(":id/"),
                text = node.text?.toString()?.take(80),
                contentDescription = node.contentDescription?.toString()?.take(80),
                selected = node.isSelected,
                scrollable = node.isScrollable,
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.addLast(it to depth + 1) }
                }
            }
        }
        return UiSnapshot(root.packageName?.toString().orEmpty(), screenWidth, screenHeight, out)
    }
}
