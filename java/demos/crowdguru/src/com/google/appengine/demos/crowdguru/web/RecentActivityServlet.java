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

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.demos.crowdguru.domain.Question;
import com.google.appengine.demos.crowdguru.service.QuestionService;

@SuppressWarnings("serial")
public class RecentActivityServlet extends HttpServlet {

  public void service(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    QuestionService questionService = new QuestionService();

    List<Question> answeredQuestions = questionService.getAnswered();
    request.setAttribute("answered", answeredQuestions);

    List<Question> unansweredQuestions = questionService.getUnanswered();
    request.setAttribute("unanswered", unansweredQuestions);

    getServletContext().getRequestDispatcher("/WEB-INF/jsp/template.jsp").
        forward(request, response);
  }
}
