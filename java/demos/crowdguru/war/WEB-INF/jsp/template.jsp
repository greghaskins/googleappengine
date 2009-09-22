<%@ page isELIgnored="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<html>
  <head>
    <title>Crowd Guru: recent activity</title>
  </head>
  <body>
    <h1>Recently answered</h1>
    <ul>
      <c:forEach items="${answered}" var="question">
        <li>
          <c:out value="${question.question}"/>
        </li>
      </c:forEach>
    </ul>

    <h1>Awaiting an answer test</h1>
    <ul>
      <c:forEach items="${unanswered}" var="question">
        <li>
          <c:out value="${question.question}"/>
        </li>
      </c:forEach>
    </ul>
  </body>
</html>