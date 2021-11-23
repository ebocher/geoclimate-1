package org.orbisgis.geoclimate.geoindicators

import groovy.transform.BaseScript
import org.orbisgis.geoclimate.Geoindicators
import org.orbisgis.orbisdata.datamanager.jdbc.*
import org.orbisgis.orbisdata.processmanager.api.IProcess

@BaseScript Geoindicators geoindicators


/**
 * This process calculates the ratio between the area of hole within a block and the area of the block.
 *
 * @param datasource A connexion to a database (H2GIS, PostGIS, ...) where are stored the input Table and in which
 * the resulting database will be stored
 * @param blockTable the name of the input ITable where are stored the block geometries
 * @param prefixName String use as prefix to name the output table
 *
 * @return outputTableName Table name in which the block id and their corresponding indicator value are stored
 *
 * @author Jérémy Bernard
 */
IProcess holeAreaDensity() {
    return create {
        title "Hole area ratio"
        id "holeAreaDensity"
        inputs blockTable: String, prefixName: String, datasource: JdbcDataSource
        outputs outputTableName: String
        run { blockTable, prefixName, datasource ->

            def GEOMETRIC_FIELD = "the_geom"
            def ID_COLUMN_BL = "id_block"
            def BASE_NAME = "hole_area_density"

            debug "Executing Hole area ratio"

            // The name of the outputTableName is constructed
            def outputTableName = prefix(prefixName, "block_" + BASE_NAME)

            def query = """
            DROP TABLE IF EXISTS $outputTableName; 
            CREATE TABLE $outputTableName AS 
                SELECT $ID_COLUMN_BL, ST_AREA(ST_HOLES($GEOMETRIC_FIELD))/ST_AREA($GEOMETRIC_FIELD) AS $BASE_NAME 
                FROM $blockTable
        """

            datasource query
            [outputTableName: outputTableName]
        }
    }
}
/**
 * The sum of the building free external area composing the block are divided by the sum of the building volume.
 * The sum of the building volume is calculated at the power 2/3 in order to have a ratio of homogeneous quantities (same unit)
 *
 * @param datasource A connexion to a database (H2GIS, PostGIS, ...) where are stored the input Table and in which
 * the resulting database will be stored
 * @param buildTable the name of the input ITable where are stored the "building_volume", the "building_contiguity",
 * the geometry field and the block id
 * @param correlationTableName the name of the input ITable where are stored the relationships between blocks and buildings
 * @param buildingVolumeField the name of the input field where are stored the "building_volume" values within the "buildTable"
 * @param buildingContiguityField the name of the input field where are stored the "building_contiguity"
 * values within the "buildTable"
 * @param prefixName String use as prefix to name the output table
 *
 * @return outputTableName Table name in which the block id and their corresponding indicator value are stored
 *
 * @author Jérémy Bernard
 */
IProcess netCompactness() {
    return create {
        title "Block net compactness"
        id "netCompactness"
        inputs buildTable: String, buildingVolumeField: String, buildingContiguityField: String,
                prefixName: String, datasource: JdbcDataSource
        outputs outputTableName: String
        run { buildTable, buildingVolumeField, buildingContiguityField, prefixName, datasource ->

            def GEOMETRY_FIELD_BU = "the_geom"
            def ID_COLUMN_BL = "id_block"
            def HEIGHT_WALL = "height_wall"
            def BASE_NAME = "net_compactness"

            debug "Executing Block net net_compactness"

            // The name of the outputTableName is constructed
            def outputTableName = prefix(prefixName, "block_" + BASE_NAME)

            datasource."$buildTable".id_block.createIndex()

            def query = """
            DROP TABLE IF EXISTS $outputTableName; 
            CREATE TABLE $outputTableName AS 
                SELECT $ID_COLUMN_BL, 
                    SUM(
                        $buildingContiguityField*(
                            ST_PERIMETER($GEOMETRY_FIELD_BU)+
                            ST_PERIMETER(ST_HOLES($GEOMETRY_FIELD_BU)))*
                        $HEIGHT_WALL)/
                    POWER(
                        SUM($buildingVolumeField), 
                        2./3) 
                    AS $BASE_NAME 
                FROM $buildTable 
                GROUP BY $ID_COLUMN_BL
        """

            datasource query
            [outputTableName: outputTableName]
        }
    }
}


/**
 * This indicator is usefull for the urban fabric classification proposed in Thornay et al. (2017) and also described
 * in Bocher et al. (2018). It answers to the Step 11 of the manual decision tree which consists in checking whether
 * the block is closed (continuous buildings the aligned along road). In order to identify the RSU with closed blocks,
 * the difference  between the st_holes(bloc scale) and sum(st_holes(building scale)) indicators will be calculated.
 *
 * WARNING: this method will not be able to identify blocks that are nearly closed (e.g. 99 % of the RSU perimeter).
 *
 * References:
 *   Bocher, E., Petit, G., Bernard, J., & Palominos, S. (2018). A geoprocessing framework to compute
 * urban indicators: The MApUCE tools chain. Urban climate, 24, 153-174.
 *   Tornay, Nathalie, Robert Schoetter, Marion Bonhomme, Serge Faraut, and Valéry Masson. "GENIUS: A methodology to
 * define a detailed description of buildings for urban climate and building energy consumption simulations."
 * Urban Climate 20 (2017): 75-93.
 *
 * @param datasource A connexion to a database (H2GIS, PostGIS, ...) where are stored the input Table and in which
 * the resulting database will be stored
 * @param blockTable the name of the input ITable where are stored the geometry field and the block ID
 * @param correlationTableName the name of the input ITable where are stored the relationships between blocks and buildings
 * @param prefixName String use as prefix to name the output table
 *
 * @return outputTableName Table name in which the block id and their corresponding indicator value are stored
 * @author Jérémy Bernard
 */
IProcess closingness() {
    return create {
        title "Closingness of a block"
        id "closingness"
        inputs correlationTableName: String, blockTable: String, prefixName: String, datasource: JdbcDataSource
        outputs outputTableName: String
        run { correlationTableName, blockTable, prefixName, datasource ->

            def GEOMETRY_FIELD_BU = "the_geom"
            def GEOMETRY_FIELD_BL = "the_geom"
            def ID_COLUMN_BL = "id_block"
            def BASE_NAME = "closingness"

            debug "Executing Closingness of a block"

            // The name of the outputTableName is constructed
            def outputTableName = prefix(prefixName, "block_" + BASE_NAME)

            datasource."$blockTable".id_block.createIndex()
            datasource."$correlationTableName".id_block.createIndex()

            def query = """
            DROP TABLE IF EXISTS $outputTableName; 
            CREATE TABLE $outputTableName AS 
                SELECT b.$ID_COLUMN_BL, 
                    ST_AREA(ST_HOLES(b.$GEOMETRY_FIELD_BL))-
                    SUM(ST_AREA(ST_HOLES(a.$GEOMETRY_FIELD_BU))) 
                    AS $BASE_NAME 
                FROM $correlationTableName a, 
                    $blockTable b 
                WHERE a.$ID_COLUMN_BL = b.$ID_COLUMN_BL 
                GROUP BY b.$ID_COLUMN_BL"""

            datasource query
            [outputTableName: outputTableName]
        }
    }
}