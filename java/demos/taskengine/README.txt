Google App Engine Java Runtime SDK - TaskEngine Demo


A mobile Time management/Task list app, targeted specifically for iPhone and 
Android browsers.

The front end is an exercise in writing a light weight mobile app with some 
tricks to minimize code size and round trips (DOM programming, resource 
bundling, and style injection).

This app uses GWT RPC, authentication and user accounts using UserService, and 
persistence using JDO.

You can also checkout more recent builds from Task Engine's Google Code site at:
http://code.google.com/p/taskengine


How to build Task Engine:

1. Install Apache ant.
2. Download Java Appengine SDK.
3. Download GWT 1.6.
4. Download a build of GWT Incubator (gwt-incubator.jar).
5. Add the correct path information for the Appengine SDK, GWT 1.6, and 
   Incubator to 'taskengine/build.xml'. The top of build.xml should
   clearly indicate where you need to edit. 
6. Drop to the command line, and in the taskengine directory type:
   'ant'.


To run TaskEngine locally, type:
'ant runserver'
