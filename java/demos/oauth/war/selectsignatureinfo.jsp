<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.demos.oauth.SignatureMethod" %>
<%@ page import="com.google.appengine.demos.oauth.UserInfoContainer" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<jsp:include page="header.jsp" />
<script type="text/javascript">
  function showSecretInput(sigmethod) {
    if (sigmethod == 'default') {
      document.getElementById('defaultinfo').style.display = 'block';
      document.getElementById('hmacinfo').style.display = 'none';
      document.getElementById('rsainfo').style.display = 'none';
      document.getElementById('secret').style.display = 'none';
      document.getElementById('consumerkey').style.display = 'none';
    } else {
      var secrettext = '';
      if (sigmethod == '<%= SignatureMethod.HMAC.getKey() %>') {
        secrettext = 'Consumer Secret';
        document.getElementById('hmacinfo').style.display = 'block';
        document.getElementById('rsainfo').style.display = 'none';
      } else if (sigmethod == '<%= SignatureMethod.RSA.getKey() %>') {
        secrettext = 'Private Key';
        document.getElementById('hmacinfo').style.display = 'none';
        document.getElementById('rsainfo').style.display = 'block';
      }
      document.getElementById('defaultinfo').style.display = 'none';
      document.getElementById('secrettext').innerHTML = secrettext;
      document.getElementById('secret').style.display = 'block';
      document.getElementById('consumerkey').style.display = 'block';
    }
  }
</script>
<p>OAuth requires that each request be signed using either RSA-SHA1, HMAC-SHA1
or plaintext (see
<a href="http://oauth.net/core/1.0/#signing_process" target="_blank">Section 9
of the OAuth spec</a>).  Google supports either HMAC-SHA1 or RSA-SHA1.</p>

<p>In order to use these signature methods, you must first register your domain
with Google.  You can register or view your values for the consumer key and
other signing variables here:
<a href="https://www.google.com/accounts/ManageDomains" target="_blank">https://www.google.com/accounts/ManageDomains</a>
If you don't want to register a domain, select the "default signature method"
option to use the sample credentials.</p>
<div>
<%
int tabindex = 0;
for (SignatureMethod smethod : SignatureMethod.values()) {
  out.println("<input type=\"radio\" name=\"signaturemethod\" value=\"" +  StringEscapeUtils.escapeHtml(smethod.getKey()) + "\" onclick=\"showSecretInput('" + StringEscapeUtils.escapeHtml(smethod.getKey()) + "')\" tabindex=\"" + (++tabindex) + "\" /> " +  StringEscapeUtils.escapeHtml(smethod.toString()) + "<br />");
}
%>
<input type="radio" name="signaturemethod" value="default" onclick="showSecretInput('default')" tabindex="<%= (++tabindex) %>" /> Default signature method<br />
</div>

<div id="hmacinfo" style="display:none;">
<p>Using the HMAC-SHA1 signature method requires the following fields:</p>
<p><b>Consumer Key</b>: This is the domain name you have
<a href="https://www.google.com/accounts/ManageDomains" target="_blank">previously registered
with Google</a>.</p>
<p><b>Consumer Secret</b>: This string is provided to you when you register your
domain with Google.</p>
</div>
<div id="rsainfo" style="display:none;">
<p>Using the RSA-SHA1 signature method requires the following fields:</p>
<p><b>Consumer Key</b>: This is the domain name you have
<a href="https://www.google.com/accounts/ManageDomains" target="_blank">previously registered
with Google</a>.</p>
<p><b>Private Key</b>: You must generate a public/private key pair and upload the
public key to Google.  Instructions on how to do so can be found
<a href-"http://code.google.com/apis/gdata/authsub.html#Registered">here</a>.
In order to use the private key with the Google Data Java Client, the private
key must first be converted to a Base-64 encoded PKCS#8 formatted string.  You
can do that with the following command:</p>
<blockquote>openssl pkcs8 -in myrsakey.pem -topk8 -nocrypt -out myrsakey.pk8</blockquote>
<p>The string in "myrsakey.pk8" should be used in the Private Key field below.</p>
</div>
<div id="defaultinfo" style="display:none;">
<p>The default signature method is provided by this demo so that you can continue
to use this demo without supplying your own credentials.</p>
</div>
<div id="consumerkey" style="display:none;">
Consumer Key: <input type="text" name="consumerkey" tabindex="<%= ++tabindex %>" />
</div>
<div id="secret" style="display:none;">
<span id="secrettext">Consumer Secret</span>: <input type="text" name="secret" tabindex="<%= ++tabindex %>" />
</div>
<jsp:include page="footer.jsp" />
