*****************************
* WS-Aggregation            *
* Installation Instructions *
*****************************
http://www.infosys.tuwien.ac.at/prototypes/WS-Aggregation/

(0) requirements
-----------------

* Java JDK version 1.6 or above
* Apache Maven version 2.2 or above
* Apache Ant version 1.8.1 or above
* Mozilla Firefox version 3.6 or above (for graphical user interface)
* Flash browser plugin (for graphical user interface)
* Optional: MySQL version 5.1 or above (alternatively, a standalone HSQLDB server is used by default)
* Operating System: tested with Linux (Debian/Ubuntu), Microsoft Windows (XP/Vista/7), Mac OS X

* Please be aware that WS-Aggregation relies on a number of third-party libraries, which
  are downloaded automatically by the Maven platform when you launch the application for 
  the first time. The libraries and Maven meta files (per default in the folder ~/.m2/repository) 
  may take up to 200MB of your disk space.

(1) quick start
----------------

start the following commands from the root directory of the project:

$ ant clean build

Run each of the following three commands in a separate terminal window:
$ ant registry
$ ant gateway
$ ant aggregator

Alternatively, you can start registry/gateway/aggregators together in one terminal window with a single command:
$ ant

Start the user interface (requires Flash with privileges to open network connections):
$ ant web
$ firefox http://localhost:8892/WS-Aggregation

(2) configuration
-----------------

 * Adapt the persistence (database connection) settings in 
   code/wsaggr-core/src/main/resources/META-INF/persistence.xml
   (Or: Create a local MySQL database 'wsaggr' with user 'root' 
   and empty password to use the default settings.)
 * [If necessary, adapt the configuration values in config.properties 
   (Per default, all service hosts are set to 'localhost')]

After changing any configuration values, all services need to be terminated and restarted.

(3) start the registry
----------------------

$ ant registry

After some seconds the WSDL should be available at http://localhost:8890/registry?wsdl

(4) start the gateway
---------------------

$ ant gateway

After some seconds the WSDL should be available at http://localhost:8891/gateway?wsdl

(5) start some aggregator services
----------------------------------

Run in separate terminal sessions:
$ ant aggregator 
$ ant aggregator 
$ ant aggregator 

This should start up three aggregator nodes after some seconds:
http://localhost:9701/aggregator
http://localhost:9702/aggregator
http://localhost:9703/aggregator


(6) deploy and start the GUI
----------------------------

We provide an embedded Jetty server, which you can start with:
$ ant web

Then, open the Web GUI http://localhost:8892/WS-Aggregation in your browser.
You may also want to copy the 'web' folder to your Web container (e.g., Apache).

