package org.orbisgis.geoclimate.geoindicators

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.orbisgis.geoclimate.Geoindicators

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.orbisgis.orbisdata.datamanager.jdbc.h2gis.H2GIS.open

class BuildingIndicatorsTests {

    private static def h2GIS
    private static def randomDbName() {"${BuildingIndicatorsTests.simpleName}_${UUID.randomUUID().toString().replaceAll"-", "_"}"}


    @BeforeAll
    static void beforeAll(){
        h2GIS = open"./target/${randomDbName()};AUTO_SERVER=TRUE"
    }

    @BeforeEach
    void beforeEach(){
        h2GIS.executeScript(getClass().getResourceAsStream("data_for_tests.sql"))
    }

    @Test
    void sizePropertiesTest() {
        // Only the first 1 first created buildings are selected for the tests
        h2GIS.execute "DROP TABLE IF EXISTS tempo_build, test_building_size_properties; " +
                "CREATE TABLE tempo_build AS SELECT * FROM building_test WHERE id_build = 7;"

        def p =  Geoindicators.BuildingIndicators.sizeProperties()
        assert p([inputBuildingTableName: "tempo_build",
                  operations:["volume", "floor_area", "total_facade_length",
                              "passive_volume_ratio"],
                  prefixName : "test",datasource:h2GIS])
        h2GIS.getTable("test_building_size_properties").eachRow {
            row ->
                assertEquals(141, (int)row.volume)
                assertEquals(47, row.floor_area)
                assertEquals(38, row.total_facade_length)
                assertEquals(0, row.passive_volume_ratio)
        }
    }

    @Test
    void neighborsPropertiesTest() {
        // Only the first 6 first created buildings are selected since any new created building may alter the results
        h2GIS.execute "DROP TABLE IF EXISTS tempo_build, test_building_neighbors_properties; " +
                "CREATE TABLE tempo_build AS SELECT * FROM building_test WHERE id_build < 7"

        def  p =  Geoindicators.BuildingIndicators.neighborsProperties()
        assertTrue p.execute([inputBuildingTableName: "tempo_build",
                              operations:["contiguity","common_wall_fraction",
                                          "number_building_neighbor"],
                              prefixName : "test",datasource:h2GIS])
        def concat = ["", "", ""]
        h2GIS.eachRow("SELECT * FROM test_building_neighbors_properties WHERE id_build = 1 OR id_build = 5 " +
                "ORDER BY id_build ASC"){
            row ->
                concat[0]+= "${row.contiguity.round(5)}\n"
                concat[1]+= "${row.common_wall_fraction.round(5)}\n"
                concat[2]+= "${row.number_building_neighbor}\n"

        }
        assertEquals("0.0\n${(50/552).round(5)}\n".toString(),concat[0].toString())
        assertEquals("0.0\n${(10/46).round(5)}\n".toString(), concat[1].toString())
        assertEquals("0\n1\n".toString(),  concat[2].toString())
    }

    @Test
    void formPropertiesTest() {
        // Only the first 1 first created buildings are selected for the tests
        h2GIS.execute "DROP TABLE IF EXISTS tempo_build, test_building_form_properties; CREATE TABLE tempo_build " +
                "AS SELECT * FROM building_test WHERE id_build < 8 OR id_build = 30"

        def  p =  Geoindicators.BuildingIndicators.formProperties()
        assertTrue p.execute([inputBuildingTableName: "tempo_build",
                              operations:["area_concavity","form_factor",
                                          "raw_compactness", "perimeter_convexity"],
                              prefixName : "test",datasource:h2GIS])
        def concat = ["", "", "", ""]
        h2GIS.eachRow("SELECT * FROM test_building_form_properties WHERE id_build = 1 OR id_build = 7 ORDER BY id_build ASC"){
            row ->
                concat[0]+= "${row.area_concavity}\n"
                concat[1]+= "${row.form_factor.round(5)}\n"
        }
        h2GIS.eachRow("SELECT * FROM test_building_form_properties WHERE id_build = 2 ORDER BY id_build ASC"){
            row -> concat[2]+= "${row.raw_compactness.round(3)}\n"
        }
        h2GIS.eachRow("SELECT * FROM test_building_form_properties WHERE id_build = 1 OR id_build = 7 OR " +
                "id_build = 30 ORDER BY id_build ASC"){
            row -> concat[3]+= "${row.perimeter_convexity.round(5)}\n"
        }
        assertEquals("1.0\n0.94\n".toString(),concat[0].toString())
        assertEquals("${(0.0380859375).round(5)}\n${(0.0522222222222222).round(5)}\n".toString(), concat[1].toString())
        assertEquals("5.607\n".toString(),  concat[2].toString())
        assertEquals("1.0\n0.78947\n0.85714\n".toString(), concat[3].toString())
    }

    @Test
    void minimumBuildingSpacingTest() {
        // Only the first 1 first created buildings are selected for the tests
        h2GIS.execute "DROP TABLE IF EXISTS tempo_build, test_building_form_properties; CREATE TABLE tempo_build AS " +
                "SELECT * FROM building_test WHERE id_build < 7"

        def  p =  Geoindicators.BuildingIndicators.minimumBuildingSpacing()
        assertTrue p.execute([inputBuildingTableName: "tempo_build", bufferDist:100, prefixName : "test",
                              datasource:h2GIS])
        def concat = ""
        h2GIS.eachRow("SELECT * FROM test_building_minimum_building_spacing WHERE id_build = 2 OR id_build = 4 " +
                "OR id_build = 6 ORDER BY id_build ASC"){
            row -> concat+= "${row.minimum_building_spacing}\n"
        }
        assertEquals("2.0\n0.0\n7.0\n", concat)
    }

    @Test
    void roadDistanceTest() {
        // Only the first 1 first created buildings are selected for the tests
        h2GIS.execute "DROP TABLE IF EXISTS tempo_road, test_building_road_distance; CREATE TABLE tempo_road " +
                "AS SELECT * FROM road_test WHERE id_road < 5"

        def  p =  Geoindicators.BuildingIndicators.roadDistance()
        assertTrue p.execute([inputBuildingTableName: "building_test", inputRoadTableName: "tempo_road", bufferDist:100,
                              prefixName : "test",datasource:h2GIS])
        def concat = ""
        h2GIS.eachRow("SELECT * FROM test_building_road_distance WHERE id_build = 6 OR id_build = 33 ORDER BY id_build ASC"){
            row -> concat+= "${row.road_distance.round(4)}\n"
        }
        assertEquals("23.9556\n100.0\n", concat)
    }

    @Test
    void likelihoodLargeBuildingTest() {
        // Only the first 1 first created buildings are selected for the tests
        h2GIS.execute("DROP TABLE IF EXISTS tempo_build, tempo_build2, test_building_neighbors_properties; " +
                "CREATE TABLE tempo_build AS SELECT * FROM building_test WHERE id_build < 29")

        def  pneighb =  Geoindicators.BuildingIndicators.neighborsProperties()
        assertTrue pneighb.execute([inputBuildingTableName: "tempo_build",
                                    operations:["number_building_neighbor"],
                                    prefixName : "test", datasource:h2GIS])

        // The number of neighbors are added to the tempo_build table
        h2GIS.execute "CREATE TABLE tempo_build2 AS SELECT a.id_build, a.the_geom, b.number_building_neighbor" +
                " FROM tempo_build a, test_building_neighbors_properties b WHERE a.id_build = b.id_build"

        def  p =  Geoindicators.BuildingIndicators.likelihoodLargeBuilding()
        assertTrue p.execute([inputBuildingTableName: "tempo_build2", nbOfBuildNeighbors: "number_building_neighbor",
                              prefixName : "test", datasource:h2GIS])
        def concat = ""
        h2GIS.eachRow("SELECT * FROM test_building_likelihood_large_building WHERE id_build = 4 OR id_build = 7 OR " +
                "id_build = 28 ORDER BY id_build ASC"){
            row -> concat+= "${row.likelihood_large_building.round(2)}\n"
        }
        assertEquals("0.0\n0.02\n1.0\n", concat)
    }

}