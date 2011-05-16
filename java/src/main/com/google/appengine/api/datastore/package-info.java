// Copyright 2009 Google Inc.  All rights reserved.

/**
 * The datastore provides persistent storage for App Engine applications, used
 * either directly or via the provided JDO or JPA interfaces.  It provides
 * redundant storage for fault-tolerance.  More information is available in the
 * <a href="http://code.google.com/appengine/docs/java/datastore/">on-line
 * documentation</a>.
 * <p>
 * This package contains a low-level API to the datastore that is intended
 * primarily for framework authors.  Applications authors should consider using
 * either the provided JDO or JPA interfaces to the datastore.
 *
 * If using the datastore API directly, a common pattern of usage is:
 *
 * <pre>
 * // Get a handle on the datastore itself
 * DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
 *
 * // Lookup data by known key name
 * Entity userEntity = datastore.get(KeyFactory.createKey("UserInfo", email));
 *
 * // Or perform a query
 * Query query = new Query("Task");
 * query.addFilter("dueDate", Query.FilterOperator.LESS_THAN, today);
 * for (Entity taskEntity : datastore.prepare(query).asIterable()) {
 *   if ("done".equals(taskEntity.getProperty("status"))) {
 *     datastore.delete(taskEntity);
 *   } else {
 *     taskEntity.setProperty("status", "overdue");
 *     datastore.put(taskEntity);
 *   }
 * }
 * </pre>
 *
 * This illustrates several basic points:
 * <ul>
 * <li> The actual datastore itself is accessed through a
 *      {@link DatastoreService} object, produced from a
 *      {@link DatastoreServiceFactory}.
 * <li> The unit of storage is the {@link Entity} object, which are of named
 *      kinds ("UserInfo" and "Task" above).
 * <li> Entities have a {@link Key} value, which can be created by a {@link
 *      KeyFactory} to retrieve a specific known entity.  If the key is not
 *      readily determined, then {@link Query} objects can be used to retrieve
 *      one Entity, multiple as a list, {@link Iterable}, or {@link
 *      java.util.Iterator}, or to retrive the count of matching entities.
 * <li> Entities have named properties, the values of which may be basic types
 *      or collections of basic types.  Richer objects, of course, may be
 *      stored if serialized as byte arrays, although that may prevent
 *      effective querying by those properties.
 * <li> Entities may be associated in a tree structure; the {@link Query} in
 *      the snippet above searches only for Task entities associated with a
 *      specific UserInfo entity, and then filters those for Tasks due before
 *      today.
 * </ul>
 *
 * <p>
 *
 * In production, non-trivial queries cannot be performed until one or more
 * indexes have been built to ensure that the individual queries can be
 * processed efficiently.  You can specify the set of indexes your application
 * requires in a {@code WEB-INF/datastore-indexes.xml} file, or they can be
 * generated automatically as you test your application in the Development
 * Server.  If a query requires an index that cannot be found, a
 * {@link DatastoreNeedIndexException} will be thrown at runtime.
 *
 * <p>
 *
 * Although Google App Engine allows many versions of your application to be
 * accessible, there is only one datastore for your application, shared by
 * all versions.  Similarly, the set of indexes is shared by all application
 * versions.
 */
package com.google.appengine.api.datastore;

