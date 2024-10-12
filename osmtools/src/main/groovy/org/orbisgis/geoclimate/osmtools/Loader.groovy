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
package org.orbisgis.geoclimate.osmtools

import groovy.transform.BaseScript
import groovy.transform.Field
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.orbisgis.data.jdbc.JdbcDataSource

import java.util.regex.Pattern

import static org.orbisgis.geoclimate.osmtools.utils.OSMElement.*

@BaseScript OSMTools pf

/** Default SRID */
@Field DEFAULT_SRID = 4326

/**
 * This process extracts OSM data file and load it in a database using an area
 * The area must be a JTS envelope
 *
 * @param datasource A connexion to a DB to load the OSM file
 * @param filterArea Filtering area as envelope
 * @param distance to expand the envelope of the query box. Default is 0
 *
 * @return The name of the tables that contains the geometry representation of the extracted area (outputZoneTable) and
 * its envelope (outputZoneEnvelopeTable)
 *
 * @author Erwan Bocher (CNRS LAB-STICC)
 * @author Elisabeth Le Saux (UBS LAB-STICC)
 */
Map fromArea(JdbcDataSource datasource, Object filterArea, float distance = 0) throws Exception {
    if (!datasource) {
        throw new Exception("No datasource provided.")
    }
    if (!filterArea) {
        throw new Exception("Filter area not defined")
    }
    def outputZoneTable = postfix "ZONE"
    def outputZoneEnvelopeTable = postfix "ZONE_ENVELOPE"
    def osmTablesPrefix = postfix "OSM_DATA"
    def geom
    if (filterArea instanceof Envelope) {
        geom = new GeometryFactory().toGeometry(filterArea)
    } else if (filterArea instanceof Polygon) {
        geom = filterArea
    } else if (filterArea in Collection && filterArea.size() == 4) {
        geom = Utilities.geometryFromValues(filterArea)
    } else {
        throw new Exception("The filter area must be an Envelope or a Polygon")
    }

    def epsg = DEFAULT_SRID
    def env = org.h2gis.utilities.GeographyUtilities.expandEnvelopeByMeters(geom.getEnvelopeInternal(), distance)

    //Create table to store the geometry and the envelope of the extracted area
    datasource.execute("""
                CREATE TABLE $outputZoneTable (the_geom GEOMETRY(POLYGON, $epsg));
                INSERT INTO $outputZoneTable VALUES (ST_GEOMFROMTEXT('${geom}', $epsg));
        """)

    def geometryFactory = new GeometryFactory()
    def geomEnv = geometryFactory.toGeometry(env)

    datasource.execute """CREATE TABLE $outputZoneEnvelopeTable (the_geom GEOMETRY(POLYGON, $epsg));
                    INSERT INTO $outputZoneEnvelopeTable VALUES 
                    (ST_GEOMFROMTEXT('$geomEnv',$epsg));""".toString()

    def query = OSMTools.Utilities.buildOSMQuery(geomEnv, [], NODE, WAY, RELATION)
    def extract = extract(query)
    if (extract) {
        info "Downloading OSM data from the area $filterArea"
        if (load(datasource, osmTablesPrefix, extract)) {
            info "Loading OSM data from the area $filterArea"
            return [zone    : outputZoneTable,
                    envelope: outputZoneEnvelopeTable,
                    prefix  : osmTablesPrefix,
                    epsg    : epsg]
        } else {
            throw new Exception("Cannot load the OSM data from the area $filterArea".toString())
        }
    } else {
        throw new Exception("Cannot download OSM data from the area $filterArea".toString())
    }
}


/**
 * This process extracts OSM data file and load it in a database using a place name
 *
 * @param datasource A connexion to a DB to load the OSM file
 * @param placeName The name of the place to extract
 * @param distance to expand the envelope of the query box. Default is 0
 *
 * @return The name of the tables that contains the geometry representation of the extracted area (outputZoneTable) and
 * its envelope extended or not by a distance (outputZoneEnvelopeTable)
 *
 * @author Erwan Bocher (CNRS LAB-STICC)
 * @author Elisabeth Le Saux (UBS LAB-STICC)
 */
Map fromPlace(JdbcDataSource datasource, String placeName, float distance = 0) throws Exception {
    if (!placeName) {
        throw new Exception("Cannot find an area from a void place name.")
    }
    if (!datasource) {
        throw new Exception("No datasource provided.")
    }
    def formatedPlaceName = placeName.trim().replaceAll("([\\s|,|\\-|])+", "_")
    def outputZoneTable = postfix "ZONE_$formatedPlaceName"
    def outputZoneEnvelopeTable = postfix "ZONE_ENVELOPE_$formatedPlaceName"
    def osmTablesPrefix = postfix "OSM_DATA_$formatedPlaceName"
    def epsg = DEFAULT_SRID

    Map nominatimRes = OSMTools.Utilities.getNominatimData(placeName);

    if (!nominatimRes) {
        throw new Exception("Cannot find an area from the place name $placeName".toString())
    }
    def geom = nominatimRes["geom"]
    if (!geom) {
        throw new Exception("Cannot find an area from the place name $placeName".toString())
    }
    if (distance < 0) {
        throw new Exception("Cannot use a negative distance")
    }
    def env = org.h2gis.utilities.GeographyUtilities.expandEnvelopeByMeters(geom.getEnvelopeInternal(), distance)

    //Create table to store the geometry and the envelope of the extracted area
    datasource.execute("""
                CREATE TABLE $outputZoneTable (the_geom GEOMETRY(POLYGON, $epsg), ID_ZONE VARCHAR);
                INSERT INTO $outputZoneTable VALUES (ST_GEOMFROMTEXT('${geom}', $epsg), '$placeName');
        """)

    def geometryFactory = new GeometryFactory()
    def geomEnv = geometryFactory.toGeometry(env)
    datasource.execute("""
                CREATE TABLE $outputZoneEnvelopeTable (the_geom GEOMETRY(POLYGON, $epsg), ID_ZONE VARCHAR);
                INSERT INTO $outputZoneEnvelopeTable VALUES (ST_GEOMFROMTEXT('$geomEnv',$epsg), '$placeName');
        """)

    def query = OSMTools.Utilities.buildOSMQuery(geomEnv, [], NODE, WAY, RELATION)
    String extract = extract(query)
    if (extract) {
        info "Downloading OSM data from the place $placeName"
        if (load(datasource, osmTablesPrefix, extract)) {
            info "Loading OSM data from the place $placeName"
            return [zone    : outputZoneTable,
                    envelope: outputZoneEnvelopeTable,
                    prefix  : osmTablesPrefix]
        } else {
            throw new Exception("Cannot load the OSM data from the place $placeName".toString())
        }

    } else {
        throw new Exception("Cannot download OSM data from the place $placeName".toString())
    }
}

/**
 * This process extracts OSM data as an XML file using the Overpass API
 *
 * @param overpassQuery The overpass api to be executed
 *
 * @author Erwan Bocher (CNRS LAB-STICC)
 * @author Elisabeth Le Saux (UBS LAB-STICC)
 */
String extract(String overpassQuery) throws Exception {
    info "Extract the OSM data"
    if (!overpassQuery) {
        throw new Exception("The query should not be null or empty.")
    }
    def bboxUrl = OSMTools.Utilities.utf8ToUrl(overpassQuery);
    //hash the query to cache it
    def queryHash = bboxUrl.digest('SHA-256')
    def outputOSMFile = new File(System.getProperty("java.io.tmpdir") + File.separator + "${queryHash}.osm")
    def osmFilePath = outputOSMFile.absolutePath
    if (outputOSMFile.exists()) {
        if (outputOSMFile.length() == 0) {
            outputOSMFile.delete()
            if (outputOSMFile.createNewFile()) {
                if (OSMTools.Utilities.executeOverPassQuery(overpassQuery, outputOSMFile)) {
                    info "The OSM file has been downloaded at ${osmFilePath}."
                } else {
                    outputOSMFile.delete()
                    throw new Exception("Cannot extract the OSM data for the query $overpassQuery")
                }
            }
        } else {
            debug "\nThe cached OSM file ${osmFilePath} will be re-used for the query :  \n$overpassQuery."
        }
    } else {
        if (outputOSMFile.createNewFile()) {
            if (OSMTools.Utilities.executeOverPassQuery(overpassQuery, outputOSMFile)) {
                info "The OSM file has been downloaded at ${osmFilePath}."
            } else {
                outputOSMFile.delete()
                throw new Exception("Cannot extract the OSM data for the query $overpassQuery")
            }
        }
    }
    return osmFilePath
}

/**
 * This process is used to load an OSM file in a database.
 *
 * @param datasource A connection to a database
 * @param osmTablesPrefix A prefix to identify the 10 OSM tables
 * @param omsFilePath The path where the OSM file is
 *
 * @return datasource The connection to the database
 *
 * @author Erwan Bocher (CNRS LAB-STICC)
 * @author Elisabeth Le Saux (UBS LAB-STICC)
 */
boolean load(JdbcDataSource datasource, String osmTablesPrefix, String osmFilePath) throws Exception {
    if (!datasource) {
        throw new Exception("Please set a valid database connection.")
    }

    if (!osmTablesPrefix ||
            !Pattern.compile('^[a-zA-Z0-9_]*$').matcher(osmTablesPrefix).matches()) {
        throw new Exception("Please set a valid table prefix.")
    }

    if (!osmFilePath) {
        throw new Exception("Please set a valid osm file path.")
    }
    def osmFile = new File(osmFilePath)
    if (!osmFile.exists()) {
        throw new Exception("The input OSM file does not exist.")
    }

    info "Load the OSM file in the database."
    if (datasource.load(osmFile, osmTablesPrefix, true)) {
        info "The input OSM file has been loaded in the database."
        //We must check if there is some data at least one tag
        if (datasource.getRowCount("${osmTablesPrefix}_node".toString()) == 0) {
            throw new Exception("The downloaded OSM file doesn't contain any data.\n Please check the file ${osmFile} to see what happens.".toString())
        }
        return true
    }
    return false

}
