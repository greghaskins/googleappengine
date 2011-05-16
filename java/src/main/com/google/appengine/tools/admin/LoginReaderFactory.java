package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.AppAdminFactory.ConnectOptions;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

public class LoginReaderFactory {

  private static final String PASSWORD_ECHO_WARNING_MAC =
  "Warning: in Java 1.5, there is no reliable way to prevent your password\n"
  + "from displaying to the screen.  You should consider using the --passin\n"
  + "argument, and read the password either from a script or file.  Java 1.6\n"
  + "can also resolve this, if you are not using GWT.  (Java 1.6 and GWT\n"
  + "hosted mode have known incompatibilities on Mac OX X.)\n";

  private static final String PASSWORD_ECHO_WARNING_NOTMAC =
  "Warning: in Java 1.5, there is no reliable way to prevent your password\n"
  + "from displaying to the screen.  You should consider either using the \n"
  + "--passin argument, and read the password either from a script or file,\n"
  + "or upgrade to Java 1.6.\n";

  private static final String SWING_DIALOG_WARNING =
  "Please sign in. If you cannot see the Swing dialog box you should consider using\n"
  + "the --passin argument, and read the password either from a script or file.";

  public static class SimpleLoginReader implements LoginReader {
    protected int count = 0;
    private String email;
    private String password;
    private ConnectOptions options;
  
    public SimpleLoginReader(ConnectOptions options) {
      this.options = options;
    }
    
    public void doPrompt() {
      email = options.getUserId();
      if (email == null || count > 0) {
        email = promptForEmail("Email: ");
      }
      password = promptForPassword("Password for " + email + ": ");
      count += 1;
    }
  
    protected String promptForEmail(String prompt) {
      return prompt(prompt);
    }
  
    protected String promptForPassword(String prompt) {
      return prompt(prompt);
    }
  
    protected String prompt(String prompt) {
      System.out.print(prompt);
      System.out.flush();
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
       try {
         return in.readLine();
       } catch (IOException ioe) {
         return null;
       }
    }
  
    public String getUsername() {
      return email;
    }
  
    public String getPassword() {
      return password;
    }
  
  }

  public static class ConsoleReader extends SimpleLoginReader {
    private java.io.Console console = System.console();
  
    public ConsoleReader(ConnectOptions options) {
      super(options);
    }

    protected String promptForUsername(String prompt) {
      return console.readLine(prompt);
    }
  
    @Override
    public String promptForPassword(String prompt) {
      return new String(console.readPassword(prompt));
    }
  
    public boolean hasConsole() {
      return console != null;
    }
  }

  public static class PassinReader extends SimpleLoginReader {
    public PassinReader(ConnectOptions options) {
      super(options);
    }

    @Override
    public void doPrompt() {
      if (count == 0) {
        super.doPrompt();
      }
    }
  }

  public static class SwingReader implements LoginReader {
    private String email;
    private String password;
    private ConnectOptions options;
    private String prefsEmail;
    
    public SwingReader(ConnectOptions options, String prefsEmail) {
      if (GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance()) {
        throw new RuntimeException("Cannot use SwingReader in headless mode.");
      }
      this.options = options;
      this.prefsEmail = prefsEmail;
    }
  
    public String getUsername() {
      return email;
    }
  
    public String getPassword() {
      return password;
    }
    
    public void doPrompt() {
      System.out.println(SWING_DIALOG_WARNING);

      email = options.getUserId();
      String initialEmail = email == null ? prefsEmail : email;
      final JTextField emailField = new JTextField(initialEmail, 30);
      JPasswordField passwordField = new JPasswordField(30);
      JOptionPane pane = new JOptionPane(
          new Object[]{new JLabel("Email:"), emailField,
                       new JLabel("Password:"), passwordField},
          JOptionPane.QUESTION_MESSAGE,
          JOptionPane.OK_CANCEL_OPTION);
      JDialog dialog = pane.createDialog(null, "Please Sign In");
      emailField.addAncestorListener(new AncestorListener() {
          public void ancestorAdded(AncestorEvent event) {
            emailField.selectAll();
            emailField.requestFocus();
          }
          public void ancestorMoved(AncestorEvent event) {
          }
          public void ancestorRemoved(AncestorEvent event) {
          }
        });
      dialog.setVisible(true);
      int result = (Integer)pane.getValue();
      dialog.dispose();
      if (result == JOptionPane.CANCEL_OPTION) {
        System.exit(1);
      } else if (result == JOptionPane.OK_OPTION) {
        email = emailField.getText();
        password = new String(passwordField.getPassword());
      }
    }

  }

  public static LoginReader createLoginReader(ConnectOptions options, boolean passin,
                                              String prefsEmail) {
    if (passin) {
      return new PassinReader(options);
    } else {
      try {
        ConsoleReader reader = new ConsoleReader(options);
        if (reader.hasConsole()) {
          return reader;
        }
      } catch (NoSuchMethodError ex) {
        try {
          return new SwingReader(options, prefsEmail);
        } catch (Throwable t) {
          System.out.println(System.getProperty("os.name").startsWith("Mac ")
              ? PASSWORD_ECHO_WARNING_MAC : PASSWORD_ECHO_WARNING_NOTMAC);
        }
      }
    }
    return new SimpleLoginReader(options); 
  }

}