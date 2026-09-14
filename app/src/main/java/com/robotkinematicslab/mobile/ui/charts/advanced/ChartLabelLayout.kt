package com.robotkinematicslab.mobile.ui.charts.advanced

internal data class ChartLabelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: ChartLabelRect): Boolean = left < other.right && right > other.left && top < other.bottom && bottom > other.top
}
internal fun placeChartLabel(x: Float, y: Float, width: Float, height: Float, plot: ChartLabelRect, occupied: List<ChartLabelRect>, gap: Float = 8f): ChartLabelRect? {
    if(listOf(x,y,width,height,gap,plot.left,plot.top,plot.right,plot.bottom).any { !it.isFinite() } || width <= 0 || height <= 0 || gap < 0) return null
    val origins = listOf(x + gap to y - height - gap, x - width - gap to y - height - gap, x + gap to y + gap, x - width - gap to y + gap)
    return origins.map { (left,top) -> ChartLabelRect(left,top,left+width,top+height) }.firstOrNull { candidate ->
        candidate.left >= plot.left && candidate.top >= plot.top && candidate.right <= plot.right && candidate.bottom <= plot.bottom && occupied.none(candidate::overlaps)
    }
}
