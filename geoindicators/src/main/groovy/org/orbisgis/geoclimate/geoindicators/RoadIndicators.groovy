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

import groovy.transform.BaseScript
import org.locationtech.jts.geom.Geometry
import org.orbisgis.data.jdbc.JdbcDataSource
import org.orbisgis.geoclimate.Geoindicators

import java.sql.SQLException

@BaseScript Geoindicators geoindicators

/**
 * Generate traffic data according the type of roads and references available in
 * a traffic properties table
 * If any traffic properties table is set, the process uses a default one.
 * The parameters of the default table refer to the European Commission Working Group Assessment of Exposure to Noise.
 * See table Tool 4.5: No heavy vehicle data available
 *
 * For each  3 time periods, day (06:00-18:00), evening (ev) (18:00-22:00) and night (22:00-06:00),
 * estimated traffic variables are  : *
 * lv_hour = Number of Light Vehicles per hour (mean)
 * hv_hour = Number of Heavy Vehicles per hour (mean)
 * Number of hours when the lv_hour and hv_hour are evaluated
 * lv_speed = Mean speed of Light Vehicles
 * hv_speed = Mean speed of Heavy Vehicles
 * percent_lv = percentage of Light Vehicles per road types
 * percent_hv = percentage of Heavy Vehicles per road types
 *
 *
 * @param datasource A connexion to a DB to load the OSM file
 * @param roadThe road table that contains the WG_AEN types and maxspeed values
 * @param zone an envelope to reduce the study area
 * @param jsonFilename A traffic properties table stored in a SQL file
 *
 * @return The name of the road table
 */
String build_road_traffic(JdbcDataSource datasource, String road, String zone = "", String jsonFilename = "") throws Exception {
    debug('Create the default traffic data')
    def outputTableName = postfix "ROAD_TRAFFIC"
    datasource """
                DROP TABLE IF EXISTS $outputTableName;
                CREATE TABLE $outputTableName (THE_GEOM GEOMETRY, ID_ROAD SERIAL, ID_SOURCE VARCHAR, 
                ROAD_TYPE VARCHAR, SOURCE_ROAD_TYPE VARCHAR, SURFACE VARCHAR, DIRECTION INTEGER, SLOPE DOUBLE PRECISION,PAVEMENT VARCHAR,
                DAY_LV_HOUR INTEGER, DAY_HV_HOUR INTEGER, DAY_LV_SPEED INTEGER ,DAY_HV_SPEED INTEGER, 
                NIGHT_LV_HOUR INTEGER, NIGHT_HV_HOUR INTEGER, NIGHT_LV_SPEED INTEGER, NIGHT_HV_SPEED INTEGER, 
                EV_LV_HOUR INTEGER,EV_HV_HOUR INTEGER, EV_LV_SPEED INTEGER, EV_HV_SPEED INTEGER);
                """.toString()

    if (road) {
        def paramsDefaultFile = this.class.getResourceAsStream("roadTrafficParams.json")
        def traffic_flow_params = Geoindicators.DataUtils.parametersMapping(jsonFilename, paramsDefaultFile)
        if (!traffic_flow_params) {
            error "The road traffic flow parameters cannot be null"
            return
        }
        def flow_data = traffic_flow_params.flow_data
        def flow_period = traffic_flow_params.flow_periods
        def maxspeed = traffic_flow_params.maxspeed
        def pavements = traffic_flow_params.pavements
        def road_types = traffic_flow_params.road_types

        if (!flow_data) {
            throw new IllegalArgumentException("The traffic flow data cannot be null")
        }

        if (!flow_period) {
            throw new IllegalArgumentException("The traffic flow period cannot be null")
        }

        if (!maxspeed) {
            throw new IllegalArgumentException("The maxspeed values data cannot be null")
        }

        if (!pavements) {
            throw new IllegalArgumentException("The CNOSSOS road pavement identifier cannot be null")
        }

        if (!road_types) {
            throw new IllegalArgumentException("The road type cannot be null")
        }

        try {
            //Define the mapping between the values in OSM and those used in the abstract model
            def queryMapper = "SELECT "
            if (datasource.getRowCount(road) > 0) {
                def columnNames = datasource.getColumnNames(road)
                columnNames.remove("THE_GEOM")
                def flatListColumns = columnNames.inject([]) { result, iter ->
                    result += "a.\"$iter\""
                }.join(",")

                if (zone) {
                    datasource.createSpatialIndex(road, "the_geom")
                    queryMapper += "${flatListColumns}, CASE WHEN st_overlaps(st_force2D(a.the_geom), b.the_geom) " +
                            "THEN st_force2D(st_makevalid(st_intersection(st_force2D(a.the_geom), b.the_geom))) " +
                            "ELSE st_force2D(a.the_geom) " +
                            "END AS the_geom " +
                            "FROM " +
                            "$road AS a, $zone AS b " +
                            "WHERE " +
                            "a.the_geom && b.the_geom and a.type not in ('track', 'path', 'cycleway', 'steps') "
                } else {
                    queryMapper += "${flatListColumns}, st_force2D(a.the_geom) as the_geom FROM $road  as a where type not in ('track', 'path', 'cycleway', 'steps')"
                }
                datasource.withBatch(100) { stmt ->
                    datasource.eachRow(queryMapper) { row ->
                        //Find road type
                        def source_road_type = row."type"
                        def road_type = getTrafficRoadType(road_types, source_road_type)
                        //Set a default road
                        if (road_type) {
                            def maxspeed_value = row."maxspeed"
                            //Find best speed from road type
                            if (maxspeed_value == -1) {
                                maxspeed_value = maxspeed[road_type]
                            }
                            def direction = row."direction"
                            def surface = row."surface"
                            def pavement_value = getPavement(pavements, surface)
                            def traffic_data = getNumberVehiclesPerHour(road_type, direction, flow_data, flow_period)
                            Geometry geom = row.the_geom
                            int epsg = geom.getSRID()
                            if (geom) {
                                //Explode geometries
                                for (int i = 0; i < geom.getNumGeometries(); i++) {
                                    stmt.addBatch """insert into $outputTableName (THE_GEOM, ID_SOURCE, ROAD_TYPE, SOURCE_ROAD_TYPE,
                                        SURFACE, DIRECTION, SLOPE ,PAVEMENT,
                                        DAY_LV_HOUR, DAY_HV_HOUR , DAY_LV_SPEED  ,DAY_HV_SPEED , 
                                        NIGHT_LV_HOUR , NIGHT_HV_HOUR , NIGHT_LV_SPEED , NIGHT_HV_SPEED , 
                                        EV_LV_HOUR ,EV_HV_HOUR , EV_LV_SPEED , EV_HV_SPEED ) 
                                        values(ST_GEOMFROMTEXT('${geom.getGeometryN(i)}',$epsg), '${row.id_road}','${road_type}','${source_road_type}',
                                        '${surface}',${direction},null, '${pavement_value}',
                                        ${traffic_data.day_lv_hour},${traffic_data.day_hv_hour},${maxspeed_value},${maxspeed_value},
                                        ${traffic_data.night_lv_hour},${traffic_data.night_hv_hour},${maxspeed_value},${maxspeed_value},
                                        ${traffic_data.ev_lv_hour},${traffic_data.ev_hv_hour},${maxspeed_value},${maxspeed_value})""".toString()
                                }
                            }
                        }
                    }
                }
                datasource.execute("""COMMENT ON COLUMN ${outputTableName}."ROAD_TYPE" IS 'Default value road type';
            COMMENT ON COLUMN ${outputTableName}."DAY_LV_HOUR" IS 'Number of light vehicles per hour for day';
            COMMENT ON COLUMN ${outputTableName}."DAY_HV_HOUR" IS 'Number of heavy vehicles per hour for day';
            COMMENT ON COLUMN ${outputTableName}."DAY_LV_SPEED" IS 'Light vehicles speed for day';
            COMMENT ON COLUMN ${outputTableName}."DAY_HV_SPEED" IS 'Heavy vehicles speed for day';
            COMMENT ON COLUMN ${outputTableName}."NIGHT_LV_HOUR" IS 'Number of light vehicles per hour for night';
            COMMENT ON COLUMN ${outputTableName}."NIGHT_HV_HOUR" IS 'Number of heavy vehicles per hour for night';
            COMMENT ON COLUMN ${outputTableName}."NIGHT_LV_SPEED" IS 'Light vehicles speed for night';
            COMMENT ON COLUMN ${outputTableName}."NIGHT_HV_SPEED" IS 'Heavy vehicles speed for night';
            COMMENT ON COLUMN ${outputTableName}."EV_LV_HOUR" IS 'Number of light vehicles per hour for evening';
            COMMENT ON COLUMN ${outputTableName}."EV_HV_HOUR" IS 'Number of heavy vehicles per hour for evening';
            COMMENT ON COLUMN ${outputTableName}."EV_LV_SPEED" IS 'Light vehicles speed for evening';
            COMMENT ON COLUMN ${outputTableName}."EV_HV_SPEED" IS 'Number of heavy vehicles per hour for evening';
            COMMENT ON COLUMN ${outputTableName}."SLOPE" IS 'Slope (in %) of the road section.';
            COMMENT ON COLUMN ${outputTableName}."DIRECTION" IS 'Define the direction of the road section. 1 = one way road section and the traffic goes in the same way that the slope definition you have used, 2 = one way road section and the traffic goes in the inverse way that the slope definition you have used, 3 = bi-directional traffic flow, the flow is split into two components and correct half for uphill and half for downhill'""")
            }
        } catch (SQLException e) {
            throw new SQLException("Cannot compute the road traffic", e)
        }
    }
    return outputTableName
}
/**
 * Return a pavement identifier according the CNOSSOS-EU specification
 * @param pavements an array of pavement regarding the osm value
 * @param surface the surface value defined by OSM
 * @return a pavement identifier, default is NL05
 */
static String getPavement(def pavements, def surface) {
    String pvmt = "NL05"
    if (!surface) {
        return pvmt
    }
    return pavements.get(surface, pvmt)
}

/**
 * Compute the number of light and heavy vehicles per hour for the 3 time periods
 * day (06:00-18:00), evening (ev) (18:00-22:00) and night (22:00-06:00)
 *
 * @param type the road type according the table Tool 4.5: No heavy vehicle data available
 * @param direction Define the direction of the road section.
 * 1 = one way road section and the traffic goes in the same way that the slope definition you have used,
 * 2 = one way road section and the traffic goes in the inverse way that the slope definition you have used,
 * 3 = bi-directional traffic flow, the flow is split into two components and correct half for uphill and half for downhill
 * -1 = unknown road direction
 * @param flow_data static flow data values for a road type
 * @param flow_period the 3 time periods
 * @return a Map with the number of vehicules per hour stored in the variables
 *
 *  day_lv_hour :  'Number of light vehicles per hour for day'
 *  day_hv_hour : 'Number of heavy vehicles per hour for day'
 *  night_lv_hour : 'Number of light vehicles per hour for night'
 *  night_hv_hour : 'Number of heavy vehicles per hour for night'
 *  ev_lv_hour : 'Number of light vehicles per hour for evening'
 *  ev_hv_hour : 'Number of heavy vehicles per hour for evening'
 *
 */
static Map getNumberVehiclesPerHour(def type, int direction, def flow_data, def flow_period) {
    def flow_data_type = flow_data[type]
    def day_nb_hours = flow_period.day_nb_hours
    def ev_nb_hours = flow_period.ev_nb_hours
    def night_nb_hours = flow_period.night_nb_hours
    def day_lv_hour = 0, day_hv_hour = 0, night_lv_hour = 0, night_hv_hour = 0, ev_lv_hour = 0, ev_hv_hour = 0
    if (direction in [1, 2]) {
        day_lv_hour = (flow_data_type.day_nb_vh * flow_data_type.day_percent_lv / day_nb_hours) / 2
        day_hv_hour = (flow_data_type.day_nb_vh * flow_data_type.day_percent_hv / day_nb_hours) / 2
        night_lv_hour = (flow_data_type.night_nb_vh * flow_data_type.night_percent_lv / night_nb_hours) / 2
        night_hv_hour = (flow_data_type.night_nb_vh * flow_data_type.night_percent_hv / night_nb_hours) / 2
        ev_lv_hour = (flow_data_type.ev_nb_vh * flow_data_type.ev_percent_lv / ev_nb_hours) / 2
        ev_hv_hour = (flow_data_type.ev_nb_vh * flow_data_type.ev_percent_hv / ev_nb_hours) / 2
    } else if (direction == 3) {
        day_lv_hour = (flow_data_type.day_nb_vh * flow_data_type.day_percent_lv / day_nb_hours)
        day_hv_hour = (flow_data_type.day_nb_vh * flow_data_type.day_percent_hv / day_nb_hours)
        night_lv_hour = (flow_data_type.night_nb_vh * flow_data_type.night_percent_lv / night_nb_hours)
        night_hv_hour = (flow_data_type.night_nb_vh * flow_data_type.night_percent_hv / night_nb_hours)
        ev_lv_hour = (flow_data_type.ev_nb_vh * flow_data_type.ev_percent_lv / ev_nb_hours)
        ev_hv_hour = (flow_data_type.ev_nb_vh * flow_data_type.ev_percent_hv / ev_nb_hours)
    }

    return ["day_lv_hour"  : Math.round(day_lv_hour), "day_hv_hour": Math.round(day_hv_hour),
            "night_lv_hour": Math.round(night_lv_hour), "night_hv_hour": Math.round(night_hv_hour),
            "ev_lv_hour"   : Math.round(ev_lv_hour), "ev_hv_hour": Math.round(ev_hv_hour)]

}

/**
 * Find the road type from the table parameters
 * @param road_types a list of road type and osm key value associations
 * @param columnNames the name of the columns in the put table
 * @param row the row to process
 * @return
 */
static String getTrafficRoadType(def road_types, def road_type) {
    String type_key = null
    for (def type : road_types) {
        if (type.value.contains(road_type)) {
            return type.key
        }
    }
    return type_key
}