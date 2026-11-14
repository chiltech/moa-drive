package com.moagauge.pleos.passport

import org.junit.Assert.*
import org.junit.Test

class PassportCityAtlasTest {
    private fun ring(s:Double,w:Double,n:Double,e:Double) = listOf(CityBoundaryPoint(s,w),CityBoundaryPoint(s,e),CityBoundaryPoint(n,e),CityBoundaryPoint(n,w),CityBoundaryPoint(s,w))
    private fun city(id:String,s:Double,w:Double,n:Double,e:Double,kind:String="city") = PassportCity(id,"${id}시","테스트도",kind,
        CityBoundaryPoint((s+n)/2,(w+e)/2),listOf(CityBoundaryPolygon(ring(s,w,n,e))))
    private fun visit(trip:String,lat:Double,lon:Double,time:Long=1) = PassportVisit(trip,PassportCell.from(lat,lon)!!,time)

    @Test fun polygonRequiresRealInteriorAndExcludesHolesAndEdges() {
        val polygon=CityBoundaryPolygon(ring(0.0,0.0,4.0,4.0),listOf(ring(1.0,1.0,2.0,2.0)))
        assertTrue(polygon.contains(CityBoundaryPoint(3.0,3.0)))
        assertFalse(polygon.contains(CityBoundaryPoint(1.5,1.5)))
        assertFalse(polygon.contains(CityBoundaryPoint(0.0,2.0)))
        assertFalse(polygon.contains(CityBoundaryPoint(1.0,1.5)))
    }
    @Test fun boundingBoxAloneCannotGrantConcaveTerritory() {
        val polygon=CityBoundaryPolygon(listOf(CityBoundaryPoint(0.0,0.0),CityBoundaryPoint(0.0,4.0),
            CityBoundaryPoint(1.0,4.0),CityBoundaryPoint(1.0,1.0),CityBoundaryPoint(4.0,1.0),CityBoundaryPoint(4.0,0.0)))
        assertTrue(polygon.contains(CityBoundaryPoint(.5,3.0)))
        assertFalse(polygon.contains(CityBoundaryPoint(3.0,3.0)))
    }
    @Test fun islandsResolveButInvalidUnknownAndOverlappingLocationsDoNot() {
        val a=city("a",0.0,0.0,2.0,2.0).let { it.copy(polygons=it.polygons+CityBoundaryPolygon(ring(5.0,5.0,6.0,6.0))) }
        val atlas=PassportCityAtlas(listOf(a,city("b",1.0,1.0,3.0,3.0)))
        assertEquals("a",atlas.resolve(5.5,5.5)?.id)
        assertNull(atlas.resolve(1.5,1.5))
        assertNull(atlas.resolve(20.0,20.0))
        assertNull(atlas.resolve(Double.NaN,1.0))
    }
    @Test fun visitAggregationPreservesTripsAndNeverAwardsUnknownCells() {
        val atlas=PassportCityAtlas(listOf(city("a",0.0,0.0,2.0,2.0)))
        val first=visit("one",.2,.2,2)
        val result=atlas.visited(listOf(first,first,visit("two",.3,.3,8),visit("unknown",7.0,7.0)))
        assertEquals(1,result.size)
        assertEquals(2,result.single().tripCount)
        assertEquals(2,result.single().visits.size)
        assertEquals(2L,result.single().firstAt)
        assertEquals(8L,result.single().lastAt)
    }
    @Test fun recommendationsExcludeAllVisitedCitiesAndCountiesWithStableNearestOrder() {
        val cities=listOf(city("far",8.0,8.0,9.0,9.0),city("visited",0.0,0.0,1.0,1.0),city("near",2.0,2.0,3.0,3.0),city("county",1.1,1.1,1.9,1.9,"county"))
        val atlas=PassportCityAtlas(cities)
        val visits=listOf(visit("one",.5,.5))
        assertEquals(listOf("near","far"),atlas.recommend(visits,5).map { it.id })
        assertEquals(atlas.recommend(visits),PassportCityAtlas(cities.reversed()).recommend(visits))
        assertTrue(atlas.recommend(visits,0).isEmpty())
    }
}
