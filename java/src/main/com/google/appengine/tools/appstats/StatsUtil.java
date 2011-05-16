// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.appstats;

import com.google.appengine.tools.appstats.StatsProtos.AggregateRpcStatsProto;
import com.google.appengine.tools.appstats.StatsProtos.IndividualRpcStatsProto;
import com.google.appengine.tools.appstats.StatsProtos.RequestStatProto;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Utility methods used by this package.
 *
 */
class StatsUtil {

  private StatsUtil(){}

  private static final double MCYCLES_PER_SECOND = 1200.0;

  private static final Comparator<List<Object>> BY_SECOND_ENTRY = makeComparator(1, 0);
  private static final Comparator<List<Object>> BY_FIRST_ENTRY = makeComparator(0, 1);
  private static final Comparator<Entry<String, Long>> BY_ENTRY_VALUE =
      new Comparator<Map.Entry<String, Long>>(){
        @Override
        public int compare(Entry<String, Long> o1, Entry<String, Long> o2) {
          int c = -o1.getValue().compareTo(o2.getValue());
          if (c == 0) {
            c = o1.getKey().compareTo(o2.getKey());
          }
          return c;
        }};

  private static String extractKey(RequestStatProto summary) {
    String result = summary.getHttpPath();
    if (!summary.getHttpMethod().equals("GET")) {
      result = summary.getHttpMethod() + " " + result;
    }
    return result;
  }

  static long megaCyclesToMilliseconds(long megaCycles) {
    return (long) (1000 * megaCycles / MCYCLES_PER_SECOND);
  }

  private static Map<String, Object> toMap(Message proto) {
    Map<String, Object> result = new HashMap<String, Object>();
    for (FieldDescriptor field : proto.getDescriptorForType().getFields()) {
      Object value = proto.getField(field);
      if (value != null) {
        result.put(field.getName(), value);
        if (value instanceof List) {
          result.put(field.getName() + "_list", value);
          result.put(field.getName() + "_size", ((List<?>) value).size());
        }
      }
    }
    return result;
  }

  private static Map<String, Object> augmentProto(RequestStatProto proto) {
    Map<String, Object> result = toMap(proto);
    result.put("api_milliseconds",
        megaCyclesToMilliseconds(proto.getApiMcycles()));
    result.put("processor_milliseconds",
        megaCyclesToMilliseconds(proto.getProcessorMcycles()));
    result.put("start_time_formatted", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        .format(new Date(proto.getStartTimestampMilliseconds())));
    List<Object> sublist = new ArrayList<Object>();
    for (IndividualRpcStatsProto stat : proto.getIndividualStatsList()) {
      Map<String, Object> subMap = toMap(stat);
      subMap.put("api_milliseconds",
          megaCyclesToMilliseconds(stat.getApiMcycles()));
      sublist.add(subMap);
    }
    result.put("individual_stats", sublist);
    result.put("individual_stats_list", sublist);
    long count = 0;
    for (AggregateRpcStatsProto stat : proto.getRpcStatsList()) {
      count += stat.getTotalAmountOfCalls();
    }
    result.put("combined_rpc_count", count);
    return result;
  }

  private static List<Map.Entry<String, Long>> order(Map<String, Long> elements) {
    List<Map.Entry<String, Long>> result =
        new ArrayList<Map.Entry<String, Long>>(elements.entrySet());
    Collections.sort(result, BY_ENTRY_VALUE);
    return result;
  }

  private static Comparator<List<Object>> makeComparator(final int... fieldOrder) {
    return new Comparator<List<Object>>() {
      @SuppressWarnings("unchecked")
      @Override
      public int compare(List<Object> o1, List<Object> o2) {
        int result = 0;
        for (int i : fieldOrder) {
          Comparable c1 = (Comparable) o1.get(i);
          Comparable c2 = (Comparable) o2.get(i);
          result = (c1 instanceof Number) ? -c1.compareTo(c2) : c1.compareTo(c2);
          if (result != 0) {
            return result;
          }
        }
        return result;
      }
    };
  }

  static Map<String, Object> createSummaryStats(List<RequestStatProto> records) {
    records = new ArrayList<RequestStatProto>(records);
    Collections.sort(records, new Comparator<RequestStatProto>(){
      @Override
      public int compare(RequestStatProto o1, RequestStatProto o2) {
        Long l1 = o1.getStartTimestampMilliseconds();
        Long l2 = o2.getStartTimestampMilliseconds();
        return -l1.compareTo(l2);
      }});
    List<Map<String, Object>> augmented = new ArrayList<Map<String, Object>>();
    for (RequestStatProto proto : records) {
      augmented.add(augmentProto(proto));
    }
    Map<String, List<Integer>> pathStats = new HashMap<String, List<Integer>>();
    Map<String, Map<String, Long>> pivotPathRpc = new HashMap<String, Map<String, Long>>();
    Map<String, Map<String, Long>> pivotRpcPath = new HashMap<String, Map<String, Long>>();
    Map<String, Long> allStats = new HashMap<String, Long>();
    for (int i = 0; i < records.size(); i++) {
      String pathKey = extractKey(records.get(i));

      if (!pathStats.containsKey(pathKey)) {
        List<Integer> list = new ArrayList<Integer>();
        list.add(1);
        list.add(i + 1);
        pathStats.put(pathKey, list);
      } else {
        List<Integer> list = pathStats.get(pathKey);
        list.set(0, list.get(0) + 1);
        if (list.size() >= 11) {
          if (list.get(list.size() - 1) != 0) {
            list.add(0);
          }
        } else {
          list.add(i + 1);
        }
      }

      if (!pivotPathRpc.containsKey(pathKey)) {
        pivotPathRpc.put(pathKey, new HashMap<String, Long>());
      }
      for (AggregateRpcStatsProto stat : records.get(i).getRpcStatsList()) {
        String rpcKey = stat.getServiceCallName();

        long previous = allStats.containsKey(rpcKey) ? allStats.get(rpcKey) : 0;
        allStats.put(rpcKey, previous + stat.getTotalAmountOfCalls());

        Map<String, Long> pathRpc = pivotPathRpc.get(pathKey);
        previous = pathRpc.containsKey(rpcKey) ? pathRpc.get(rpcKey) : 0;
        pathRpc.put(rpcKey, previous + stat.getTotalAmountOfCalls());

        if (!pivotRpcPath.containsKey(rpcKey)) {
          pivotRpcPath.put(rpcKey, new HashMap<String, Long>());
        }
        Map<String, Long> rpcPath = pivotRpcPath.get(rpcKey);
        previous = rpcPath.containsKey(pathKey) ? rpcPath.get(pathKey) : 0;
        rpcPath.put(pathKey, previous + stat.getTotalAmountOfCalls());
      }
    }

    List<List<Object>> allStatsByName = new ArrayList<List<Object>>();
    for (Map.Entry<String, Long> stat : allStats.entrySet()) {
      List<Map.Entry<String, Long>> ordered = order(pivotRpcPath.get(stat.getKey()));
      List<Object> tuple = new ArrayList<Object>();
      tuple.add(stat.getKey());
      tuple.add(stat.getValue());
      tuple.add(ordered);
      allStatsByName.add(tuple);
    }
    Collections.sort(allStatsByName, BY_FIRST_ENTRY);
    List<List<Object>> allStatsByCount = new ArrayList<List<Object>>(allStatsByName);
    Collections.sort(allStatsByCount, BY_SECOND_ENTRY);
    List<List<Object>> pathStatsByName = new ArrayList<List<Object>>();
    for (Entry<String, List<Integer>> stat : pathStats.entrySet()) {
      List<Map.Entry<String, Long>> ordered = order(pivotPathRpc.get(stat.getKey()));
      Integer elem0 = stat.getValue().remove(0);
      long rpcCount = 0;
      for (Map.Entry<String, Long> rpc : ordered) {
        rpcCount += rpc.getValue();
      }
      List<Object> tuple = new ArrayList<Object>();
      tuple.add(stat.getKey());
      tuple.add(rpcCount);
      tuple.add(elem0);
      tuple.add(stat.getValue());
      tuple.add(ordered);
      pathStatsByName.add(tuple);
    }
    Collections.sort(pathStatsByName, BY_FIRST_ENTRY);
    List<List<Object>> pathStatsByCount = new ArrayList<List<Object>>(pathStatsByName);
    Collections.sort(pathStatsByCount, BY_SECOND_ENTRY);

    Map<String, Object> values = new HashMap<String, Object>();
    values.put("requests", augmented);
    values.put("allstats_by_count", allStatsByCount);
    values.put("pathstats_by_count", pathStatsByCount);
    return values;
  }

  static Map<String, Object> createDetailedStats(RequestStatProto data) {
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put("record", augmentProto(data));
    parameters.put("file_url", false);

    Map<String, List<Long>> rpcStatsMap = new HashMap<String, List<Long>>();
    for (IndividualRpcStatsProto rpcStat : data.getIndividualStatsList()) {
      String key = rpcStat.getServiceCallName();
      List<Long> tuple = rpcStatsMap.get(key);
      if (tuple == null) {
        tuple = new ArrayList<Long>();
        tuple.add(0L);
        tuple.add(0L);
        tuple.add(0L);
        rpcStatsMap.put(key, tuple);
      }
      tuple.set(0, tuple.get(0) + 1);
      tuple.set(1, tuple.get(1) + rpcStat.getDurationMilliseconds());
      tuple.set(2, tuple.get(2) + rpcStat.getApiMcycles());
    }
    List<List<Object>> rpcStatsByCount = new ArrayList<List<Object>>();
    for (Map.Entry<String, List<Long>> entry : rpcStatsMap.entrySet()) {
      List<Object> l = new ArrayList<Object>();
      l.add(entry.getKey());
      l.add(entry.getValue().get(0));
      l.add(entry.getValue().get(1));
      l.add(megaCyclesToMilliseconds(entry.getValue().get(2)));
      rpcStatsByCount.add(l);
    }
    Collections.sort(rpcStatsByCount, BY_SECOND_ENTRY);
    parameters.put("rpcstats_by_count", rpcStatsByCount);

    long realTotal = 0;
    long apiTotalMcycles = 0;
    for (IndividualRpcStatsProto stat : data.getIndividualStatsList()) {
      realTotal += stat.getDurationMilliseconds();
      apiTotalMcycles += stat.getApiMcycles();
    }
    long apiTotal = megaCyclesToMilliseconds(apiTotalMcycles);
    long chargedTotal = megaCyclesToMilliseconds(
        data.getProcessorMcycles() + apiTotalMcycles);
    parameters.put("real_total", realTotal);
    parameters.put("api_total", apiTotal);
    parameters.put("charged_total", chargedTotal);
    return parameters;
  }

}
