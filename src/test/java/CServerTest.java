import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;


@RunWith(VertxUnitRunner.class)
public class CServerTest
{
    private Vertx vertx;
    private int mPort = 8080;
    private Logger log = LoggerFactory.getLogger(CServerTest.class);

    @Ignore
    @Before
    public void setUp(TestContext context) throws IOException
    {
        vertx = Vertx.vertx();
        vertx.deployVerticle(CServer.class.getName(), context.asyncAssertSuccess());
    }

    @Ignore
    @After
    public void tearDown(TestContext context)
    {
        vertx.close(context.asyncAssertSuccess());
    }

    @Ignore
    @Test
    public void checkInIndex(TestContext context)
    {
        log.info("*** checkInIndex ***");

        Async async = context.async();
        vertx.createHttpClient().getNow(mPort, "localhost", "/index.html", response ->
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

    @Ignore
    @Test
    public void getReply(TestContext context)
    {
        log.info("*** getReply ***");
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
}
