/* Copyright (c) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.appengine.demos.crowdguru.service;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.xmpp.JID;
import com.google.appengine.demos.crowdguru.PMF;
import com.google.appengine.demos.crowdguru.domain.Question;

import java.util.Date;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;

public class QuestionService {

  // The maximum amount of time a user has to answer a given question before
  // another user can be assigned the question, in milliseconds --
  // 120,000 ms = 2 minutes
  private static final int MAX_ANSWER_TIME = 120000;

  /**
   * Gets an unanswered question and assigns it to a user to answer.
   *
   * @param  key
   * @param  user The identity of the user to assign a question to.
   * @return The Question entity assigned to the user or null if there are no
   *         unanswered questions.
   */
  @SuppressWarnings("unchecked")
  public Question assignQuestion(JID user) {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Question question = null;

    // Typically the loop will only run once or twice, but if many people try
    // to grab the same question at the same time, it's possible we'll have to
    // try a few times before we succeed.
    while (question == null || !question.getAssignees().contains(
        user.getId())) {
      // Assignments made before this timestamp have expired.
      Date expiry = new Date(new Date().getTime() - MAX_ANSWER_TIME);

      // Find a candidate question
      Query query = pm.newQuery(Question.class, "answerer == null && " +
          "lastAssigned < dateParam");
      query.declareParameters("java.util.Date dateParam");
      // If a question has never been assigned, order by when it was asked
      query.setOrdering("lastAssigned asc, asked asc");
      query.setRange(0, 2);

      List<Question> candidates = (List<Question>) query.execute(expiry);
      for (Question candidate : candidates) {
        if (candidate.getAsker().equals(user.getId())) {
          candidates.remove(candidate);
        }
      }

      if (candidates.size() == 0) {
        // No valid questions in queue.
        return null;
      }

      // Try and assign it
      question = assign(candidates.get(0).getKey(), user, expiry);
    }

    pm.close();

    // Expire the assignment after a couple of minutes
    return question;
  }

  /**
   * Assigns and returns the question if it's not assigned already.
   *
   * @param  key The key of a Question to try and assign.
   * @param  user The user to assign the question to.
   * @return The Question object. If it was already assigned, no change is
   *         made.
   */
  private Question assign(Key key, JID user, Date expiry) {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Transaction tx = pm.currentTransaction();

    Question question = null;

    try {
      tx.begin();

      question = pm.getObjectById(Question.class, key);

      if (question.getLastAssigned() == null ||
          question.getLastAssigned().before(expiry)) {
        question.getAssignees().add(user.getId());
        question.setLastAssigned(new Date());
        pm.makePersistent(question);
      }

      question = pm.detachCopy(question);
      tx.commit();
    } finally {
      if (tx.isActive()) {
        tx.rollback();
      }
    }

    return question;
  }

  /**
   * Unassigns the given user to this question.
   *
   * @param key  The datastore key of the Question to be updated.
   * @param user The user who will no longer be answering this question.
   */
  public void unassign(Key key, JID user) {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Transaction tx = pm.currentTransaction();

    try {
      tx.begin();

      Question question = pm.getObjectById(Question.class, key);

      if (question.getAssignees().contains(user.getId())) {
        question.getAssignees().remove(user.getId());
        pm.makePersistent(question);
      }

      tx.commit();
    } finally {
      if (tx.isActive()) {
        tx.rollback();
      }
    }
  }

  /**
   * Returns the user's outstanding asked question, if any.
   *
   * @param user
   * @return
   */
  @SuppressWarnings("unchecked")
  public Question getAsked(JID user) {
    PersistenceManager pm = PMF.get().getPersistenceManager();

    Query query = pm.newQuery(Question.class,
        "asker == userParam && answer == null");
    query.declareParameters("String userParam");
    query.setRange(0, 1);

    List<Question> results = null;
    try {
      results = (List<Question>) query.execute(user.getId());
      results = (List<Question>) pm.detachCopyAll(results);
    } finally {
      pm.close();
    }

    if (results.size() == 0) {
      return null;
    }

    return results.get(0);
  }

  /**
   * Returns the question the user is answering, if any.
   *
   * @param user
   * @return
   */
  @SuppressWarnings("unchecked")
  public Question getAnswering(JID user) {
    PersistenceManager pm = PMF.get().getPersistenceManager();

    Query query = pm.newQuery(Question.class,
      "assignees.contains(userParam) && answer == null");
    query.declareParameters("String userParam");
    query.setRange(0, 1);

    List<Question> results = null;
    try {
      results = (List<Question>) query.execute(user.getId());
      results = (List<Question>) pm.detachCopyAll(results);
    } finally {
      pm.close();
    }

    if (results.size() == 0) {
      return null;
    }

    return results.get(0);
  }

  public List<Question> getAnswered() {
    PersistenceManager pm = PMF.get().getPersistenceManager();

    Query query = pm.newQuery(Question.class, "answer > null");
    query.setOrdering("answer asc, asked desc");
    query.setRange(0, 10);

    List<Question> results = null;
    try {
      results = (List<Question>) query.execute();
      results = (List<Question>) pm.detachCopyAll(results);
    } finally {
      pm.close();
    }

    return results;
  }

  public List<Question> getUnanswered() {
    PersistenceManager pm = PMF.get().getPersistenceManager();

    Query query = pm.newQuery(Question.class, "answer == null");
    query.setOrdering("asked desc");
    query.setRange(0, 10);

    List<Question> results = null;
    try {
      results = (List<Question>) query.execute();
      results = (List<Question>) pm.detachCopyAll(results);
    } finally {
      pm.close();
    }

    return results;
  }

  /**
   * Persists passed Question object.
   *
   * @param question Question object to persist
   */
  public void storeQuestion(Question question) {
    PersistenceManager pm = PMF.get().getPersistenceManager();

    try {
      pm.makePersistent(question);
    } finally {
      pm.close();
    }
  }
}
