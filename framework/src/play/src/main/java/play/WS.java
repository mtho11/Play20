package play;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilderBase;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Realm.RealmBuilder;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import play.libs.Scala;
import play.libs.F.Promise;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Asynchronous API to to query web services, as an http client
 *
 * Usage example:
 * WS.url("http://example.com/feed").get()
 *
 * The value returned is a Promise<Response>,
 * and you should use Play's asynchronous mechanisms to use this response.
 *
 */
public class WS {

    private static AsyncHttpClient client = play.api.libs.WS.client();

    /**
     * Prepare a new request. You can then construct it by chaining calls.
     * @param url the URL to request
     */
    public static WSRequest url(String url) {
        return new WSRequest().setUrl(url);
    }

    public static class WSRequest extends RequestBuilderBase<WSRequest> {

        public WSRequest() {
            super(WSRequest.class, "GET");
        }

        /**
         * Perform a GET on the request asynchronously.
         */
        public Promise<Response> get() {
            return execute("GET");
        }

        /**
         * Perform a POST on the request asynchronously.
         */
        public Promise<Response> post() {
            return execute("POST");
        }

        /**
         * Perform a PUT on the request asynchronously.
         */
        public Promise<Response> put() {
            return execute("PUT");
        }

        /**
         * Perform a DELETE on the request asynchronously.
         */
        public Promise<Response> delete() {
            return execute("DELETE");
        }

        /**
         * Perform a HEAD on the request asynchronously.
         */
        public Promise<Response> head() {
            return execute("HEAD");
        }

        /**
         * Perform a OPTION on the request asynchronously.
         */
        public Promise<Response> option() {
            return execute("OPTION");
        }

        /**
         * Add http auth headers
         */
        public WSRequest auth(String username, String password, AuthScheme scheme) {
            this.setRealm((new RealmBuilder())
                .setScheme(scheme)
                .setPrincipal(username)
                .setPassword(password)
                .setUsePreemptiveAuth(true)
                .build());
            return this;
        }

        private Promise<Response> execute(String method) {
            final play.api.libs.concurrent.STMPromise<Response> scalaPromise = new play.api.libs.concurrent.STMPromise<Response>();
            Request request = this.setMethod(method).build();
            try {
                WS.client.executeRequest(request, new AsyncCompletionHandler<com.ning.http.client.Response>() {
                    @Override
                    public com.ning.http.client.Response onCompleted(com.ning.http.client.Response response) {
                        final com.ning.http.client.Response ahcResponse = response;
                        scalaPromise.redeem(new Scala.Function0<Response>() {
                            public Response apply() {
                                return new Response(ahcResponse);
                            }
                        });
                        return response;
                    }
                    @Override
                    public void onThrowable(Throwable t) {
                        scalaPromise.throwing(t);
                    }
                });
            } catch (IOException exception) {
                scalaPromise.throwing(exception);
            }
            return new Promise(scalaPromise);
        }
    }

    public static class Response {

        private com.ning.http.client.Response ahcResponse;

        public Response(com.ning.http.client.Response ahcResponse) {
            this.ahcResponse = ahcResponse;
        }

        /**
         * Get the HTTP status code of the response
         */
        public int getStatus() {
            return ahcResponse.getStatusCode();
        }

        /**
         * Get the given HTTP header of the response
         */
        public String getHeader(String key) {
            return ahcResponse.getHeader(key);
        }

        /**
         * Get the response body as a string
         */
        public String getBody() {
            try {
                return ahcResponse.getResponseBody();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Get the response body as a {@link Document DOM document}
         * @return a DOM document
         */
        public Document asXml() {
            try {
                return play.libs.XML.fromInputStream(ahcResponse.getResponseBodyAsStream(), "utf-8");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Get the response body as a {@link org.codehaus.jackson.JsonNode}
         * @return the json response
         */
        public JsonNode asJson() {
            String json = getBody();
            ObjectMapper mapper = new ObjectMapper();
            try {
                return mapper.readValue(json, JsonNode.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

}



