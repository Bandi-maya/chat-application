package com.example.chat.ui.camera
import org.junit.Assert.*
import org.junit.Test
class DocumentQuadTest {
 @Test fun defaultQuadValid(){assertTrue(PaperQuad.inset.isValid())}
 @Test fun crossedAndCollapsedRefused(){assertFalse(PaperQuad(PaperPoint(0f,0f),PaperPoint(1f,1f),PaperPoint(1f,0f),PaperPoint(0f,1f)).isValid());assertFalse(PaperQuad.inset.copy(topRight=PaperQuad.inset.topLeft).isValid())}
 @Test fun flatSceneDoesNotInventBoundary(){assertNull(detectPaperQuad(IntArray(10000){220},100,100))}
 @Test fun syntheticPaperOnDarkSurfaceDetected(){val p=IntArray(10000){20};for(y in 20..80)for(x in 25..75)p[y*100+x]=240;val q=detectPaperQuad(p,100,100);assertNotNull(q);assertTrue(q!!.isValid());assertEquals(25f/99,q.topLeft.x,.02f);assertEquals(80f/99,q.bottomRight.y,.02f)}
 @Test fun outOfBoundsClampedButValidityExplicit(){val q=PaperQuad.inset.moved(0,PaperPoint(-2f,-1f));assertEquals(0f,q.topLeft.x);assertTrue(q.isValid())}
}
