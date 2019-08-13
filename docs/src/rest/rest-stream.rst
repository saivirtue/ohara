..
.. Copyright 2019 is-land
..
.. Licensed under the Apache License, Version 2.0 (the "License");
.. you may not use this file except in compliance with the License.
.. You may obtain a copy of the License at
..
..     http://www.apache.org/licenses/LICENSE-2.0
..
.. Unless required by applicable law or agreed to in writing, software
.. distributed under the License is distributed on an "AS IS" BASIS,
.. WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
.. See the License for the specific language governing permissions and
.. limitations under the License.
..

.. _rest-stream:

StreamApp
=========

Ohara StreamApp is a unparalleled wrap of kafka streaming. It leverages
and enhances `Kafka Streams`_ to make
developer easily design and implement the streaming application. More
details of developing streaming application is in :ref:`custom stream guideline <streamapp>`.

Assume that you have completed a streaming application via ohara Java
APIs, and you have generated a jar including your streaming code. By
Ohara Restful APIs, you are enable to control, deploy, and monitor
your streaming application. As with cluster APIs, ohara leverages
docker container to host streaming application. Of course, you can
apply your favor container management tool including simple (based on ssh)
and k8s when you are starting ohara.

Before stating to use restful APIs, please ensure that all nodes have
downloaded the `StreamApp image`_.
The jar you uploaded to run streaming application will be included in
the image and then executes as a docker container. The `StreamApp image`_
is kept in each node so don’t worry about the network. We all hate
re-download everything when running services.

The following information of StreamApp are updated by ohara.

.. _rest-streamapp-stored-data:

streamApp stored data
~~~~~~~~~~~~~~~~~~~~~

#. settings (**object**) — custom settings. Apart from the following fields, you can add any setting if needed. Each
   setting should have it's own definition to be used in streamApp runtime.
#. definition (**Option(object)**) — definition for current streamApp. If there was no **jarKey** defined, this
   field will be disappeared. See :ref:`StreamApp Setting Definition <streamapp-setting-definitions>` for more details.

   - className (**string**) — this streamApp entry class name.
   - definitions (**array(object)**) — the detail definition of each setting.

     - definitions[j].displayName (**string**) — the readable name of this setting
     - definitions[j].group (**string**) — the group of this setting (all core setting are in core group)

       - definitions[j].orderInGroup (**int**) — the order in group
       - definitions[j].editable (**boolean**) — true if this setting is modifiable
       - definitions[j].key (**string**) — the key of setting
       - definitions[j].[valueType](#setting-type) (**string**) — the type of value
       - definitions[j].defaultValue (**string**) — the default value
       - definitions[j].documentation (**string**) — the explanation of this definition
       - definitions[j].[reference](#setting-reference) (**string**) — works for ohara manager. It represents the reference of value.
       - definitions[j].required(**boolean**) — true if this setting has no default value and you have to assign a value.
       - definitions[j].internal (**string**) — true if this setting is assigned by system automatically.
       - definitions[j].tableKeys (**array(string)**) — the column name when the type is TABLE

#. nodeNames (**array(string)**) — node list of streamApp running container
#. deadNodes (**array(string)**) — dead node list of the exited containers from this cluster
#. state (**option(string)**) — only started/failed streamApp has state (DEAD if all containers are not running, else RUNNING)
#. error (**option(string)**) — the error message from a failed streamApp.
   If the streamApp is fine or un-started, you won't get this field.
#. :ref:`metrics <connector-metrics>` (**object**) — the metrics from this streamApp.

   - meters (**array(object)**) — the metrics in meter type

     - meters[i].value (**double**) — the number stored in meter
     - meters[i].unit (**string**) — unit for value
     - meters[i].document (**string**) — document of this meter
     - meters[i].queryTime (**long**) — the time of query metrics from remote machine
     - meters[i].startTime (**option(long)**) — the time of record generated in remote machine

#. lastModified (**long**) — last modified this jar time

create properties of specific streamApp
---------------------------------------

Create the properties of a streamApp.

*POST /v0/stream*

Example Request
  #. name (**string**) — new streamApp name. This is the object unique name ; default is random string.

     - The acceptable char is [0-9a-z]
     - The maximum length is 20 chars

  #. group (**string**) — group name for current streamApp. It uses default value for current version.
  #. imageName (**string**) — image name of streamApp used to ; default is official streamapp image of current version
  #. nodeNames (**array(string)**) — node name list of streamApp used to ; default is empty
  #. tags (**object**) — a key-value map of user defined data ; default is empty
  #. jarKey (**option(object)**) — the used jar key

     - group (**string**) — the group name of this jar
     - name (**string**) — the name without extension of this jar

  #. jmxPort (**int**) — expose port for jmx ; default is random port
  #. from (**array(string)**) — source topic ; default is empty array
      .. note::
       we only support one topic for current version. We will throw exception in start api if you assign
       more than 1 topic.
  #. to (**array(string)**) — target topic ; default is empty array
      .. note::
       we only support one topic for current version. We will throw exception in start api if you assign
       more than 1 topic.
  #. instances (**int**) — number of running streamApp ; default is 1

     The above fields are pre-defined and could use in request body for convenient. The option fields will have no default value,
     but others will auto fill default value as we describe above. The minimum required request fields is empty:

     .. code-block:: json

        {
        }

Examples of create streamApp properties:

  .. code-block:: json

     {
       "name": "myapp",
       "jarKey": {
         "group": "wk01",
         "name": "stream-app.jar"
       },
       "from": ["topic1"],
       "to": ["topic2"],
       "jmxPort": 5678,
       "instances": 3
     }

Example Response
  Response format is as :ref:`streamApp stored format <rest-streamapp-stored-data>`.

All default value response
**************************

  .. code-block:: json

    {
      "lastModified": 1563462747977,
      "deadNodes": [],
      "metrics": {
        "meters": []
      },
      "nodeNames": [],
      "settings": {
        "name": "db810cd561044c10ac21",
        "group": "default",
        "tags": {},
        "from": [],
        "to": [],
        "instances": 1,
        "imageName": "oharastream/streamapp:0.7.0-SNAPSHOT",
        "jmxPort": 3383,
        "nodeNames": []
      }
    }

All default value response with only supply jarKey field
********************************************************

The following request will generate definition for you:

  .. code-block:: json

    {
      "jarKey": {
        "group": "default",
        "name": "name.jar"
      }
    }

And the response:

  .. code-block:: json

    {
      "lastModified": 1563499550267,
      "deadNodes": [],
      "definition": {
        "className": "com.island.ohara.it.streamapp.DumbStreamApp",
        "definitions": [
          {
            "reference": "NONE",
            "displayName": "Author",
            "internal": false,
            "documentation": "Author of streamApp",
            "valueType": "STRING",
            "tableKeys": [],
            "orderInGroup": -1,
            "key": "author",
            "required": false,
            "defaultValue": "unknown",
            "group": "core",
            "editable": true
          },
          {
            "reference": "NONE",
            "displayName": "Enable Exactly Once",
            "internal": false,
            "documentation": "Enable this streamApp to process each record exactly once",
            "valueType": "BOOLEAN",
            "tableKeys": [],
            "orderInGroup": -1,
            "key": "exactlyOnce",
            "required": false,
            "defaultValue": "false",
            "group": "core",
            "editable": true
          },
          {
            "reference": "NONE",
            "displayName": "Topic of Consuming from",
            "internal": false,
            "documentation": "The topic name of this streamApp should consume from",
            "valueType": "STRING",
            "tableKeys": [],
            "orderInGroup": -1,
            "key": "from",
            "required": true,
            "defaultValue": null,
            "group": "core",
            "editable": true
          },
          {
            "reference": "NONE",
            "displayName": "Instances",
            "internal": false,
            "documentation": "The running container number of this streamApp",
            "valueType": "INT",
            "tableKeys": [],
            "orderInGroup": -1,
            "key": "instances",
            "required": true,
            "defaultValue": null,
            "group": "core",
            "editable": true
          },
          {
            "reference": "NONE",
            "displayName": "Application Name",
            "internal": false,
            "documentation": "The unique name of this streamApp",
            "valueType": "STRING",
            "tableKeys": [],
            "orderInGroup": -1,
            "key": "name",
            "required": true,
            "defaultValue": null,
            "group": "core",
            "editable": true
          },
          {
            "reference": "NONE",
            "displayName": "Revision",
            "internal": false,
            "documentation": "Revision of streamApp",
            "valueType": "STRING",
            "tableKeys": [],
            "orderInGroup": -1,
            "key": "revision",
            "required": false,
            "defaultValue": "unknown",
            "group": "core",
            "editable": true
          },
          {
            "reference": "NONE",
            "displayName": "Broker List",
            "internal": false,
            "documentation": "The broker list of current workspace",
            "valueType": "ARRAY",
            "tableKeys": [],
            "orderInGroup": -1,
            "key": "servers",
            "required": true,
            "defaultValue": null,
            "group": "core",
            "editable": false
          },
          {
            "reference": "NONE",
            "displayName": "Topic of Producing to",
            "internal": false,
            "documentation": "The topic name of this streamApp should produce to",
            "valueType": "STRING",
            "tableKeys": [],
            "orderInGroup": -1,
            "key": "to",
            "required": true,
            "defaultValue": null,
            "group": "core",
            "editable": true
          },
          {
            "reference": "NONE",
            "displayName": "Version",
            "internal": false,
            "documentation": "Version of streamApp",
            "valueType": "STRING",
            "tableKeys": [],
            "orderInGroup": -1,
            "key": "version",
            "required": false,
            "defaultValue": "unknown",
            "group": "core",
            "editable": true
          }
        ]
      },
      "metrics": {
        "meters": []
      },
      "nodeNames": [],
      "settings": {
        "name": "a5eddb5b9fd144f1a75e",
        "group": "default",
        "tags": {},
        "instances": 1,
        "imageName": "oharastream/streamapp:0.7.0-SNAPSHOT",
        "from": [],
        "to": [],
        "jarKey": {
          "group": "wk01",
          "name": "ohara-streamapp.jar"
        },
        "jmxPort": 3792,
        "nodeNames": []
      }
    }

  .. note::
     The streamApp, which is just created, does not have any metrics.


.. _rest-stream-get-information:

get information from a specific streamApp cluster
-------------------------------------------------

*GET /v0/stream/${name}*

Example Response
  Response format is as :ref:`streamApp stored format <rest-streamapp-stored-data>`.

  .. code-block:: json

     {
       "name": "myapp",
       "group": "default",
       "imageName": "oharastream/streamapp:$|version|",
       "instances": 3,
       "jar": {
         "name": "stream-app",
         "group": "wk01"
       },
       "from": ["topic1"],
       "to": ["topic2"],
       "jmxPort": 5678,
       "exactlyOnce": "false",
       "metrics": [],
       "lastModified": 1563499550267,
       "deadNodes": [],
       "definition": {
         "className": "com.island.ohara.it.streamapp.DumbStreamApp",
         "definitions": []
       },
       "metrics": {
         "meters": []
       },
       "nodeNames": [],
       "settings": {
         "name": "a5eddb5b9fd144f1a75e",
         "group": "default",
         "tags": {},
         "instances": 1,
         "imageName": "oharastream/streamapp:$|version|",
         "jarKey": {
           "group": "wk01",
           "name": "ohara-streamapp.jar"
         },
         "jmxPort": 3792,
         "nodeNames": []
       }
     }

list information of streamApp cluster
-------------------------------------

*GET /v0/stream*

Example Response
  Response format is as :ref:`streamApp stored format <rest-streamapp-stored-data>`.

  .. code-block:: json

     [
       {
         "name": "myapp",
         "group": "default",
         "imageName": "oharastream/streamapp:$|version|",
         "instances": 3,
         "jar": {
           "name": "stream-app",
           "group": "wk01"
         },
         "from": ["topic1"],
         "to": ["topic2"],
         "jmxPort": 5678,
         "exactlyOnce": "false",
         "metrics": [],
         "lastModified": 1563499550267,
         "deadNodes": [],
         "definition": {
           "className": "com.island.ohara.it.streamapp.DumbStreamApp",
           "definitions": []
         },
         "metrics": {
           "meters": []
         },
         "nodeNames": [],
         "settings": {
           "name": "a5eddb5b9fd144f1a75e",
           "group": "default",
           "tags": {},
           "instances": 1,
           "imageName": "oharastream/streamapp:$|version|",
           "jarKey": {
             "group": "wk01",
             "name": "ohara-streamapp.jar"
           },
           "jmxPort": 3792,
           "nodeNames": []
         }
       }
     ]

update properties of specific streamApp
---------------------------------------

Update the properties of a non-started streamApp.

*PUT /v0/stream/${name}*

Example Request
  #. group (**string**) — group name for current streamApp. Update this field has no effect.
  #. imageName (**string**) — image name of streamApp used to.
  #. nodeNames (**array(string)**) — node name list of streamApp used to.
  #. tags (**object**) — a key-value map of user defined data.
  #. jarKey (**option(object)**) — the used jar key

     - group (**string**) — the group name of this jar
     - name (**string**) — the name without extension of this jar

  #. jmxPort (**int**) — expose port for jmx.
  #. from (**array(string)**) — source topic.
      .. note::
       we only support one topic for current version. We will throw exception in start api if you assign
       more than 1 topic.
  #. to (**array(string)**) — target topic.
      .. note::
       we only support one topic for current version. We will throw exception in start api if you assign
       more than 1 topic.
  #. instances (**int**) — number of running streamApp.

  .. code-block:: json

     {
       "imageName": "myimage",
       "from": ["newTopic1"],
       "to": ["newTopic2"],
       "jarKey": {
         "group": "newGroup",
         "name": "newJar.jar"
       },
       "jmxPort": 8888,
       "instances": 3,
       "nodeNames": ["node1", "node2"]
     }

Example Response
  Response format is as :ref:`streamApp stored format <rest-streamapp-stored-data>`.

  .. code-block:: json

     {
        "lastModified": 1563503358666,
        "deadNodes": [],
        "definition": {
          "className": "com.island.ohara.it.streamapp.DumbStreamApp",
          "definitions": []
        },
        "metrics": {
          "meters": []
        },
        "nodeNames": [
          "node1", "node2"
        ],
        "settings": {
          "name": "myapp",
          "group": "default",
          "tags": {},
          "instances": 3,
          "imageName": "myimage",
          "jarKey": {
              "group": "newGroup",
              "name": "newJar.jar"
          },
          "to": ["newTopic2"],
          "from": ["newTopic1"],
          "jmxPort": 8888,
          "nodeNames": ["node1", "node2"]
        }
     }


delete properties of specific streamApp
---------------------------------------

Delete the properties of a non-started streamApp. This api only remove
the streamApp component which is stored in pipeline.

*DELETE /v0/stream/${name}*

**Example Response**

  ::

     204 NoContent

  .. note::
     It is ok to delete an nonexistent properties, and the response is 204
     NoContent.


start a StreamApp
-----------------

*PUT /v0/stream/${name}/start*

Example Response
  ::

    202 Accepted

  .. note::
     You should use :ref:`get streamapp <rest-stream-get-information>` to fetch up-to-date status

  .. code-block:: json

   {
     "lastModified": 1563499550267,
     "deadNodes": [],
     "definition": {
       "className": "com.island.ohara.it.streamapp.DumbStreamApp",
       "definitions": []
     },
     "metrics": {
       "meters": []
     },
     "nodeNames": [],
     "settings": {
       "name": "a5eddb5b9fd144f1a75e",
       "group": "default",
       "tags": {},
       "instances": 1,
       "imageName": "oharastream/streamapp:$|version|",
       "jarKey": {
         "group": "wk01",
         "name": "ohara-streamapp.jar"
       },
       "jmxPort": 3792,
       "nodeNames": []
     }
   }

.. _rest-stop-streamapp:

stop a StreamApp
----------------

This action will graceful stop and remove all docker containers belong
to this streamApp. Note: successful stop streamApp will have no status.

*PUT /v0/stream/${name}/stop*

Example Response
  ::

    202 Accepted

  .. note::

     You should use :ref:`get streamapp <rest-stream-get-information>` to fetch up-to-date status


  .. code-block:: json

     {
       "lastModified": 1563499550267,
       "deadNodes": [],
       "definition": {
         "className": "com.island.ohara.it.streamapp.DumbStreamApp",
         "definitions": []
       },
       "metrics": {
         "meters": []
       },
       "nodeNames": [],
       "settings": {
         "name": "a5eddb5b9fd144f1a75e",
         "group": "default",
         "tags": {},
         "instances": 1,
         "imageName": "oharastream/streamapp:$|version|",
         "jarKey": {
           "group": "wk01",
           "name": "ohara-streamapp.jar"
         },
         "jmxPort": 3792,
         "nodeNames": []
       }
     }

get topology tree graph from specific streamApp
-----------------------------------------------

[TODO] This is not implemented yet !

*GET /v0/stream/view/${name}*

Example Response
  #. jarInfo (**string**) — the upload jar information
  #. name (**string**) — the streamApp name
  #. poneglyph (**object**) — the streamApp topology tree graph

      - steles (**array(object)**) — the topology collection

         - steles[i].kind (**string**) — this component kind (SOURCE,
           PROCESSOR, or SINK)
         - steles[i].key (**string**) — this component kind with order
         - steles[i].name (**string**) — depend on kind, the name is

            - SOURCE — source topic name
            - PROCESSOR — the function name
            - SINK — target topic name

         - steles[i].from (**string**) — the prior component key (could be
           empty if this is the first component)
         - steles[i].to (**string**) — the posterior component key (could be
           empty if this is the final component)

  .. code-block:: json

     {
       "jarInfo": {
         "name": "stream-app",
         "group": "wk01",
         "size": 1234,
         "lastModified": 1542102595892
       },
       "name": "my-app",
       "poneglyph": {
         "steles": [
           {
             "kind": "SOURCE",
             "key" : "SOURCE-0",
             "name": "stream-in",
             "from": "",
             "to": "PROCESSOR-1"
           },
           {
             "kind": "PROCESSOR",
             "key" : "PROCESSOR-1",
             "name": "filter",
             "from": "SOURCE-0",
             "to": "PROCESSOR-2"
           },
           {
             "kind": "PROCESSOR",
             "key" : "PROCESSOR-2",
             "name": "mapvalues",
             "from": "PROCESSOR-1",
             "to": "SINK-3"
           },
           {
             "kind": "SINK",
             "key" : "SINK-3",
             "name": "stream-out",
             "from": "PROCESSOR-2",
             "to": ""
           }
         ]
       }
     }

.. _Kafka Streams: kafka streams <https://kafka.apache.org/documentation/streams
.. _StreamApp image: https://cloud.docker.com/u/oharastream/repository/docker/oharastream/streamapp