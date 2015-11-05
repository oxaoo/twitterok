import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import org.junit.Ignore;
import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;


/**
 * Created by Alexander on 05.11.2015.
 */

@RunWith(VertxUnitRunner.class)
public class CServerTest
{
    private Vertx vertx;
    private int m_port = 8080;
    private Logger m_log = LoggerFactory.getLogger(CServerTest.class);

    @Before
    public void setUp(TestContext context) throws IOException
    {
        vertx = Vertx.vertx();
        vertx.deployVerticle(CServer.class.getName(), context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context)
    {
        vertx.close(context.asyncAssertSuccess());
    }

    //@Ignore
    @Test
    public void checkInIndex(TestContext context)
    {
        LoggerFactory.getLogger(CServer.class).info("*** checkInIndex ***");

        Async async = context.async();
        vertx.createHttpClient().getNow(m_port, "localhost", "/index.html", response ->
        {
            context.assertEquals(response.statusCode(), 200); //страница загружена.
            context.assertEquals(response.headers().get("content-type"), "text/html"); //формат index - text/html.
            response.bodyHandler(body ->
            {
                //страница содержит заголовок Twitterok.
                context.assertTrue(body.toString().contains("<title>Twitterok</title>"));
                async.complete();
            });
        });
    }

    //@Ignore
    @Test
    public void getReply(TestContext context)
    {
        m_log.info("*** getReply ***");
        Async async = context.async();

        EventBus eb = vertx.eventBus();

        eb.consumer("chat.to.server").handler(message ->
        {
            String getMsg = message.body().toString();
            context.assertEquals(getMsg, "hello");
            async.complete();
        });

        eb.publish("chat.to.server", "hello");
    }

    //@Ignore
    @Test
    public void stressTest(TestContext context)
    {
        m_log.info("*** stressTest ***");
        Async async = context.async();

        EventBus eb = vertx.eventBus();

        int iter = 1000000;
        final int[] counter = {iter};
        String msg = "hello";

        eb.consumer("chat.to.server").handler(message ->
        {
            String getMsg = message.body().toString();
            m_log.debug("New message: " + getMsg);
            if (counter[0] % 100000 == 0)
                m_log.info("Counter=" + counter[0]);

            context.assertEquals(getMsg, msg + counter[0]);
            counter[0]--;

            JSONObject jmsg = new JSONObject();
            jmsg.put("type", "publish");
            jmsg.put("count", "-1");
            jmsg.put("time", "n/a");
            jmsg.put("addr", "test");
            jmsg.put("message", getMsg);
            eb.publish("chat.to.client", jmsg.toJSONString());

            if (counter[0] <= 0)
                async.complete();
        });

        for (int i = 0; i < iter; i++)
        {
            eb.publish("chat.to.server", msg + (iter - i));
        }
    }

}
