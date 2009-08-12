Google App Engine Java Runtime SDK - GWT Guestbook Demo

The GWT Guestbook is a sample application demonstrating how to create and deploy
an application built with GWT on Google App Engine Java Runtime. The Guestbook
uses GWT RPC to store and retrieve guestbook entries and presents a simple UI
to display and enter new guest entries.

This sample application is intended for developers who would like to have a
reference implementation to quickly get started with GWT and the Google App
Engine Java Runtime SDK. For a tutorial on how to create a full-featured GWT
application and deploying it on the Google App Engine Java Runtime, check out
the StockWatcher tutorial on the GWT website at the link below:

http://code.google.com/webtoolkit/tutorials/1.6/index.html


SETUP INSTRUCTIONS

To be able to compile and run the GWT Guestbook sample application, you will
need to download the tools and libraries listed below in addition to the Google
App Engine Java Runtime SDK.

Apache Ant (1.7.1 or higher)  http://ant.apache.org/
--------------------------------------------------------
The Ant build tool is required to compile the GWT Guestbook sample application
as well as startup the Hosted Mode browser. Once downloaded and extracted, you 
should configure Ant as prescribed on the homepage (e.g. set an ANT_HOME
environment variable and add the Ant distribution binaries to the system path
variable.

GWT 1.6  http://code.google.com/webtoolkit/download.html
--------------------------------------------------------
The GWT libraries are required in order to resolve the usage of the GWT widgets
and RPC subsystem in the Guestbook sample application. See the Getting Started
guide on the GWT homepage for a better understanding on how to configure a GWT
application. For the Guestbook sample application, all you need to do is extract
the GWT distribution to your local drive and define the following properties in
the Guestbook build.properties file:

# Extracted GWT distribution location
gwt.home=<path_to_gwt_sdk>

# Platform-specific GWT Dev JAR
gwt.dev.jar=gwt-dev-<platform>.jar

# App Engine SDK home
appengine.sdk.home=<path_to_app_engine_sdk>

*NOTE: If you are using the Google Eclipse plug-in, you can skip downloading
GWT 1.6 since it should come bundled with the plug-in itself. You can also
create run configurations in the Eclipse plug-in to compile and run the
Guestbook application in hosted mode, so you can skip the configuration steps
for the build.properties file as well.


RUNNING IN HOSTED MODE

Once you're done with the setup instructions above, you can simply run the
command below while in the Guestbook project root directory:

ant hosted


COMPILING AND DEPLOYING

You can compile the Guestbook sample application by running the command below
while in the Guestbook project root directory:

ant

The GWT compiler will have generated a set of HTML and JavaScript files and
will also have copied over public resources like the Guestbook.css file to a
WAR style output directory. You can then upload your application following the
instructions described in the Google App Engine Java Runtime SDK documentation:

http://code.google.com/appengine/docs/java/gettingstarted/uploading.html
