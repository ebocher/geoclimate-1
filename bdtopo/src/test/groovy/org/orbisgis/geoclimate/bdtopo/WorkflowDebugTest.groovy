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
package org.orbisgis.geoclimate.bdtopo

import groovy.json.JsonSlurper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Tests for debug purpose
 */
class WorkflowDebugTest {


    @Disabled
    @Test
    void runFromFile() {
        BDTopo.v2('/../bdtopo_demo_test.json')
    }

    @Disabled
    //Use it for integration test with a postgis database
    @Test
    void testIntegrationPostGIS() {
        def db_config = new File("/tmp/postgis_config.json")
        if (db_config.exists()) {
            String directory = "/tmp/geoclimate/"
            def jsonSlurper = new JsonSlurper()
            def postgis_b = jsonSlurper.parse(new File("/tmp/postgis_config.json"))
            File dirFile = new File(directory)
            dirFile.delete()
            dirFile.mkdir()
            def user = postgis_b.user
            def password = postgis_b.password
            def url = postgis_b.url
            def locations = ["54395"]
            def local_database_name = "geoclimate_test_integration;AUTO_SERVER=TRUE"

            /*================================================================================
            * Input database and tables
            */
            def input = [
                    "locations": locations,
                    "database" : [
                            "user"    : user,
                            "password": password,
                            "url"     : url,
                            "tables"  : ["commune"                : "ign_bdtopo_2018.commune",
                                         "bati_indifferencie"     : "ign_bdtopo_2018.bati_indifferencie",
                                         "bati_industriel"        : "ign_bdtopo_2018.bati_industriel",
                                         "bati_remarquable"       : "ign_bdtopo_2018.bati_remarquable",
                                         "route"                  : "ign_bdtopo_2018.route",
                                         "troncon_voie_ferree"    : "ign_bdtopo_2018.troncon_voie_ferree",
                                         "surface_eau"            : "ign_bdtopo_2018.surface_eau",
                                         "zone_vegetation"        : "ign_bdtopo_2018.zone_vegetation",
                                         "terrain_sport"          : "ign_bdtopo_2018.terrain_sport",
                                         "construction_surfacique": "ign_bdtopo_2018.construction_surfacique",
                                         "surface_route"          : "ign_bdtopo_2018.surface_route",
                                         "surface_activite"       : "ign_bdtopo_2018.surface_activite",
                                         "piste_aerodrome"        : "ign_bdtopo_2018.piste_aerodrome"]
                    ]]


            /*================================================================================
            * output tables in the database
            */
            def output = [
                    "database": [
                            "user"    : user,
                            "password": password,
                            "url"     : url,
                            "tables"  : [
                                    "building_indicators": "building_indicators_2154",
                                    "block_indicators"   : "block_indicators_2154",
                                    "rsu_indicators"     : "rsu_indicators_2154",
                                    "rsu_lcz"            : "rsu_lcz_2154",
                                    "zone"               : "zone_2154",
                                    "building_utrf"      : "building_utrf_2154",
                                    "rsu_utrf_area"      : "rsu_utrf_area_2154",
                                    "rsu_utrf_floor_area": "rsu_utrf_floor_area_2154",
                                    "grid_indicators"    : "grid_indicators_2154",
                                    "road_traffic"       : "road_traffic_2154"]
                    ]
            ]


            /*================================================================================
            * WORKFLOW PARAMETERS
            */
            def workflow_parameters = [
                    "description" : "Run the Geoclimate chain with BDTopo data imported and exported to a POSTGIS database",
                    "geoclimatedb": [
                            "folder": directory,
                            "name"  : "${local_database_name};AUTO_SERVER=TRUE",
                            "delete": true
                    ],
                    "input"       : input,
                    "output"      : output,
                    "parameters"  : ["distance"      : 100,
                                     "rsu_indicators": [
                                             "indicatorUse": ["LCZ", "UTRF", "TEB"]
                                     ],
                                     /*"grid_indicators": [
                                             "x_size": 100,
                                             "y_size": 100,
                                             "indicators": ["BUILDING_FRACTION","BUILDING_HEIGHT", "BUILDING_TYPE_FRACTION","WATER_FRACTION","VEGETATION_FRACTION",
                                                            "ROAD_FRACTION", "IMPERVIOUS_FRACTION", "UTRF_AREA_FRACTION", "LCZ_FRACTION"]
                                     ],*/
                                     "road_traffic"  : true
                    ]
            ]
            BDTopo.v2(workflow_parameters)

        } else {
            println("The configuration file for the input database doesn't exist")
        }
    }

    @Disabled
    @Test
    void testIntegrationFolderInput() {
        def input_data = "/home/bernardj/Data/BDT/V3/BDTOPO_3-3_TOUSTHEMES_SHP_LAMB93_R11_2024-03-15"
        def locations = [
                         [6864452.136340265, 651978.2850116278, 6865452.136340265, 652978.2850116278]]
        String directory = "/tmp/bdtopo3"
        File dirFile = new File(directory)
        dirFile.delete()
        dirFile.mkdir()
        def bdTopoParameters = [
                "description" : "Example of configuration file to run the BDTopo workflow and store the results in a folder",
                "geoclimatedb": [
                        "folder": dirFile.absolutePath,
                        "name"  : "bdtopo_workflow_db",
                        "delete": true
                ],
                "input"       : [
                        "folder"   : input_data,
                        "locations": locations,
                        "srid"     : 2154],
                "output"      : [
                        "folder": ["path": directory]],
                "parameters"  :
                        ["distance"    : 0,
                         rsu_indicators: [
                                 "indicatorUse": ["LCZ", "UTRF", "TEB"]

                         ],
                         /*"grid_indicators": [
                                 "x_size"    : 100,
                                 "y_size"    : 100,
                                 "indicators" :["FREE_EXTERNAL_FACADE_DENSITY", "BUILDING_FRACTION"]
                                 //, "lcz_lod":2
                         ]*/
                        ]
        ]
        //BDTopo.v2(bdTopoParameters)
        /*
        input_data = "/home/ebocher/Téléchargements/BDTOPO_3-3_TOUSTHEMES_SHP_LAMB93_D033_2023-12-15/BDTOPO/1_DONNEES_LIVRAISON_2023-12-00099"

        directory = "/tmp/bdtopo3"
        dirFile = new File(directory)
        dirFile.delete()
        dirFile.mkdir()

        bdTopoParameters.input.folder=input_data
        bdTopoParameters.output.folder.path=directory*/
        BDTopo.v3(bdTopoParameters)

    }


}
