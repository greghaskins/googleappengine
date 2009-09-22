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

package com.google.appengine.demos.crowdguru.web;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;
import com.google.appengine.demos.crowdguru.domain.Question;
import com.google.appengine.demos.crowdguru.service.QuestionService;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class XmppReceiverServlet extends HttpServlet {

  private static final XMPPService xmppService =
      XMPPServiceFactory.getXMPPService();

  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Message message = xmppService.parseMessage(request);

    if (message.getBody().startsWith("/askme")) {
      handleAskMeCommand(message);
    } else if (message.getBody().startsWith("/tellme ")) {
      String questionText = message.getBody().replaceFirst("/tellme ", "");
      handleTellMeCommand(message, questionText);
    } else if (message.getBody().startsWith("/")) {
      handleUnrecognizedCommand(message);
    } else {
      handleAnswer(message);
    }
  }

  private void handleAskMeCommand(Message message) {
    QuestionService questionService = new QuestionService();
    JID sender = message.getFromJid();

    Question currentlyAnswering = questionService.getAnswering(sender);
    Question newlyAssigned = questionService.assignQuestion(sender);

    if (newlyAssigned != null) {
      // Don't un-assign their current question until we've picked a new one.
      if (currentlyAnswering != null) {
        questionService.unassign(currentlyAnswering.getKey(), sender);
      }

      replyToMessage(message, "While I'm thinking, perhaps you can answer " +
          "me this: " + newlyAssigned.getQuestion());
    } else {
      replyToMessage(message, "Sorry, I don't have anything to ask you at " +
          "the moment.");
    }
  }

  private void handleTellMeCommand(Message message, String questionText) {
    QuestionService questionService = new QuestionService();
    JID sender = message.getFromJid();

    Question currentlyAnswering = questionService.getAnswering(sender);
    Question previouslyAsked = questionService.getAsked(sender);

    if (previouslyAsked != null) {
      // Already have a question
      replyToMessage(message, "Please! One question at a time! You can ask " +
          "me another once you have an answer to your current question.");
      return;
    } else {
      // Asking a question
      Question question = new Question();
      question.setQuestion(questionText);
      question.setAsked(new Date());
      question.setAsker(sender);

      questionService.storeQuestion(question);

      if (currentlyAnswering == null) {
        // Try and find one for them to answer
        Question newlyAssigned = questionService.assignQuestion(sender);

        if (newlyAssigned != null) {
          replyToMessage(message, "While I'm thinking, perhaps you can " +
              "answer me this: " + newlyAssigned.getQuestion());
          return;
        }
      }

      replyToMessage(message, "Hmm. Let me think on that a bit.");
    }
  }

  private void handleUnrecognizedCommand(Message message) {
    // Show help text
    replyToMessage(message, "I am the amazing Crowd Guru. Ask me a question " +
        "by typing '/tellme the meaning of life', and I will answer you " +
        "forthwith! If you just want me to ask you a question, type " +
        "'/askme'.");
  }

  private void handleAnswer(Message message) {
    QuestionService questionService = new QuestionService();
    JID sender = message.getFromJid();

    Question currentlyAnswering = questionService.getAnswering(sender);

    if (currentlyAnswering != null) {
      List<String> otherAssignees = currentlyAnswering.getAssignees();
      otherAssignees.remove(sender.getId());

      // Answering a question
      currentlyAnswering.getAssignees().clear();
      currentlyAnswering.setAnswer(message.getBody());
      currentlyAnswering.setAnswered(new Date());
      currentlyAnswering.setAnswerer(sender);

      questionService.storeQuestion(currentlyAnswering);

      // Send the answer to the asker
      sendMessage(currentlyAnswering.getAsker(), "You asked me: " +
          currentlyAnswering.getQuestion());
      sendMessage(currentlyAnswering.getAsker(), "I have thought long and " +
          "hard, and concluded: " + currentlyAnswering.getAnswer());

      // Send acknowledgment to the answerer
      Question previouslyAsked = questionService.getAsked(sender);

      if (previouslyAsked != null) {
        replyToMessage(message, "Thank you for your wisdom. I'm still " +
            "thinking about your question.");
      } else {
        replyToMessage(message, "Thank you for your wisdom.");
      }

      // Tell any other assignees their help is no longer required
      sendMessage(otherAssignees, "We seek those who are wise and fast. One " +
          "out of two is not enough. Another has answered my question.");
    } else {
      handleUnrecognizedCommand(message);
    }
  }

  private void replyToMessage(Message message, String body) {
    Message reply = new MessageBuilder()
        .withRecipientJids(message.getFromJid())
        .withMessageType(MessageType.NORMAL)
        .withBody(body)
        .build();

    xmppService.sendMessage(reply);
  }

  private void sendMessage(String recipient, String body) {
    sendMessage(new JID[] {new JID(recipient)}, body);
  }

  private void sendMessage(List<String> recipientList, String body) {
    sendMessage((JID[]) recipientList.toArray(), body);
  }

  private void sendMessage(JID[] recipients, String body) {
    Message message = new MessageBuilder()
    .withRecipientJids(recipients)
    .withMessageType(MessageType.NORMAL)
    .withBody(body)
    .build();

    xmppService.sendMessage(message);
  }
}
