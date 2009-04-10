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

package com.google.appengine.demos.autoshoppe;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.users.UserService;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.servlet.http.HttpServletRequest;

/**
 * An AOP method advice on AutoController. ( Pointcut .*)
 *
 * @author zoso@google.com (Anirudh Dewani)
 */
public class AuditInterceptor implements MethodInterceptor,
    ApplicationContextAware {

  private ApplicationContext applicationContext;

  /**
   * An audit advice to the proxy bean based on a MethodInterceptor that
   * wraps up the method invocation. It adds an audit record in the datastore
   * for all actions that invoke DispatcherServlet's 'handleRequest'.
   *
   * @param methodInvocation MethodInvocation object
   * @return the proxied method return object
   *
   * @throws Throwable
   */
  public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    long start = System.currentTimeMillis();
    Object returnParam = methodInvocation.proceed();
    long stop = System.currentTimeMillis();
    if (methodInvocation.getMethod().getName().equals("handleRequest")) {
      Object[] params = methodInvocation.getArguments();
      HttpServletRequest req = (HttpServletRequest) params[0];
      UserService userService = (UserService) applicationContext.getBean(
          AutoShoppeConstants.BEAN_USERSERVICE);
      DatastoreService dsService = (DatastoreService)
          applicationContext.getBean(AutoShoppeConstants.BEAN_DSSERVICE);

      Entity audit = new Entity(AutoShoppeConstants.AUDIT_ENTITY);
      if (userService.isUserLoggedIn()) {
        audit.setProperty(AutoShoppeConstants.AUTH_USER,
            userService.getCurrentUser().getEmail());
      }
      audit.setProperty("IP", req.getRemoteAddr());
      audit.setProperty("time", stop - start);
      audit.setProperty("method", req.getRequestURI().substring(
          req.getRequestURI().lastIndexOf('/') + 1,
          req.getRequestURI().lastIndexOf('.')));
      audit.setProperty("logtime", System.currentTimeMillis());
      dsService.put(audit);
    }
    return returnParam;
  }

  /**
   * Spring injected dependency.
   * ApplicationContext is injected by spring.
   *
   * @param applicationContext Spring application context
   * @throws BeansException if BeanFactory fails
   */
  public void setApplicationContext(ApplicationContext applicationContext)
      throws BeansException {
    this.applicationContext = applicationContext;
  }
}

