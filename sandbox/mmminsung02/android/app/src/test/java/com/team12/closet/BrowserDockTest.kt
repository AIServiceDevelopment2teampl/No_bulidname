package com.team12.closet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.team12.closet.ui.shop.DockEdge
import com.team12.closet.ui.shop.clampDock
import com.team12.closet.ui.shop.dragDock
import com.team12.closet.ui.shop.tuckEdge
import com.team12.closet.ui.shop.untuckedOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** 떠 있는 버블은 웹페이지 영역 안에서만 움직인다 */
class BrowserDockTest {
    private val area = IntSize(1000, 2000)
    private val dock = IntSize(300, 100)

    @Test
    fun staysInsideTheArea() {
        // 왼쪽 끝: -(1000 - 300 - 2*40) = -620, 위쪽 끝: -(2000 - 100 - 80) = -1820
        assertEquals(Offset(-620f, -1820f), clampDock(Offset(-5000f, -5000f), area, dock, 40f))
        assertEquals(Offset(0f, 0f), clampDock(Offset(50f, 50f), area, dock, 40f))
        assertEquals(Offset(-100f, -200f), clampDock(Offset(-100f, -200f), area, dock, 40f))
    }

    @Test
    fun areaNotMeasuredYetKeepsItInTheCorner() {
        assertEquals(Offset(0f, 0f), clampDock(Offset(-100f, -100f), IntSize.Zero, dock, 40f))
    }

    // 화면 밖으로 밀어 숨기기. 버블 폭 300, 여백 40 → 오른쪽은 x > 40 + 120, 왼쪽은 x < -620 - 40 - 120
    @Test
    fun pushingPastTheEdgeTucksIt() {
        assertEquals(DockEdge.RIGHT, tuckEdge(Offset(161f, -100f), area, dock, 40f))
        assertEquals(null, tuckEdge(Offset(159f, -100f), area, dock, 40f))
        assertEquals(DockEdge.LEFT, tuckEdge(Offset(-781f, -100f), area, dock, 40f))
        assertEquals(null, tuckEdge(Offset(-779f, -100f), area, dock, 40f))
        assertEquals(null, tuckEdge(Offset(-300f, -100f), area, dock, 40f))
    }

    @Test
    fun draggingCanGoOneDockWidthPastTheEdgesButNotUpOrDown() {
        assertEquals(Offset(340f, 0f), dragDock(Offset(5000f, 500f), area, dock, 40f))
        assertEquals(Offset(-960f, -1820f), dragDock(Offset(-5000f, -5000f), area, dock, 40f))
    }

    @Test
    fun handleBringsItBackOnTheSameSideAndHeight() {
        assertEquals(Offset(0f, -300f), untuckedOffset(DockEdge.RIGHT, -300f, area, dock, 40f))
        assertEquals(Offset(-620f, -300f), untuckedOffset(DockEdge.LEFT, -300f, area, dock, 40f))
    }
}
