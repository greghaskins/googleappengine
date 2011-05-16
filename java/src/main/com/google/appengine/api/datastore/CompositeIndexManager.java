// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import com.google.apphosting.api.DatastorePb;
import com.google.apphosting.api.DatastorePb.Query.Filter;
import com.google.apphosting.api.DatastorePb.Query.Order;
import com.google.storage.onestore.v3.OnestoreEntity.Index;
import com.google.storage.onestore.v3.OnestoreEntity.Index.Property;
import com.google.storage.onestore.v3.OnestoreEntity.Index.Property.Direction;

import java.util.List;

/**
 * Composite index management operations needed by the datastore api.
 *
 */
public class CompositeIndexManager {

  /**
   * The format of a datastore-index xml element when it has properties.
   */
  private static final String DATASTORE_INDEX_WITH_PROPERTIES_XML_FORMAT =
      "    <datastore-index kind=\"%s\" ancestor=\"%s\" source=\"%s\">\n%s"
    + "    </datastore-index>\n\n";

  /**
   * The format of a datastore-index xml element when it does not have
   * properties.
   */
  private static final String DATASTORE_INDEX_NO_PROPERTIES_XML_FORMAT =
      "    <datastore-index kind=\"%s\" ancestor=\"%s\" source=\"%s\"/>\n\n";

  /**
   * The format of a property xml element.
   */
  private static final String PROPERTY_XML_FORMAT =
      "        <property name=\"%s\" direction=\"%s\"/>\n";

  /**
   * Apologies for the lowercase literals, but the whole point of these enums
   * is to represent constants in an xml document, and it's silly to have
   * the code literals not match the xml literals - you end up with a bunch
   * of case conversion just support the java naming conversion.
   */

  /**
   * The source of an index in the index file.  These are used as literals
   * in an xml document that we read and write.
   */
  protected enum IndexSource { auto, manual }

  /**
   * Generate an xml representation of the provided {@link Index}.
   *
   * <datastore-indexes autoGenerate="true">
   *     <datastore-index kind="a" ancestor="false">
   *         <property name="yam" direction="asc"/>
   *         <property name="not yam" direction="desc"/>
   *     </datastore-index>
   * </datastore-indexes>
   *
   * @param index The index for which we want an xml representation.
   * @param source The source of the provided index.
   * @return The xml representation of the provided index.
   */
  protected String generateXmlForIndex(Index index, IndexSource source) {
    boolean isAncestor = index.isAncestor();
    if (index.propertySize() == 0) {
      return String.format(
          DATASTORE_INDEX_NO_PROPERTIES_XML_FORMAT,
          index.getEntityType(), isAncestor, source);
    }
    StringBuilder sb = new StringBuilder();
    for (Property prop : index.propertys()) {
      String dir = prop.getDirectionEnum() == Direction.ASCENDING ? "asc" : "desc";
      sb.append(String.format(PROPERTY_XML_FORMAT, prop.getName(), dir));
    }
    return String.format(
        DATASTORE_INDEX_WITH_PROPERTIES_XML_FORMAT,
        index.getEntityType(), isAncestor, source, sb.toString());
  }

  /**
   * Given a {@link DatastorePb.Query}, return the {@link Index}
   * needed to fulfill the query, or {@code null} if no index is needed.
   *
   * This code needs to remain in sync with its counterparts in other
   * languages.  If you modify this code please make sure you make the
   * same update in the local datastore for other languages.
   *
   * @param indexOnlyQuery The query.
   * @return The index that must be present in order to fulfill the query, or
   * {@code null} if no index is needed.
   */
  protected Index compositeIndexForQuery(final IndexComponentsOnlyQuery indexOnlyQuery) {
    DatastorePb.Query query = indexOnlyQuery.getQuery();

    boolean hasKind = query.hasKind();
    boolean hasAncestor = query.hasAncestor();
    List<Filter> filters = query.filters();
    List<Order> orders = query.orders();

    if (filters.isEmpty() && orders.isEmpty()) {
      return null;
    }

    List<String> eqProps = indexOnlyQuery.getEqualityProps();
    List<Property> indexProperties = indexOnlyQuery.getIndexProps();

    if (hasKind && !eqProps.isEmpty() &&
        eqProps.size() == filters.size() &&
        !indexOnlyQuery.hasKeyProperty() &&
        orders.isEmpty()) {
      return null;
    }

    if (hasKind && !hasAncestor && indexProperties.size() <= 1 &&
        (!indexOnlyQuery.hasKeyProperty() ||
            indexProperties.get(0).getDirectionEnum() == Property.Direction.ASCENDING)) {
      return null;
    }

    Index index = new Index();
    index.setEntityType(query.getKind());
    index.setAncestor(hasAncestor);
    index.mutablePropertys().addAll(indexProperties);
    return index;
  }

  /**
   * Protected alias that allows us to make this class available to the local
   * datastore without publicly exposing it in the api.
   */
  protected static class IndexComponentsOnlyQuery
      extends com.google.appengine.api.datastore.IndexComponentsOnlyQuery {
    public IndexComponentsOnlyQuery(DatastorePb.Query query) {
      super(query);
    }
  }

  /**
   * Protected alias that allows us to make this class available to the local
   * datastore without publicly exposing it in the api.
   */
  protected static class ValidatedQuery
      extends com.google.appengine.api.datastore.ValidatedQuery {
    public ValidatedQuery(DatastorePb.Query query) {
      super(query);
    }
  }

  /**
   * Protected alias that allows us to make this class available to the local
   * datastore without publicly exposing it in the api.
   */
  protected static class KeyTranslator extends com.google.appengine.api.datastore.KeyTranslator {
    protected KeyTranslator() { }
  }
}
