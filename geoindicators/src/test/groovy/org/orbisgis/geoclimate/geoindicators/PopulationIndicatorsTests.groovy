/**
 * GeoClimate is a geospatial processing toolbox for environmental and climate studies
 * <a href="https://github.com/orbisgis/geoclimate">https://github.com/orbisgis/geoclimate</a>.
 *
 * This code is part of the GeoClimate project. GeoClimate is free software;
 * you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * GeoClimate is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details <http://www.gnu.org/licenses/>.
 *
 *
 * For more information, please consult:
 * <a href="https://github.com/orbisgis/geoclimate">https://github.com/orbisgis/geoclimate</a>
 *
 */
package org.orbisgis.geoclimate.geoindicators

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.orbisgis.data.H2GIS
import org.orbisgis.geoclimate.Geoindicators

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull
import static org.orbisgis.data.H2GIS.open

class PopulationIndicatorsTests {

    @TempDir
    static File folder
    private static H2GIS h2GIS

    @BeforeAll
    static void beforeAll() {
        h2GIS = open(folder.getAbsolutePath() + File.separator + "populationIndicatorsTests;AUTO_SERVER=TRUE")
    }

    @Test
    void formatPopulationTableTest() {
        h2GIS.execute("""DROP TABLE IF EXISTS population;
        CREATE TABLE population (id serial, the_geom GEOMETRY(POLYGON), pop float, pop_old float);
        INSERT INTO population VALUES (1,'POLYGON ((105 312, 230 312, 230 200, 105 200, 105 312))'::GEOMETRY, 12, 10 ),
        (1,'POLYGON((280 170, 390 170, 390 70, 280 70, 280 170))'::GEOMETRY, 1, 200 );""")
        String populationTable = Geoindicators.PopulationIndicators.formatPopulationTable(h2GIS, "population", ["pop"])
        assertEquals(2, h2GIS.firstRow("select count(*) as count from $populationTable".toString()).count)
    }

    @Test
    void formatPopulationTableWithZoneTest() {
        h2GIS.execute("""DROP TABLE IF EXISTS population, zone;
        CREATE TABLE population (id serial, the_geom GEOMETRY(POLYGON), pop float, pop_old float);
        INSERT INTO population VALUES (1,'POLYGON ((105 312, 230 312, 230 200, 105 200, 105 312))'::GEOMETRY, 12, 10 ),
        (1,'POLYGON((280 170, 390 170, 390 70, 280 70, 280 170))'::GEOMETRY, 1, 200 );
        CREATE TABLE zone as select 'POLYGON ((70 390, 290 390, 290 270, 70 270, 70 390))'::GEOMETRY as the_geom;""")
        String populationTable = Geoindicators.PopulationIndicators.formatPopulationTable(h2GIS, "population", ["pop"],
                "zone")
        assertEquals(1, h2GIS.firstRow("select count(*) as count from $populationTable".toString()).count)
    }

    @Test
    void multiScalePopulationTest() {
        h2GIS.execute("""DROP TABLE IF EXISTS building, population, rsu, grid;
        CREATE TABLE population (id_pop serial, the_geom GEOMETRY(POLYGON), pop float);
        INSERT INTO population VALUES (1,'POLYGON ((0 0, 0 10, 10 10, 10 0, 0 0))'::GEOMETRY, 10 ),
        (2,'POLYGON((0 10, 0 20, 10 20, 10 10, 0 10))'::GEOMETRY, 10 ),
        (3,'POLYGON((10 0, 10 10, 20 10, 20 0, 10 0))'::GEOMETRY, 10 ),
        (4,'POLYGON((10 10, 10 20, 20 20, 20 10, 10 10))'::GEOMETRY, 10 );
        
        CREATE TABLE building (id_build serial, the_geom GEOMETRY(POLYGON), nb_lev integer, main_use varchar, type varchar, id_rsu integer);
        INSERT INTO building VALUES (1,'POLYGON ((2.4 16.15, 5.75 16.15, 5.75 13.85, 2.4 13.85, 2.4 16.15))'::GEOMETRY,1,
        'residential', 'residential', 1 ),
        (2,'POLYGON ((9 2, 11 2, 11 0, 9 0, 9 2))'::GEOMETRY,1, 'residential', 'residential',2 );
        
        CREATE TABLE rsu(id_rsu serial, the_geom GEOMETRY(POLYGON));
        INSERT INTO rsu VALUES (1, 'POLYGON ((-4.2 17.5, 9 17.5, 9 7.1, -4.2 7.1, -4.2 17.5))'::GEOMETRY),
        (2, 'POLYGON ((8 4, 16.5 4, 16.5 -1.8, 8 -1.8, 8 4))'::GEOMETRY), 
        (3, 'POLYGON ((-12 4.3, -1.8 4.3, -1.8 -1.2, -12 -1.2, -12 4.3))'::GEOMETRY);
        
        CREATE TABLE grid (id_grid serial, the_geom GEOMETRY(POLYGON));
        INSERT INTO grid VALUES (1,'POLYGON ((0 -5, 0 6, 10 6, 10 -5, 0 -5))'::GEOMETRY ),
        (2,'POLYGON((0 6, 0 20, 10 20, 10 6, 0 6))'::GEOMETRY ),
        (3,'POLYGON((10 -5, 10 6, 20 6, 20 -5, 10 -5))'::GEOMETRY ),
        (4,'POLYGON((10 6, 10 20, 20 20, 20 6, 10 6))'::GEOMETRY );""".toString())

        Map results = Geoindicators.PopulationIndicators.multiScalePopulation(h2GIS, "population", ["pop"],
                "building", "rsu", "grid")

        results.each { it ->
            h2GIS.save(it.value, "./target/${it.value}.fgb", true)
        }

        def rows = h2GIS.rows("SELECT id_build, pop from ${results.buildingTable} order by id_build".toString())
        assertEquals(10, rows[0].POP, 0.1)
        assertEquals(20, rows[1].POP, 0.1)

        rows = h2GIS.rows("SELECT * from ${results.rsuTable} order by id_rsu".toString())
        assertEquals(10, rows[0].SUM_POP, 0.1)
        assertEquals(20, rows[1].SUM_POP, 0.1)
        assertEquals(0, rows[2].SUM_POP, 0.1)

        rows = h2GIS.rows("SELECT * from ${results.gridTable} order by id_grid".toString())
        assertEquals(10, rows[0].SUM_POP, 0.1)
        assertEquals(10, rows[1].SUM_POP, 0.1)
        assertEquals(10, rows[2].SUM_POP, 0.1)
        assertNull(rows[3].SUM_POP)
    }
}
